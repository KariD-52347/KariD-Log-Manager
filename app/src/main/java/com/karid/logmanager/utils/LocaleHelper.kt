package com.karid.logmanager.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context): Context {
        val lang = PrefsHelper.getLanguage(context)
        if (lang == "system") return context

        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun applyAndRestart(context: Context) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        if (context is Activity) {
            context.finish()
        }
    }
}
