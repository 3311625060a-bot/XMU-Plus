package cn.edu.xmu.plus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun compareSemanticVersions_handlesPrefixesAndPatchBumps() {
        assertTrue(AppUpdateChecker.compareSemanticVersions("v1.0.1", "1.0.0") > 0)
        assertTrue(AppUpdateChecker.compareSemanticVersions("1.1.0", "1.0.9") > 0)
        assertTrue(AppUpdateChecker.compareSemanticVersions("v2.0", "1.9.9") > 0)
        assertEquals(0, AppUpdateChecker.compareSemanticVersions("v1.0.0", "1.0.0"))
        assertTrue(AppUpdateChecker.compareSemanticVersions("v1.0.0-beta.1", "1.0.0") < 0)
        assertTrue(AppUpdateChecker.compareSemanticVersions("1.0.0", "1.0.0-rc.1") > 0)
    }

    @Test
    fun parseReleasesResponse_emptyArrayReportsUpToDateWithoutRelease() {
        val result = AppUpdateChecker.parseReleasesResponse("[]", "1.0.0")
        assertTrue(result is UpdateCheckResult.UpToDate)
        val upToDate = result as UpdateCheckResult.UpToDate
        assertEquals(false, upToDate.hasAnyRelease)
        assertEquals("1.0.0", upToDate.currentVersion)
    }

    @Test
    fun parseReleasesResponse_detectsNewerReleaseWithApkAsset() {
        val sampleJson = """
            [
              {
                "tag_name": "v1.1.0",
                "name": "XMU Plus v1.1.0",
                "body": "优化在线预览与作业排序",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-30T10:00:00Z",
                "html_url": "https://github.com/3311625060a-bot/XMU-Plus/releases/tag/v1.1.0",
                "assets": [
                  {
                    "name": "xmu-plus-v1.1.0-release.apk",
                    "browser_download_url": "https://github.com/3311625060a-bot/XMU-Plus/releases/download/v1.1.0/xmu-plus-v1.1.0-release.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = AppUpdateChecker.parseReleasesResponse(sampleJson, "1.0.0")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("v1.1.0", available.latestVersion)
        assertEquals("2026-11-30", available.publishedDate)
        assertTrue(available.apkDownloadUrl?.endsWith(".apk") == true)
    }
}
