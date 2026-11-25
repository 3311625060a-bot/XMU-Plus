package cn.edu.xmu.plus.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.xmu.plus.auth.AuthState
import cn.edu.xmu.plus.auth.LoginUiState
import cn.edu.xmu.plus.auth.WebViewLoginRequest
import cn.edu.xmu.plus.auth.XmuAuthClient
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedIconButton
import cn.edu.xmu.plus.ui.components.FrostedToggleSwitch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    authState: AuthState,
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleRememberCredentials: (Boolean) -> Unit,
    onPrewarmLogin: () -> Unit,
    onSubmitNativeLogin: () -> Unit,
    onOpenWebViewDirectly: () -> Unit,
    onDismissWebView: () -> Unit,
    onWebViewCookiesCaptured: (String) -> Unit,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        onPrewarmLogin()
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn && !authState.isRefreshing) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientGlowBackground(animate = false)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Top navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrostedIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "统一身份认证",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "厦门大学 · ids.xmu.edu.cn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Editorial Hero Card
            FrostedGlassCard(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "连接数字化教学平台",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "原生 AES 加密直连登录，遇风控滑块自动切出内置安全网页，会话由 Keystore 硬件加密保存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Credentials Card
            FrostedGlassCard(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChange,
                        label = { Text("学号 / 工号") },
                        placeholder = { Text("请输入厦大学号或工号") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Badge, contentDescription = null)
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = { Text("统一身份认证密码") },
                        placeholder = { Text("请输入密码") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onSubmitNativeLogin()
                            },
                        ),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "记住账号密码",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "使用 Android Keystore AES-256-GCM 硬件加密存储",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FrostedToggleSwitch(
                                checked = uiState.rememberCredentials,
                                onCheckedChange = onToggleRememberCredentials,
                                enabled = !uiState.isLoading,
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSubmitNativeLogin()
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (uiState.isLoading) {
                            LoadingIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(uiState.statusMessage ?: "正在登录…")
                        } else {
                            Text("安全登录")
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            keyboardController?.hide()
                            onOpenWebViewDirectly()
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("网页认证登录（滑块 / 短信验证入口）")
                    }
                }
            }
        }

        // In-App WebView Fallback Overlay for CAPTCHA / Interactive SSO
        AnimatedVisibility(
            visible = uiState.webViewRequest != null,
            enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
        ) {
            uiState.webViewRequest?.let { request ->
                SsoWebViewOverlay(
                    request = request,
                    onCookiesCaptured = onWebViewCookiesCaptured,
                    onDismiss = onDismissWebView,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SsoWebViewOverlay(
    request: WebViewLoginRequest,
    onCookiesCaptured: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var pageProgress by remember { mutableIntStateOf(10) }
    var currentHost by remember { mutableStateOf("ids.xmu.edu.cn") }
    val completed = remember { AtomicBoolean(false) }
    val prefilled = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrostedIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭网页认证",
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "统一身份认证安全验证",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${request.reason} · $currentHost",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()

                    WebView(context).apply {
                        webViewRef = this
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = XmuAuthClient.MOBILE_UA

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                val parsed = runCatching { android.net.Uri.parse(url.orEmpty()) }.getOrNull()
                                parsed?.host?.takeIf { it.isNotBlank() }?.let { currentHost = it }
                                checkAndFinishIfAuthenticated(url, cookieManager, completed, onCookiesCaptured)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                requestNav: WebResourceRequest?,
                            ): Boolean {
                                val targetUrl = requestNav?.url?.toString()
                                if (checkAndFinishIfAuthenticated(targetUrl, cookieManager, completed, onCookiesCaptured)) {
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val u = url.orEmpty()
                                val parsed = runCatching { android.net.Uri.parse(u) }.getOrNull()
                                parsed?.host?.takeIf { it.isNotBlank() }?.let { currentHost = it }

                                if (checkAndFinishIfAuthenticated(u, cookieManager, completed, onCookiesCaptured)) {
                                    return
                                }

                                // Pre-fill username & password in #pwdFromId once so user only slides captcha
                                if (u.contains("ids.xmu.edu.cn/authserver/login") &&
                                    request.username.isNotBlank() &&
                                    request.password.isNotBlank() &&
                                    prefilled.compareAndSet(false, true)
                                ) {
                                    val safeUser = JSONObject.quote(request.username)
                                    val safePass = JSONObject.quote(request.password)
                                    val js = """
                                        (function() {
                                            var form = document.getElementById('pwdFromId');
                                            if (!form) {
                                                var clltInput = document.querySelector('form input[value="userNameLogin"]');
                                                form = clltInput ? clltInput.closest('form') : null;
                                            }
                                            if (!form) return;
                                            var u = form.querySelector('#username, input[name="username"]');
                                            var p = form.querySelector('#password, input[type="password"]');
                                            if (u && !u.value) {
                                                u.value = $safeUser;
                                                u.dispatchEvent(new Event('input', { bubbles: true }));
                                            }
                                            if (p && !p.value) {
                                                p.value = $safePass;
                                                p.dispatchEvent(new Event('input', { bubbles: true }));
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(js, null)
                                }
                            }
                        }

                        loadUrl(request.ssoUrl)
                    }
                },
            )
        }
    }
}

private fun checkAndFinishIfAuthenticated(
    url: String?,
    cookieManager: CookieManager,
    completed: AtomicBoolean,
    onCookiesCaptured: (String) -> Unit,
): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
    val host = uri.host.orEmpty()
    val path = uri.path.orEmpty()

    val landedOnTronClass =
        (host == "lnt.xmu.edu.cn" && path != "/login" && !path.startsWith("/auth")) ||
            host == "c-mobile.xmu.edu.cn"

    if (!landedOnTronClass) return false

    cookieManager.flush()
    val rawCookie = cookieManager.getCookie(XmuAuthClient.LNT_BASE_URL).orEmpty()
    if (rawCookie.contains("session=")) {
        if (completed.compareAndSet(false, true)) {
            onCookiesCaptured(rawCookie)
        }
        return true
    }
    return false
}
