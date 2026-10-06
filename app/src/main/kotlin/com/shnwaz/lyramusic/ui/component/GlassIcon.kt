/*
 * Lyra Music Project (2026)
 * Glossy "liquid glass bubble" icons.
 *
 * Icon, FilledIconButton and FilledTonalIconButton in this package mirror the
 * Material3 signatures. Importing these instead of the Material3 ones gives
 * every icon a glossy glass layer (rim light, dome sheen, corner highlight,
 * soft shadow) and removes the filled container behind icon buttons.
 */

package com.shnwaz.lyramusic.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private fun DrawScope.drawGlyph(
    painter: Painter,
    color: Color,
    alpha: Float,
    dx: Float = 0f,
    dy: Float = 0f,
) {
    translate(left = dx, top = dy) {
        with(painter) {
            draw(size = size, alpha = alpha, colorFilter = ColorFilter.tint(color))
        }
    }
}

// Colors for the glass bubbles. Each icon picks one and keeps it.
private val BUBBLE_COLORS = listOf(
    Color(0xFFE8833A), // orange
    Color(0xFF7FBF6A), // green
    Color(0xFF4FA3B8), // teal
    Color(0xFFE06A7C), // rose
    Color(0xFF8C7AE0), // violet
    Color(0xFF4F8DEB), // blue
    Color(0xFFE5B73B), // amber
    Color(0xFFD9534F), // red
    Color(0xFF47B89A), // mint
)

/** Colored glass bubble drawn behind the glyph (body, dome sheen, rim, corner highlight, shadow). */
private fun DrawScope.drawGlassBubble(color: Color) {
    val pad = size.minDimension * 0.30f
    val tl = Offset(-pad, -pad)
    val bs = Size(size.width + pad * 2f, size.height + pad * 2f)
    val radius = CornerRadius(bs.minDimension * 0.30f)
    val d = 1.dp.toPx()

    // soft shadow under the bubble, tinted with the bubble color
    for (i in 1..4) {
        drawRoundRect(
            color = color.copy(alpha = 0.20f),
            topLeft = Offset(tl.x, tl.y + d * i * 1.1f),
            size = bs,
            cornerRadius = radius,
        )
    }
    // glass body
    drawRoundRect(
        color = color.copy(alpha = 0.88f),
        topLeft = tl,
        size = bs,
        cornerRadius = radius,
    )
    // dome sheen: bright top, clear middle, deeper color at the bottom
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.50f),
            0.5f to Color.White.copy(alpha = 0.04f),
            1.0f to Color.Black.copy(alpha = 0.16f),
            startY = tl.y,
            endY = tl.y + bs.height,
        ),
        topLeft = tl,
        size = bs,
        cornerRadius = radius,
    )
    // emboss bevel: light edge top-left, shade edge bottom-right, just inside the border
    val inset = 2.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.80f),
            0.45f to Color.White.copy(alpha = 0.0f),
            0.55f to Color.Black.copy(alpha = 0.0f),
            1.0f to Color.Black.copy(alpha = 0.35f),
            start = tl,
            end = Offset(tl.x + bs.width, tl.y + bs.height),
        ),
        topLeft = Offset(tl.x + inset, tl.y + inset),
        size = Size(bs.width - inset * 2f, bs.height - inset * 2f),
        cornerRadius = CornerRadius(radius.x - inset),
        style = Stroke(width = 2.5.dp.toPx()),
    )
    // rim: bright on top, darker at the bottom edge
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.90f),
            0.5f to Color.White.copy(alpha = 0.20f),
            1.0f to Color.Black.copy(alpha = 0.22f),
            startY = tl.y,
            endY = tl.y + bs.height,
        ),
        topLeft = tl,
        size = bs,
        cornerRadius = radius,
        style = Stroke(width = 1.2.dp.toPx()),
    )
    // specular highlight, top-right corner
    rotate(
        degrees = -25f,
        pivot = Offset(tl.x + bs.width * 0.75f, tl.y + bs.height * 0.15f),
    ) {
        drawOval(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = Offset(tl.x + bs.width * 0.62f, tl.y + bs.height * 0.11f),
            size = Size(bs.width * 0.24f, bs.height * 0.08f),
        )
    }
}

/** Small icons: no bubble, just a glossy glyph. */
private fun DrawScope.drawGlossyGlyph(painter: Painter, base: Color) {
    val a = base.alpha
    val d = 1.dp.toPx()
    val solid = base.copy(alpha = 1f)
    drawGlyph(painter, Color.Black, 0.18f * a, 0f, d * 1.2f)
    drawGlyph(painter, Color.White, 0.75f * a, -0.6f * d, -0.6f * d)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawGlyph(painter, solid, 0.85f * a)
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.65f),
                0.5f to Color.White.copy(alpha = 0.05f),
                1.0f to Color.White.copy(alpha = 0.30f),
            ),
            blendMode = BlendMode.SrcAtop,
        )
        canvas.restore()
    }
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val base = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val bubbleIndex = rememberSaveable { Random.nextInt(BUBBLE_COLORS.size) }
    val density = LocalDensity.current
    val intrinsic = painter.intrinsicSize
    val sizeModifier =
        if (intrinsic.isSpecified && intrinsic.width.isFinite() && intrinsic.height.isFinite()) {
            with(density) { Modifier.size(intrinsic.width.toDp(), intrinsic.height.toDp()) }
        } else {
            Modifier
        }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(semanticsModifier)
            .then(sizeModifier)
            .drawBehind {
                if (size.minDimension >= 18.dp.toPx() && base.alpha > 0.3f) {
                    drawGlassBubble(BUBBLE_COLORS[bubbleIndex])
                    drawGlyph(painter, Color.Black, 0.30f * base.alpha, 0f, 1.5.dp.toPx())
                    drawGlyph(painter, Color.White, base.alpha)
                } else {
                    drawGlossyGlyph(painter, base)
                }
            },
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = Icon(
    painter = rememberVectorPainter(imageVector),
    contentDescription = contentDescription,
    modifier = modifier,
    tint = tint,
)

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

private fun IconButtonColors.clear(): IconButtonColors = copy(
    containerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
)

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) = androidx.compose.material3.FilledIconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors.clear(),
    interactionSource = interactionSource,
    content = content,
)

@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) = androidx.compose.material3.FilledTonalIconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors.clear(),
    interactionSource = interactionSource,
    content = content,
)
