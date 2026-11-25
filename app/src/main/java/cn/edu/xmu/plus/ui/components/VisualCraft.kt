package cn.edu.xmu.plus.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalSharedTransitionApi::class)
val ContainerBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 760f)
}

@OptIn(ExperimentalSharedTransitionApi::class)
val ContainerResizeMode: SharedTransitionScope.ResizeMode =
    SharedTransitionScope.ResizeMode.scaleToBounds(
        contentScale = ContentScale.FillWidth,
        alignment = Alignment.TopCenter,
    )

val ContainerEnterTransition = fadeIn(tween(120, easing = FastOutSlowInEasing))
val ContainerExitTransition = fadeOut(tween(90, easing = FastOutLinearInEasing))
val DetailOverlayClipShape = RoundedCornerShape(24.dp)

val LocalSharedElementEnabled = compositionLocalOf { false }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.optionalSharedBounds(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    key: String,
    clipShape: Shape,
    enabled: Boolean = LocalSharedElementEnabled.current,
): Modifier {
    if (!enabled) return this
    return with(sharedTransitionScope) {
        this@optionalSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = ContainerEnterTransition,
            exit = ContainerExitTransition,
            boundsTransform = ContainerBoundsTransform,
            resizeMode = ContainerResizeMode,
            clipInOverlayDuringTransition = OverlayClip(clipShape),
        )
    }
}

/**
 * 1. 纯净纸白 / 深曜石画布（Plan A：去掉浮夸背景光斑，保留全透明占位兼容）
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AmbientGlowBackground(
    scrollOffsetPx: () -> Float = { 0f },
    animate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Pure neutral Swiss canvas (#FAFAFA / #0C0D10) with zero background blobs.
}

private const val FILM_GRAIN_AGSL = """
    uniform float uAlpha;
    half4 main(float2 fragCoord) {
        float2 p = floor(fragCoord);
        float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
        float shade = step(0.5, n) * 0.85 + 0.15 * n;
        return half4(shade, shade, shade, uAlpha * (0.45 + 0.55 * n));
    }
"""

/**
 * 2. 细腻纸质/胶片颗粒纹理（Subtle Film Grain）
 * Toggleable in Settings. Uses AGSL RuntimeShader on Android 13+ with a tiny
 * tileable Bitmap fallback on older Android versions.
 */
@Composable
fun FilmGrainOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDark) 0.045f else 0.032f

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(FILM_GRAIN_AGSL) }
        shader.setFloatUniform("uAlpha", alpha)
        val brush = remember(shader, alpha) { ShaderBrush(shader) }
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(brush = brush)
        }
    } else {
        val noiseBrush = remember(isDark) {
            val sizePx = 96
            val bmp = android.graphics.Bitmap.createBitmap(
                sizePx,
                sizePx,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            val pixels = IntArray(sizePx * sizePx)
            var seed = 1337
            for (i in pixels.indices) {
                seed = (seed * 1664525 + 1013904223)
                val v = (seed ushr 24) and 0xFF
                val a = ((v / 255f) * alpha * 255f).toInt().coerceIn(0, 255)
                val lum = if (v > 128) 235 else 30
                pixels[i] = Color(lum / 255f, lum / 255f, lum / 255f, a / 255f).toArgb()
            }
            bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            ShaderBrush(
                ImageShader(
                    image = bmp.asImageBitmap(),
                    tileModeX = TileMode.Repeated,
                    tileModeY = TileMode.Repeated,
                ),
            )
        }
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(brush = noiseBrush)
        }
    }
}

/**
 * Zero-lag instantaneous modifier (kept for source compatibility without staggered coroutine delay).
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.staggeredEntrance(
    index: Int,
    triggerKey: Any = Unit,
    enabled: Boolean = true,
): Modifier = this
