package com.opencode.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val title: String,
    val project: String,
    val running: Boolean = false,
    val updatedAt: Long = 0L,
    val viewed: Boolean = true,
    val archived: Boolean = false,
    val directory: String = ""
)

enum class ToolStatus { PENDING, RUNNING, COMPLETED, ERROR }

@Serializable
data class ToolCall(
    val name: String,
    val cmd: String,
    val id: String = "",
    val status: ToolStatus = ToolStatus.COMPLETED,
    val input: String? = null,
    val output: String? = null,
    val error: String? = null,
    val durationMs: Long? = null
)

@Serializable
data class TokensInfo(
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L
)

sealed interface Part {
    data class Text(val text: String) : Part
    data class Reasoning(val text: String, val isExpanded: Boolean = false) : Part
    data class Status(val label: String, val cost: Double? = null, val tokens: TokensInfo? = null) : Part
    data class Tools(val calls: List<ToolCall>) : Part
    data class FileAttachment(val name: String, val url: String, val mime: String? = null) : Part
    data class Diff(val filePath: String, val additions: Int = 0, val deletions: Int = 0, val patch: String = "") : Part
}

sealed interface Msg {
    val id: String
}

data class UserMsg(
    override val id: String,
    val text: String,
    val time: String,
    val files: List<String> = emptyList(),
    val isOptimistic: Boolean = false
) : Msg

data class AssistantMsg(
    override val id: String,
    val parts: List<Part>,
    val agent: String? = null,
    val model: String? = null,
    val error: String? = null,
    val isStreaming: Boolean = false
) : Msg

@Serializable
data class ModelOption(
    val name: String,
    val provider: String,
    val modelId: String = "",
    val variant: String? = null,
    val variants: List<String> = emptyList()
)

@Serializable
data class SlashCommand(
    val name: String,
    val hint: String
)

@Serializable
data class MentionItem(
    val name: String,
    val kind: String,
    val path: String? = null
)

@Serializable
data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val tool: String,
    val input: String,
    val command: String? = null
)

@Serializable
data class QuestionForm(
    val id: String,
    val sessionId: String,
    val title: String,
    val questions: List<String> = emptyList()
)

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val urls: List<String>,
    val username: String = "opencode",
    val password: String = "",
    val activeUrl: String? = null,
    val isConnected: Boolean = false
)

enum class ConnectionState {
    OFFLINE, CONNECTING, CONNECTED, RECONNECTING
}

object AppConstants {
    val languages = listOf(
        "Azərbaycan", "Bosanski", "Català", "Čeština", "Dansk", "Deutsch", "Eesti",
        "English", "Español", "Français", "Hrvatski", "Italiano", "Magyar", "Nederlands",
        "Polski", "Português", "Română", "Suomi", "Svenska", "Türkçe", "हिन्दी", "日本語", "한국어"
    )

    val whatsNew = listOf(
        "7 November" to listOf(
            Triple("New Model Picker", "Tap the model in the message box and the list grows out of it, with Favorites and Recent on top.", 0xFFB44CF0),
            Triple("Calls on Your Server's Keys", "Server Voice is now on for everyone, with no switch. Calls can use your server's key.", 0xFF30D158),
        ),
        "6 November" to listOf(
            Triple("Steadier Server Connections", "OpenCode tries all of a server's addresses at once and uses whichever answers first.", 0xFF30D158),
        ),
        "5 November" to listOf(
            Triple("Select Across a Reply", "Hold or double-tap a word in a reply, then drag the handles across paragraphs.", 0xFF0A84FF),
        ),
        "4 November" to listOf(
            Triple("Real-Time Streaming", "Token-by-token streaming, tool call execution, and server resync.", 0xFF30D158),
        ),
    )
}
