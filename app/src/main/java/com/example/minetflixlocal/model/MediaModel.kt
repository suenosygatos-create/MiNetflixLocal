package com.example.minetflixlocal.model

data class VideoItem(
    val id: String,
    val title: String,
    val videoPath: String,
    val duration: String = "22 min"
)

data class Season(
    val seasonNumber: Int,
    val seasonName: String,
    val episodes: List<VideoItem>
)

data class MediaSeries(
    val id: String,
    val title: String,
    val isMovie: Boolean = false,
    val seasons: List<Season> = emptyList(),
    val movieVideo: VideoItem? = null
)
