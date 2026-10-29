package com.github.blogshot.crtv.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil3.compose.AsyncImage

/** Farben. Akzent in Crunchyroll-Orange, Rest dunkel und neutral wie beim hanime-Client. */
object CrColors {
    val Background = Color(0xFF0B0B10)
    val Accent = Color(0xFFF47521)
    val Text = Color(0xFFF2F2F5)
    val TextMuted = Color(0xFFA0A0AE)
    val GlassFill = Color.White.copy(alpha = 0.07f)
    val GlassFillFocused = Color.White.copy(alpha = 0.16f)
    val GlassEdgeTop = Color.White.copy(alpha = 0.28f)
    val GlassEdgeBottom = Color.White.copy(alpha = 0.06f)
}

/**
 * Hintergrundbild der aktuell fokussierten Serie. Karten setzen es beim Fokus; der [GlassBackground] zeigt es
 * stark weichgezeichnet hinter allen Glasflächen.
 */
val LocalBackdrop = staticCompositionLocalOf<MutableState<String?>> { mutableStateOf(null) }

@Composable
fun CrTheme(content: @Composable () -> Unit) {
    val backdrop = remember { mutableStateOf<String?>(null) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CrColors.Accent,
            onPrimary = Color.White,
            background = CrColors.Background,
            surface = Color.Transparent,
            onSurface = CrColors.Text,
            onSurfaceVariant = CrColors.TextMuted,
            border = CrColors.GlassEdgeTop,
        ),
    ) {
        // Ohne umgebende Surface wäre die Standard-Textfarbe schwarz.
        CompositionLocalProvider(LocalBackdrop provides backdrop, LocalContentColor provides CrColors.Text, content = content)
    }
}

/** Unterste Ebene: dunkler Verlauf mit farbigem Schimmer, darüber das weichgezeichnete Bild der Auswahl. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val backdrop by LocalBackdrop.current
    Box(
        Modifier
            .fillMaxSize()
            .background(CrColors.Background)
            .background(
                Brush.radialGradient(
                    0f to Color(0xFF3A1A0C),
                    0.6f to CrColors.Background,
                    radius = 1600f,
                ),
            ),
    ) {
        Crossfade(targetState = backdrop, animationSpec = tween(600), label = "backdrop") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // blur wirkt ab Android 12; darunter sorgt der Schleier allein für Lesbarkeit.
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                    alpha = 0.55f,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to CrColors.Background.copy(alpha = 0.85f),
                    0.5f to CrColors.Background.copy(alpha = 0.45f),
                    1f to CrColors.Background.copy(alpha = 0.65f),
                ),
            ),
        )
        content()
    }
}

/** Mattglas-Fläche: halbtransparente Füllung, heller Rand oben, dunkler unten. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    focused: Boolean = false,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(if (focused) CrColors.GlassFillFocused else CrColors.GlassFill, shape)
    .border(
        width = if (focused) 2.dp else borderWidth,
        brush = if (focused) SolidColor(CrColors.Accent)
        else Brush.verticalGradient(listOf(CrColors.GlassEdgeTop, CrColors.GlassEdgeBottom)),
        shape = shape,
    )

/** Textfeld im Glas-Stil für die Fernbedienung (öffnet die Bildschirmtastatur bei OK). */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = CrColors.Text),
        cursorBrush = SolidColor(CrColors.Accent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .glass(RoundedCornerShape(14.dp), focused = focused)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.titleLarge, color = CrColors.TextMuted)
                }
                inner()
            }
        },
    )
}
