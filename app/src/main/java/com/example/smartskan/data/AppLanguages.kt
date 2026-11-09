package com.example.smartskan.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** A language the app is translated into, named in that language so it can always be recognised. */
class AppLanguage(val tag: String, val name: String)

object AppLanguages {
    /** Follow the device's language. Shown under the translated "System default" label. */
    val SYSTEM = AppLanguage("", "")

    val all = listOf(
        SYSTEM,
        AppLanguage("bs", "Bosanski"),
        AppLanguage("ca", "Català"),
        AppLanguage("cs", "Čeština"),
        AppLanguage("cy", "Cymraeg"),
        AppLanguage("da", "Dansk"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("et", "Eesti"),
        AppLanguage("en-GB", "English"),
        AppLanguage("es", "Español"),
        AppLanguage("eu", "Euskara"),
        AppLanguage("fr", "Français"),
        AppLanguage("ga", "Gaeilge"),
        AppLanguage("gl", "Galego"),
        AppLanguage("hr", "Hrvatski"),
        AppLanguage("is", "Íslenska"),
        AppLanguage("it", "Italiano"),
        AppLanguage("sw", "Kiswahili"),
        AppLanguage("lv", "Latviešu"),
        AppLanguage("lb", "Lëtzebuergesch"),
        AppLanguage("lt", "Lietuvių"),
        AppLanguage("hu", "Magyar"),
        AppLanguage("mt", "Malti"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("nb", "Norsk bokmål"),
        AppLanguage("pl", "Polski"),
        AppLanguage("pt", "Português"),
        AppLanguage("ro", "Română"),
        AppLanguage("sq", "Shqip"),
        AppLanguage("sk", "Slovenčina"),
        AppLanguage("sl", "Slovenščina"),
        AppLanguage("fi", "Suomi"),
        AppLanguage("sv", "Svenska"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("el", "Ελληνικά"),
        AppLanguage("be", "Беларуская"),
        AppLanguage("bg", "Български"),
        AppLanguage("mk", "Македонски"),
        AppLanguage("ru", "Русский"),
        AppLanguage("sr", "Српски"),
        AppLanguage("uk", "Українська"),
    )

    /** The language picked in Settings, or [SYSTEM] if the app follows the device. */
    fun current(): AppLanguage {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return SYSTEM
        return all.firstOrNull { it.tag == locale.toLanguageTag() }
            ?: all.firstOrNull { it !== SYSTEM && it.tag.substringBefore('-') == locale.language }
            ?: SYSTEM
    }

    /** Applies [language] and remembers it. Android recreates the activity with the new translations. */
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}
