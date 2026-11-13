package io.github.nicechester.gobirdie

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val code: String?, val displayName: String) {
    SYSTEM(null, "System Default"),
    ENGLISH("en", "English"),
    KOREAN("ko", "한국어"),
    SPANISH("es", "Español"),
    JAPANESE("ja", "日本語");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

object LocaleHelper {
    private const val PREFS_NAME = "gobirdie_locale"
    private const val KEY_LANGUAGE = "language"

    fun getSavedLanguage(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANGUAGE, null)
        return AppLanguage.fromCode(code)
    }

    /**
     * Applies the saved in-app language to a base context.
     * Android 13+ handles this via LocaleManager (per-app locales), so only
     * older devices (minSdk 27) need the context to be wrapped manually.
     * Must be called from attachBaseContext of Application and Activity.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        // "System Default" resolves to the device locale explicitly, so a previously applied override can't linger.
        val locale = getSavedLanguage(base).code?.let { Locale.forLanguageTag(it) }
            ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun setLanguage(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .commit()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = activity.getSystemService(LocaleManager::class.java)
            val localeList = if (language.code != null) {
                LocaleList.forLanguageTags(language.code)
            } else {
                LocaleList.getEmptyLocaleList()
            }
            localeManager.applicationLocales = localeList
        } else {
            // Pre-13: keep process-wide resources (used by ViewModels via application context) in sync,
            // then recreate the activity so attachBaseContext re-runs with the new locale.
            applyToProcess(activity.application, language)
            activity.recreate()
        }
    }

    @Suppress("DEPRECATION") // updateConfiguration is the only way to retarget Application resources pre-13
    private fun applyToProcess(app: Context, language: AppLanguage) {
        val locale = language.code?.let { Locale.forLanguageTag(it) }
            ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(locale)
        val res = app.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        res.updateConfiguration(config, res.displayMetrics)
    }
}
