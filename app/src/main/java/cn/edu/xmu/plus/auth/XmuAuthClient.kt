package cn.edu.xmu.plus.auth

import android.util.Log
import android.webkit.CookieManager
import cn.edu.xmu.plus.debug.DebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CasFormFields(
    val execution: String,
    val lt: String,
    val dllt: String,
    val cllt: String,
    val eventId: String,
    val pwdEncryptSalt: String,
)

class XmuAuthClient(
    private val sessionStore: SessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val apiClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val snapshot = sessionStore.currentSessionSnapshot()
            val builder = original.newBuilder()
                .header("User-Agent", MOBILE_UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept-Language", "zh-Hans")

            if (snapshot != null && snapshot.sessionId.isNotBlank()) {
                builder.header("x-session-id", snapshot.sessionId)
                val cookiePairs = LinkedHashMap<String, String>()
                snapshot.cookies.forEach { cookie ->
                    if (cookie.name.isNotBlank() && cookie.value.isNotBlank()) {
                        cookiePairs[cookie.name] = cookie.value
                    }
                }
                cookiePairs["session"] = snapshot.sessionId
                val cookieHeader = cookiePairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
                builder.header("Cookie", cookieHeader)
            }

            val startMs = System.currentTimeMillis()
            val response = chain.proceed(builder.build())
            val elapsedMs = System.currentTimeMillis() - startMs
            val path = original.url.encodedPath
            if (DebugLogStore.isEnabled() && path != "/api/login") {
                val query = original.url.encodedQuery?.let { "?$it" }.orEmpty()
                val level = if (response.code >= 400) "ERR" else "INFO"
                DebugLogStore.log("NET", "${original.method} $path$query -> ${response.code} (${elapsedMs}ms)", level)
                if (response.code >= 400 ||
                    path.startsWith("/api/radar/rollcalls") ||
                    path.startsWith("/api/rollcall/") ||
                    path.startsWith("/api/course/activities/")
                ) {
                    val snippet = runCatching { response.peekBody(512).string() }.getOrNull()
                    if (!snippet.isNullOrBlank()) {
                        DebugLogStore.log("NET", "Body: ${snippet.take(320)}", level)
                    }
                }
            }
            if (path.startsWith("/api/") && path != "/api/logout") {
                val refreshedHeader = response.header("x-session-id")
                val refreshedFromCookie = response.headers("Set-Cookie")
                    .asSequence()
                    .mapNotNull { parseSessionCookieValue(it) }
                    .firstOrNull()
                val nextSessionId = refreshedHeader?.takeIf { it.isNotBlank() } ?: refreshedFromCookie
                if (!nextSessionId.isNullOrBlank() && nextSessionId != snapshot?.sessionId) {
                    scope.launch {
                        sessionStore.updateSessionId(nextSessionId)
                    }
                }
            }
            response
        }
        .build()

    suspend fun loginNative(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val trimmedUser = username.trim()
        if (trimmedUser.isEmpty() || password.isEmpty()) {
            return@withContext LoginResult.Error("请输入学号和密码", canFallbackToWebView = false)
        }

        val cookieJar = InMemoryCookieJar()
        val ssoClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        try {
            // 1. Enter TronClass login -> Keycloak -> CAS login page
            val casPageRequest = Request.Builder()
                .url(SSO_ENTRY_URL)
                .header("User-Agent", MOBILE_UA)
                .get()
                .build()

            val (casPostUrl, casHtml) = ssoClient.newCall(casPageRequest).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                resp.request.url.toString() to bodyStr
            }

            val form = parseCasPwdForm(casHtml)
                ?: return@withContext LoginResult.CaptchaRequired(
                    ssoUrl = SSO_ENTRY_URL,
                    username = trimmedUser,
                    password = password,
                    reason = "正在切换至统一身份认证网页完成登录",
                )

            // 2. Check whether CAS requires slider/image captcha for this account
            val encodedUser = URLEncoder.encode(trimmedUser, "UTF-8")
            val captchaCheckUrl =
                "https://ids.xmu.edu.cn/authserver/checkNeedCaptcha.htl?username=$encodedUser&_=${System.currentTimeMillis()}"
            val captchaReq = Request.Builder()
                .url(captchaCheckUrl)
                .header("User-Agent", MOBILE_UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", casPostUrl)
                .get()
                .build()

            val needCaptcha = ssoClient.newCall(captchaReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                runCatching {
                    json.parseToJsonElement(raw).jsonObject["isNeed"]?.jsonPrimitive?.booleanOrNull ?: false
                }.getOrDefault(false)
            }

            if (needCaptcha) {
                return@withContext LoginResult.CaptchaRequired(
                    ssoUrl = SSO_ENTRY_URL,
                    username = trimmedUser,
                    password = password,
                    reason = "统一身份认证触发安全校验，请在下方页面完成验证",
                )
            }

            // 3. Encrypt password using CAS AES-128-CBC salt & submit #pwdFromId
            val encryptedPassword = CasCrypto.encryptPassword(password, form.pwdEncryptSalt)
            val formBody = FormBody.Builder()
                .add("username", trimmedUser)
                .add("password", encryptedPassword)
                .add("captcha", "")
                .add("_eventId", form.eventId)
                .add("cllt", form.cllt)
                .add("dllt", form.dllt)
                .add("lt", form.lt)
                .add("execution", form.execution)
                .build()

            val loginReq = Request.Builder()
                .url(casPostUrl)
                .header("User-Agent", MOBILE_UA)
                .header("Origin", "https://ids.xmu.edu.cn")
                .header("Referer", casPostUrl)
                .post(formBody)
                .build()

            val (finalUrl, finalHost, finalHtml, responseSessionHeader) = ssoClient.newCall(loginReq).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                val reqUrl = resp.request.url
                Quad(
                    reqUrl.toString(),
                    reqUrl.host,
                    html,
                    resp.header("x-session-id"),
                )
            }

            if (finalHost.contains("ids.xmu.edu.cn")) {
                val errorTip = extractCasErrorTip(finalHtml)
                if (errorTip != null && (errorTip.contains("验证码") || errorTip.contains("滑块"))) {
                    return@withContext LoginResult.CaptchaRequired(
                        ssoUrl = SSO_ENTRY_URL,
                        username = trimmedUser,
                        password = password,
                        reason = "需要完成安全验证，请通过内置认证页继续",
                    )
                }
                return@withContext LoginResult.Error(
                    message = errorTip ?: "学号或密码错误，请核对后重试",
                    canFallbackToWebView = true,
                )
            }

            val lntCookies = cookieJar.cookiesForHost("lnt.xmu.edu.cn")
            if (finalHost.contains("lnt.xmu.edu.cn") ||
                finalHost.contains("c-mobile.xmu.edu.cn") ||
                lntCookies.any { it.name == "session" && it.value.isNotBlank() }
            ) {
                val sessionCookie = lntCookies.firstOrNull { it.name == "session" }?.value
                val sessionId = sessionCookie?.takeIf { it.isNotBlank() }
                    ?: responseSessionHeader?.takeIf { it.isNotBlank() }

                if (sessionId.isNullOrBlank()) {
                    return@withContext LoginResult.CaptchaRequired(
                        ssoUrl = SSO_ENTRY_URL,
                        username = trimmedUser,
                        password = password,
                        reason = "未获取到有效会话凭据，请通过网页认证登录",
                    )
                }

                val storedCookies = lntCookies.map {
                    StoredCookie(
                        name = it.name,
                        value = it.value,
                        domain = it.domain,
                        path = it.path,
                        expiresAt = it.expiresAt,
                        secure = it.secure,
                        httpOnly = it.httpOnly,
                    )
                }
                sessionStore.saveSession(
                    PersistedSession(
                        sessionId = sessionId,
                        cookies = storedCookies,
                        profile = null,
                        savedAtMillis = System.currentTimeMillis(),
                    ),
                )

                val profileResult = fetchProfile()
                return@withContext profileResult.fold(
                    onSuccess = { profile ->
                        sessionStore.saveSession(
                            PersistedSession(
                                sessionId = sessionId,
                                cookies = storedCookies,
                                profile = profile,
                                savedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        LoginResult.Success(profile)
                    },
                    onFailure = { err ->
                        if (DebugLogStore.isEnabled()) {
                            Log.w("XmuAuthDiag", "loginNative verify profile failed: ${err.javaClass.simpleName} ${err.message}")
                            DebugLogStore.log("AUTH", "loginNative verify failed: ${err.javaClass.simpleName}", "ERR")
                        }
                        sessionStore.clearSession()
                        LoginResult.CaptchaRequired(
                            ssoUrl = SSO_ENTRY_URL,
                            username = trimmedUser,
                            password = password,
                            reason = "会话校验未通过，请通过内置网页完成认证",
                        )
                    },
                )
            }

            return@withContext LoginResult.CaptchaRequired(
                ssoUrl = finalUrl.ifBlank { SSO_ENTRY_URL },
                username = trimmedUser,
                password = password,
                reason = "请通过内置认证页完成登录",
            )
        } catch (e: IOException) {
            return@withContext LoginResult.Error(
                message = "网络连接失败，请检查网络后重试",
                canFallbackToWebView = true,
            )
        } catch (e: Exception) {
            return@withContext LoginResult.Error(
                message = "登录过程出现异常，可尝试网页认证登录",
                canFallbackToWebView = true,
            )
        }
    }

    suspend fun completeWebViewLogin(rawCookieHeader: String): LoginResult = withContext(Dispatchers.IO) {
        val parsedCookies = parseRawCookieHeader(rawCookieHeader, "lnt.xmu.edu.cn")
        val sessionId = parsedCookies.firstOrNull { it.name == "session" }?.value
        if (sessionId.isNullOrBlank()) {
            return@withContext LoginResult.Error("未能从网页会话中读取到登录凭据，请重试")
        }

        val session = PersistedSession(
            sessionId = sessionId,
            cookies = parsedCookies,
            profile = null,
            savedAtMillis = System.currentTimeMillis(),
        )
        sessionStore.saveSession(session)

        return@withContext fetchProfile().fold(
            onSuccess = { profile ->
                sessionStore.saveSession(session.copy(profile = profile))
                LoginResult.Success(profile)
            },
            onFailure = {
                sessionStore.clearSession()
                LoginResult.Error("网页认证已返回，但验证个人资料失败，请重试")
            },
        )
    }

    suspend fun fetchProfile(): Result<XmuUserProfile> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$LNT_BASE_URL/api/profile")
                .get()
                .build()

            apiClient.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                if (DebugLogStore.isEnabled()) {
                    val locHost = runCatching { android.net.Uri.parse(location).host }.getOrNull()
                    Log.d("XmuAuthDiag", "fetchProfile HTTP=$code locHost=$locHost")
                    DebugLogStore.log("AUTH", "fetchProfile HTTP=$code locHost=$locHost")
                }
                if (code == 401 || (code in 300..399 && (location.contains("c-identity") || location.contains("ids.xmu.edu.cn")))) {
                    return@withContext Result.failure(SessionExpiredException())
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP $code"))
                }

                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                if (DebugLogStore.isEnabled()) {
                    Log.d("XmuAuthDiag", "fetchProfile keys=${root.keys}")
                }
                val id = root["id"]?.jsonPrimitive?.longOrNull
                    ?: return@withContext Result.failure(SessionExpiredException())
                val userNo = root["user_no"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val name = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "厦大同学" }
                val roles = runCatching {
                    root["roles"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                }.getOrNull().orEmpty()
                val roleLabel = when {
                    roles.any { it.equals("teacher", ignoreCase = true) || it.equals("instructor", ignoreCase = true) } -> "教师"
                    roles.any { it.equals("ta", ignoreCase = true) } -> "助教"
                    else -> "学生"
                }
                val department = root["department"]?.let { elem ->
                    runCatching { elem.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                } ?: root["org"]?.let { elem ->
                    runCatching { elem.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                }.orEmpty()
                val avatarUrl = runCatching { root["avatar_big_url"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: runCatching { root["avatar_small_url"]?.jsonPrimitive?.contentOrNull }.getOrNull()

                Result.success(
                    XmuUserProfile(
                        id = id,
                        userNo = userNo,
                        name = name,
                        role = roleLabel,
                        department = department,
                        avatarUrl = avatarUrl,
                    ),
                )
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyStoredSession(): AuthState {
        val stored = sessionStore.loadSession() ?: return AuthState.LoggedOut
        if (stored.sessionId.isBlank()) {
            sessionStore.clearSession()
            return AuthState.LoggedOut
        }

        val result = fetchProfile()
        return result.fold(
            onSuccess = { profile ->
                sessionStore.saveSession(stored.copy(profile = profile))
                AuthState.LoggedIn(profile = profile, isRefreshing = false)
            },
            onFailure = { error ->
                if (error is SessionExpiredException) {
                    sessionStore.clearSession()
                    AuthState.LoggedOut
                } else {
                    val cached = stored.profile
                    if (cached != null) {
                        AuthState.LoggedIn(profile = cached, isRefreshing = false)
                    } else {
                        AuthState.LoggedOut
                    }
                }
            },
        )
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$LNT_BASE_URL/api/logout")
                    .post("{}".toRequestBody())
                    .build()
                apiClient.newCall(req).execute().close()
            }
            sessionStore.clearSession()
        }
        withContext(Dispatchers.Main) {
            runCatching {
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
            }
        }
    }

    fun syncCookiesToWebView() {
        val snapshot = sessionStore.currentSessionSnapshot() ?: return
        if (snapshot.sessionId.isBlank()) return
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val domains = listOf(
                LNT_BASE_URL,
                "https://c-mobile.xmu.edu.cn",
            )
            snapshot.cookies.forEach { cookie ->
                if (cookie.name.isNotBlank() && cookie.value.isNotBlank()) {
                    domains.forEach { origin ->
                        cm.setCookie(origin, "${cookie.name}=${cookie.value}; path=/; Secure")
                    }
                }
            }
            domains.forEach { origin ->
                cm.setCookie(origin, "session=${snapshot.sessionId}; path=/; Secure")
            }
            cm.flush()
        }
    }

    private fun extractCasErrorTip(html: String): String? {
        val tipRegex = Regex("""id\s*=\s*["']showErrorTip["'][^>]*>([\s\S]*?)</""")
        val raw = tipRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
        val cleaned = raw.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifBlank { null }
    }

    private fun parseSessionCookieValue(setCookieHeader: String): String? {
        val firstPart = setCookieHeader.substringBefore(';').trim()
        val idx = firstPart.indexOf('=')
        if (idx <= 0) return null
        val name = firstPart.substring(0, idx).trim()
        val value = firstPart.substring(idx + 1).trim()
        return if (name == "session" && value.isNotBlank()) value else null
    }

    private fun parseRawCookieHeader(rawCookie: String, domain: String): List<StoredCookie> {
        return rawCookie.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) return@mapNotNull null
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isBlank() || value.isBlank()) return@mapNotNull null
                StoredCookie(name = name, value = value, domain = domain)
            }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private class InMemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val list = store.getOrPut(host) { mutableListOf() }
            synchronized(list) {
                cookies.forEach { incoming ->
                    list.removeAll { it.name == incoming.name && it.path == incoming.path }
                    if (incoming.expiresAt > System.currentTimeMillis()) {
                        list.add(incoming)
                    }
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val result = mutableListOf<Cookie>()
            store.forEach { (host, list) ->
                if (url.host == host || url.host.endsWith(".$host")) {
                    synchronized(list) {
                        list.removeAll { it.expiresAt <= now }
                        result.addAll(list)
                    }
                }
            }
            return result
        }

        fun cookiesForHost(targetHost: String): List<Cookie> {
            val now = System.currentTimeMillis()
            val result = mutableListOf<Cookie>()
            store.forEach { (host, list) ->
                if (host == targetHost || targetHost.endsWith(".$host")) {
                    synchronized(list) {
                        list.removeAll { it.expiresAt <= now }
                        result.addAll(list)
                    }
                }
            }
            return result
        }
    }

    companion object {
        const val LNT_BASE_URL = "https://lnt.xmu.edu.cn"
        const val SSO_ENTRY_URL = "https://lnt.xmu.edu.cn/login"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private val ANY_FORM_REGEX = Regex(
            """<form\b[^>]*>([\s\S]*?)</form>""",
            RegexOption.IGNORE_CASE,
        )
        private val PWD_FORM_REGEX = Regex(
            """<form\b[^>]*\bid\s*=\s*["']pwdFromId["'][^>]*>([\s\S]*?)</form>""",
            RegexOption.IGNORE_CASE,
        )
        private val INPUT_TAG_REGEX = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR_REGEX = Regex("""([a-zA-Z0-9_:-]+)\s*=\s*["']([^"']*)["']""")

        internal fun parseCasPwdForm(html: String): CasFormFields? {
            val formBody = PWD_FORM_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?: ANY_FORM_REGEX.findAll(html)
                    .map { it.groupValues[1] }
                    .firstOrNull { body ->
                        body.contains("userNameLogin") && body.contains("pwdEncryptSalt")
                    }
                ?: return null

            val byKey = HashMap<String, String>()
            INPUT_TAG_REGEX.findAll(formBody).forEach { match ->
                val attrs = HashMap<String, String>()
                ATTR_REGEX.findAll(match.value).forEach { attrMatch ->
                    attrs[attrMatch.groupValues[1].lowercase()] = attrMatch.groupValues[2]
                }
                val value = attrs["value"].orEmpty()
                attrs["name"]?.takeIf { it.isNotBlank() }?.let { byKey[it] = value }
                attrs["id"]?.takeIf { it.isNotBlank() }?.let { byKey.putIfAbsent(it, value) }
            }

            val execution = byKey["execution"].orEmpty()
            val salt = byKey["pwdEncryptSalt"].orEmpty()
            if (execution.isBlank() || salt.isBlank()) return null

            return CasFormFields(
                execution = execution,
                lt = byKey["lt"].orEmpty(),
                dllt = byKey["dllt"].orEmpty().ifBlank { "generalLogin" },
                cllt = byKey["cllt"].orEmpty().ifBlank { "userNameLogin" },
                eventId = byKey["_eventId"].orEmpty().ifBlank { "submit" },
                pwdEncryptSalt = salt,
            )
        }
    }
}
