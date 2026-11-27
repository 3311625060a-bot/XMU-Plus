package cn.edu.xmu.plus.data

import androidx.compose.runtime.Immutable

@Immutable
data class OpenSourceNotice(
    val name: String,
    val author: String,
    val version: String,
    val license: String,
    val url: String,
    val description: String,
)

object OpenSourceLicenses {
    val items: List<OpenSourceNotice> = listOf(
        OpenSourceNotice(
            name = "Kotlin & kotlinx.serialization",
            author = "JetBrains s.r.o.",
            version = "2.3.21 / 1.8.1",
            license = "Apache-2.0",
            url = "https://github.com/JetBrains/kotlin",
            description = "编程语言与 JSON 序列化框架",
        ),
        OpenSourceNotice(
            name = "AndroidX & Jetpack Compose",
            author = "The Android Open Source Project",
            version = "BOM 2026.05.01 / Material 3 1.5.0-alpha16",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack",
            description = "Compose UI、Navigation、DataStore、Glance 微件、WorkManager 与 CameraX",
        ),
        OpenSourceNotice(
            name = "OkHttp",
            author = "Square, Inc.",
            version = "4.12.0",
            license = "Apache-2.0",
            url = "https://square.github.io/okhttp/",
            description = "HTTP/HTTPS 网络客户端与连接池",
        ),
        OpenSourceNotice(
            name = "Haze",
            author = "Chris Banes",
            version = "1.6.10",
            license = "Apache-2.0",
            url = "https://github.com/chrisbanes/haze",
            description = "Compose 实时背景高斯模糊（液态玻璃底栏效果）",
        ),
        OpenSourceNotice(
            name = "ML Kit Barcode Scanning",
            author = "Google LLC",
            version = "17.3.0",
            license = "Google APIs ToS",
            url = "https://developers.google.com/ml-kit/vision/barcode-scanning",
            description = "本地离线课堂签到二维码识别引擎",
        ),
        OpenSourceNotice(
            name = "JUnit 4",
            author = "JUnit Team",
            version = "4.13.2",
            license = "EPL-1.0",
            url = "https://junit.org/junit4/",
            description = "本地单元测试框架（仅用于测试）",
        ),
    )
}
