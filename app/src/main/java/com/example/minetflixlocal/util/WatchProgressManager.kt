package com.example.minetflixlocal.util

import android.content.Context
import android.content.SharedPreferences

data class WatchProgress(
    val mediaId: String,
    val episodeId: String,
    val positionMs: Long,
    val totalDurationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)

class WatchProgressManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netflix_watch_progress", Context.MODE_PRIVATE)

    // Clave única combinando perfil + contenido
    private fun makeKey(profileId: String, mediaId: String): String {
        return "${profileId}_${mediaId}"
    }

    // Guardar avance de un perfil
    fun saveProgress(
        profileId: String,
        mediaId: String,
        episodeId: String,
        positionMs: Long,
        totalDurationMs: Long
    ) {
        val key = makeKey(profileId, mediaId)
        val value = "$episodeId|$positionMs|$totalDurationMs|${System.currentTimeMillis()}"
        prefs.edit().putString(key, value).apply()
    }

    // Obtener todo el historial de reproducción de un perfil específico
    fun getProgressForProfile(profileId: String): Map<String, WatchProgress> {
        val resultMap = mutableMapOf<String, WatchProgress>()
        val prefix = "${profileId}_"

        prefs.all.forEach { (key, value) ->
            if (key.startsWith(prefix) && value is String) {
                val parts = value.split("|")
                if (parts.size >= 4) {
                    val mediaId = key.removePrefix(prefix)
                    resultMap[mediaId] = WatchProgress(
                        mediaId = mediaId,
                        episodeId = parts[0],
                        positionMs = parts[1].toLongOrNull() ?: 0L,
                        totalDurationMs = parts[2].toLongOrNull() ?: 0L,
                        lastUpdated = parts[3].toLongOrNull() ?: 0L
                    )
                }
            }
        }
        return resultMap
    }
}
