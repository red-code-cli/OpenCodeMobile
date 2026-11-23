package com.opencode.mobile.data

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.backend.AgentItem
import com.opencode.mobile.data.backend.ProjectItem
import com.opencode.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen(val depth: Int) {
    Main(0), Settings(1), Appearance(2), Language(2), Sessions(2), WhatsNew(2), Experimental(2), Voice(2), Servers(2)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = OpenCodeRepository(app.applicationContext, viewModelScope)

    // ---- navigation
    val stack = mutableStateListOf(Screen.Main)
    val top: Screen get() = stack.lastOrNull() ?: Screen.Main
    fun push(s: Screen) { if (top != s) stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // ---- server & connection state
    val activeServer by repo.activeProfile.collectAsState(null)
    val connectionState by repo.connectionState.collectAsState(ConnectionState.OFFLINE)
    var isConnectingServer by mutableStateOf(false)
    var serverConnectError by mutableStateOf<String?>(null)

    // ---- projects / agents / models
    private val rawProjects by repo.projects.collectAsState(emptyList())
    val projects: List<String> get() = rawProjects.map { it.name }.ifEmpty { listOf("root") }
    var selectedProject by mutableStateOf("root")

    private val rawAgents by repo.agents.collectAsState(emptyList())
    val agents: List<String> get() = rawAgents.filter { it.mode == "primary" && !it.hidden }.map { it.name }.ifEmpty { listOf("Build") }
    var agent by mutableStateOf("Build")

    val rawModels by repo.models.collectAsState(emptyList())
    val defaultModel by repo.defaultModel.collectAsState(null)
    var model by mutableStateOf(ModelOption("DeepSeek V4.1 Flash (Free)", "Tokenharbor", "deepseek-v4.1-flash:free", "High"))

    val commands by repo.commands.collectAsState(emptyList())
    val permissions by repo.permissions.collectAsState(emptyList())
    val forms by repo.forms.collectAsState(emptyList())
    val isVoiceSupported by repo.isVoiceSupported.collectAsState(false)

    // ---- settings & preferences
    var themeMode by mutableStateOf(ThemeMode.System)
    var themeName by mutableStateOf("OpenCode")
    var language by mutableStateOf("system")
    var returnKeySends by mutableStateOf(false)
    var agentSelection by mutableStateOf(true)
    var defaultAgent by mutableStateOf("Server Default")
    var voiceEnabled by mutableStateOf(false)

    val experimentKeys = listOf(
        "Hide Unused Models", "Background Call Shortcut", "OpenCode Session Delegator",
        "Share Screen with Agents", "Inbox Sidebar", "Session Live Updates", "Sound Effects", "Open Links by Server Address"
    )
    val experiments = mutableStateMapOf<String, Boolean>().apply {
        experimentKeys.forEach { put(it, it !in setOf("Inbox Sidebar", "Sound Effects")) }
    }
    var allExperiments by mutableStateOf(true)

    fun setAllExperiments(v: Boolean) {
        allExperiments = v
        experimentKeys.forEach { experiments[it] = v }
        viewModelScope.launch { repo.prefsStore.setExperiments(experiments.toMap()) }
    }

    // ---- sessions & chat
    val sessions = mutableStateListOf<Session>()
    var currentSession by mutableStateOf<String?>(null)
    val messages = mutableStateMapOf<String, List<Msg>>()

    val working: Boolean
        get() {
            val curr = currentSession ?: return false
            return sessions.firstOrNull { it.id == curr }?.running == true
        }

    init {
        // Observe sessions from repository
        viewModelScope.launch {
            repo.sessions.collect { list ->
                sessions.clear()
                sessions.addAll(list)
                if (selectedProject == "root" && list.isNotEmpty()) {
                    val firstProj = list.first().project
                    if (firstProj.isNotBlank()) selectedProject = firstProj
                }
            }
        }

        // Observe messages from repository
        viewModelScope.launch {
            repo.messages.collect { map ->
                messages.clear()
                messages.putAll(map)
            }
        }

        // Observe default model
        viewModelScope.launch {
            repo.defaultModel.collect { def ->
                if (def != null && model.modelId.isEmpty()) {
                    model = def
                }
            }
        }

        // Observe preferences
        viewModelScope.launch { repo.prefsStore.themeModeFlow.collect { themeMode = it } }
        viewModelScope.launch { repo.prefsStore.themeNameFlow.collect { themeName = it } }
        viewModelScope.launch { repo.prefsStore.languageFlow.collect { language = it } }
        viewModelScope.launch { repo.prefsStore.returnKeySendsFlow.collect { returnKeySends = it } }
        viewModelScope.launch { repo.prefsStore.agentSelectionFlow.collect { agentSelection = it } }
        viewModelScope.launch { repo.prefsStore.defaultAgentFlow.collect { defaultAgent = it } }
        viewModelScope.launch { repo.prefsStore.voiceEnabledFlow.collect { voiceEnabled = it } }
        viewModelScope.launch {
            repo.prefsStore.experimentsFlow.collect { map ->
                map.forEach { (k, v) -> experiments[k] = v }
            }
        }
    }

    // ---- Server Management
    fun connectNewServer(url: String, user: String, pass: String, pairingCode: String? = null) {
        val trimmedUrl = url.trim().trimEnd('/')
        if (trimmedUrl.isEmpty()) {
            serverConnectError = "Please enter server URL"
            return
        }
        isConnectingServer = true
        serverConnectError = null

        viewModelScope.launch {
            try {
                var password = pass.trim()
                if (pairingCode?.isNotBlank() == true) {
                    val pairRes = repo.serverStore.raceAddresses(listOf(trimmedUrl), "")
                    // Try redeem pairing code
                    val tokenRes = repo.v2Backend.redeemPairing(trimmedUrl, pairingCode)
                    if (tokenRes.isSuccess) {
                        password = tokenRes.getOrThrow()
                    }
                }

                val profile = ServerProfile(
                    id = UUID.randomUUID().toString().take(8),
                    name = trimmedUrl.removePrefix("http://").removePrefix("https://"),
                    urls = listOf(trimmedUrl),
                    username = user.ifBlank { "opencode" },
                    password = password,
                    activeUrl = trimmedUrl
                )
                repo.serverStore.saveProfile(profile)
                repo.connectServer(profile)
                isConnectingServer = false
            } catch (e: Exception) {
                isConnectingServer = false
                serverConnectError = e.message ?: "Connection failed"
            }
        }
    }

    fun removeServer(id: String) {
        repo.serverStore.deleteProfile(id)
        val active = repo.serverStore.getActiveProfile()
        if (active != null) repo.connectServer(active) else repo.refreshAll()
    }

    fun switchServer(profile: ServerProfile) {
        repo.serverStore.setActiveProfile(profile.id)
        repo.connectServer(profile)
    }

    // ---- Session actions
    fun newSession() {
        stop()
        currentSession = null
        repo.setActiveSession(null)
        stack.clear()
        stack.add(Screen.Main)
    }

    fun openSession(id: String) {
        sessions.firstOrNull { it.id == id }?.let {
            if (it.project.isNotBlank()) selectedProject = it.project
        }
        currentSession = id
        repo.setActiveSession(id)
        stack.clear()
        stack.add(Screen.Main)
    }

    fun send(text: String, attachments: List<String> = emptyList()) {
        val t = text.trim()
        if (t.isEmpty() && attachments.isEmpty()) return
        if (working) return

        viewModelScope.launch {
            var id = currentSession
            if (id == null) {
                // Find matching directory for selectedProject
                val proj = rawProjects.firstOrNull { it.name == selectedProject }
                val dir = proj?.directory ?: "/root"
                val session = repo.createSession(dir, title = t.take(28))
                if (session == null) return@launch
                id = session.id
                currentSession = id
                repo.setActiveSession(id)
            }

            repo.sendPrompt(
                sessionId = id,
                text = t,
                agent = agent,
                modelId = model.modelId.ifEmpty { model.name },
                providerId = model.provider.lowercase(),
                variant = model.variant,
                files = attachments
            )
        }
    }

    fun stop() {
        val curr = currentSession ?: return
        viewModelScope.launch {
            repo.interruptSession(curr)
        }
    }

    fun rename(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.renameSession(id, title.trim())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.deleteSession(id)
            if (currentSession == id) newSession()
        }
    }

    fun fork(id: String) {
        viewModelScope.launch {
            val forked = repo.forkSession(id)
            if (forked != null) openSession(forked.id)
        }
    }

    fun compact(id: String) {
        viewModelScope.launch {
            repo.compactSession(id)
        }
    }

    fun replyPermission(requestId: String, decision: String) {
        val curr = currentSession ?: return
        viewModelScope.launch {
            repo.replyPermission(requestId, curr, decision)
        }
    }

    fun replyForm(formId: String, answers: List<String>) {
        val curr = currentSession ?: return
        viewModelScope.launch {
            repo.replyForm(formId, curr, answers)
        }
    }

    // ---- Settings updates
    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        viewModelScope.launch { repo.prefsStore.setThemeMode(mode) }
    }

    fun updateThemeName(name: String) {
        themeName = name
        viewModelScope.launch { repo.prefsStore.setThemeName(name) }
    }

    fun updateLanguage(lang: String) {
        language = lang
        viewModelScope.launch { repo.prefsStore.setLanguage(lang) }
    }

    fun updateReturnKeySends(v: Boolean) {
        returnKeySends = v
        viewModelScope.launch { repo.prefsStore.setReturnKeySends(v) }
    }

    fun updateAgentSelection(v: Boolean) {
        agentSelection = v
        viewModelScope.launch { repo.prefsStore.setAgentSelection(v) }
    }

    fun updateDefaultAgent(v: String) {
        defaultAgent = v
        if (v != "Server Default") agent = v
        viewModelScope.launch { repo.prefsStore.setDefaultAgent(v) }
    }

    fun updateVoiceEnabled(v: Boolean) {
        voiceEnabled = v
        viewModelScope.launch { repo.prefsStore.setVoiceEnabled(v) }
    }
}
