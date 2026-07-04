package com.familyshield.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
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

    /** Saved language tag, or "" to follow the system. */
    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    /** Persist the choice and recreate the activity so it takes effect immediately. */
    fun apply(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        context.findActivity()?.recreate()
    }

    /** Wrap a base context so resources + layout direction follow the saved language. */
    fun wrap(context: Context): Context {
        val tag = saved(context)
        if (tag.isEmpty()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
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
