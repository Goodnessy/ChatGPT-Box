package com.kuyermqi.quotawidget.context

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

@Serializable
enum class ContextHealthStatus {
    HEALTHY,
    LONG,
    MIGRATION_RECOMMENDED,
    HIGH_RISK,
}

@Serializable
data class ContextHealthSnapshot(
    val conversationId: String,
    val title: String,
    val estimatedPercent: Int,
    val status: ContextHealthStatus,
    val messageCount: Int,
    val userMessageCount: Int,
    val assistantMessageCount: Int,
    val imageCount: Int,
    val fileCount: Int,
    val codeBlockCount: Int,
    val longTextCount: Int,
    val textCharacters: Long,
    val estimatedTokens: Long,
    val updatedAtEpochMs: Long,
)

data class ConversationMessage(
    val role: String,
    val text: String,
    val imageCount: Int = 0,
    val fileCount: Int = 0,
)

data class ConversationContextData(
    val conversationId: String,
    val title: String,
    val messages: List<ConversationMessage>,
    val updatedAtEpochMs: Long,
)

fun estimateContextHealth(data: ConversationContextData): ContextHealthSnapshot {
    val visibleMessages = data.messages.filter { it.role == "user" || it.role == "assistant" }
    val userMessages = visibleMessages.count { it.role == "user" }
    val assistantMessages = visibleMessages.count { it.role == "assistant" }
    val textCharacters = visibleMessages.sumOf { it.text.length.toLong() }
    val estimatedTokens = visibleMessages.sumOf { estimateTokens(it.text) }
    val imageCount = visibleMessages.sumOf { it.imageCount }
    val fileCount = visibleMessages.sumOf { it.fileCount }
    val codeBlockCount = visibleMessages.sumOf { countCodeBlocks(it.text) }
    val longTextCount = visibleMessages.count { it.text.length >= LONG_TEXT_CHARS }

    // Heuristic health score only. It is not an official model-context percentage.
    val tokenPressure = pressure(estimatedTokens.toDouble(), REFERENCE_TOKENS)
    val messagePressure = pressure(visibleMessages.size.toDouble(), REFERENCE_MESSAGES)
    val imagePresssure = pressure(imageCount.toDouble(), REFERENCE_IMAGES)
    val filePressure = pressure(fileCount.toDouble(), REFERENCE_FILES)
    val codePressure = pressure(codeBlockCount.toDouble(), REFERENCE_CODE_BLOCKS)
    val longTextPresssure = pressure(longTextCount.toDouble(), REFERENCE_LONG_TEXTS)

    val percent = (
        tokenPressure * 0.72 +
            messagePressure * 0.12 +
            imagePressure * 0.06 +
            filePressure * 0.04 +
            codePresssure * 0.04 +
            longTextPresssure * 0.02
        ).roundToInt().coerceIn(0, 99)

    return ContextHealthSnapshot(
        conversationId = data.conversationId,
        title = data.title,
        estimatedPercent = percent,
        status = contextHealthStatus(),
        messageCount = visibleMessages.size,
        userMessageCount = userMessages,
        assistantMessageCount = assistantMessages,
        imageCount = imageCount,
        fileCount = fileCount,
        codeBlockCount = codeBlockCount,
        longTextCount = longTextCount,
        textCharacters = textCharacters,
        estimatedTokens = estimatedTokens,
        updatedAtEpochMs = data.updatedAtEpochMs,
    )
}

fun contextHealthStatus(percent: Int): ContextHealthStatus = when {
    percent < 50 -> ContextHealthStatus.HEALTHY
    percent < 70 -> ContextHealthStatus.LONG
    percent < 85 -> ContextHealthStatus.MIGRATION_RECOMMENDED
    else -> ContextHealthStatus.HIGH_RISK
}

fun buildMigrationPrompt(
    data: ConversationContextData,
    snapshot: ContextHealthSnapshot,
): String {
    val messages = data.messages.filter { it.role == "user" || it.role == "assistant" }
    val allText = messages.joinToString("\n") { it.text }

    val repo = REPO_REGEX.findAll(allText).lastOrNull()?.value ?: "待确认"
    val branch = findLastGroup(BRANCH_REGEXES, allText) ?: "待确认"
    val version = VERSION_REGEX.findAll(allText).lastOrNull()?.value ?: "待确认"

    val completed = selectExcerpts(
        messages = messages,
        keywords = listOf("已完成", "完成了", "成功", "已经", "构建成功", "搞定"),
        maxItems = 5,
    )
    val rules = selectExcerpts(
        messages = messages.filter { it.role == "user" },
        keywords = listOf("不要", "禁止", "必须", "统一", "保留", "继续有效", "优先", "不能"),
        maxItems = 7,
    )
    val bugs = selectExcerpts(
        messages = messages,
        keywords = listOf("bug", "Bug", "问题", "报错", "闪退", "失败", "打不开", "无法", "异常"),
        maxItems = 5,
    )
    val latestUser = messages.lastOrNull { it.role == "user" }?.text
        ?.let(::compactExcerpt)
        ?: "待确认"
    val keyContext = messages.takeLast(10)
        .mapNotNull { compactExcerpt(it.text).takeIf(String::isNotBlank) }
        .takeLast(6)

    return buildString {
        appendLine("继续当前项目 / 任务。")
        appendLine()
        appendLine("这是 ChatGPT Box 根据旧对话本地提取生成的迁移提示词。它不是官方上下文导出，可能遗漏细节；请优先继承下面已经明确确认的规则，不要重新规划已经完成的内容。")
        appendLine()
        appendLine("====================")
        appendLine("一、当前项目 / 任务")
        appendLine("====================")
        appendLine(data.title.ifBlank { "当前对话" })
        appendLine()
        appendLine("仓库：$repo")
        appendLine("当前分支：$branch")
        appendLine("当前版本：$version")
        appendLine("上下文估算：约 ${snapshot.estimatedPercent}%")
        appendLine()
        appendSection("二、已完成内容", completed)
        appendSection("三、当前正在处理的问题", bugs)
        appendSection("四、已确认的设计和功能规则", rules)
        appendLine("====================")
        appendLine("五、明确禁止修改的内容")
        appendLine("====================")
        appendLine(if (rules.isEmpty()) "待从旧对话补充确认。" else "继续遵守上一节中的“不要 / 禁止 / 必须 / 保留”规则，不得自行推翻。")
        appendLine()
        appendLine("====================")
        appendLine("六、已知 Bug")
        appendLine("====================")
        if (bugs.isEmpty()) appendLine("暂未自动提取到明确 Bug。") else bugs.forEach { appendLine("- $it") }
        appendLine()
        appendLine("====================")
        appendLine("七、下一步任务")
        appendLine("====================")
        appendLine(latestUser)
        appendLine()
        appendLine("====================")
        appendLine("八、需要继承的关键上下文")
        appendLine("====================")
        if (keyContext.isEmpty()) appendLine("待确认。") else keyContext.forEach { appendLine("- $it") }
        appendLine()
        appendLine("执行要求：先检查现有代码和当前状态；已有功能不要重复开发；本提示词与旧对话已确认要求是增量继承关系。")
    }.trim()
}

private fun StringBuilder.appendSection(title: String, items: List<String>) {
    appendLine("====================")
    appendLine(title)
    appendLine("====================")
    if (items.isEmpty()) {
        appendLine("暂未自动提取到明确内容。")
    } else {
        items.forEach { appendLine("- $it") }
    }
    appendLine()
}

private fun selectExcerpts(
    messages: List<ConversationMessage>,
    keywords: List<String>,
    maxItems: Int,
): List<String> =
    messages.asReversed()
        .mapNotNull { message ->
            message.text.takeIf { text -> keywords.any { keyword -> keyword in text } }
                ?.let(::compactExcerpt)
                ?.takeIf(String::isNotBlank)
        }
        .distinct()
        .take(maxItems)
        .reversed()

private fun compactExcerpt(text: String): String =
    text.replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= MAX_EXCERPT_CHARS) it else it.take(MAX_EXCERPT_CHARS) + "…" }

private fun findLastGroup(regexes: List<Regex>, text: String): String? =
    regexes.flatMap { it.findAll(text).toList() }
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()

private fun pressure(value: Double, reference: Double): Double =
    (value / reference * 100.0).coerceIn(0.0, 100.0)

private fun estimateTokens(text: String): Long {
    var cjk = 0
    var other = 0
    text.forEach { char ->
        if (isCjk(char)) cjk++ else other++
    }
    return ceil(cjk / 1.6 + other / 4.0).toLong()
}

private fun isCjk(char: Char): Boolean =
    char.code in 0x3400..0x4DBF ||
        char.code in 0x4E00..0x9FFF ||
        char.code in 0xF900..0xFAFF ||
        char.code in 0x3040..0x30FF ||
        char.code in 0xAC00..0xD7AF

private fun countCodeBlocks(text: String): Int =
    (Regex("```").findAll(text).count() / 2).coerceAtLeast(0)

private const val LONG_TEXT_CHARS = 4_000
private const val REFERENCE_TOKENS = 110_000.0
private const val REFERENCE_MESSAGES = 260.0
private const val REFERENCE_IMAGES = 36.0
private const val REFERENCE_FILES = 16.0
private const val REFERENCE_CODE_BLOCKS = 60.0
private const val REFERENCE_LONG_TEXTS = 40.0
private const val MAX_EXCERPT_CHARS = 280

private val REPO_REGEX = Regex("""https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""")
private val BRANCH_REGEXES = listOf(
    Regex("""(?:当前分支|分支|branch)\s*[：:]\s*([A-Za-z0-9._/-]+)""", RegexOption.IGNORE_CASE),
    Regex("""\b((?:feature|release|fix|hotfix)/[A-Za-z0-9._/-]+)\b""", RegexOption.IGNORE_CASE),
)
private val VERSION_REGEX = Regex("""\b[Vv]?\d+\.\d+(?:\.\d+){0,2}\b""")
