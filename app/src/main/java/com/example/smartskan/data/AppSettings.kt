package com.example.smartskan.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.smartskan.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

/** The app's colour palette. */
enum class ColourStyle(@StringRes val label: Int, @StringRes val description: Int) {
    COLOURFUL(R.string.style_colourful, R.string.style_colourful_desc),
    CLASSIC(R.string.style_classic, R.string.style_classic_desc),
    WALLPAPER(R.string.style_wallpaper, R.string.style_wallpaper_desc),
}

/** How much editing the document scanner offers after capture. */
enum class ScanMode(@StringRes val label: Int, @StringRes val description: Int) {
    FULL(R.string.scan_mode_full, R.string.scan_mode_full_desc),
    FILTERS(R.string.scan_mode_standard, R.string.scan_mode_standard_desc),
    BASIC(R.string.scan_mode_basic, R.string.scan_mode_basic_desc),
}

/** User preferences, persisted in SharedPreferences and exposed as flows. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(enumPref(KEY_THEME, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colourStyle = MutableStateFlow(enumPref(KEY_COLOUR_STYLE, ColourStyle.COLOURFUL))
    val colourStyle: StateFlow<ColourStyle> = _colourStyle.asStateFlow()

    private val _scanMode = MutableStateFlow(enumPref(KEY_SCAN_MODE, ScanMode.FULL))
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _galleryImport = MutableStateFlow(prefs.getBoolean(KEY_GALLERY, true))
    val galleryImport: StateFlow<Boolean> = _galleryImport.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setColourStyle(style: ColourStyle) {
        _colourStyle.value = style
        prefs.edit().putString(KEY_COLOUR_STYLE, style.name).apply()
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        prefs.edit().putString(KEY_SCAN_MODE, mode.name).apply()
    }

    fun setGalleryImport(enabled: Boolean) {
        _galleryImport.value = enabled
        prefs.edit().putBoolean(KEY_GALLERY, enabled).apply()
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_COLOUR_STYLE = "colour_style"
        const val KEY_SCAN_MODE = "scan_mode"
        const val KEY_GALLERY = "gallery_import"
    }
}

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
