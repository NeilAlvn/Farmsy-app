package app.farmsy.android.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/// In-app language override — the Android twin of iOS LanguageManager. Lets a
/// user on an English phone run the app in Dutch/French/German (and back to the
/// system default). The chosen code is stored in SharedPreferences; the locale is
/// applied by wrapping the base context in MainActivity.attachBaseContext, and a
/// change takes effect on recreate() (no manual restart, unlike iOS). Endonyms
/// stay untranslated on purpose — a French speaker looks for "Français", not
/// "French".
object LanguageStore {

    enum class Lang(val code: String, val displayName: String, val flag: String) {
        SYSTEM("", "System", "🌐"),
        EN("en", "English", "🇬🇧"),
        NL("nl", "Nederlands", "🇳🇱"),
        FR("fr", "Français", "🇫🇷"),
        DE("de", "Deutsch", "🇩🇪");

        companion object {
            fun fromCode(code: String?): Lang = entries.firstOrNull { it.code == code } ?: SYSTEM
        }
    }

    private const val PREFS = "farmsy"
    private const val KEY = "app_language"

    fun current(context: Context): Lang =
        Lang.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ""))

    fun set(context: Context, lang: Lang) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang.code).apply()
    }

    /// Wrap a base context so the whole activity resolves resources in the chosen
    /// locale. Returns the base unchanged when set to System default.
    fun wrap(base: Context): Context {
        val code = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (code.isEmpty()) return base
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
