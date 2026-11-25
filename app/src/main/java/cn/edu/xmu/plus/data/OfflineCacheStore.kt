package cn.edu.xmu.plus.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DashboardCacheSnapshot(
    val semesters: List<SemesterItem> = emptyList(),
    val selectedSemesterId: Long? = null,
    val courses: List<CourseItem> = emptyList(),
    val homeworks: List<HomeworkItem> = emptyList(),
)

@Serializable
data class CourseDetailCacheSnapshot(
    val homeworks: List<HomeworkItem> = emptyList(),
    val coursewares: List<CoursewareItem> = emptyList(),
    val bulletins: List<BulletinItem> = emptyList(),
)

@Serializable
data class RollcallHistoryCacheSnapshot(
    val records: List<RollcallHistoryItem> = emptyList(),
)

@Serializable
data class WidgetDdlItem(
    val id: Long,
    val courseName: String,
    val title: String,
    val deadlineText: String,
    val remainingText: String,
    val endTimeIso: String? = null,
    val urgency: UrgencyLevel = UrgencyLevel.NONE,
)

@Serializable
data class WidgetDdlSnapshot(
    val isLoggedIn: Boolean = false,
    val pendingCount: Int = 0,
    val items: List<WidgetDdlItem> = emptyList(),
    val updatedAtText: String = "",
)

data class CachedValue<T>(
    val data: T,
    val updatedAtMillis: Long,
)

/**
 * Lightweight SQLite offline-first JSON cache for semesters, courses, homeworks,
 * coursewares, and bulletins. Cleared on logout.
 */
class OfflineCacheStore(context: Context) {

    private val dbHelper = CacheDbHelper(context.applicationContext)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun <T> putJson(key: String, value: T, serializer: KSerializer<T>) =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(serializer, value)
            val cv = ContentValues().apply {
                put(COL_KEY, key)
                put(COL_PAYLOAD, payload)
                put(COL_UPDATED_AT, System.currentTimeMillis())
            }
            dbHelper.writableDatabase.insertWithOnConflict(
                TABLE_CACHE,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

    suspend fun <T> getJson(key: String, serializer: KSerializer<T>): CachedValue<T>? =
        withContext(Dispatchers.IO) {
            val cursor = dbHelper.readableDatabase.query(
                TABLE_CACHE,
                arrayOf(COL_PAYLOAD, COL_UPDATED_AT),
                "$COL_KEY = ?",
                arrayOf(key),
                null,
                null,
                null,
                "1",
            )
            cursor.use { c ->
                if (!c.moveToFirst()) return@withContext null
                val payload = c.getString(0)
                val updatedAt = c.getLong(1)
                val decoded = runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
                    ?: return@withContext null
                CachedValue(data = decoded, updatedAtMillis = updatedAt)
            }
        }

    suspend fun <T> getJsonMap(
        keys: List<String>,
        serializer: KSerializer<T>,
    ): Map<String, CachedValue<T>> = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext emptyMap()
        val placeholders = keys.joinToString(",") { "?" }
        val result = LinkedHashMap<String, CachedValue<T>>(keys.size)
        val cursor = dbHelper.readableDatabase.query(
            TABLE_CACHE,
            arrayOf(COL_KEY, COL_PAYLOAD, COL_UPDATED_AT),
            "$COL_KEY IN ($placeholders)",
            keys.toTypedArray(),
            null,
            null,
            null,
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0)
                val payload = c.getString(1)
                val updatedAt = c.getLong(2)
                val decoded = runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
                if (decoded != null) {
                    result[key] = CachedValue(data = decoded, updatedAtMillis = updatedAt)
                }
            }
        }
        result
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete(TABLE_CACHE, null, null)
    }

    private class CacheDbHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CACHE (
                    $COL_KEY TEXT PRIMARY KEY,
                    $COL_PAYLOAD TEXT NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "xmu_plus_cache.db"
        private const val DB_VERSION = 1
        private const val TABLE_CACHE = "cache_entries"
        private const val COL_KEY = "cache_key"
        private const val COL_PAYLOAD = "payload"
        private const val COL_UPDATED_AT = "updated_at"
    }
}
