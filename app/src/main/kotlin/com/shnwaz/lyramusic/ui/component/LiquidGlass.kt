package com.shnwaz.lyramusic.ui.component

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.sign
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop

typealias PlatformBackdrop = LayerBackdrop

val LocalBackdrop = staticCompositionLocalOf<PlatformBackdrop?> { null }

@Composable
fun rememberBackdrop(): PlatformBackdrop = rememberLayerBackdrop {
    drawRect(Color.Black)
    drawContent()
}

/**
 * Backdrop that records the app content using the theme background color
 * underneath, so the glass has something solid to refract.
 */
@Composable
fun rememberGlassBackdrop(): PlatformBackdrop {
    val background = MaterialTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

fun Modifier.layerBackdrop(backdrop: PlatformBackdrop): Modifier = this.nativeBackdrop(backdrop)

/**
 * PhoneX style liquid glass:
 *  1. vibrancy + light blur of whatever is behind the element
 *  2. lens refraction at the edges (circle-map curve) with chromatic aberration
 *  3. soft tint on top
 *  4. diagonal rim light along the border
 *
 * Refraction and vibrancy need Android 13+. Android 12 gets blur only,
 * older versions get a dark solid surface.
 */
fun Modifier.phoneXGlass(
    backdrop: PlatformBackdrop,
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.10f),
    blurRadius: Dp = 3.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 28.dp,
    rimAlpha: Float = 0.55f,
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    effects = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrancy()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blur(blurRadius.toPx())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val limit = size.minDimension / 2f
            lens(
                minOf(refractionHeight.toPx(), limit),
                minOf(refractionAmount.toPx(), limit),
                true,
                true,
            )
        }
    },
    onDrawBackdrop = { drawBackdrop ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            drawBackdrop()
        }
    },
    shape = { shape },
    onDrawSurface = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            drawRect(tint)
        } else {
            drawRect(Color(0xE6121212))
        }
        // rim light: bright top-left, dark middle, soft bottom-right
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = rimAlpha),
                0.45f to Color.White.copy(alpha = 0.04f),
                0.55f to Color.White.copy(alpha = 0.04f),
                1.0f to Color.White.copy(alpha = rimAlpha * 0.55f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 2.dp.toPx()),
        )
    },
)

fun Modifier.drawBackdropCustomShape(
    backdrop: PlatformBackdrop,
    layer: GraphicsLayer,
    luminanceAnimation: Float,
    shape: Shape
): Modifier {
    return this.drawBackdrop(
        backdrop = backdrop,
        effects = {
            val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
            vibrancy()
            colorControls(
                brightness =
                    if (l > 0f) {
                        lerp(0.1f, 0.5f, l)
                    } else {
                        lerp(0.1f, -0.2f, -l)
                    },
                contrast =
                    if (l > 0f) {
                        lerp(1f, 0f, l)
                    } else {
                        1f
                    },
                saturation = 1.5f,
            )
            blur(
                if (l > 0f) {
                    lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                } else {
                    lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                },
            )
            lens(24f.dp.toPx(), size.minDimension / 2f, true)
        },
        onDrawBackdrop = { drawBackdrop ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                drawBackdrop()
            }
        },
        shape = { shape },
        onDrawSurface = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                drawRect(Color.Black.copy(alpha = 0.1f))
            } else {
                drawRect(Color(0xE6121212))
            }
        }
    )
}
