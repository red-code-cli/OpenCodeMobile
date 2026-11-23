package com.opencode.mobile.data.net

import com.opencode.mobile.data.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.random.Random

sealed interface OpenCodeEvent {
    data class ServerConnected(val eventId: String) : OpenCodeEvent
    data class SessionStatus(val sessionId: String, val isBusy: Boolean, val timestamp: Long) : OpenCodeEvent
    data class SessionCreated(val sessionId: String) : OpenCodeEvent
    data class SessionUpdated(val sessionId: String, val title: String? = null, val directory: String? = null) : OpenCodeEvent
    data class SessionDeleted(val sessionId: String) : OpenCodeEvent
    data class MessageUpdated(val sessionId: String, val messageId: String, val rawData: JsonObject) : OpenCodeEvent
    data class TextDelta(val sessionId: String, val messageId: String, val delta: String) : OpenCodeEvent
    data class ToolUpdated(val sessionId: String, val messageId: String, val toolId: String, val status: String, val output: String? = null, val error: String? = null) : OpenCodeEvent
    data class PermissionAsked(val requestId: String, val sessionId: String, val tool: String, val input: String) : OpenCodeEvent
    data class FormCreated(val formId: String, val sessionId: String, val title: String, val questions: List<String>) : OpenCodeEvent
}

class EventStream(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for SSE stream
        .build()
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val _events = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<OpenCodeEvent> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastEventTime = 0L

    fun start(scope: CoroutineScope, baseUrl: String, authHeader: String, isV2: Boolean = true) {
        stop()
        _connectionState.value = ConnectionState.CONNECTING
        streamJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive) {
                try {
                    val endpoint = if (isV2) "/api/event" else "/event"
                    val url = "${baseUrl.trimEnd('/')}$endpoint"
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .header("Authorization", authHeader)
                        .build()

                    val call = client.newCall(request)
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("SSE Connection HTTP ${response.code}")
                        }
                        _connectionState.value = ConnectionState.CONNECTED
                        attempt = 0
                        startHeartbeatMonitor(scope, call::cancel)

                        val source = response.body?.byteStream() ?: return@use
                        val reader = BufferedReader(InputStreamReader(source))
                        var line: String?
                        while (isActive && reader.readLine().also { line = it } != null) {
                            lastEventTime = System.currentTimeMillis()
                            val cur = line?.trim().orEmpty()
                            if (cur.startsWith("data:")) {
                                val dataStr = cur.removePrefix("data:").trim()
                                parseAndEmit(dataStr)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _connectionState.value = ConnectionState.RECONNECTING
                        attempt++
                        val baseDelay = (1000L * (1 shl (attempt.coerceAtMost(4))))
                        val jitter = Random.nextLong(200, 800)
                        delay((baseDelay + jitter).coerceAtMost(15000L))
                    }
                } finally {
                    heartbeatJob?.cancel()
                }
            }
            _connectionState.value = ConnectionState.OFFLINE
        }
    }

    private fun startHeartbeatMonitor(scope: CoroutineScope, onStall: () -> Unit) {
        heartbeatJob?.cancel()
        lastEventTime = System.currentTimeMillis()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10000)
                val diff = System.currentTimeMillis() - lastEventTime
                if (diff > 35000) { // No event or heartbeat for > 35s
                    onStall()
                    break
                }
            }
        }
    }

    private suspend fun parseAndEmit(raw: String) {
        if (raw.isBlank() || raw == "[DONE]") return
        try {
            val element = json.parseToJsonElement(raw)
            if (element !is JsonObject) return
            val type = element["type"]?.jsonPrimitive?.content.orEmpty()
            val id = element["id"]?.jsonPrimitive?.content.orEmpty()
            val data = element["data"]?.jsonObject ?: JsonObject(emptyMap())

            when (type) {
                "server.connected" -> {
                    _events.emit(OpenCodeEvent.ServerConnected(id))
                }
                "session.created" -> {
                    val sid = data["id"]?.jsonPrimitive?.content ?: id
                    _events.emit(OpenCodeEvent.SessionCreated(sid))
                }
                "session.updated" -> {
                    val sid = data["id"]?.jsonPrimitive?.content ?: id
                    val title = data["title"]?.jsonPrimitive?.content
                    val dir = data["location"]?.jsonObject?.get("directory")?.jsonPrimitive?.content
                    _events.emit(OpenCodeEvent.SessionUpdated(sid, title, dir))
                }
                "session.deleted" -> {
                    val sid = data["id"]?.jsonPrimitive?.content ?: id
                    _events.emit(OpenCodeEvent.SessionDeleted(sid))
                }
                "session.status" -> {
                    val sid = data["sessionID"]?.jsonPrimitive?.content ?: data["id"]?.jsonPrimitive?.content.orEmpty()
                    val status = data["status"]?.jsonPrimitive?.content.orEmpty()
                    val isBusy = status == "busy" || status == "running"
                    _events.emit(OpenCodeEvent.SessionStatus(sid, isBusy, System.currentTimeMillis()))
                }
                "message.updated" -> {
                    val sid = data["sessionID"]?.jsonPrimitive?.content.orEmpty()
                    val mid = data["id"]?.jsonPrimitive?.content.orEmpty()
                    _events.emit(OpenCodeEvent.MessageUpdated(sid, mid, data))
                }
                "session.text.delta", "message.part.delta" -> {
                    val sid = data["sessionID"]?.jsonPrimitive?.content.orEmpty()
                    val mid = data["messageID"]?.jsonPrimitive?.content ?: data["id"]?.jsonPrimitive?.content.orEmpty()
                    val delta = data["delta"]?.jsonPrimitive?.content ?: data["text"]?.jsonPrimitive?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        _events.emit(OpenCodeEvent.TextDelta(sid, mid, delta))
                    }
                }
                "permission.asked" -> {
                    val rid = data["id"]?.jsonPrimitive?.content ?: id
                    val sid = data["sessionID"]?.jsonPrimitive?.content.orEmpty()
                    val tool = data["tool"]?.jsonPrimitive?.content ?: "Tool"
                    val input = data["input"]?.toString() ?: ""
                    _events.emit(OpenCodeEvent.PermissionAsked(rid, sid, tool, input))
                }
                "form.created", "question.asked" -> {
                    val fid = data["id"]?.jsonPrimitive?.content ?: id
                    val sid = data["sessionID"]?.jsonPrimitive?.content.orEmpty()
                    val title = data["title"]?.jsonPrimitive?.content ?: "Question"
                    val questions = data["questions"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    _events.emit(OpenCodeEvent.FormCreated(fid, sid, title, questions))
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        streamJob?.cancel()
        streamJob = null
        _connectionState.value = ConnectionState.OFFLINE
    }
}
