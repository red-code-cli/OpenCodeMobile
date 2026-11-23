package com.opencode.mobile.data.backend

import com.opencode.mobile.data.*
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoResponse(
    val version: String = "",
    val pid: Long = 0L,
    val hostname: String? = null,
    val urls: List<String> = emptyList()
)

data class ProjectItem(
    val id: String,
    val name: String,
    val directory: String
)

data class AgentItem(
    val id: String,
    val name: String,
    val description: String = "",
    val mode: String = "primary",
    val hidden: Boolean = false
)

interface OpenCodeBackend {
    suspend fun getInfo(baseUrl: String, authHeader: String): Result<ServerInfoResponse>
    suspend fun redeemPairing(baseUrl: String, code: String): Result<String>
    suspend fun getProjects(baseUrl: String, authHeader: String): Result<List<ProjectItem>>
    suspend fun getSessions(baseUrl: String, authHeader: String, directory: String? = null, search: String? = null, archived: Boolean = false): Result<List<Session>>
    suspend fun createSession(baseUrl: String, authHeader: String, directory: String?, title: String? = null): Result<Session>
    suspend fun renameSession(baseUrl: String, authHeader: String, sessionId: String, title: String): Result<Boolean>
    suspend fun deleteSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean>
    suspend fun forkSession(baseUrl: String, authHeader: String, sessionId: String): Result<Session>
    suspend fun interruptSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean>
    suspend fun getMessages(baseUrl: String, authHeader: String, sessionId: String, limit: Int = 100): Result<List<Msg>>
    suspend fun sendPrompt(baseUrl: String, authHeader: String, sessionId: String, text: String, agent: String?, modelId: String?, providerId: String?, variant: String?, files: List<String> = emptyList()): Result<Boolean>
    suspend fun getAgents(baseUrl: String, authHeader: String, directory: String? = null): Result<List<AgentItem>>
    suspend fun getModels(baseUrl: String, authHeader: String): Result<List<ModelOption>>
    suspend fun getDefaultModel(baseUrl: String, authHeader: String, directory: String? = null): Result<ModelOption?>
    suspend fun getCommands(baseUrl: String, authHeader: String, directory: String? = null): Result<List<SlashCommand>>
    suspend fun getPermissions(baseUrl: String, authHeader: String): Result<List<PermissionRequest>>
    suspend fun replyPermission(baseUrl: String, authHeader: String, sessionId: String, requestId: String, decision: String): Result<Boolean>
    suspend fun getForms(baseUrl: String, authHeader: String): Result<List<QuestionForm>>
    suspend fun replyForm(baseUrl: String, authHeader: String, sessionId: String, formId: String, answers: List<String>): Result<Boolean>
    suspend fun markViewed(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean>
    suspend fun compactSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean>
    suspend fun checkVoiceSupport(baseUrl: String, authHeader: String): Result<Boolean>
}
