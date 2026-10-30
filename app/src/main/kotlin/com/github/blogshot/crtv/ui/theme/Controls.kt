package com.github.blogshot.crtv.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle

/*
 * Eigene Bedienelemente auf Basis von Modifier.clickable: Sie reagieren auf Fernbedienung (OK/Enter) UND auf
 * Touch. Die tv-material-Varianten nehmen nur Tasten an, deshalb ließ sich auf dem Handy nichts auswählen.
 */

/** Klickbare Glasfläche mit Fokus-Rahmen und leichtem Vergrößern bei Fokus. */
@Composable
fun GlassClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    focusScale: Float = 1.05f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.97f
            focused -> focusScale
            else -> 1f
        },
        label = "scale",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.5f)
            .glass(shape, focused = focused)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) { content(focused) }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GlassClickable(onClick, modifier, enabled, RoundedCornerShape(14.dp)) { focused ->
        Row(
            Modifier
                .background(if (primary || focused) CrColors.Accent.copy(alpha = if (focused) 0.9f else 0.55f) else CrColors.GlassFill)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides CrColors.Text) {
                ProvideTextStyle(MaterialTheme.typography.titleSmall) { content() }
            }
        }
    }
}

/** Zweitrangige Aktion: Glas statt Orange, Orange erst bei Fokus. */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = GlassButton(onClick, modifier, enabled, primary = false, content = content)

/** Eintrag der Seitenleiste. */
@Composable
fun GlassNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassClickable(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), focusScale = 1.03f) { focused ->
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        focused -> CrColors.Accent.copy(alpha = 0.85f)
                        selected -> CrColors.Accent.copy(alpha = 0.25f)
                        else -> CrColors.GlassFill
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides CrColors.Text) {
                ProvideTextStyle(MaterialTheme.typography.titleSmall) { headlineContent() }
            }
        }
    }
}

/** Auswahl-Chip (z. B. Staffeln). */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    GlassClickable(onClick, modifier, shape = RoundedCornerShape(999.dp)) { focused ->
        Row(
            Modifier
                .background(
                    when {
                        focused -> CrColors.Accent.copy(alpha = 0.85f)
                        selected -> CrColors.Accent.copy(alpha = 0.35f)
                        else -> CrColors.GlassFill
                    },
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides CrColors.Text) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
            }
        }
    }
}
