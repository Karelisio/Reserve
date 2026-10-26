package com.karelisio.reserve.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** App language (Android 13+): system, French or English. Applied and persisted by Android. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null), FRENCH("fr"), ENGLISH("en");

    companion object {
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun current(context: Context): AppLanguage {
            if (!supported) return SYSTEM
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return SYSTEM
            return when (locales.takeIf { !it.isEmpty }?.get(0)?.language) {
                "fr" -> FRENCH
                "en" -> ENGLISH
                else -> SYSTEM
            }
        }

        fun apply(context: Context, language: AppLanguage) {
            if (!supported) return
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        }
    }
}
