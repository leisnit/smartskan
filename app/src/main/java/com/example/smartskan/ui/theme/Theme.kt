package com.example.smartskan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun SmartSkanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
