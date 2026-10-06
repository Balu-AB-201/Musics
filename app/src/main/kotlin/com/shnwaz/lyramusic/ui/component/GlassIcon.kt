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

// Glass look: frosted body + soft shadow below + bright rim above. No gradients.
private const val BODY_ALPHA = 0.62f
private const val RIM_ALPHA = 0.85f
private const val SHADOW_ALPHA = 0.28f

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val base = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val glassModifier = modifier.drawBehind {
        val dx = 0.7.dp.toPx()
        val dy = 1.2.dp.toPx()
        // soft depth shadow (down/right)
        translate(left = dx, top = dy) {
            with(painter) {
                draw(
                    size = size,
                    alpha = SHADOW_ALPHA * base.alpha,
                    colorFilter = ColorFilter.tint(Color.Black),
                )
            }
        }
        // bright glass rim (up/left)
        translate(left = -dx * 0.7f, top = -dy * 0.7f) {
            with(painter) {
                draw(
                    size = size,
                    alpha = RIM_ALPHA * base.alpha,
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
    androidx.compose.material3.Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = glassModifier,
        tint = base.copy(alpha = base.alpha * BODY_ALPHA),
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
