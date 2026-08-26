package com.example.minetflixlocal.model

import android.net.Uri

data class Episode(
    val id: String,
    val title: String,
    val videoPath: String,
    val episodeNumber: Int = 0
)

data class Season(
    val seasonNumber: Int,
    val title: String,
    val episodes: List<Episode>
)

data class MediaSeries(
    val id: String,
    val title: String,
    val isMovie: Boolean = false,
    var posterUri: Uri? = null,
    val seasons: List<Season> = emptyList()
)
