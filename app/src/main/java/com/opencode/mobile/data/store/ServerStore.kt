package com.opencode.mobile.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.opencode.mobile.data.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "opencode_secure_servers",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("opencode_servers_fallback", Context.MODE_PRIVATE)
        }
    }

    private val KEY_SERVERS = "saved_servers"
    private val KEY_ACTIVE_SERVER_ID = "active_server_id"

    fun getProfiles(): List<ServerProfile> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ServerProfile>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfile(profile: ServerProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(current)).apply()
        if (getActiveProfile() == null || current.size == 1) {
            setActiveProfile(profile.id)
        }
    }

    fun deleteProfile(id: String) {
        val current = getProfiles().filterNot { it.id == id }
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(current)).apply()
        if (getActiveServerId() == id) {
            val next = current.firstOrNull()?.id
            setActiveProfile(next)
        }
    }

    fun getActiveServerId(): String? = prefs.getString(KEY_ACTIVE_SERVER_ID, null)

    fun setActiveProfile(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_SERVER_ID, id).apply()
    }

    fun getActiveProfile(): ServerProfile? {
        val activeId = getActiveServerId() ?: return getProfiles().firstOrNull()
        return getProfiles().firstOrNull { it.id == activeId } ?: getProfiles().firstOrNull()
    }

    fun updateActiveUrl(profileId: String, url: String) {
        val profile = getProfiles().firstOrNull { it.id == profileId } ?: return
        saveProfile(profile.copy(activeUrl = url))
    }

    /**
     * Races all candidate addresses for a server in parallel.
     * OpenCode tries all addresses at once and uses whichever answers first.
     */
    suspend fun raceAddresses(urls: List<String>, basicAuthHeader: String): String? = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext null
        if (urls.size == 1) {
            val single = urls.first().trim().trimEnd('/')
            return@withContext if (checkHealth(single, basicAuthHeader)) single else null
        }

        coroutineScope {
            val jobs = urls.map { rawUrl ->
                val normalized = rawUrl.trim().trimEnd('/')
                async {
                    if (checkHealth(normalized, basicAuthHeader)) normalized else null
                }
            }
            val results = jobs.awaitAll()
            results.firstOrNull { it != null }
        }
    }

    private fun checkHealth(baseUrl: String, authHeader: String): Boolean {
        // Try V2 /api/info
        try {
            val reqV2 = Request.Builder()
                .url("$baseUrl/api/info")
                .header("Authorization", authHeader)
                .build()
            httpClient.newCall(reqV2).execute().use { resp ->
                if (resp.isSuccessful) return true
            }
        } catch (_: Exception) {}

        // Try V1 /global/health
        try {
            val reqV1 = Request.Builder()
                .url("$baseUrl/global/health")
                .header("Authorization", authHeader)
                .build()
            httpClient.newCall(reqV1).execute().use { resp ->
                if (resp.isSuccessful) return true
            }
        } catch (_: Exception) {}

        return false
    }
}
