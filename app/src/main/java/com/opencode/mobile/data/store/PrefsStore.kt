package com.opencode.mobile.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.opencode.mobile.data.ModelOption
import com.opencode.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opencode_settings")

class PrefsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_NAME = stringPreferencesKey("theme_name")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_RETURN_KEY_SENDS = booleanPreferencesKey("return_key_sends")
        val KEY_AGENT_SELECTION = booleanPreferencesKey("agent_selection")
        val KEY_DEFAULT_AGENT = stringPreferencesKey("default_agent")
        val KEY_VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val KEY_FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val KEY_RECENT_MODELS = stringPreferencesKey("recent_models")
        val KEY_EXPERIMENTS = stringPreferencesKey("experiments_json")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            when (prefs[KEY_THEME_MODE]) {
                "Light" -> ThemeMode.Light
                "Dark" -> ThemeMode.Dark
                else -> ThemeMode.System
            }
        }

    val themeNameFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME_NAME] ?: "OpenCode" }

    val languageFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LANGUAGE] ?: "system" }

    val returnKeySendsFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_RETURN_KEY_SENDS] ?: false }

    val agentSelectionFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AGENT_SELECTION] ?: true }

    val defaultAgentFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_AGENT] ?: "Server Default" }

    val voiceEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_VOICE_ENABLED] ?: false }

    val favoriteModelsFlow: Flow<List<ModelOption>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[KEY_FAVORITE_MODELS] ?: return@map emptyList()
            try { json.decodeFromString<List<ModelOption>>(raw) } catch (_: Exception) { emptyList() }
        }

    val recentModelsFlow: Flow<List<ModelOption>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[KEY_RECENT_MODELS] ?: return@map emptyList()
            try { json.decodeFromString<List<ModelOption>>(raw) } catch (_: Exception) { emptyList() }
        }

    val experimentsFlow: Flow<Map<String, Boolean>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[KEY_EXPERIMENTS] ?: return@map emptyMap()
            try { json.decodeFromString<Map<String, Boolean>>(raw) } catch (_: Exception) { emptyMap() }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setThemeName(name: String) {
        context.dataStore.edit { it[KEY_THEME_NAME] = name }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setReturnKeySends(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RETURN_KEY_SENDS] = enabled }
    }

    suspend fun setAgentSelection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AGENT_SELECTION] = enabled }
    }

    suspend fun setDefaultAgent(agent: String) {
        context.dataStore.edit { it[KEY_DEFAULT_AGENT] = agent }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VOICE_ENABLED] = enabled }
    }

    suspend fun setFavoriteModels(models: List<ModelOption>) {
        context.dataStore.edit { it[KEY_FAVORITE_MODELS] = json.encodeToString(models) }
    }

    suspend fun setRecentModels(models: List<ModelOption>) {
        context.dataStore.edit { it[KEY_RECENT_MODELS] = json.encodeToString(models) }
    }

    suspend fun setExperiments(experiments: Map<String, Boolean>) {
        context.dataStore.edit { it[KEY_EXPERIMENTS] = json.encodeToString(experiments) }
    }
}
