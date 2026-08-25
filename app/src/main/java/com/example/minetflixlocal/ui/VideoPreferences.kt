package com.example.minetflixlocal

import android.content.Context
import android.content.SharedPreferences

object VideoPreferences {
    private const val PREF_NAME = "netflix_user_prefs"
    private const val KEY_HIDDEN_VIDEOS = "hidden_video_ids"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Guarda el ID del video que se desea ocultar
    fun hideVideo(context: Context, videoId: String) {
        val hiddenIds = getHiddenVideoIds(context).toMutableSet()
        hiddenIds.add(videoId)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_VIDEOS, hiddenIds).apply()
    }

    // Obtiene el conjunto de IDs de videos ocultos
    fun getHiddenVideoIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_HIDDEN_VIDEOS, emptySet()) ?: emptySet()
    }

    // Restaura un video oculto (por si quieres revertirlo en el futuro)
    fun unhideVideo(context: Context, videoId: String) {
        val hiddenIds = getHiddenVideoIds(context).toMutableSet()
        hiddenIds.remove(videoId)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_VIDEOS, hiddenIds).apply()
    }
}
