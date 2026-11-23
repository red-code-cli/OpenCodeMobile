package com.opencode.mobile.data

import android.content.Context
import android.util.Base64
import com.opencode.mobile.data.backend.*
import com.opencode.mobile.data.net.EventStream
import com.opencode.mobile.data.net.OpenCodeEvent
import com.opencode.mobile.data.store.PrefsStore
import com.opencode.mobile.data.store.ServerStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

class OpenCodeRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val serverStore = ServerStore(context)
    val prefsStore = PrefsStore(context)
    private val v2Backend = V2Backend()
    private val v1Backend = V1Backend()
    private var currentBackend: OpenCodeBackend = v2Backend
    private val eventStream = EventStream()

    val connectionState: StateFlow<ConnectionState> = eventStream.connectionState

    private val _activeProfile = MutableStateFlow<ServerProfile?>(null)
    val activeProfile: StateFlow<ServerProfile?> = _activeProfile.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectItem>>(emptyList())
    val projects: StateFlow<List<ProjectItem>> = _projects.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Msg>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Msg>>> = _messages.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentItem>>(emptyList())
    val agents: StateFlow<List<AgentItem>> = _agents.asStateFlow()

    private val _models = MutableStateFlow<List<ModelOption>>(emptyList())
    val models: StateFlow<List<ModelOption>> = _models.asStateFlow()

    private val _defaultModel = MutableStateFlow<ModelOption?>(null)
    val defaultModel: StateFlow<ModelOption?> = _defaultModel.asStateFlow()

    private val _commands = MutableStateFlow<List<SlashCommand>>(emptyList())
    val commands: StateFlow<List<SlashCommand>> = _commands.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val permissions: StateFlow<List<PermissionRequest>> = _permissions.asStateFlow()

    private val _forms = MutableStateFlow<List<QuestionForm>>(emptyList())
    val forms: StateFlow<List<QuestionForm>> = _forms.asStateFlow()

    private val _isVoiceSupported = MutableStateFlow(false)
    val isVoiceSupported: StateFlow<Boolean> = _isVoiceSupported.asStateFlow()

    private var safetyNetJob: Job? = null
    private var activeSessionId: String? = null

    init {
        // Load active profile on launch
        val profile = serverStore.getActiveProfile()
        _activeProfile.value = profile
        if (profile != null) {
            connectServer(profile)
        }

        // Listen for SSE events
        scope.launch {
            eventStream.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun getAuthHeader(profile: ServerProfile): String {
        val userPass = "${profile.username}:${profile.password}"
        val encoded = Base64.encodeToString(userPass.toByteArray(), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    fun setActiveSession(sessionId: String?) {
        activeSessionId = sessionId
        if (sessionId != null) {
            loadMessages(sessionId)
        }
    }

    fun connectServer(profile: ServerProfile) {
        scope.launch {
            val authHeader = getAuthHeader(profile)
            val activeUrl = if (!profile.activeUrl.isNullOrBlank()) {
                profile.activeUrl
            } else {
                serverStore.raceAddresses(profile.urls, authHeader) ?: profile.urls.firstOrNull()?.trimEnd('/')
            }

            if (activeUrl == null) return@launch

            val updatedProfile = profile.copy(activeUrl = activeUrl, isConnected = true)
            serverStore.saveProfile(updatedProfile)
            _activeProfile.value = updatedProfile

            // Detect version
            val v2Info = v2Backend.getInfo(activeUrl, authHeader)
            val isV2 = v2Info.isSuccess
            currentBackend = if (isV2) v2Backend else v1Backend

            // Start SSE stream
            eventStream.start(scope, activeUrl, authHeader, isV2 = isV2)

            // Initial data sync
            refreshAll()
        }
    }

    fun refreshAll() {
        val profile = _activeProfile.value ?: return
        val url = profile.activeUrl ?: return
        val auth = getAuthHeader(profile)

        scope.launch {
            val projJob = async { currentBackend.getProjects(url, auth) }
            val sessJob = async { currentBackend.getSessions(url, auth) }
            val agentsJob = async { currentBackend.getAgents(url, auth) }
            val modelsJob = async { currentBackend.getModels(url, auth) }
            val defaultModelJob = async { currentBackend.getDefaultModel(url, auth) }
            val cmdJob = async { currentBackend.getCommands(url, auth) }
            val permJob = async { currentBackend.getPermissions(url, auth) }
            val formJob = async { currentBackend.getForms(url, auth) }
            val voiceJob = async { currentBackend.checkVoiceSupport(url, auth) }

            projJob.await().onSuccess { _projects.value = it }
            sessJob.await().onSuccess { _sessions.value = it }
            agentsJob.await().onSuccess { _agents.value = it }
            modelsJob.await().onSuccess { _models.value = it }
            defaultModelJob.await().onSuccess { _defaultModel.value = it }
            cmdJob.await().onSuccess { _commands.value = it }
            permJob.await().onSuccess { _permissions.value = it }
            formJob.await().onSuccess { _forms.value = it }
            voiceJob.await().onSuccess { _isVoiceSupported.value = it }

            activeSessionId?.let { loadMessages(it) }
        }
    }

    fun loadMessages(sessionId: String) {
        val profile = _activeProfile.value ?: return
        val url = profile.activeUrl ?: return
        val auth = getAuthHeader(profile)

        scope.launch {
            currentBackend.getMessages(url, auth, sessionId).onSuccess { list ->
                val currentMap = _messages.value.toMutableMap()
                val existing = currentMap[sessionId].orEmpty()
                // Retain optimistic message if not yet in server list
                val optimistic = existing.filter { it is UserMsg && it.isOptimistic && list.none { serverMsg -> serverMsg is UserMsg && serverMsg.text == it.text } }
                currentMap[sessionId] = list + optimistic
                _messages.value = currentMap
            }
        }
    }

    suspend fun createSession(directory: String?, title: String? = null): Session? {
        val profile = _activeProfile.value ?: return null
        val url = profile.activeUrl ?: return null
        val auth = getAuthHeader(profile)

        return currentBackend.createSession(url, auth, directory, title).getOrNull()?.also { session ->
            _sessions.value = listOf(session) + _sessions.value.filterNot { it.id == session.id }
        }
    }

    suspend fun renameSession(sessionId: String, title: String): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)

        val success = currentBackend.renameSession(url, auth, sessionId, title).getOrDefault(false)
        if (success) {
            _sessions.value = _sessions.value.map { if (it.id == sessionId) it.copy(title = title) else it }
        }
        return success
    }

    suspend fun deleteSession(sessionId: String): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)

        val success = currentBackend.deleteSession(url, auth, sessionId).getOrDefault(false)
        if (success) {
            _sessions.value = _sessions.value.filterNot { it.id == sessionId }
            val map = _messages.value.toMutableMap()
            map.remove(sessionId)
            _messages.value = map
        }
        return success
    }

    suspend fun forkSession(sessionId: String): Session? {
        val profile = _activeProfile.value ?: return null
        val url = profile.activeUrl ?: return null
        val auth = getAuthHeader(profile)

        return currentBackend.forkSession(url, auth, sessionId).getOrNull()?.also { newSession ->
            _sessions.value = listOf(newSession) + _sessions.value
            loadMessages(newSession.id)
        }
    }

    suspend fun redeemPairing(url: String, code: String): Result<String> = v2Backend.redeemPairing(url, code)

    suspend fun interruptSession(sessionId: String): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)

        val success = currentBackend.interruptSession(url, auth, sessionId).getOrDefault(false)
        if (success) {
            _sessions.value = _sessions.value.map { if (it.id == sessionId) it.copy(running = false) else it }
            stopSafetyNet()
        }
        return success
    }

    suspend fun compactSession(sessionId: String): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)
        return currentBackend.compactSession(url, auth, sessionId).getOrDefault(false)
    }

    suspend fun replyPermission(requestId: String, sessionId: String, decision: String): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)

        val success = currentBackend.replyPermission(url, auth, sessionId, requestId, decision).getOrDefault(false)
        if (success) {
            _permissions.value = _permissions.value.filterNot { it.id == requestId }
        }
        return success
    }

    suspend fun replyForm(formId: String, sessionId: String, answers: List<String>): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)

        val success = currentBackend.replyForm(url, auth, sessionId, formId, answers).getOrDefault(false)
        if (success) {
            _forms.value = _forms.value.filterNot { it.id == formId }
        }
        return success
    }

    suspend fun sendPrompt(
        sessionId: String,
        text: String,
        agent: String?,
        modelId: String?,
        providerId: String?,
        variant: String?,
        files: List<String> = emptyList()
    ): Boolean {
        val profile = _activeProfile.value ?: return false
        val url = profile.activeUrl ?: return false
        val auth = getAuthHeader(profile)

        // Optimistic UI update
        val tempId = "opt_" + UUID.randomUUID().toString().take(8)
        val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val optMsg = UserMsg(id = tempId, text = text, time = nowStr, files = files, isOptimistic = true)

        val currentMap = _messages.value.toMutableMap()
        currentMap[sessionId] = currentMap[sessionId].orEmpty() + optMsg
        _messages.value = currentMap

        // Mark session as running
        _sessions.value = _sessions.value.map { if (it.id == sessionId) it.copy(running = true) else it }
        startSafetyNet(sessionId)

        val res = currentBackend.sendPrompt(url, auth, sessionId, text, agent, modelId, providerId, variant, files)
        if (res.isFailure) {
            // Remove optimistic on hard failure
            val fallbackMap = _messages.value.toMutableMap()
            fallbackMap[sessionId] = fallbackMap[sessionId].orEmpty().filterNot { it.id == tempId }
            _messages.value = fallbackMap
            _sessions.value = _sessions.value.map { if (it.id == sessionId) it.copy(running = false) else it }
            stopSafetyNet()
            return false
        }
        return true
    }

    private fun handleEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.ServerConnected -> {
                refreshAll()
            }
            is OpenCodeEvent.SessionStatus -> {
                _sessions.value = _sessions.value.map {
                    if (it.id == event.sessionId) it.copy(running = event.isBusy) else it
                }
                if (!event.isBusy) {
                    stopSafetyNet()
                    loadMessages(event.sessionId)
                }
            }
            is OpenCodeEvent.SessionCreated -> {
                scope.launch {
                    val profile = _activeProfile.value ?: return@launch
                    val url = profile.activeUrl ?: return@launch
                    val auth = getAuthHeader(profile)
                    currentBackend.getSessions(url, auth).onSuccess { _sessions.value = it }
                }
            }
            is OpenCodeEvent.SessionUpdated -> {
                _sessions.value = _sessions.value.map {
                    if (it.id == event.sessionId) {
                        it.copy(
                            title = event.title ?: it.title,
                            directory = event.directory ?: it.directory
                        )
                    } else it
                }
            }
            is OpenCodeEvent.SessionDeleted -> {
                _sessions.value = _sessions.value.filterNot { it.id == event.sessionId }
                val map = _messages.value.toMutableMap()
                map.remove(event.sessionId)
                _messages.value = map
            }
            is OpenCodeEvent.TextDelta -> {
                // Streaming text chunk reducer
                val sid = event.sessionId
                val map = _messages.value.toMutableMap()
                val list = map[sid].orEmpty().toMutableList()
                val lastMsg = list.lastOrNull()

                if (lastMsg is AssistantMsg && (lastMsg.id == event.messageId || lastMsg.isStreaming)) {
                    val parts = lastMsg.parts.toMutableList()
                    val lastPart = parts.lastOrNull()
                    if (lastPart is Part.Text) {
                        parts[parts.lastIndex] = Part.Text(lastPart.text + event.delta)
                    } else {
                        parts.add(Part.Text(event.delta))
                    }
                    list[list.lastIndex] = lastMsg.copy(parts = parts, isStreaming = true)
                } else {
                    list.add(
                        AssistantMsg(
                            id = event.messageId,
                            parts = listOf(Part.Text(event.delta)),
                            isStreaming = true
                        )
                    )
                }
                map[sid] = list
                _messages.value = map
            }
            is OpenCodeEvent.MessageUpdated -> {
                loadMessages(event.sessionId)
            }
            is OpenCodeEvent.PermissionAsked -> {
                val req = PermissionRequest(
                    id = event.requestId,
                    sessionId = event.sessionId,
                    tool = event.tool,
                    input = event.input
                )
                _permissions.value = _permissions.value.filterNot { it.id == req.id } + req
            }
            is OpenCodeEvent.FormCreated -> {
                val form = QuestionForm(
                    id = event.formId,
                    sessionId = event.sessionId,
                    title = event.title,
                    questions = event.questions
                )
                _forms.value = _forms.value.filterNot { it.id == form.id } + form
            }
            else -> {}
        }
    }

    /**
     * Safety net: polls /message if session is busy and no events arrive for 2.5s.
     */
    private fun startSafetyNet(sessionId: String) {
        safetyNetJob?.cancel()
        safetyNetJob = scope.launch {
            while (isActive) {
                delay(2500)
                loadMessages(sessionId)
            }
        }
    }

    private fun stopSafetyNet() {
        safetyNetJob?.cancel()
        safetyNetJob = null
    }
}
