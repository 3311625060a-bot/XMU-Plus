package cn.edu.xmu.plus.auth

import android.util.Log
import android.webkit.CookieManager
import cn.edu.xmu.plus.debug.DebugLogStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    private data class PrewarmedCasSession(
        val cookieJar: InMemoryCookieJar,
        val casPostUrl: String,
        val form: CasFormFields,
        val fetchedAtMillis: Long,
    )

    private val sharedConnectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 12
    }
    private val preferIpv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            if (all.size <= 1) return all
            val v4 = all.filterIsInstance<Inet4Address>()
            if (v4.isEmpty() || v4.size == all.size) return all
            val other = all.filterNot { it is Inet4Address }
            return v4 + other
        }
    }
    private val captchaCheckClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .dns(preferIpv4Dns)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    private val prewarmMutex = Mutex()
    private val prewarmedCas = AtomicReference<PrewarmedCasSession?>(null)
    @Volatile
    private var inFlightPrewarm: CompletableDeferred<PrewarmedCasSession?>? = null

    val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .dispatcher(sharedDispatcher)
        .dns(preferIpv4Dns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

    private fun buildSsoHttpClient(cookieJar: CookieJar, followRedirects: Boolean): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .dns(preferIpv4Dns)
            .cookieJar(cookieJar)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun prewarmSso() {
        val existing = prewarmedCas.get()
        if (existing != null && System.currentTimeMillis() - existing.fetchedAtMillis < PREWARM_TTL_MS) {
            return
        }
        val deferred = prewarmMutex.withLock {
            val active = inFlightPrewarm
            if (active != null && active.isActive) return@withLock active
            val fresh = CompletableDeferred<PrewarmedCasSession?>()
            inFlightPrewarm = fresh
            scope.launch {
                val res = coroutineScope {
                    val keystoreJob = launch { KeystoreCrypto.warmup() }
                    val casRes = runCatching { fetchCasSessionPage() }.getOrNull()
                    keystoreJob.join()
                    casRes
                }
                if (res != null) {
                    prewarmedCas.set(res)
                }
                fresh.complete(res)
                inFlightPrewarm = null
            }
            fresh
        }
        deferred.await()
    }

    private fun fetchCasSessionPage(): PrewarmedCasSession? {
        val cookieJar = InMemoryCookieJar()
        val client = buildSsoHttpClient(cookieJar = cookieJar, followRedirects = true)
        val casPageRequest = Request.Builder()
            .url(SSO_ENTRY_URL)
            .header("User-Agent", MOBILE_UA)
            .get()
            .build()
        val (casPostUrl, casHtml) = client.newCall(casPageRequest).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            resp.request.url.toString() to bodyStr
        }
        val form = parseCasPwdForm(casHtml) ?: return null
        return PrewarmedCasSession(
            cookieJar = cookieJar,
            casPostUrl = casPostUrl,
            form = form,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun checkNeedCaptchaRemote(username: String, referer: String? = null): Boolean {
        val encodedUser = URLEncoder.encode(username, "UTF-8")
        val captchaCheckUrl =
            "https://ids.xmu.edu.cn/authserver/checkNeedCaptcha.htl?username=$encodedUser&_=${System.currentTimeMillis()}"
        val reqBuilder = Request.Builder()
            .url(captchaCheckUrl)
            .header("User-Agent", MOBILE_UA)
            .header("X-Requested-With", "XMLHttpRequest")
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }
        return captchaCheckClient.newCall(reqBuilder.get().build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            runCatching {
                json.parseToJsonElement(raw).jsonObject["isNeed"]?.jsonPrimitive?.booleanOrNull ?: false
            }.getOrDefault(false)
        }
    }

    suspend fun loginNative(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val trimmedUser = username.trim()
        if (trimmedUser.isEmpty() || password.isEmpty()) {
            return@withContext LoginResult.Error("请输入学号和密码", canFallbackToWebView = false)
        }

        val loginStartMs = System.currentTimeMillis()

        try {
            val inFlight = inFlightPrewarm
            var cachedPrewarm = prewarmedCas.getAndSet(null)
                ?.takeIf { System.currentTimeMillis() - it.fetchedAtMillis < PREWARM_TTL_MS }

            var usedPrewarm = false
            val sessionSetup: PrewarmedCasSession?
            val needCaptcha: Boolean

            if (cachedPrewarm != null) {
                usedPrewarm = true
                sessionSetup = cachedPrewarm
                needCaptcha = checkNeedCaptchaRemote(trimmedUser, cachedPrewarm.casPostUrl)
            } else if (inFlight != null && inFlight.isActive) {
                coroutineScope {
                    val captchaDeferred = async { runCatching { checkNeedCaptchaRemote(trimmedUser) }.getOrDefault(false) }
                    val prewarmRes = inFlight.await()
                    prewarmedCas.compareAndSet(prewarmRes, null)
                    if (prewarmRes != null) {
                        usedPrewarm = true
                        sessionSetup = prewarmRes
                    } else {
                        sessionSetup = fetchCasSessionPage()
                    }
                    needCaptcha = captchaDeferred.await()
                }
            } else {
                // Cold path: fetch CAS login page & captcha check in parallel
                coroutineScope {
                    val casDeferred = async { fetchCasSessionPage() }
                    val captchaDeferred = async { runCatching { checkNeedCaptchaRemote(trimmedUser) }.getOrDefault(false) }
                    sessionSetup = casDeferred.await()
                    needCaptcha = captchaDeferred.await()
                }
            }

            if (sessionSetup == null) {
                return@withContext LoginResult.CaptchaRequired(
                    ssoUrl = SSO_ENTRY_URL,
                    username = trimmedUser,
                    password = password,
                    reason = "正在切换至统一身份认证网页完成登录",
                )
            }

            if (needCaptcha) {
                return@withContext LoginResult.CaptchaRequired(
                    ssoUrl = SSO_ENTRY_URL,
                    username = trimmedUser,
                    password = password,
                    reason = "统一身份认证触发安全校验，请在下方页面完成验证",
                )
            }

            var activeSetup = sessionSetup
            var outcome = submitCasCredentials(activeSetup, trimmedUser, password)

            // If prewarmed CAS form/ticket expired server-side without an explicit wrong-password tip, retry once cleanly
            if (usedPrewarm && outcome.retryWithFreshCas) {
                val fresh = fetchCasSessionPage()
                if (fresh != null) {
                    activeSetup = fresh
                    outcome = submitCasCredentials(fresh, trimmedUser, password)
                }
            }

            outcome.earlyResult?.let { early ->
                if (early is LoginResult.Error) {
                    scope.launch { runCatching { prewarmSso() } }
                }
                return@withContext early
            }

            val lntCookies = activeSetup.cookieJar.cookiesForHost("lnt.xmu.edu.cn")
            val sessionCookie = lntCookies.firstOrNull { it.name == "session" }?.value
            val sessionId = sessionCookie?.takeIf { it.isNotBlank() }
                ?: outcome.responseSessionHeader?.takeIf { it.isNotBlank() }

            if (sessionId.isNullOrBlank()) {
                return@withContext LoginResult.CaptchaRequired(
                    ssoUrl = outcome.finalUrl.ifBlank { SSO_ENTRY_URL },
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
            val pendingSession = PersistedSession(
                sessionId = sessionId,
                cookies = storedCookies,
                profile = null,
                savedAtMillis = System.currentTimeMillis(),
            )
            // Keep in memory immediately for fetchProfile() without redundant double Keystore+DataStore disk write
            sessionStore.setMemorySession(pendingSession)

            val profileResult = fetchProfile()
            return@withContext profileResult.fold(
                onSuccess = { profile ->
                    val finalSession = pendingSession.copy(profile = profile)
                    sessionStore.setMemorySession(finalSession)
                    scope.launch(Dispatchers.IO) {
                        runCatching { sessionStore.saveSession(finalSession) }
                    }
                    if (DebugLogStore.isEnabled()) {
                        val totalMs = System.currentTimeMillis() - loginStartMs
                        DebugLogStore.log("AUTH", "loginNative success in ${totalMs}ms (prewarmed=$usedPrewarm)")
                    }
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

    private data class CasSubmitOutcome(
        val finalUrl: String = "",
        val responseSessionHeader: String? = null,
        val earlyResult: LoginResult? = null,
        val retryWithFreshCas: Boolean = false,
    )

    private fun submitCasCredentials(
        setup: PrewarmedCasSession,
        username: String,
        password: String,
    ): CasSubmitOutcome {
        val encryptedPassword = CasCrypto.encryptPassword(password, setup.form.pwdEncryptSalt)
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", encryptedPassword)
            .add("captcha", "")
            .add("_eventId", setup.form.eventId)
            .add("cllt", setup.form.cllt)
            .add("dllt", setup.form.dllt)
            .add("lt", setup.form.lt)
            .add("execution", setup.form.execution)
            .build()

        // Step redirects manually so we abort immediately once lnt.xmu.edu.cn grants a valid session cookie
        val manualClient = buildSsoHttpClient(cookieJar = setup.cookieJar, followRedirects = false)
        var currentRequest = Request.Builder()
            .url(setup.casPostUrl)
            .header("User-Agent", MOBILE_UA)
            .header("Origin", "https://ids.xmu.edu.cn")
            .header("Referer", setup.casPostUrl)
            .post(formBody)
            .build()

        var lastUrl = setup.casPostUrl
        var lastSessionHeader: String? = null

        for (hop in 0 until 10) {
            manualClient.newCall(currentRequest).execute().use { resp ->
                val reqUrl = resp.request.url
                lastUrl = reqUrl.toString()
                val headerSession = resp.header("x-session-id")
                if (!headerSession.isNullOrBlank()) {
                    lastSessionHeader = headerSession
                }

                // Early exit as soon as lnt.xmu.edu.cn issues the session cookie, avoiding extra portal HTML fetch
                val hasLntSession = setup.cookieJar.cookiesForHost("lnt.xmu.edu.cn")
                    .any { it.name == "session" && it.value.isNotBlank() }
                if (hasLntSession || !lastSessionHeader.isNullOrBlank()) {
                    return CasSubmitOutcome(
                        finalUrl = lastUrl,
                        responseSessionHeader = lastSessionHeader,
                    )
                }

                val code = resp.code
                val location = resp.header("Location")
                if (code in 300..399 && !location.isNullOrBlank()) {
                    val nextUrl = reqUrl.resolve(location) ?: return CasSubmitOutcome(finalUrl = lastUrl)
                    currentRequest = Request.Builder()
                        .url(nextUrl)
                        .header("User-Agent", MOBILE_UA)
                        .get()
                        .build()
                    return@use
                }

                val html = resp.body?.string().orEmpty()
                val host = reqUrl.host
                if (host.contains("ids.xmu.edu.cn")) {
                    val errorTip = extractCasErrorTip(html)
                    if (errorTip != null && (errorTip.contains("验证码") || errorTip.contains("滑块"))) {
                        return CasSubmitOutcome(
                            earlyResult = LoginResult.CaptchaRequired(
                                ssoUrl = SSO_ENTRY_URL,
                                username = username,
                                password = password,
                                reason = "需要完成安全验证，请通过内置认证页继续",
                            ),
                        )
                    }
                    if (errorTip == null && parseCasPwdForm(html) != null) {
                        return CasSubmitOutcome(finalUrl = lastUrl, retryWithFreshCas = true)
                    }
                    return CasSubmitOutcome(
                        earlyResult = LoginResult.Error(
                            message = errorTip ?: "学号或密码错误，请核对后重试",
                            canFallbackToWebView = true,
                        ),
                    )
                }

                return CasSubmitOutcome(
                    finalUrl = lastUrl,
                    responseSessionHeader = lastSessionHeader,
                )
            }
        }
        return CasSubmitOutcome(finalUrl = lastUrl, responseSessionHeader = lastSessionHeader)
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
        sessionStore.setMemorySession(session)

        return@withContext fetchProfile().fold(
            onSuccess = { profile ->
                val finalSession = session.copy(profile = profile)
                sessionStore.setMemorySession(finalSession)
                scope.launch(Dispatchers.IO) {
                    runCatching { sessionStore.saveSession(finalSession) }
                }
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
            val origins = listOf(
                LNT_BASE_URL,
                "https://c-mobile.xmu.edu.cn",
                "https://xmu.edu.cn",
            )
            val validCookies = snapshot.cookies.filter { it.name.isNotBlank() && it.value.isNotBlank() }
            origins.forEach { origin ->
                validCookies.forEach { cookie ->
                    cm.setCookie(origin, "${cookie.name}=${cookie.value}; path=/; Secure")
                }
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
        private const val PREWARM_TTL_MS = 90_000L
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
