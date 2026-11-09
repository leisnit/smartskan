package com.example.smartskan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.smartskan.data.ColourStyle
import com.example.smartskan.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A65),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF2EA),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6361),
    secondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFF48607B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5CE),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFF9DF2EA),
    secondary = Color(0xFFB0CCC9),
    secondaryContainer = Color(0xFF324B49),
    tertiary = Color(0xFFB0C9E8),
)

/** Whether [mode] resolves to a dark theme right now. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// Colourful: bright blue on clean white (or deep grey in dark mode).
private val ColourfulLight = lightColorScheme(
    primary = Color(0xFF2F7BF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF5B6478),
    secondaryContainer = Color(0xFFE4E9F4),
    onSecondaryContainer = Color(0xFF182033),
    tertiary = Color(0xFFF5A000),
    background = Color.White,
    surface = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF3F5FA),
    surfaceContainerHigh = Color(0xFFEDF0F7),
    surfaceContainerHighest = Color(0xFFE7EBF3),
)

private val ColourfulDark = darkColorScheme(
    primary = Color(0xFF8DB4FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF1B4BA0),
    onPrimaryContainer = Color(0xFFDDE8FF),
    secondary = Color(0xFFBEC6DB),
    secondaryContainer = Color(0xFF3A4357),
    tertiary = Color(0xFFFFC55C),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
)

/** Per-tool accent colours used by the Colourful style. */
object Accent {
    val Blue = Color(0xFF2F7BF5)
    val Green = Color(0xFF1DB36B)
    val Cyan = Color(0xFF0FB5D6)
    val Amber = Color(0xFFF5A000)
    val Purple = Color(0xFF8B5CF6)
    val Red = Color(0xFFEF4444)
    val Orange = Color(0xFFF97316)
    val Teal = Color(0xFF0EA5A0)
    val Indigo = Color(0xFF5B5BF0)
}

/** True when the Colourful style is active, so icons use their own accent colours. */
val LocalColourful = staticCompositionLocalOf { false }

/**
 * Container and icon colours for an icon tile. Colourful: a pastel tint of [accent] with the accent
 * as the icon colour. Otherwise the [classicContainer]/[classicContent] theme colours.
 */
@Composable
fun accentTileColors(accent: Color, classicContainer: Color, classicContent: Color): Pair<Color, Color> {
    if (!LocalColourful.current) return classicContainer to classicContent
    val surface = MaterialTheme.colorScheme.surface
    return if (surface.luminance() < 0.5f) {
        accent.copy(alpha = 0.22f).compositeOver(surface) to lerp(accent, Color.White, 0.3f)
    } else {
        accent.copy(alpha = 0.12f).compositeOver(surface) to accent
    }
}

@Composable
fun SmartSkanTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colourStyle: ColourStyle = ColourStyle.COLOURFUL,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val context = LocalContext.current
    val colors = when {
        colourStyle == ColourStyle.COLOURFUL -> if (dark) ColourfulDark else ColourfulLight
        colourStyle == ColourStyle.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalColourful provides (colourStyle == ColourStyle.COLOURFUL)) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
