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

class V2Backend(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : OpenCodeBackend {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private fun formatTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "Now"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }

    override suspend fun getInfo(baseUrl: String, authHeader: String): Result<ServerInfoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/info")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<ServerInfoResponse>(body)
            }
        }
    }

    override suspend fun redeemPairing(baseUrl: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/auth/connect/${code.trim()}")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Pairing failed (HTTP ${resp.code})")
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                parsed["token"]?.jsonPrimitive?.content ?: throw Exception("No token in response")
            }
        }
    }

    override suspend fun getProjects(baseUrl: String, authHeader: String): Result<List<ProjectItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/project")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = if (element is JsonArray) element else element.jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                list.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val canonical = obj["canonical"]?.jsonPrimitive?.content.orEmpty()
                    val name = canonical.trimEnd('/').substringAfterLast('/').ifEmpty { canonical }
                    ProjectItem(id = id, name = name, directory = canonical)
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
            var url = "${baseUrl.trimEnd('/')}/api/session"
            val queryParams = mutableListOf<String>()
            if (!directory.isNullOrBlank()) queryParams.add("location[directory]=${java.net.URLEncoder.encode(directory, "UTF-8")}")
            if (!search.isNullOrBlank()) queryParams.add("search=${java.net.URLEncoder.encode(search, "UTF-8")}")
            if (archived) queryParams.add("archived=true")
            if (queryParams.isNotEmpty()) url += "?" + queryParams.joinToString("&")

            val req = Request.Builder().url(url).header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val items = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }

                items.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val title = obj["title"]?.jsonPrimitive?.content ?: "Session"
                    val loc = obj["location"]?.jsonObject?.get("directory")?.jsonPrimitive?.content.orEmpty()
                    val projName = loc.trimEnd('/').substringAfterLast('/').ifEmpty { "root" }
                    val time = obj["time"]?.jsonObject
                    val busy = time?.get("busy")?.jsonPrimitive?.longOrNull
                    val idle = time?.get("idle")?.jsonPrimitive?.longOrNull
                    val viewed = time?.get("viewed")?.jsonPrimitive?.longOrNull
                    val updated = time?.get("updated")?.jsonPrimitive?.longOrNull ?: 0L

                    val isRunning = busy != null && (idle == null || busy > idle)
                    val isViewed = viewed != null && (idle == null || viewed >= idle)

                    Session(
                        id = id,
                        title = title,
                        project = projName,
                        running = isRunning,
                        updatedAt = updated,
                        viewed = isViewed,
                        archived = archived,
                        directory = loc
                    )
                }.sortedByDescending { it.updatedAt }
            }
        }
    }

    override suspend fun createSession(baseUrl: String, authHeader: String, directory: String?, title: String?): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyObj = buildJsonObject {
                if (!directory.isNullOrBlank()) {
                    putJsonObject("location") { put("directory", directory) }
                }
                if (!title.isNullOrBlank()) put("title", title)
            }
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session")
                .header("Authorization", authHeader)
                .post(bodyObj.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val sessionTitle = obj["title"]?.jsonPrimitive?.content ?: (title ?: "New Session")
                val loc = obj["location"]?.jsonObject?.get("directory")?.jsonPrimitive?.content ?: directory.orEmpty()
                val projName = loc.trimEnd('/').substringAfterLast('/').ifEmpty { "root" }
                Session(id = id, title = sessionTitle, project = projName, running = false, directory = loc)
            }
        }
    }

    override suspend fun renameSession(baseUrl: String, authHeader: String, sessionId: String, title: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject { put("title", title) }.toString().toRequestBody(mediaTypeJson)
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId")
                .header("Authorization", authHeader)
                .patch(body)
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun deleteSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId")
                .header("Authorization", authHeader)
                .delete()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun forkSession(baseUrl: String, authHeader: String, sessionId: String): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/fork")
                .header("Authorization", authHeader)
                .post("{}".toRequestBody(mediaTypeJson))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val title = obj["title"]?.jsonPrimitive?.content ?: "Forked Session"
                val loc = obj["location"]?.jsonObject?.get("directory")?.jsonPrimitive?.content.orEmpty()
                val projName = loc.trimEnd('/').substringAfterLast('/').ifEmpty { "root" }
                Session(id = id, title = title, project = projName, running = false, directory = loc)
            }
        }
    }

    override suspend fun interruptSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/interrupt")
                .header("Authorization", authHeader)
                .post("{}".toRequestBody(mediaTypeJson))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun getMessages(baseUrl: String, authHeader: String, sessionId: String, limit: Int): Result<List<Msg>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/message?limit=$limit")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val array = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }

                val result = mutableListOf<Msg>()
                for (item in array) {
                    val m = item.jsonObject
                    val id = m["id"]?.jsonPrimitive?.content ?: continue
                    val type = m["type"]?.jsonPrimitive?.content.orEmpty()
                    val timeMillis = m["time"]?.jsonObject?.get("created")?.jsonPrimitive?.longOrNull

                    if (type == "user") {
                        val text = m["text"]?.jsonPrimitive?.content.orEmpty()
                        val files = m["files"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                        result.add(UserMsg(id = id, text = text, time = formatTime(timeMillis), files = files))
                    } else if (type == "assistant") {
                        val parts = mutableListOf<Part>()
                        val contentArr = m["content"]?.jsonArray ?: JsonArray(emptyList())
                        val toolCalls = mutableListOf<ToolCall>()

                        for (c in contentArr) {
                            val cObj = c.jsonObject
                            val partType = cObj["type"]?.jsonPrimitive?.content
                            when (partType) {
                                "reasoning" -> {
                                    val rText = cObj["text"]?.jsonPrimitive?.content.orEmpty()
                                    if (rText.isNotBlank()) parts.add(Part.Reasoning(rText))
                                }
                                "text" -> {
                                    val tText = cObj["text"]?.jsonPrimitive?.content.orEmpty()
                                    if (tText.isNotBlank()) parts.add(Part.Text(tText))
                                }
                                "tool" -> {
                                    val toolId = cObj["id"]?.jsonPrimitive?.content.orEmpty()
                                    val toolName = cObj["name"]?.jsonPrimitive?.content ?: "tool"
                                    val state = cObj["state"]?.jsonObject
                                    val statusStr = state?.get("status")?.jsonPrimitive?.content.orEmpty()
                                    val status = when (statusStr) {
                                        "running" -> ToolStatus.RUNNING
                                        "error" -> ToolStatus.ERROR
                                        "pending" -> ToolStatus.PENDING
                                        else -> ToolStatus.COMPLETED
                                    }
                                    val inputCommand = state?.get("input")?.jsonObject?.get("command")?.jsonPrimitive?.content
                                        ?: state?.get("input")?.toString().orEmpty()
                                    val outputText = state?.get("content")?.jsonArray?.joinToString("\n") {
                                        it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
                                    } ?: state?.get("output")?.jsonPrimitive?.content.orEmpty()
                                    val errorMsg = state?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content

                                    toolCalls.add(
                                        ToolCall(
                                            name = toolName.replaceFirstChar { it.uppercase() },
                                            cmd = inputCommand,
                                            id = toolId,
                                            status = status,
                                            input = inputCommand,
                                            output = outputText,
                                            error = errorMsg
                                        )
                                    )
                                }
                            }
                        }

                        if (toolCalls.isNotEmpty()) {
                            parts.add(Part.Tools(toolCalls))
                        }

                        val tokensObj = m["tokens"]?.jsonObject
                        val costVal = m["cost"]?.jsonPrimitive?.doubleOrNull
                        if (tokensObj != null) {
                            val input = tokensObj["input"]?.jsonPrimitive?.longOrNull ?: 0L
                            val output = tokensObj["output"]?.jsonPrimitive?.longOrNull ?: 0L
                            val reasoning = tokensObj["reasoning"]?.jsonPrimitive?.longOrNull ?: 0L
                            val cacheRead = tokensObj["cache"]?.jsonObject?.get("read")?.jsonPrimitive?.longOrNull ?: 0L
                            val cacheWrite = tokensObj["cache"]?.jsonObject?.get("write")?.jsonPrimitive?.longOrNull ?: 0L
                            val tokensInfo = TokensInfo(input, output, reasoning, cacheRead, cacheWrite)
                            parts.add(Part.Status("Completed", cost = costVal, tokens = tokensInfo))
                        }

                        val agentName = m["agent"]?.jsonPrimitive?.content
                        val modelObj = m["model"]?.jsonObject
                        val modelName = modelObj?.get("id")?.jsonPrimitive?.content
                        val errorStr = m["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content

                        result.add(AssistantMsg(id = id, parts = parts, agent = agentName, model = modelName, error = errorStr))
                    }
                }
                result
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
            val bodyObj = buildJsonObject {
                put("text", text)
                if (!agent.isNullOrBlank() && agent != "Server Default") put("agent", agent)
                if (!modelId.isNullOrBlank() && !providerId.isNullOrBlank()) {
                    putJsonObject("model") {
                        put("id", modelId)
                        put("providerID", providerId)
                        if (!variant.isNullOrBlank()) put("variant", variant)
                    }
                }
                if (files.isNotEmpty()) {
                    putJsonArray("files") { files.forEach { add(it) } }
                }
            }
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/prompt")
                .header("Authorization", authHeader)
                .post(bodyObj.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
                true
            }
        }
    }

    override suspend fun getAgents(baseUrl: String, authHeader: String, directory: String?): Result<List<AgentItem>> = withContext(Dispatchers.IO) {
        runCatching {
            var url = "${baseUrl.trimEnd('/')}/api/agent"
            if (!directory.isNullOrBlank()) {
                url += "?location[directory]=${java.net.URLEncoder.encode(directory, "UTF-8")}"
            }
            val req = Request.Builder().url(url).header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                list.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val name = obj["name"]?.jsonPrimitive?.content ?: id
                    val desc = obj["description"]?.jsonPrimitive?.content.orEmpty()
                    val mode = obj["mode"]?.jsonPrimitive?.content ?: "primary"
                    val hidden = obj["hidden"]?.jsonPrimitive?.booleanOrNull ?: false
                    AgentItem(id, name, desc, mode, hidden)
                }
            }
        }
    }

    override suspend fun getModels(baseUrl: String, authHeader: String): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/model")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                list.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val name = obj["name"]?.jsonPrimitive?.content ?: id
                    val provider = obj["providerID"]?.jsonPrimitive?.content.orEmpty()
                    val variants = obj["variants"]?.jsonArray?.mapNotNull { v ->
                        v.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    } ?: emptyList()
                    val defaultVariant = variants.firstOrNull()

                    ModelOption(
                        name = name,
                        provider = provider.replaceFirstChar { it.uppercase() },
                        modelId = id,
                        variant = defaultVariant,
                        variants = variants
                    )
                }
            }
        }
    }

    override suspend fun getDefaultModel(baseUrl: String, authHeader: String, directory: String?): Result<ModelOption?> = withContext(Dispatchers.IO) {
        runCatching {
            var url = "${baseUrl.trimEnd('/')}/api/model/default"
            if (!directory.isNullOrBlank()) {
                url += "?location[directory]=${java.net.URLEncoder.encode(directory, "UTF-8")}"
            }
            val req = Request.Builder().url(url).header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val obj = when (element) {
                    is JsonObject -> element["data"]?.jsonObject ?: element
                    else -> return@runCatching null
                }
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val name = obj["name"]?.jsonPrimitive?.content ?: id
                val provider = obj["providerID"]?.jsonPrimitive?.content.orEmpty()
                val variants = obj["variants"]?.jsonArray?.mapNotNull { v ->
                    v.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                } ?: emptyList()
                ModelOption(name = name, provider = provider.replaceFirstChar { it.uppercase() }, modelId = id, variant = variants.firstOrNull(), variants = variants)
            }
        }
    }

    override suspend fun getCommands(baseUrl: String, authHeader: String, directory: String?): Result<List<SlashCommand>> = withContext(Dispatchers.IO) {
        runCatching {
            var url = "${baseUrl.trimEnd('/')}/api/command"
            if (!directory.isNullOrBlank()) {
                url += "?location[directory]=${java.net.URLEncoder.encode(directory, "UTF-8")}"
            }
            val req = Request.Builder().url(url).header("Authorization", authHeader).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                list.map { item ->
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val desc = obj["description"]?.jsonPrimitive?.content ?: "Command"
                    SlashCommand("/$name", desc)
                }
            }
        }
    }

    override suspend fun getPermissions(baseUrl: String, authHeader: String): Result<List<PermissionRequest>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/permission")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                list.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val sessionId = obj["sessionID"]?.jsonPrimitive?.content.orEmpty()
                    val tool = obj["tool"]?.jsonPrimitive?.content ?: "Shell"
                    val input = obj["input"]?.toString() ?: ""
                    PermissionRequest(id = id, sessionId = sessionId, tool = tool, input = input)
                }
            }
        }
    }

    override suspend fun replyPermission(
        baseUrl: String,
        authHeader: String,
        sessionId: String,
        requestId: String,
        decision: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject { put("decision", decision) }.toString().toRequestBody(mediaTypeJson)
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/permission/$requestId/reply")
                .header("Authorization", authHeader)
                .post(body)
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun getForms(baseUrl: String, authHeader: String): Result<List<QuestionForm>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/form")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val list = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                list.map { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val sessionId = obj["sessionID"]?.jsonPrimitive?.content.orEmpty()
                    val title = obj["title"]?.jsonPrimitive?.content ?: "Question"
                    val questions = obj["questions"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    QuestionForm(id = id, sessionId = sessionId, title = title, questions = questions)
                }
            }
        }
    }

    override suspend fun replyForm(
        baseUrl: String,
        authHeader: String,
        sessionId: String,
        formId: String,
        answers: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                putJsonArray("answers") { answers.forEach { add(it) } }
            }.toString().toRequestBody(mediaTypeJson)
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/form/$formId/reply")
                .header("Authorization", authHeader)
                .post(body)
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun markViewed(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/view")
                .header("Authorization", authHeader)
                .post("{}".toRequestBody(mediaTypeJson))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun compactSession(baseUrl: String, authHeader: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/session/$sessionId/compact")
                .header("Authorization", authHeader)
                .post("{}".toRequestBody(mediaTypeJson))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    override suspend fun checkVoiceSupport(baseUrl: String, authHeader: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/experimental/voice/models")
                .header("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body)
                val list = when (parsed) {
                    is JsonArray -> parsed
                    is JsonObject -> parsed["data"]?.jsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                list.isNotEmpty()
            }
        }
    }
}
