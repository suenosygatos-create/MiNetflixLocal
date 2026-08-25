package com.example.minetflixlocal.util

import android.content.Context

object VideoPreferences {
    private const val PREF_NAME = "video_preferences"
    private const val KEY_HIDDEN_IDS = "hidden_video_ids"

    fun getHiddenVideoIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()
    }

    fun hideVideo(context: Context, videoId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getHiddenVideoIds(context).toMutableSet()
        current.add(videoId)
        prefs.edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }
}
