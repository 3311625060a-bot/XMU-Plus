package cn.edu.xmu.plus.auth

import kotlinx.serialization.Serializable

@Serializable
data class XmuUserProfile(
    val id: Long,
    val userNo: String,
    val name: String,
    val role: String = "学生",
    val department: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAt: Long = Long.MAX_VALUE,
    val secure: Boolean = true,
    val httpOnly: Boolean = true,
)

@Serializable
data class PersistedSession(
    val sessionId: String,
    val cookies: List<StoredCookie> = emptyList(),
    val profile: XmuUserProfile? = null,
    val savedAtMillis: Long = 0L,
)

@Serializable
data class SavedCredentials(
    val username: String,
    val password: String,
)

sealed interface AuthState {
    data object Checking : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(
        val profile: XmuUserProfile,
        val isRefreshing: Boolean = false,
    ) : AuthState
}

sealed interface LoginResult {
    data class Success(val profile: XmuUserProfile) : LoginResult
    data class CaptchaRequired(
        val ssoUrl: String,
        val username: String,
        val password: String,
        val reason: String,
    ) : LoginResult

    data class Error(
        val message: String,
        val canFallbackToWebView: Boolean = true,
    ) : LoginResult
}

class SessionExpiredException : Exception("会话已过期，请重新登录")
