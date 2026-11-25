package cn.edu.xmu.plus.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cn.edu.xmu.plus.data.AccentTone
import cn.edu.xmu.plus.data.DarkModePreference
import cn.edu.xmu.plus.data.ThemeSettings

private fun buildLightScheme(accent: AccentTone): ColorScheme {
    val onPrimary = Color.White
    return lightColorScheme(
        primary = accent.lightPrimary,
        onPrimary = onPrimary,
        primaryContainer = accent.lightContainer,
        onPrimaryContainer = Color(0xFF111214),
        secondary = Color(0xFF525866),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1F2F4),
        onSecondaryContainer = Color(0xFF1F2329),
        tertiary = Color(0xFF3E7B67),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDF5F2),
        onTertiaryContainer = Color(0xFF14382D),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF111214),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF111214),
        surfaceVariant = Color(0xFFF1F2F4),
        onSurfaceVariant = Color(0xFF6B7280),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F7F8),
        surfaceContainer = Color(0xFFF3F4F6),
        surfaceContainerHigh = Color(0xFFECEEF1),
        surfaceContainerHighest = Color(0xFFE4E6EB),
        outline = Color(0xFFA1A1AA),
        outlineVariant = Color(0xFFE4E4E7),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEF2F2),
        onErrorContainer = Color(0xFF991B1B),
    )
}

private fun buildDarkScheme(accent: AccentTone): ColorScheme {
    val onPrimary = if (accent == AccentTone.GRAPHITE) Color(0xFF111214) else Color(0xFF0A111A)
    return darkColorScheme(
        primary = accent.darkPrimary,
        onPrimary = onPrimary,
        primaryContainer = accent.darkContainer,
        onPrimaryContainer = Color(0xFFF4F4F5),
        secondary = Color(0xFF9CA3AF),
        onSecondary = Color(0xFF111214),
        secondaryContainer = Color(0xFF1A1C22),
        onSecondaryContainer = Color(0xFFE4E4E7),
        tertiary = Color(0xFF6BB59C),
        onTertiary = Color(0xFF0B1F18),
        tertiaryContainer = Color(0xFF152620),
        onTertiaryContainer = Color(0xFFD6EFE5),
        background = Color(0xFF0C0D10),
        onBackground = Color(0xFFF4F4F5),
        surface = Color(0xFF0C0D10),
        onSurface = Color(0xFFF4F4F5),
        surfaceVariant = Color(0xFF1A1C22),
        onSurfaceVariant = Color(0xFF8E95A2),
        surfaceContainerLowest = Color(0xFF13151A),
        surfaceContainerLow = Color(0xFF16181E),
        surfaceContainer = Color(0xFF1B1E25),
        surfaceContainerHigh = Color(0xFF22252E),
        surfaceContainerHighest = Color(0xFF2B2F3A),
        outline = Color(0xFF646A78),
        outlineVariant = Color(0xFF242730),
        error = Color(0xFFF87171),
        onError = Color(0xFF2E0B0B),
        errorContainer = Color(0xFF2C1618),
        onErrorContainer = Color(0xFFFCA5A5),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmuPlusTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (settings.darkMode) {
        DarkModePreference.SYSTEM -> isSystemDark
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    val targetScheme = if (useDarkTheme) {
        buildDarkScheme(settings.accentTone)
    } else {
        buildLightScheme(settings.accentTone)
    }
    val animatedScheme = animateThemeColorScheme(targetScheme)

    MaterialExpressiveTheme(
        colorScheme = animatedScheme,
        motionScheme = MotionScheme.standard(),
        typography = XmuTypography,
        shapes = XmuShapes,
        content = content,
    )
}

@Composable
private fun animateThemeColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 150)

    @Composable
    fun anim(color: Color): Color = animateColorAsState(
        targetValue = color,
        animationSpec = spec,
        label = "themeColor",
    ).value

    return target.copy(
        primary = anim(target.primary),
        onPrimary = anim(target.onPrimary),
        primaryContainer = anim(target.primaryContainer),
        onPrimaryContainer = anim(target.onPrimaryContainer),
        secondary = anim(target.secondary),
        onSecondary = anim(target.onSecondary),
        secondaryContainer = anim(target.secondaryContainer),
        onSecondaryContainer = anim(target.onSecondaryContainer),
        tertiary = anim(target.tertiary),
        onTertiary = anim(target.onTertiary),
        tertiaryContainer = anim(target.tertiaryContainer),
        onTertiaryContainer = anim(target.onTertiaryContainer),
        background = anim(target.background),
        onBackground = anim(target.onBackground),
        surface = anim(target.surface),
        onSurface = anim(target.onSurface),
        surfaceVariant = anim(target.surfaceVariant),
        onSurfaceVariant = anim(target.onSurfaceVariant),
        surfaceContainerLowest = anim(target.surfaceContainerLowest),
        surfaceContainerLow = anim(target.surfaceContainerLow),
        surfaceContainer = anim(target.surfaceContainer),
        surfaceContainerHigh = anim(target.surfaceContainerHigh),
        surfaceContainerHighest = anim(target.surfaceContainerHighest),
        outline = anim(target.outline),
        outlineVariant = anim(target.outlineVariant),
        error = anim(target.error),
        onError = anim(target.onError),
        errorContainer = anim(target.errorContainer),
        onErrorContainer = anim(target.onErrorContainer),
    )
}
