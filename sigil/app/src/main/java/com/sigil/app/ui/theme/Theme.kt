package com.sigil.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import com.sigil.app.designsystem.CircadianPhase
import com.sigil.app.designsystem.circadianColors
import com.sigil.app.designsystem.rememberCircadianPhase

@Composable
fun SigilTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val phase = rememberCircadianPhase()
    val base = if (darkTheme) DarkSigilColors else LightSigilColors
    val c = circadianColors(base, DarkSigilColors, phase)
    // Evening and sleep surfaces are dark whatever the system theme says.
    val dark = darkTheme || phase == CircadianPhase.EVENING || phase == CircadianPhase.SLEEP
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.tint, background = c.bg, surface = c.paper, onBackground = c.label,
            onSurface = c.label, secondary = c.seal, error = c.critical, outline = c.sep,
        )
    } else {
        lightColorScheme(
            primary = c.tint, background = c.bg, surface = c.paper, onBackground = c.label,
            onSurface = c.label, secondary = c.seal, error = c.critical, outline = c.sep,
        )
    }
    val selection = TextSelectionColors(handleColor = c.tint, backgroundColor = c.tint.copy(alpha = 0.28f))
    MaterialTheme(colorScheme = scheme, typography = SigilTypography) {
        CompositionLocalProvider(
            LocalSigilColors provides c,
            LocalTextSelectionColors provides selection,
            LocalTextStyle provides SigilType.body.copy(color = c.label),
            content = content,
        )
    }
}

/** Shorthand for the current palette: `Sigil.colors.tint`. */
object Sigil {
    val colors: SigilColors
        @Composable @ReadOnlyComposable
        get() = LocalSigilColors.current
}
