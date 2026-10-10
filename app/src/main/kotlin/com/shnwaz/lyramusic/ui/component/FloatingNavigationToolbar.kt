@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Lyra Music Project (2026)
 * Shnwaz (github.com/shnwazdeveloper)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.shnwaz.lyramusic.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import com.shnwaz.lyramusic.ui.component.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shnwaz.lyramusic.R
import com.shnwaz.lyramusic.ui.screens.Screens

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    liquidGlass: Boolean = false,
    modifier: Modifier = Modifier,
    onFabClick: (() -> Unit)? = null,
    fabIconRes: Int? = null,
    fabContentDescription: String = "",
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
) {
    val hasOverflowAction = onShuffleClick != null && shuffleIconRes != null
    val hasFabAction = onFabClick != null && fabIconRes != null

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val showSelectedLabels = maxWidth >= 360.dp

        val glassBackdrop = LocalBackdrop.current
        val glassTint =
            if (pureBlack) Color.Black.copy(alpha = 0.30f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
        val glassModifier =
            if (liquidGlass && glassBackdrop != null) {
                Modifier
                    .phoneXGlass(
                        backdrop = glassBackdrop,
                        shape = CircleShape,
                        tint = glassTint,
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            } else if (liquidGlass) {
                Modifier
                    .frostedGlass(shape = CircleShape, tint = glassTint)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            } else {
                Modifier
            }

        Row(
            modifier = glassModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { screen ->
                val selected = isSelected(screen)
                FloatingNavigationToolbarItem(
                    screen = screen,
                    selected = selected,
                    showSelectedLabel = showSelectedLabels,
                    pureBlack = pureBlack,
                    liquidGlass = liquidGlass,
                    onClick = { onItemClick(screen, selected) },
                )
            }

            if (hasOverflowAction) {
                FloatingToolbarOverflowAction(
                    pureBlack = pureBlack,
                    liquidGlass = liquidGlass,
                    onShuffleClick = onShuffleClick,
                    shuffleIconRes = shuffleIconRes,
                    shuffleContentDescription = shuffleContentDescription,
                    onMusicRecognitionClick = onMusicRecognitionClick,
                    musicRecognitionContentDescription = musicRecognitionContentDescription,
                )
            } else if (hasFabAction) {
                FloatingToolbarFabAction(
                    pureBlack = pureBlack,
                    liquidGlass = liquidGlass,
                    onClick = onFabClick,
                    iconRes = fabIconRes,
                    contentDescription = fabContentDescription,
                )
            }
        }
    }
}

// ── Efecto visual Liquid Glass ───────────────────────────────────────────────

/**
 * Aplica el estilo Liquid Glass al toolbar:
 * - Borde superior más brillante (simula refracción de luz en el borde del vidrio)
 * - Borde inferior más sutil (reflejo secundario)
 * - Overlay de highlight en la parte superior (brillo tipo lente)
 */
private fun Modifier.liquidGlassStyle(
    pureBlack: Boolean,
    shape: Shape = CircleShape,
): Modifier = this



// ── Subcomponentes ───────────────────────────────────────────────────────────

@Composable
private fun FloatingToolbarOverflowAction(
    pureBlack: Boolean,
    liquidGlass: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
) {
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { fabMenuExpanded = !fabMenuExpanded },
            modifier = Modifier
                .size(48.dp)
                .toolbarGlass(
                    liquidGlass = liquidGlass,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                ),
            shape = CircleShape,
            color = if (glassActive(liquidGlass)) Color.Transparent else floatingToolbarFabContainerColor(
                pureBlack = pureBlack,
                liquidGlass = liquidGlass
            ),
            contentColor = if (glassActive(liquidGlass)) MaterialTheme.colorScheme.onSurface else floatingToolbarFabContentColor(
                pureBlack = pureBlack,
                liquidGlass = liquidGlass
            ),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription =
                        shuffleContentDescription.ifEmpty {
                            stringResource(R.string.more)
                        },
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        DropdownMenu(
            expanded = fabMenuExpanded,
            onDismissRequest = { fabMenuExpanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = {
                    fabMenuExpanded = false
                    onMusicRecognitionClick?.invoke()
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                        contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.mic),
                                contentDescription =
                                    musicRecognitionContentDescription.ifEmpty {
                                        stringResource(R.string.music_recognition)
                                    },
                            )
                        }
                    }
                },
                enabled = onMusicRecognitionClick != null,
                colors =
                    MenuDefaults.itemColors(
                        textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor = if (pureBlack) Color.White.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        disabledLeadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = {
                        fabMenuExpanded = false
                        onShuffleClick()
                    },
                    leadingIcon = {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                            contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(shuffleIconRes),
                                    contentDescription =
                                        shuffleContentDescription.ifEmpty {
                                            stringResource(R.string.shuffle)
                                        },
                                )
                            }
                        }
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FloatingToolbarFabAction(
    pureBlack: Boolean,
    liquidGlass: Boolean,
    onClick: (() -> Unit)?,
    iconRes: Int?,
    contentDescription: String,
) {
    if (onClick == null || iconRes == null) return

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .toolbarGlass(
                liquidGlass = liquidGlass,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
            ),
        shape = CircleShape,
        color = if (glassActive(liquidGlass)) Color.Transparent else floatingToolbarFabContainerColor(pureBlack = pureBlack, liquidGlass = liquidGlass),
        contentColor = if (glassActive(liquidGlass)) MaterialTheme.colorScheme.onSurface else floatingToolbarFabContentColor(pureBlack = pureBlack, liquidGlass = liquidGlass),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription =
                    contentDescription.ifEmpty {
                        stringResource(R.string.create_playlist)
                    },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FloatingNavigationToolbarItem(
    screen: Screens,
    selected: Boolean,
    showSelectedLabel: Boolean,
    pureBlack: Boolean,
    liquidGlass: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val useGlassStyle = glassActive(liquidGlass)
    val containerColor by animateColorAsState(
        targetValue =
            when {
                selected && useGlassStyle -> Color.Transparent
                selected -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            },
        label = "tabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            when {
                selected && useGlassStyle -> MaterialTheme.colorScheme.onSurface
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            },
        label = "tabContent",
    )
    val selectedGlass =
        if (selected) {
            Modifier.toolbarGlass(
                liquidGlass = liquidGlass,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
            )
        } else {
            Modifier
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabScale",
    )
    val showLabel = false

    Box(
        modifier =
            Modifier
                .scale(scale)
                .size(48.dp)
                .then(selectedGlass)
                .clip(CircleShape)
                .background(color = containerColor, shape = CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
            contentDescription = stringResource(screen.titleId),
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Real glass helpers (bottom bar sits outside the recorded backdrop) ──────

@Composable
private fun glassActive(liquidGlass: Boolean): Boolean = liquidGlass

@Composable
private fun Modifier.toolbarGlass(liquidGlass: Boolean, tint: Color): Modifier {
    val backdrop = LocalBackdrop.current
    return if (liquidGlass && backdrop != null) {
        this.phoneXGlass(
            backdrop = backdrop,
            shape = CircleShape,
            tint = tint,
            blurRadius = 2.dp,
            refractionHeight = 12.dp,
            refractionAmount = 24.dp,
            rimAlpha = 0.7f,
        )
    } else if (liquidGlass) {
        this.frostedGlass(shape = CircleShape, tint = tint)
    } else {
        this
    }
}

// ── Color helpers ────────────────────────────────────────────────────────────

@Composable
private fun floatingToolbarContainerColor(pureBlack: Boolean, liquidGlass: Boolean): Color {
    return Color.Transparent
}

@Composable
private fun floatingToolbarFabContainerColor(pureBlack: Boolean, liquidGlass: Boolean): Color {
    return MaterialTheme.colorScheme.primary
}

@Composable
private fun floatingToolbarFabContentColor(pureBlack: Boolean, liquidGlass: Boolean): Color {
    return MaterialTheme.colorScheme.onPrimary
}

@Composable
private fun floatingToolbarSelectedItemContainerColor(pureBlack: Boolean, liquidGlass: Boolean): Color {
    return MaterialTheme.colorScheme.primary
}

@Composable
private fun floatingToolbarSelectedItemContentColor(pureBlack: Boolean, liquidGlass: Boolean): Color {
    return MaterialTheme.colorScheme.onPrimary
}

@Composable
private fun floatingToolbarItemContentColor(pureBlack: Boolean, liquidGlass: Boolean): Color {
    return Color.White.copy(alpha = 0.82f)
}


// Estas dos no cambian con liquidGlass (solo se usan en el DropdownMenu)
@Composable
private fun floatingToolbarMenuIconContainerColor(pureBlack: Boolean): Color {
    return if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun floatingToolbarMenuIconContentColor(pureBlack: Boolean): Color {
    return if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
}
