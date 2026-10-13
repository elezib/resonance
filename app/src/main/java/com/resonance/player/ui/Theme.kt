package com.resonance.player.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.resonance.player.data.ThemeMode
import com.resonance.player.data.UiSettings

private val Teal = Color(0xFF4FD1C5)
private val DeepTeal = Color(0xFF00796B)
private val Coral = Color(0xFFFF8A65)
private val Violet = Color(0xFFB39DDB)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF00201D),
    secondary = Violet,
    tertiary = Coral,
    background = Color(0xFF0D1117),
    surface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFF1F2933),
    surfaceContainer = Color(0xFF151C24),
    surfaceContainerHigh = Color(0xFF1B232C),
    surfaceContainerHighest = Color(0xFF232D37),
)

private val LightScheme = lightColorScheme(
    primary = DeepTeal,
    secondary = Color(0xFF5E4B8B),
    tertiary = Color(0xFFBF360C),
)

@Composable
fun ResonanceTheme(ui: UiSettings, artColor: Int?, content: @Composable () -> Unit) {
    val dark = when (ui.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    var scheme = when {
        ui.materialYou && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    if (ui.artColors && artColor != null) {
        val c = Color(artColor)
        val accent = if (dark) {
            if (c.luminance() < 0.35f) lerp(c, Color.White, 0.45f) else c
        } else {
            if (c.luminance() > 0.45f) lerp(c, Color.Black, 0.45f) else c
        }
        scheme = scheme.copy(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White,
        )
    }

    if (ui.themeMode == ThemeMode.AMOLED) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF121212),
            surfaceContainerHighest = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFF1A1A1A),
        )
    }

    val primary by animateColorAsState(scheme.primary, tween(600), label = "primary")
    MaterialTheme(colorScheme = scheme.copy(primary = primary), typography = Typography(), content = content)
}
