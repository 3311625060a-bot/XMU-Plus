package cn.edu.xmu.plus.data

import cn.edu.xmu.plus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseTitle: String,
        val releaseNotes: String,
        val publishedDate: String,
        val releasePageUrl: String,
        val apkDownloadUrl: String?,
        val isPreRelease: Boolean,
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersion: String,
        val hasAnyRelease: Boolean,
        val latestTag: String?,
        val releasePageUrl: String,
    ) : UpdateCheckResult

    data class Error(
        val message: String,
        val releasePageUrl: String,
    ) : UpdateCheckResult
}

object AppUpdateChecker {
    const val REPO_URL = "https://github.com/3311625060a-bot/XMU-Plus"
    const val RELEASES_PAGE_URL = "$REPO_URL/releases"
    private const val RELEASES_API_URL =
        "https://api.github.com/repos/3311625060a-bot/XMU-Plus/releases?per_page=8"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "XMU-Plus/$currentVersion")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = when (response.code) {
                        403, 429 -> "GitHub API 请求频率受限，请稍后再试或直接访问 Releases 页面"
                        404 -> "仓库暂未公开 Releases 信息"
                        else -> "检测更新失败（HTTP ${response.code}）"
                    }
                    return@withContext UpdateCheckResult.Error(hint, RELEASES_PAGE_URL)
                }
                parseReleasesResponse(body, currentVersion)
            }
        } catch (_: Exception) {
            UpdateCheckResult.Error(
                message = "连接 GitHub 失败，请检查网络或代理后重试",
                releasePageUrl = RELEASES_PAGE_URL,
            )
        }
    }

    internal fun parseReleasesResponse(
        rawJson: String,
        currentVersion: String,
    ): UpdateCheckResult {
        val arr = runCatching { json.parseToJsonElement(rawJson).jsonArray }.getOrNull()
            ?: return UpdateCheckResult.Error("GitHub 返回数据格式异常", RELEASES_PAGE_URL)

        val candidates = arr.mapNotNull { elem ->
            val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
            val isDraft = obj["draft"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isDraft) return@mapNotNull null
            val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (tag.isBlank()) return@mapNotNull null
            obj
        }

        if (candidates.isEmpty()) {
            return UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                hasAnyRelease = false,
                latestTag = null,
                releasePageUrl = RELEASES_PAGE_URL,
            )
        }

        // Prefer latest non-prerelease if available, else newest prerelease
        val chosen = candidates.firstOrNull { obj ->
            obj["prerelease"]?.jsonPrimitive?.booleanOrNull != true
        } ?: candidates.first()

        val tagName = chosen["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val releaseName = chosen["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { tagName } ?: tagName
        val notes = chosen["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val htmlUrl = chosen["html_url"]?.jsonPrimitive?.contentOrNull?.ifBlank { RELEASES_PAGE_URL }
            ?: RELEASES_PAGE_URL
        val isPre = chosen["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
        val publishedAt = chosen["published_at"]?.jsonPrimitive?.contentOrNull?.take(10).orEmpty()

        val assets = runCatching { chosen["assets"]?.jsonArray }.getOrNull()
        val apkAssetUrl = assets?.firstNotNullOfOrNull { assetElem ->
            val assetObj = runCatching { assetElem.jsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
            val name = assetObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val url = assetObj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("http")) url else null
        }

        return if (compareSemanticVersions(tagName, currentVersion) > 0) {
            UpdateCheckResult.UpdateAvailable(
                latestVersion = tagName,
                releaseTitle = releaseName,
                releaseNotes = notes,
                publishedDate = publishedAt,
                releasePageUrl = htmlUrl,
                apkDownloadUrl = apkAssetUrl,
                isPreRelease = isPre,
            )
        } else {
            UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                hasAnyRelease = true,
                latestTag = tagName,
                releasePageUrl = htmlUrl,
            )
        }
    }

    /**
     * Compares two semantic version strings (e.g. "v1.0.1" vs "1.0.0").
     * Returns > 0 if [remote] is newer than [local], 0 if equal, < 0 if older.
     */
    internal fun compareSemanticVersions(remote: String, local: String): Int {
        val rClean = remote.trim().removePrefix("v").removePrefix("V")
        val lClean = local.trim().removePrefix("v").removePrefix("V")

        val rMain = rClean.substringBefore('-')
        val lMain = lClean.substringBefore('-')
        val rParts = rMain.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val lParts = lMain.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val lv = lParts.getOrElse(i) { 0 }
            if (rv != lv) return rv.compareTo(lv)
        }

        val rHasPre = rClean.contains('-')
        val lHasPre = lClean.contains('-')
        return when {
            rHasPre && !lHasPre -> -1
            !rHasPre && lHasPre -> 1
            else -> rClean.substringAfter('-', "").compareTo(lClean.substringAfter('-', ""))
        }
    }
}
