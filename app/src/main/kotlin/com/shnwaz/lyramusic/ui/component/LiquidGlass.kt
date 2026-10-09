package com.shnwaz.lyramusic.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
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
        drawGlassRim(shape, rimAlpha)
    },
)

/** True when the "Enable Liquid Glass" setting is on. Provided in MainActivity. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** Rim light: bright top-left, dark middle, soft bottom-right. */
private fun DrawScope.drawGlassRim(shape: Shape, rimAlpha: Float) {
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
}

/**
 * Frosted glass look WITHOUT live refraction: tint, top sheen and rim light.
 * Used for controls inside screens (they are part of the recorded backdrop,
 * so they cannot refract it).
 */
fun Modifier.frostedGlass(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.10f),
    rimAlpha: Float = 0.80f,
): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    // 1. milky base + caller tint (same idea as the mini player surface)
    drawOutline(outline = outline, color = Color.White.copy(alpha = 0.07f))
    drawOutline(outline = outline, color = tint)
    // 2. glass thickness: bright top sheen, soft glow at the bottom edge
    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = 0.26f),
            0.35f to Color.White.copy(alpha = 0.04f),
            0.75f to Color.Transparent,
            1.00f to Color.White.copy(alpha = 0.14f),
        ),
    )
    // 3. diagonal rim light, same as the mini player
    drawGlassRim(shape, rimAlpha)
}

/** Glass bubble for buttons. No-op unless Liquid Glass is enabled. */
@Composable
fun Modifier.glassBubble(
    shape: Shape = CircleShape,
    tint: Color = Color.White.copy(alpha = 0.10f),
): Modifier =
    if (LocalLiquidGlassEnabled.current) this.frostedGlass(shape, tint) else this

/** Backdrop of the screen background (soft color blobs). Provided in MainActivity. */
val LocalScreenBackdrop = staticCompositionLocalOf<PlatformBackdrop?> { null }

@Composable
fun rememberScreenBackdrop(): PlatformBackdrop = rememberLayerBackdrop()

/**
 * Soft colored background that the screen glass panels bend.
 * Draw it BEHIND the screens, never around them.
 */
@Composable
fun ScreenGlassBackground(backdrop: PlatformBackdrop, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val c1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val c2 = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
    val c3 = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .nativeBackdrop(backdrop)
            .drawBehind {
                drawRect(base)
                drawRect(
                    Brush.radialGradient(
                        listOf(c1, Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.15f),
                        radius = size.minDimension * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(c2, Color.Transparent),
                        center = Offset(size.width * 0.90f, size.height * 0.50f),
                        radius = size.minDimension * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(c3, Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.92f),
                        radius = size.minDimension * 0.9f,
                    ),
                )
            },
    )
}

/**
 * Large glass panel (settings cards, groups). Real bending glass when the
 * screen backdrop is available, otherwise frosted glass.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.07f),
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val backdrop = LocalScreenBackdrop.current
    return if (backdrop != null) {
        this.phoneXGlass(
            backdrop = backdrop,
            shape = shape,
            tint = tint,
            blurRadius = 3.dp,
            refractionHeight = 14.dp,
            refractionAmount = 28.dp,
            rimAlpha = 0.70f,
        )
    } else {
        this.frostedGlass(shape, tint)
    }
}

/** Backdrop of the full player background (Immersive design). Provided in Player.kt. */
val LocalPlayerBackdrop = staticCompositionLocalOf<PlatformBackdrop?> { null }

/**
 * Glass for controls that sit on top of the full player background.
 * Real bending glass when the player backdrop is available, otherwise frosted glass.
 */
@Composable
fun Modifier.glassControl(
    shape: Shape = CircleShape,
    tint: Color = Color.White.copy(alpha = 0.10f),
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val backdrop = LocalPlayerBackdrop.current
    return if (backdrop != null) {
        this.phoneXGlass(
            backdrop = backdrop,
            shape = shape,
            tint = tint,
            blurRadius = 2.dp,
            refractionHeight = 12.dp,
            refractionAmount = 24.dp,
            rimAlpha = 0.70f,
        )
    } else {
        this.frostedGlass(shape, tint)
    }
}

/**
 * Glass layer drawn on top of album art: bright top sheen, soft bottom glow
 * and a rim light. No-op unless Liquid Glass is enabled.
 */
@Composable
fun Modifier.glassOverlay(shape: Shape): Modifier =
    if (LocalLiquidGlassEnabled.current) {
        this.drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = 0.30f),
                    0.30f to Color.White.copy(alpha = 0.06f),
                    0.65f to Color.Transparent,
                    1.00f to Color.White.copy(alpha = 0.14f),
                ),
            )
            drawGlassRim(shape, 0.80f)
        }
    } else {
        this
    }

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
