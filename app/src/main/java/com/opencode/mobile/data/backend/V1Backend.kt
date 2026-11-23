package com.opencode.mobile.data.backend

import com.opencode.mobile.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class V1Backend(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : OpenCodeBackend {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    override suspend fun getInfo(baseUrl: String, authHeader: String): Result<ServerInfoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/global/health")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                val version = obj["version"]?.jsonPrimitive?.content ?: "1.0.0"
                ServerInfoResponse(version = version, hostname = baseUrl)
            }
        }
    }

    override suspend fun redeemPairing(baseUrl: String, code: String): Result<String> {
        return Result.failure(UnsupportedOperationException("Pairing is not supported on V1 servers"))
    }

    override suspend fun getProjects(baseUrl: String, authHeader: String): Result<List<ProjectItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/project")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching listOf(ProjectItem("root", "root", "/root"))
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = if (element is JsonArray) element else element.jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                list.map { item ->
                    val dir = item.jsonPrimitive.contentOrNull ?: item.jsonObject["directory"]?.jsonPrimitive?.content.orEmpty()
                    val name = dir.trimEnd('/').substringAfterLast('/').ifEmpty { dir }
                    ProjectItem(id = dir, name = name, directory = dir)
                }
            }
        }
    }

    override suspend fun getSessions(
        baseUrl: String,
        authHeader: String,
        directory: String?,
        search: String?,
        archived: Boolean
    ): Result<List<Session>> = withContext(Dispatchers.IO) {
        runCatching {
            var url = "${baseUrl.trimEnd('/')}/session"
            if (!directory.isNullOrBlank()) url += "?directory=${java.net.URLEncoder.encode(directory, "UTF-8")}"
            val req = Request.Builder().url(url).header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val array = if (element is JsonArray) element else element.jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                array.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val title = obj["title"]?.jsonPrimitive?.content ?: "Session"
                    val dir = obj["directory"]?.jsonPrimitive?.content.orEmpty()
                    val proj = dir.trimEnd('/').substringAfterLast('/').ifEmpty { "root" }
                    val running = obj["status"]?.jsonPrimitive?.content == "busy"
                    Session(id = id, title = title, project = proj, running = running, directory = dir)
                }
            }
        }
    }

    override suspend fun createSession(baseUrl: String, authHeader: String, directory: String?, title: String?): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            var url = "${baseUrl.trimEnd('/')}/session"
            if (!directory.isNullOrBlank()) url += "?directory=${java.net.URLEncoder.encode(directory, "UTF-8")}"
            val body = (title?.let { buildJsonObject { put("title", it) }.toString() } ?: "{}").toRequestBody(mediaTypeJson)
            val req = Request.Builder().url(url).header("Authorization", authHeader).post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val bodyStr = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(bodyStr).jsonObject
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val t = obj["title"]?.jsonPrimitive?.content ?: (title ?: "New Session")
                val proj = (directory ?: "root").trimEnd('/').substringAfterLast('/')
                Session(id = id, title = t, project = proj, running = false, directory = directory.orEmpty())
            }
        }
    }

    override suspend fun renameSession(baseUrl: String, authHeader: String, sessionId: String, title: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject { put("title", title) }.toString().toRequestBody(mediaTypeJson)
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/session/$sessionId").header("Authorization", authHeader).patch(body).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun deleteSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/session/$sessionId").header("Authorization", authHeader).delete().build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun forkSession(baseUrl: String, authHeader: String, sessionId: String): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/session/$sessionId/fork").header("Authorization", authHeader).post("{}".toRequestBody(mediaTypeJson)).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val bodyStr = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(bodyStr).jsonObject
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val title = obj["title"]?.jsonPrimitive?.content ?: "Forked Session"
                Session(id = id, title = title, project = "root", running = false)
            }
        }
    }

    override suspend fun interruptSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/session/$sessionId/abort").header("Authorization", authHeader).post("{}".toRequestBody(mediaTypeJson)).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun getMessages(baseUrl: String, authHeader: String, sessionId: String, limit: Int): Result<List<Msg>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/session/$sessionId/message").header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = if (element is JsonArray) element else element.jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                list.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val sender = obj["sender"]?.jsonPrimitive?.content ?: obj["role"]?.jsonPrimitive?.content ?: "user"
                    if (sender == "user") {
                        val text = obj["text"]?.jsonPrimitive?.content ?: obj["content"]?.jsonPrimitive?.content.orEmpty()
                        UserMsg(id = id, text = text, time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                    } else {
                        val text = obj["text"]?.jsonPrimitive?.content ?: obj["content"]?.jsonPrimitive?.content.orEmpty()
                        AssistantMsg(id = id, parts = listOf(Part.Text(text)))
                    }
                }
            }
        }
    }

    override suspend fun sendPrompt(
        baseUrl: String,
        authHeader: String,
        sessionId: String,
        text: String,
        agent: String?,
        modelId: String?,
        providerId: String?,
        variant: String?,
        files: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                putJsonArray("parts") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
                if (!modelId.isNullOrBlank() && !providerId.isNullOrBlank()) {
                    putJsonObject("model") { put("modelID", modelId); put("providerID", providerId) }
                }
            }.toString().toRequestBody(mediaTypeJson)
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/session/$sessionId/prompt_async").header("Authorization", authHeader).post(body).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun getAgents(baseUrl: String, authHeader: String, directory: String?): Result<List<AgentItem>> = withContext(Dispatchers.IO) {
        runCatching {
            listOf(
                AgentItem("build", "Build", "Default agent", "primary", false),
                AgentItem("plan", "Plan", "Planning agent", "primary", false)
            )
        }
    }

    override suspend fun getModels(baseUrl: String, authHeader: String): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/config/providers").header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = mutableListOf<ModelOption>()
                if (element is JsonObject) {
                    element["providers"]?.jsonArray?.forEach { p ->
                        val provObj = p.jsonObject
                        val provName = provObj["name"]?.jsonPrimitive?.content ?: "Provider"
                        provObj["models"]?.jsonArray?.forEach { m ->
                            val mObj = m.jsonObject
                            val id = mObj["id"]?.jsonPrimitive?.content.orEmpty()
                            val name = mObj["name"]?.jsonPrimitive?.content ?: id
                            list.add(ModelOption(name = name, provider = provName, modelId = id))
                        }
                    }
                }
                list
            }
        }
    }

    override suspend fun getDefaultModel(baseUrl: String, authHeader: String, directory: String?): Result<ModelOption?> = Result.success(null)
    override suspend fun getCommands(baseUrl: String, authHeader: String, directory: String?): Result<List<SlashCommand>> = Result.success(emptyList())
    override suspend fun getPermissions(baseUrl: String, authHeader: String): Result<List<PermissionRequest>> = Result.success(emptyList())
    override suspend fun replyPermission(baseUrl: String, authHeader: String, sessionId: String, requestId: String, decision: String): Result<Boolean> = Result.success(true)
    override suspend fun getForms(baseUrl: String, authHeader: String): Result<List<QuestionForm>> = Result.success(emptyList())
    override suspend fun replyForm(baseUrl: String, authHeader: String, sessionId: String, formId: String, answers: List<String>): Result<Boolean> = Result.success(true)
    override suspend fun markViewed(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = Result.success(true)
    override suspend fun compactSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = Result.success(true)
    override suspend fun checkVoiceSupport(baseUrl: String, authHeader: String): Result<Boolean> = Result.success(false)
}
