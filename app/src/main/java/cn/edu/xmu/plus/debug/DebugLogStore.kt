package cn.edu.xmu.plus.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DebugLogEntry(
    val id: Long,
    val timeText: String,
    val tag: String,
    val level: String,
    val message: String,
)

/**
 * In-app & Logcat diagnostic logger gated by Settings -> Debug Mode.
 * Automatically redacts session IDs, cookies, passwords, and upload tokens.
 */
object DebugLogStore {

    private const val LOGCAT_TAG = "XmuPlusDebug"
    private const val MAX_ENTRIES = 300
    private const val MAX_FILE_BYTES = 200_000L

    private val enabled = AtomicBoolean(false)
    private val idGen = AtomicLong(1L)
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    @Volatile
    private var logFile: File? = null

    private val sensitivePatterns = listOf(
        Regex("""(?i)(session|x-session-id|password|uptoken|token|cookie)\s*[:=]\s*[^\s,;]+"""),
        Regex("""V2-[a-f0-9-]+[^\s,;"]*"""),
    )

    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.applicationContext.filesDir, "debug_logs").apply { mkdirs() }
        val file = File(dir, "xmu_plus_debug.log")
        logFile = file
        if (file.exists()) {
            runCatching {
                val lines = file.readLines().takeLast(MAX_ENTRIES)
                val restored = lines.mapNotNull { parseLine(it) }
                _entries.value = restored
            }
        }
    }

    fun isEnabled(): Boolean = enabled.get()

    fun setEnabled(value: Boolean) {
        val prev = enabled.getAndSet(value)
        if (value && !prev) {
            log("DEBUG", "调试模式已开启（敏感凭据已自动脱敏）", "INFO")
        }
    }

    fun log(tag: String, message: String, level: String = "INFO") {
        if (!enabled.get()) return
        val safeMsg = redact(message)
        val timeText = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(Date())
        val entry = DebugLogEntry(
            id = idGen.getAndIncrement(),
            timeText = timeText,
            tag = tag,
            level = level,
            message = safeMsg,
        )

        synchronized(this) {
            val updated = (_entries.value + entry).takeLast(MAX_ENTRIES)
            _entries.value = updated
            runCatching {
                val file = logFile ?: return@runCatching
                if (file.exists() && file.length() > MAX_FILE_BYTES) {
                    val tail = file.readLines().takeLast(MAX_ENTRIES / 2).joinToString("\n")
                    file.writeText(if (tail.isEmpty()) "" else "$tail\n")
                }
                file.appendText("$timeText|$level|$tag|$safeMsg\n")
            }
        }
        Log.d(LOGCAT_TAG, "[$level][$tag] $safeMsg")
    }

    fun exportText(): String {
        return _entries.value.joinToString("\n") {
            "${it.timeText} [${it.level}][${it.tag}] ${it.message}"
        }
    }

    fun clear() {
        synchronized(this) {
            _entries.value = emptyList()
            runCatching { logFile?.writeText("") }
        }
    }

    private fun redact(input: String): String {
        var out = input
        sensitivePatterns.forEach { regex ->
            out = out.replace(regex) { match ->
                val prefix = match.groupValues.getOrNull(1).orEmpty()
                if (prefix.isNotEmpty()) "$prefix=<REDACTED>" else "<REDACTED>"
            }
        }
        return out
    }

    private fun parseLine(line: String): DebugLogEntry? {
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return null
        return DebugLogEntry(
            id = idGen.getAndIncrement(),
            timeText = parts[0],
            level = parts[1],
            tag = parts[2],
            message = parts[3],
        )
    }
}
