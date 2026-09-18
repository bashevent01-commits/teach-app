package com.knowapp.android.data.repository

import com.knowapp.android.data.Session
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class LoginResult {
    data class Success(val session: Session) : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

class AuthRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) {
    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val response = api.login(mapOf("username" to username, "password" to password))
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext LoginResult.Failure("Empty response from server")
                val session = Session(
                    accessToken = body.accessToken,
                    userId = body.userId,
                    role = body.role,
                    staffType = body.staffType,
                    fullName = body.fullName,
                    institutionId = body.institutionId,
                )
                sessionStore.save(session)
                LoginResult.Success(session)
            } else {
                LoginResult.Failure(errorMessageFor(response.code()))
            }
        } catch (e: java.io.IOException) {
            LoginResult.Failure("Couldn't reach the server. Check your connection and try again — the backend may also be waking up from sleep, which can take up to a minute.")
        }
    }

    private fun errorMessageFor(code: Int): String = when (code) {
        401 -> "Incorrect username or password."
        423 -> "Too many failed attempts. Try again in a few minutes."
        403 -> "This account is disabled. Contact your super admin."
        else -> "Something went wrong (code $code). Please try again."
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            api.logout()
        } catch (_: Exception) {
            // Logging out clears local state regardless of whether the
            // network call succeeds — same behavior as the web app's logout.
        }
        sessionStore.clear()
    }

    val session get() = sessionStore.session
}
