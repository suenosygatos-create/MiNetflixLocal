package com.example.minetflixlocal.util

import android.content.Context
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import org.json.JSONArray
import org.json.JSONObject

data class WatchProgress(
    val mediaId: String,
    val episodeId: String,
    val positionMs: Long,
    val totalDurationMs: Long,
    val lastUpdated: Long
)

object PlaybackManager {
    private const val PREF_NAME = "netflix_playback_pref"
    private const val KEY_PROGRESS = "watch_progress"
    private const val KEY_HIDDEN = "hidden_media_ids"

    fun saveProgress(context: Context, mediaId: String, episodeId: String, positionMs: Long, totalDurationMs: Long) {
        if (totalDurationMs <= 0) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val progressMap = getAllProgress(context).toMutableMap()

        // Si faltan menos de 10 segundos para terminar, se considera completado y se elimina de "Seguir viendo"
        if (totalDurationMs - positionMs < 10000) {
            progressMap.remove(mediaId)
        } else {
            progressMap[mediaId] = WatchProgress(
                mediaId = mediaId,
                episodeId = episodeId,
                positionMs = positionMs,
                totalDurationMs = totalDurationMs,
                lastUpdated = System.currentTimeMillis()
            )
        }

        val array = JSONArray()
        progressMap.values.forEach { item ->
            array.put(JSONObject().apply {
                put("mediaId", item.mediaId)
                put("episodeId", item.episodeId)
                put("positionMs", item.positionMs)
                put("totalDurationMs", item.totalDurationMs)
                put("lastUpdated", item.lastUpdated)
            })
        }
        prefs.edit().putString(KEY_PROGRESS, array.toString()).apply()
    }

    fun getProgress(context: Context, episodeId: String): Long {
        return getAllProgress(context).values.find { it.episodeId == episodeId }?.positionMs ?: 0L
    }

    fun getAllProgress(context: Context): Map<String, WatchProgress> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROGRESS, null) ?: return emptyMap()
        return try {
            val array = JSONArray(jsonStr)
            val map = mutableMapOf<String, WatchProgress>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val mediaId = obj.getString("mediaId")
                map[mediaId] = WatchProgress(
                    mediaId = mediaId,
                    episodeId = obj.getString("episodeId"),
                    positionMs = obj.getLong("positionMs"),
                    totalDurationMs = obj.getLong("totalDurationMs"),
                    lastUpdated = obj.getLong("lastUpdated")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun hideMedia(context: Context, mediaId: String) {
        val hidden = getHiddenMedia(context).toMutableSet()
        hidden.add(mediaId)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_HIDDEN, hidden)
            .apply()
    }

    fun getHiddenMedia(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
    }
}
