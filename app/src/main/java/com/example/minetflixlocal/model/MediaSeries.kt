package com.example.minetflixlocal.model

import android.net.Uri

data class MediaSeason(
    val id: String,
    val seasonNumber: Int,
    val episodes: List<MediaEpisode>
)

data class MediaEpisode(
    val id: String,
    val title: String,
    val episodeNumber: Int = 1,
    val videoUri: Uri
)

data class MediaSeries(
    val id: String,
    val title: String,
    val seasons: List<MediaSeason> = emptyList()
) {
    // Helper property para acceder a todos los episodios
    val episodes: List<MediaEpisode>
        get() = seasons.flatMap { it.episodes }
}
