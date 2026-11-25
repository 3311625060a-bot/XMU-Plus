package cn.edu.xmu.plus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLDecoder

data class ParsedQrRollcall(
    val rollcallId: Long,
    val data: String,
    val courseId: Long = 0L,
)

/**
 * Pure-Kotlin parser for TronClass QR rollcall codes:
 * 1) Raw JSON: {"rollcallId": 123, "data": "...", "courseId": 456}
 * 2) Jumper URL with `_p=<json>`: https://.../j?_p=... or /scanner-jumper?_p=...
 * 3) Jumper URL with compact `p=<encoded>`: exact lo() decoder from TronClass H5 bundle.
 */
object QrRollcallParser {

    private const val CHAR_BANG = '\u001E'
    private const val CHAR_TILDE = '\u001F'
    private const val CHAR_ENUM = '\u001A'
    private const val CHAR_NUM = '\u0010'

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val shortKeys = listOf(
        "courseId",
        "activityId",
        "activityType",
        "data",
        "rollcallId",
        "groupSetId",
        "accessCode",
        "action",
        "enableGroupRollcall",
        "createUser",
        "joinCourse",
    ).mapIndexed { index, key -> index.toString(36) to key }.toMap()

    private val enumValues = mapOf(
        "${CHAR_ENUM}1" to "true",
        "${CHAR_ENUM}0" to "false",
        "${CHAR_ENUM}2" to "classroom-exam",
        "${CHAR_ENUM}3" to "feedback",
        "${CHAR_ENUM}4" to "vote",
    )

    fun parse(raw: String): ParsedQrRollcall? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Case 1: direct JSON string
        if (trimmed.startsWith("{")) {
            return parseJsonString(trimmed)
        }

        // Case 2/3: URL or relative /j?p=... path
        val normalizedUrl = if (trimmed.startsWith("/j?") || trimmed.startsWith("/scanner-jumper?")) {
            "https://c-mobile.xmu.edu.cn$trimmed"
        } else {
            trimmed
        }

        val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return null
        val path = uri.path.orEmpty()
        if (path != "/j" && path != "/scanner-jumper") return null

        val params = parseQueryParams(uri.rawQuery.orEmpty())
        params["_p"]?.takeIf { it.isNotBlank() }?.let { rawJson ->
            parseJsonString(rawJson)?.let { return it }
        }
        params["p"]?.takeIf { it.isNotBlank() }?.let { compact ->
            val decodedMap = decodeCompactParams(compact)
            val rollcallId = decodedMap["rollcallId"]?.toLongOrNull() ?: return null
            val data = decodedMap["data"]?.takeIf { it.isNotBlank() } ?: return null
            val courseId = decodedMap["courseId"]?.toLongOrNull() ?: 0L
            return ParsedQrRollcall(rollcallId = rollcallId, data = data, courseId = courseId)
        }

        return null
    }

    internal fun decodeCompactParams(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        encoded.split('!')
            .asSequence()
            .filter { it.isNotEmpty() }
            .forEach { pair ->
                val tildeIdx = pair.indexOf('~')
                if (tildeIdx <= 0) return@forEach
                val rawKey = pair.substring(0, tildeIdx)
                val rawVal = pair.substring(tildeIdx + 1)
                val key = shortKeys[rawKey] ?: rawKey
                result[key] = decodeCompactValue(rawVal)
            }
        return result
    }

    private fun decodeCompactValue(value: String): String {
        if (value.startsWith(CHAR_ENUM)) {
            return enumValues[value] ?: value
        }
        if (value.startsWith(CHAR_NUM)) {
            val parts = value.substring(1).split('.')
            val intPart = parts.getOrNull(0)?.toLongOrNull(36)
            return if (intPart != null && parts.size > 1) {
                val fracPart = parts[1].toLongOrNull(36)
                if (fracPart != null) "$intPart.$fracPart" else intPart.toString()
            } else {
                intPart?.toString() ?: value
            }
        }
        return value
            .replace(CHAR_TILDE, '~')
            .replace(CHAR_BANG, '!')
    }

    private fun parseJsonString(jsonStr: String): ParsedQrRollcall? {
        val obj: JsonObject = runCatching { json.parseToJsonElement(jsonStr).jsonObject }.getOrNull() ?: return null
        val rollcallId = obj["rollcallId"]?.jsonPrimitive?.longOrNull
            ?: obj["rollcallId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return null
        val data = obj["data"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val courseId = obj["courseId"]?.jsonPrimitive?.longOrNull
            ?: obj["courseId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: 0L
        return ParsedQrRollcall(rollcallId = rollcallId, data = data, courseId = courseId)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { part ->
            if (part.isEmpty()) return@forEach
            val eqIdx = part.indexOf('=')
            val key = if (eqIdx >= 0) part.substring(0, eqIdx) else part
            val value = if (eqIdx >= 0) part.substring(eqIdx + 1) else ""
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
            val decodedVal = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            map[decodedKey] = decodedVal
        }
        return map
    }
}
