package cn.edu.xmu.plus.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "xmu_plus_theme_prefs")

enum class AccentTone(
    val displayName: String,
    val subtitle: String,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightContainer: Color,
    val darkContainer: Color,
) {
    MIST_SLATE(
        displayName = "雾蓝靛",
        subtitle = "冷静精密",
        lightPrimary = Color(0xFF476A8A),
        darkPrimary = Color(0xFF81A8D0),
        lightContainer = Color(0xFFEFF3F8),
        darkContainer = Color(0xFF1A232E),
    ),
    SAGE_MOSS(
        displayName = "鼠尾草",
        subtitle = "沉静草木",
        lightPrimary = Color(0xFF4A725E),
        darkPrimary = Color(0xFF79AF93),
        lightContainer = Color(0xFFEEF4F1),
        darkContainer = Color(0xFF17231E),
    ),
    TERRACOTTA(
        displayName = "嘉庚陶",
        subtitle = "厦大红砖",
        lightPrimary = Color(0xFFA0594E),
        darkPrimary = Color(0xFFD6897D),
        lightContainer = Color(0xFFF6EFEE),
        darkContainer = Color(0xFF281B1A),
    ),
    IRIS_ASH(
        displayName = "暮云紫",
        subtitle = "微醺灰紫",
        lightPrimary = Color(0xFF6B5D86),
        darkPrimary = Color(0xFFA393C4),
        lightContainer = Color(0xFFF2F0F7),
        darkContainer = Color(0xFF201C2B),
    ),
    GRAPHITE(
        displayName = "纯粹墨",
        subtitle = "极简黑白",
        lightPrimary = Color(0xFF18181B),
        darkPrimary = Color(0xFFE4E4E7),
        lightContainer = Color(0xFFF1F2F4),
        darkContainer = Color(0xFF212329),
    ),
}

enum class DarkModePreference(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class DockStyle(val label: String) {
    LIQUID_GLASS("液态玻璃"),
    CLASSIC("经典磨砂"),
}

data class ThemeSettings(
    val accentTone: AccentTone = AccentTone.MIST_SLATE,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val dockStyle: DockStyle = DockStyle.LIQUID_GLASS,
    val hapticsEnabled: Boolean = true,
    val filmGrainEnabled: Boolean = false,
    val sharedElementEnabled: Boolean = false,
    val ddlReminderEnabled: Boolean = false,
    val debugModeEnabled: Boolean = false,
)

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val ACCENT_TONE = stringPreferencesKey("accent_tone")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DOCK_STYLE = stringPreferencesKey("dock_style")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val FILM_GRAIN_ENABLED = booleanPreferencesKey("film_grain_enabled")
        val SHARED_ELEMENT_ENABLED = booleanPreferencesKey("shared_element_enabled")
        val DDL_REMINDER_ENABLED = booleanPreferencesKey("ddl_reminder_enabled")
        val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
    }

    val themeSettings: Flow<ThemeSettings> = context.themeDataStore.data.map { prefs ->
        val accentName = prefs[Keys.ACCENT_TONE]
        val darkModeName = prefs[Keys.DARK_MODE]
        val dockStyleName = prefs[Keys.DOCK_STYLE]
        ThemeSettings(
            accentTone = AccentTone.entries.find { it.name == accentName } ?: AccentTone.MIST_SLATE,
            darkMode = DarkModePreference.entries.find { it.name == darkModeName } ?: DarkModePreference.SYSTEM,
            dockStyle = DockStyle.entries.find { it.name == dockStyleName } ?: DockStyle.LIQUID_GLASS,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            filmGrainEnabled = prefs[Keys.FILM_GRAIN_ENABLED] ?: false,
            sharedElementEnabled = prefs[Keys.SHARED_ELEMENT_ENABLED] ?: false,
            ddlReminderEnabled = prefs[Keys.DDL_REMINDER_ENABLED] ?: false,
            debugModeEnabled = prefs[Keys.DEBUG_MODE_ENABLED] ?: false,
        )
    }

    suspend fun setAccentTone(tone: AccentTone) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.ACCENT_TONE] = tone.name
        }
    }

    suspend fun setDarkMode(darkMode: DarkModePreference) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = darkMode.name
        }
    }

    suspend fun setDockStyle(style: DockStyle) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.DOCK_STYLE] = style.name
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setFilmGrainEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.FILM_GRAIN_ENABLED] = enabled
        }
    }

    suspend fun setSharedElementEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.SHARED_ELEMENT_ENABLED] = enabled
        }
    }

    suspend fun setDdlReminderEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.DDL_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.DEBUG_MODE_ENABLED] = enabled
        }
    }
}
