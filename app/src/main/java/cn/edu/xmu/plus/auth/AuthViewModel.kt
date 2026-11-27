package cn.edu.xmu.plus.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebViewLoginRequest(
    val ssoUrl: String,
    val username: String,
    val password: String,
    val reason: String,
)

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val rememberCredentials: Boolean = true,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val webViewRequest: WebViewLoginRequest? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    val sessionStore = SessionStore(application.applicationContext)
    val authClient = XmuAuthClient(sessionStore)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    init {
        restoreSessionAndCredentials()
    }

    private fun restoreSessionAndCredentials() {
        viewModelScope.launch {
            val remember = sessionStore.isRememberCredentialsEnabled()
            val savedCreds = if (remember) sessionStore.loadCredentials() else null
            _loginUiState.update {
                it.copy(
                    rememberCredentials = remember,
                    username = savedCreds?.username ?: it.username,
                    password = savedCreds?.password ?: it.password,
                )
            }

            val cachedSession = sessionStore.loadSession()
            val cachedProfile = cachedSession?.profile
            if (cachedSession != null && cachedSession.sessionId.isNotBlank() && cachedProfile != null) {
                _authState.value = AuthState.LoggedIn(profile = cachedProfile, isRefreshing = true)
            }

            val verified = authClient.verifyStoredSession()
            _authState.value = verified
            if (verified == AuthState.LoggedOut) {
                launch(Dispatchers.IO) {
                    runCatching { authClient.prewarmSso() }
                }
            }
        }
    }

    fun updateUsername(value: String) {
        _loginUiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _loginUiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun toggleRememberCredentials(enabled: Boolean) {
        _loginUiState.update { it.copy(rememberCredentials = enabled) }
        viewModelScope.launch {
            sessionStore.setRememberCredentialsEnabled(enabled)
        }
    }

    fun clearError() {
        _loginUiState.update { it.copy(errorMessage = null) }
    }

    fun prewarmLogin() {
        viewModelScope.launch {
            authClient.prewarmSso()
        }
    }

    fun submitNativeLogin() {
        val state = _loginUiState.value
        val user = state.username.trim()
        val pass = state.password
        if (user.isEmpty() || pass.isEmpty()) {
            _loginUiState.update { it.copy(errorMessage = "请输入学号/工号与统一身份认证密码") }
            return
        }

        viewModelScope.launch {
            _loginUiState.update {
                it.copy(
                    isLoading = true,
                    statusMessage = "正在连接厦大统一身份认证…",
                    errorMessage = null,
                )
            }
            when (val result = authClient.loginNative(user, pass)) {
                is LoginResult.Success -> {
                    _authState.value = AuthState.LoggedIn(profile = result.profile, isRefreshing = false)
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = null,
                            webViewRequest = null,
                        )
                    }
                    launch(Dispatchers.IO) {
                        persistCredentialsIfEnabled(user, pass)
                    }
                }

                is LoginResult.CaptchaRequired -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = null,
                            webViewRequest = WebViewLoginRequest(
                                ssoUrl = result.ssoUrl,
                                username = result.username,
                                password = result.password,
                                reason = result.reason,
                            ),
                        )
                    }
                }

                is LoginResult.Error -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun openWebViewDirectly() {
        val state = _loginUiState.value
        _loginUiState.update {
            it.copy(
                errorMessage = null,
                webViewRequest = WebViewLoginRequest(
                    ssoUrl = XmuAuthClient.SSO_ENTRY_URL,
                    username = state.username.trim(),
                    password = state.password,
                    reason = "请在网页中完成统一身份认证（支持滑块与短信验证）",
                ),
            )
        }
    }

    fun dismissWebView() {
        _loginUiState.update { it.copy(webViewRequest = null, isLoading = false, statusMessage = null) }
    }

    fun onWebViewCookiesCaptured(rawCookieHeader: String) {
        val currentState = _loginUiState.value
        viewModelScope.launch {
            _loginUiState.update {
                it.copy(
                    isLoading = true,
                    statusMessage = "正在校验并保存会话…",
                    webViewRequest = null,
                )
            }
            when (val result = authClient.completeWebViewLogin(rawCookieHeader)) {
                is LoginResult.Success -> {
                    _authState.value = AuthState.LoggedIn(profile = result.profile, isRefreshing = false)
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = null,
                        )
                    }
                    launch(Dispatchers.IO) {
                        persistCredentialsIfEnabled(currentState.username.trim(), currentState.password)
                    }
                }

                is LoginResult.Error -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }

                is LoginResult.CaptchaRequired -> Unit
            }
        }
    }

    fun refreshProfile() {
        val current = _authState.value
        if (current !is AuthState.LoggedIn) return
        viewModelScope.launch {
            _authState.value = current.copy(isRefreshing = true)
            val verified = authClient.verifyStoredSession()
            _authState.value = verified
            if (verified == AuthState.LoggedOut) {
                launch(Dispatchers.IO) {
                    runCatching { authClient.prewarmSso() }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authClient.logout()
            _authState.value = AuthState.LoggedOut
        }
    }

    private suspend fun persistCredentialsIfEnabled(username: String, password: String) {
        if (_loginUiState.value.rememberCredentials && username.isNotBlank() && password.isNotBlank()) {
            sessionStore.saveCredentials(username, password)
        }
    }
}
