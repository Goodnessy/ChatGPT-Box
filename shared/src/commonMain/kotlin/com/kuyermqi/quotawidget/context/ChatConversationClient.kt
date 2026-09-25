package com.kuyermqi.quotawidget.context

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ChatConversationClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchMostRecentConversation(
        accessToken: String,
        accountId: String,
    ): ConversationContextData {
        val listBody = getText(
            url = "$BASE_URL/backend-api/conversations?offset=0&limit=1&order=updated",
            accessToken = accessToken,
            accountId = accountId,
        )
        val listRoot = json.parseToJsonElement(listBody) as? JsonObject
            ?: throw IllegalStateException("无法解析 ChatGPT 对话列表")
        val item = (listRoot["items"] as? JsonArray)
            ?.firstOrNull() as? JsonObject
            ?: throw IllegalStateException("未找到可分析的 ChatGPT 对话")

        val conversationId = item.string("id")
            ?: throw IllegalStateException("无法识别最近对话")
        val listTitle = item.string("title").orEmpty()

        val detailBody = getText(
            url = "$BASE_URL/backend-api/conversation/$conversationId",
            accessToken = accessToken,
            accountId = accountId,
        )
        val detail = json.parseToJsonElement(detailBody) as? JsonObject
            ?: throw IllegalStateException("无法解析 ChatGPT 对话")
        val title = detail.string("title").orEmpty().ifBlank { listTitle }.ifBlank { "当前对话" }

        val mapping = detail["mapping"] as? JsonObject ?: JsonObject(emptyMap())
        val messages = mapping.values.mapNotNull(::parseMessage)
            .sortedBy { it.orderKey }
            .map { it.message }

        val updatedAtMs = detail["update_time"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toDoubleOrNull()
            ?.let { seconds -> (seconds * 1000.0).toLong() }
            ?: currentTimeMillis()

        return ConversationContextData(
            conversationId = conversationId,
            title = title,
            messages = messages,
            updatedAtEpochMs = updatedAtMs,
        )
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun getText(
        url: String,
        accessToken: String,
        accountId: String,
    ): String {
        return try {
            httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("ChatGPT-Account-Id", accountId)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Origin, "https://chatgpt.com")
                header(HttpHeaders.Referrer, "https://chatgpt.com/")
            }.bodyAsText()
        } catch (e: ClientRequestException) {
            when (e.response.status.value) {
                401, 403 -> throw IllegalStateException("ChatGPT 登录状态无法读取对话，请先刷新额度或重新登录", e)
                else -> throw IllegalStateException("读取 ChatGPT 对话失败（${e.response.status.value}）", e)
            }
        }
    }

    private fun parseMessage(element: JsonElement): ParsedMessage? {
        val node = element as? JsonObject ?: return null
        val message = node["message"] as? JsonObject ?: return null
        val role = (message["author"] as? JsonObject)?.string("role").orEmpty()
        if (role != "user" && role != "assistant") return null

        val content = message["content"] as? JsonObject
        val text = content?.let(::extractText).orEmpty()
        val partImages = content?.let(::countImageParts) ?: 0
        val metadata = message["metadata"] as? JsonObject
        val attachments = metadata?.get("attachments") as? JsonArray
        var attachmentImages = 0
        var files = 0
        attachments?.forEach { attachment ->
            val obj = attachment as? JsonObject ?: return@forEach
            val mime = obj.string("mime_type")
                ?: obj.string("mimeType")
                ?: obj.string("content_type")
                ?: ""
            if (mime.startsWith("image/", ignoreCase = true)) {
                attachmentImages++
            } else {
                files++
            }
        }

        val createTime = message["create_time"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toDoubleOrNull()
            ?: 0.0

        return ParsedMessage(
            orderKey = createTime,
            message = ConversationMessage(
                role = role,
                text = text,
                imageCount = maxOf(partImages, attachmentImages),
                fileCount = files,
            ),
        )
    }

    private fun extractText(content: JsonObject): String {
        val parts = content["parts"] as? JsonArray
        if (parts != null) {
            return parts.mapNotNull(::extractPartText)
                .filter(String::isNotBlank)
                .joinToString("\n")
        }
        return content.string("text").orEmpty()
    }

    private fun extractPartText(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> {
            element.string("text")
                ?: element.string("content")
                ?: (element["parts"] as? JsonArray)
                    ?.mapNotNull(::extractPartText)
                    ?.joinToString("\n")
        }
        else -> null
    }

    private fun countImageParts(content: JsonObject): Int {
        val parts = content["parts"] as? JsonArray ?: return 0
        return parts.sumOf(::countImagesRecursively)
    }

    private fun countImagesRecursively(element: JsonElement): Int = when (element) {
        is JsonObject -> {
            val contentType = element.string("content_type")
                ?: element.string("contentType")
                ?: ""
            val pointer = element.string("asset_pointer").orEmpty()
            val self = if (
                contentType.contains("image", ignoreCase = true) ||
                pointer.startsWith("sediment://") ||
                pointer.startsWith("file-service://")
            ) 1 else 0
            self + ((element["parts"] as? JsonArray)?.sumOf(::countImagesRecursively) ?: 0)
        }
        is JsonArray -> element.sumOf(::countImagesRecursively)
        else -> 0
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private data class ParsedMessage(
        val orderKey: Double,
        val message: ConversationMessage,
    )

    companion object {
        private const val BASE_URL = "https://chatgpt.com"
        private const val USER_AGENT = "ChatGPT-Box/0.2"
    }
}
