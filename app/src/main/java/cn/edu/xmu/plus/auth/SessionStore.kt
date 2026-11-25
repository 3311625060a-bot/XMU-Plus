package cn.edu.xmu.plus.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "xmu_plus_auth_prefs")

class SessionStore(private val context: Context) {

    private object Keys {
        val ENCRYPTED_SESSION = stringPreferencesKey("encrypted_session")
        val ENCRYPTED_CREDENTIALS = stringPreferencesKey("encrypted_credentials")
        val REMEMBER_CREDENTIALS = booleanPreferencesKey("remember_credentials")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val inMemorySession = AtomicReference<PersistedSession?>(null)

    fun currentSessionSnapshot(): PersistedSession? = inMemorySession.get()

    fun setMemorySession(session: PersistedSession?) {
        inMemorySession.set(session)
    }

    suspend fun loadSession(): PersistedSession? {
        inMemorySession.get()?.let { return it }
        val prefs = context.authDataStore.data.first()
        val encrypted = prefs[Keys.ENCRYPTED_SESSION] ?: return null
        val decrypted = KeystoreCrypto.decrypt(encrypted) ?: return null
        val parsed = runCatching {
            json.decodeFromString<PersistedSession>(decrypted)
        }.getOrNull()
        inMemorySession.set(parsed)
        return parsed
    }

    suspend fun saveSession(session: PersistedSession) {
        inMemorySession.set(session)
        val encoded = json.encodeToString(session)
        val encrypted = KeystoreCrypto.encrypt(encoded)
        context.authDataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_SESSION] = encrypted
        }
    }

    suspend fun updateSessionId(newSessionId: String) {
        if (newSessionId.isBlank()) return
        val existing = loadSession() ?: return
        if (existing.sessionId == newSessionId) return
        val updatedCookies = existing.cookies
            .filterNot { it.name == "session" } +
            StoredCookie(
                name = "session",
                value = newSessionId,
                domain = "lnt.xmu.edu.cn",
            )
        saveSession(
            existing.copy(
                sessionId = newSessionId,
                cookies = updatedCookies,
                savedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearSession() {
        inMemorySession.set(null)
        context.authDataStore.edit { prefs ->
            prefs.remove(Keys.ENCRYPTED_SESSION)
        }
    }

    suspend fun loadCredentials(): SavedCredentials? {
        val prefs = context.authDataStore.data.first()
        val encrypted = prefs[Keys.ENCRYPTED_CREDENTIALS] ?: return null
        val decrypted = KeystoreCrypto.decrypt(encrypted) ?: return null
        return runCatching {
            json.decodeFromString<SavedCredentials>(decrypted)
        }.getOrNull()
    }

    suspend fun saveCredentials(username: String, password: String) {
        val encoded = json.encodeToString(SavedCredentials(username = username, password = password))
        val encrypted = KeystoreCrypto.encrypt(encoded)
        context.authDataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_CREDENTIALS] = encrypted
        }
    }

    suspend fun clearCredentials() {
        context.authDataStore.edit { prefs ->
            prefs.remove(Keys.ENCRYPTED_CREDENTIALS)
        }
    }

    suspend fun isRememberCredentialsEnabled(): Boolean {
        val prefs = context.authDataStore.data.first()
        return prefs[Keys.REMEMBER_CREDENTIALS] ?: true
    }

    suspend fun setRememberCredentialsEnabled(enabled: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_CREDENTIALS] = enabled
        }
        if (!enabled) {
            clearCredentials()
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.authDataStore.data.first()
        val existing = prefs[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        context.authDataStore.edit { editPrefs ->
            editPrefs[Keys.DEVICE_ID] = created
        }
        return created
    }
}
