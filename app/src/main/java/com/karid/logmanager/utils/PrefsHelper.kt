package com.karid.logmanager.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karid.logmanager.model.AiHistoryItem
import com.karid.logmanager.model.AiStatus

object PrefsHelper {

    private const val PREFS_NAME = "karid_log_prefs"

    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_THEME        = "theme"
    private const val KEY_LANGUAGE     = "language"
    private const val KEY_SAVE_URI     = "save_uri"
    private const val KEY_AI_HISTORY   = "ai_history"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFirstLaunch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_LAUNCH, true)

    fun setFirstLaunchDone(context: Context) =
        prefs(context).edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

    fun getTheme(context: Context): Int =
        prefs(context).getInt(KEY_THEME, 0)

    fun setTheme(context: Context, theme: Int) =
        prefs(context).edit().putInt(KEY_THEME, theme).apply()

    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "system") ?: "system"

    fun setLanguage(context: Context, lang: String) =
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()

    fun getSaveUri(context: Context): Uri? {
        val uriStr = prefs(context).getString(KEY_SAVE_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun setSaveUri(context: Context, uri: Uri) =
        prefs(context).edit().putString(KEY_SAVE_URI, uri.toString()).apply()

    fun getAiHistory(context: Context): MutableList<AiHistoryItem> {
        val json = prefs(context).getString(KEY_AI_HISTORY, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<AiHistoryItem>>() {}.type
        return try {
            Gson().fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAiHistory(context: Context, list: List<AiHistoryItem>) {
        val json = Gson().toJson(list.takeLast(5))
        prefs(context).edit().putString(KEY_AI_HISTORY, json).commit()
    }

    fun addAiHistoryItem(context: Context, item: AiHistoryItem) {
        val list = getAiHistory(context)
        list.add(item)
        saveAiHistory(context, list)
    }

    fun clearAiHistory(context: Context) {
        prefs(context).edit().remove(KEY_AI_HISTORY).commit()
    }

    fun updateAiHistoryItem(context: Context, id: String, status: AiStatus, answer: String) {
        val list = getAiHistory(context)
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(statusName = status.name, answer = answer)
            saveAiHistory(context, list)
        }
    }
}
