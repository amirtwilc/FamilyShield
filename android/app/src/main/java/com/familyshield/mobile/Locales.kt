package com.familyshield.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * In-app language selection (English / Hebrew) with automatic RTL.
 *
 * The chosen language tag is persisted in SharedPreferences and applied by wrapping
 * the Activity's base context (see [MainActivity.attachBaseContext]). Setting the
 * Configuration locale drives both string-resource selection *and* the layout
 * direction, so Hebrew renders fully right-to-left without any per-view work.
 *
 * Tag values: "" = follow the system language, "en" = English, "he" = Hebrew.
 */
object Locales {
    private const val PREFS = "familyshield"
    private const val KEY = "app_language"

    var currentTag by mutableStateOf("")
        private set

    /** Saved language tag, or "" to follow the system. */
    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun initialize(context: Context) {
        currentTag = saved(context)
    }

    /** Persist the choice and notify Compose so text and layout direction update immediately. */
    fun apply(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        currentTag = tag
        Locale.setDefault(resolveLocale(context, tag))
    }

    /** Wrap a base context so resources + layout direction follow the saved language. */
    fun wrap(context: Context, tag: String = saved(context)): Context {
        val locale = resolveLocale(context, tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun resolveLocale(context: Context, tag: String): Locale =
        if (tag.isBlank()) systemLocale(context) else Locale.forLanguageTag(tag)

    fun isRtl(locale: Locale): Boolean =
        locale.language.lowercase(Locale.US) in setOf("ar", "fa", "he", "iw", "ur")

    private fun systemLocale(context: Context): Locale {
        val configuration = context.applicationContext.resources.configuration
        return if (Build.VERSION.SDK_INT >= 24) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }
}

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
