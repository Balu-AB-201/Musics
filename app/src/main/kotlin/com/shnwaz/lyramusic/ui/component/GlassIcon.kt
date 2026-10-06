/*
 * Lyra Music Project (2026)
 * Liquid glass icon drop-ins.
 *
 * Icon, FilledIconButton and FilledTonalIconButton in this package mirror the
 * Material3 signatures. Importing these instead of the Material3 ones turns
 * every icon into a translucent glass icon (no gradient) and removes the
 * filled container behind icon buttons (transparent background only).
 */

package com.shnwaz.lyramusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

// Glossy glass glyph: soft shadow, bright rim, translucent body, top sheen,
// bottom inner glow and a small specular highlight. Container stays transparent.
@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val base = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val density = LocalDensity.current
    val intrinsic = painter.intrinsicSize
    val sizeModifier = if (intrinsic.isSpecified && intrinsic.width.isFinite() && intrinsic.height.isFinite()) {
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
                val a = base.alpha
                val d = 1.dp.toPx()

                // 1) soft drop shadow (stacked offsets fake a blur)
                for (i in 1..3) {
                    translate(left = 0f, top = d * i * 0.9f) {
                        with(painter) {
                            draw(
                                size = size,
                                alpha = 0.10f * a,
                                colorFilter = ColorFilter.tint(base.copy(alpha = 1f).darken()),
                            )
                        }
                    }
                }

                // 2) bright rim catching light on the upper-left edge
                translate(left = -0.6f * d, top = -0.6f * d) {
                    with(painter) {
                        draw(size = size, alpha = 0.75f * a, colorFilter = ColorFilter.tint(Color.White))
                    }
                }

                // 3) glass body + sheen, clipped to the glyph with SrcAtop
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    with(painter) {
                        draw(size = size, alpha = 0.80f * a, colorFilter = ColorFilter.tint(base.copy(alpha = 1f)))
                    }
                    // top sheen + bottom inner glow
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White.copy(alpha = 0.65f),
                                0.45f to Color.White.copy(alpha = 0.05f),
                                0.80f to Color.Transparent,
                                1.00f to Color.White.copy(alpha = 0.35f),
                            ),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                    // small specular highlight, top-right
                    drawOval(
                        color = Color.White.copy(alpha = 0.95f),
                        topLeft = Offset(size.width * 0.58f, size.height * 0.08f),
                        size = Size(size.width * 0.30f, size.height * 0.10f),
                        blendMode = BlendMode.SrcAtop,
                    )
                    canvas.restore()
                }
            },
    )
}

private fun Color.darken(): Color = Color(red * 0.35f, green * 0.35f, blue * 0.35f, 1f)

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
