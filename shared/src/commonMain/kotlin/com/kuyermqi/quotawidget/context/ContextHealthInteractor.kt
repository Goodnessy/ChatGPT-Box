package com.kuyermqi.quotawidget.context

import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

sealed interface ContextHealthRefreshResult {
    data class Ready(
        val snapshot: ContextHealthSnapshot,
        val migrationPrompt: String,
    ) : ContextHealthRefreshResult

    data class Unavailable(val reason: String) : ContextHealthRefreshResult
    data class Error(val message: String) : ContextHealthRefreshResult
}

class ContextHealthInteractor(
    private val settingsRepository: PlatformSettingsRepository,
    private val conversationClient: ChatConversationClient = ChatConversationClient(),
) {
    suspend fun refresh(): ContextHealthRefreshResult {
        val settings = settingsRepository.getCodexSettings()
        if (!settings.isConfigured) {
            return ContextHealthRefreshResult.Unavailable("登录 ChatGPT 后可估算上下文")
        }

        return try {
            val conversation = conversationClient.fetchMostRecentConversation(
                accessToken = settings.accessToken,
                accountId = settings.accountId,
            )
            val snapshot = estimateContextHealth(conversation)
            settingsRepository.saveContextHealth(snapshot)
            ContextHealthRefreshResult.Ready(
                snapshot = snapshot,
                migrationPrompt = buildMigrationPrompt(conversation, snapshot),
            )
        } catch (e: Exception) {
            ContextHealthRefreshResult.Error(
                e.message?.takeIf(String::isNotBlank) ?: "上下文估算失败",
            )
        }
    }

    fun close() {
        conversationClient.close()
    }
}
