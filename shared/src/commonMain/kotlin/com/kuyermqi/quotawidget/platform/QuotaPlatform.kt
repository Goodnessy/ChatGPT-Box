package com.kuyermqi.quotawidget.platform

/**
 * ChatGPT Box V0.1 only exposes the Codex quota source.
 * Other provider implementations remain in the codebase temporarily so the
 * upstream quota parsing can be updated cleanly while the product UI stays focused.
 */
interface QuotaPlatform {
    val id: String
    val displayName: String
}

object PlatformIds {
    const val DEEPSEEK = "deepseek"
    const val OPENCODE_GO = "opencode_go"
    const val CODEX = "codex"
    const val NEW_API = "new_api"
}

object PlatformRegistry {
    val platforms: List<QuotaPlatform> = listOf(
        object : QuotaPlatform {
            override val id = PlatformIds.CODEX
            override val displayName = "ChatGPT / Codex"
        },
    )

    fun find(id: String): QuotaPlatform? = platforms.find { it.id == id }

    fun displayName(id: String): String =
        find(id)?.displayName ?: id
}
