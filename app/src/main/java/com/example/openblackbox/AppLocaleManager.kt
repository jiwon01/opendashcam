package com.example.openblackbox

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocaleManager {

    private val fallbackLocale = Locale.forLanguageTag("en-US")

    fun wrapContext(base: Context): Context {
        val locale = resolveLocale(base, AppSettings(base).getAppLanguageMode())
        Locale.setDefault(locale)
        val current = base.resources.configuration.locales[0]
        if (current.language == locale.language && current.country == locale.country) {
            return base
        }

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun resolveLocale(context: Context, appLanguageMode: AppLanguageMode): Locale {
        appLanguageMode.localeTag?.let { tag ->
            return Locale.forLanguageTag(tag)
        }
        val systemLocales = context.resources.configuration.locales
        for (index in 0 until systemLocales.size()) {
            mapSupportedLocale(systemLocales[index])?.let { return it }
        }
        return fallbackLocale
    }

    private fun mapSupportedLocale(locale: Locale): Locale? {
        return when (locale.language.lowercase(Locale.ROOT)) {
            "ko" -> Locale.KOREAN
            "en" -> fallbackLocale
            "ja" -> Locale.JAPANESE
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "es" -> Locale.forLanguageTag("es")
            else -> null
        }
    }
}
