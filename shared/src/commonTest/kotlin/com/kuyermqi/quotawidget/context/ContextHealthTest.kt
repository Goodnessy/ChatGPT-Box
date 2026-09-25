package com.kuyermqi.quotawidget.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextHealthTest {
    @Test
    fun statusThresholds_matchProductBands() {
        assertEquals(ContextHealthStatus.HEALTHY, contextHealthStatus(0))
        assertEquals(ContextHealthStatus.HEALTHY, contextHealthStatus(49))
        assertEquals(ContextHealthStatus.LONG, contextHealthStatus(50))
        assertEquals(ContextHealthStatus.LONG, contextHealthStatus(69))
        assertEquals(ContextHealthStatus.MIGRATION_RECOMMENDED, contextHealthStatus(70))
        assertEquals(ContextHealthStatus.MIGRATION_RECOMMENDED, contextHealthStatus(84))
        assertEquals(ContextHealthStatus.HIGH_RISK, contextHealthStatus(85))
    }

    @Test
    fun estimate_countsConversationStructure() {
        val data = ConversationContextData(
            conversationId = "c1",
            title = "测试项目",
            messages = listOf(
                ConversationMessage("user", "请继续开发。```kotlin\nval x = 1\n```", imageCount = 2),
                ConversationMessage("assistant", "已完成第一步。", fileCount = 1),
                ConversationMessage("tool", "不应计入"),
            ),
            updatedAtEpochMs = 123L,
        )
        val snapshot = estimateContextHealth(data)
        assertEquals(2, snapshot.messageCount)
        assertEquals(1, snapshot.userMessageCount)
        assertEquals(1, snapshot.assistantMessageCount)
        assertEquals(2, snapshot.imageCount)
        assertEquals(1, snapshot.fileCount)
        assertEquals(1, snapshot.codeBlockCount)
        assertTrue(snapshot.estimatedTokens > 0)
    }

    @Test
    fun migrationPrompt_extractsKnownProjectFacts() {
        val data = ConversationContextData(
            conversationId = "c2",
            title = "ChatGPT Box 安卓版",
            messages = listOf(
                ConversationMessage("user", "仓库 https://github.com/Goodnessy/ChatGPT-Box 当前分支：feature/context-health-v0.2 当前版本 V0.2.0。不要重做额度逻辑。"),
                ConversationMessage("assistant", "已完成上下文健康度初版。"),
                ConversationMessage("user", "下一步请构建并测试 APK。"),
            ),
            updatedAtEpochMs = 456L,
        )
        val snapshot = estimateContextHealth(data)
        val prompt = buildMigrationPrompt(data, snapshot)
        assertTrue(prompt.contains("https://github.com/Goodnessy/ChatGPT-Box"))
        assertTrue(prompt.contains("feature/context-health-v0.2"))
        assertTrue(prompt.contains("V0.2.0"))
        assertTrue(prompt.contains("不要重做额度逻辑"))
        assertTrue(prompt.contains("下一步请构建并测试 APK"))
    }
}
