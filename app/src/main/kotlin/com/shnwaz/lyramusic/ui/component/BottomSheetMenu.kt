/*
 * Lyra Music Project (2026)
 * Shnwaz (github.com/shnwazdeveloper)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.shnwaz.lyramusic.ui.component

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val focusManager = LocalFocusManager.current
    val glass = LocalLiquidGlassEnabled.current
    val sheetView = LocalView.current

    if (state.isVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            containerColor = if (glass) Color.Transparent else background,
            scrimColor = if (glass) Color.Black.copy(alpha = 0.10f) else BottomSheetDefaults.ScrimColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            },
            modifier = modifier
                .fillMaxHeight()
                .glassBubble(
                    shape = BottomSheetDefaults.ExpandedShape,
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                )
        ) {
            // Real blur of the app behind the sheet (Android 12+, if the device allows it)
            val contentView = LocalView.current
            SideEffect {
                if (glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val root = contentView.rootView
                        val lp = root.layoutParams as? WindowManager.LayoutParams
                        if (lp != null && lp.blurBehindRadius != 40) {
                            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                            lp.blurBehindRadius = 40
                            val wm = contentView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                            wm.updateViewLayout(root, lp)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .weight(1f, fill = false)
            ) {
                state.content(this)
            }
        }
    }
}
