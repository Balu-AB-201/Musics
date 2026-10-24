package com.shnwaz.lyramusic.ui.component

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** True on Android TV, Google TV, Fire TV and other leanback devices. */
fun isTelevision(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Provided once in MainActivity. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Draws a clear highlight ring around the item the remote's D-pad has selected.
 * Does nothing on phones and tablets.
 *
 * Put it BEFORE the clickable in the modifier chain, so it can see the clickable's focus.
 */
@Composable
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(12.dp)): Modifier {
    if (!LocalIsTv.current) return this
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.hasFocus }
        .drawWithContent {
            drawContent()
            if (focused) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline = outline, color = ring.copy(alpha = 0.18f))
                drawOutline(outline = outline, color = ring, style = Stroke(width = 3.dp.toPx()))
            }
        }
}
