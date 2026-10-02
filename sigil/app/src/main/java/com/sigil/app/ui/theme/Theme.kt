package com.sigil.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun SigilTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val sigilColors = if (darkTheme) DarkSigilColors else LightSigilColors

    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = sigilColors.tint,
            background = sigilColors.bg,
            surface = sigilColors.paper,
            onBackground = sigilColors.label,
            onSurface = sigilColors.label,
            secondary = sigilColors.seal,
            error = sigilColors.critical,
            outline = sigilColors.sep,
        )
    } else {
        lightColorScheme(
            primary = sigilColors.tint,
            background = sigilColors.bg,
            surface = sigilColors.paper,
            onBackground = sigilColors.label,
            onSurface = sigilColors.label,
            secondary = sigilColors.seal,
            error = sigilColors.critical,
            outline = sigilColors.sep,
        )
    }

    CompositionLocalProvider(LocalSigilColors provides sigilColors) {
        MaterialTheme(colorScheme = materialScheme, typography = SigilTypography, content = content)
    }
}
