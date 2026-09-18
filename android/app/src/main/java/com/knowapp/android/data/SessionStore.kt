package com.knowapp.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the current session: the bearer token plus the small bits of user
 * context the login response already gives us (role, staff_type,
 * institution_id, full_name) — no separate "/me" round trip needed.
 * Persisted in EncryptedSharedPreferences since this is a live session
 * token for a financial-audit app.
 */
data class Session(
    val accessToken: String,
    val userId: Int,
    val role: String,
    val staffType: String?,
    val fullName: String,
    val institutionId: Int?,
)

class SessionStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "know_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _session = MutableStateFlow(loadFromDisk())
    val session: StateFlow<Session?> = _session

    private fun loadFromDisk(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userId = prefs.getInt(KEY_USER_ID, -1)
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        val fullName = prefs.getString(KEY_FULL_NAME, null) ?: return null
        return Session(
            accessToken = token,
            userId = userId,
            role = role,
            staffType = prefs.getString(KEY_STAFF_TYPE, null),
            fullName = fullName,
            institutionId = prefs.getInt(KEY_INSTITUTION_ID, -1).takeIf { it != -1 },
        )
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putInt(KEY_USER_ID, session.userId)
            .putString(KEY_ROLE, session.role)
            .putString(KEY_STAFF_TYPE, session.staffType)
            .putString(KEY_FULL_NAME, session.fullName)
            .putInt(KEY_INSTITUTION_ID, session.institutionId ?: -1)
            .apply()
        _session.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    fun currentToken(): String? = _session.value?.accessToken

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_ROLE = "role"
        const val KEY_STAFF_TYPE = "staff_type"
        const val KEY_FULL_NAME = "full_name"
        const val KEY_INSTITUTION_ID = "institution_id"
    }
}
