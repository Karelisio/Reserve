package com.karelisio.reserve.domain

import java.util.Locale

/**
 * The app speaks French and English. Texts are written in both languages where they are
 * used (`t("Réglages", "Settings")`), and follow the app language (Android 13+ per-app
 * language, otherwise the system language). Works everywhere: screens, notifications,
 * widget, Android Auto, background work and domain code.
 */
object Lang {
    val isEnglish: Boolean get() = Locale.getDefault().language == "en"

    /** Locale for numbers, prices and dates. */
    val locale: Locale get() = if (isEnglish) Locale.UK else Locale.FRANCE
}

/** French text, or its English translation when the app is in English. */
fun t(fr: String, en: String): String = if (Lang.isEnglish) en else fr
