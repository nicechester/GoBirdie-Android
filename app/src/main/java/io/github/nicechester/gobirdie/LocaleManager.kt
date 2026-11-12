package io.github.nicechester.gobirdie

import android.app.LocaleManager
import android.content.Context
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

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val localeList = if (language.code != null) {
                LocaleList.forLanguageTags(language.code)
            } else {
                LocaleList.getEmptyLocaleList()
            }
            localeManager.applicationLocales = localeList
        }
    }
}
