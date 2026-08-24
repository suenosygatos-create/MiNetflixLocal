package com.example.minetflixlocal.model

import android.net.Uri

data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: Long
)

data class VideoItem(
    val id: String,
    val title: String,
    val videoPath: String,
    val duration: String = "22 min",
    val thumbnailUri: Uri? = null
)

data class Season(
    val seasonNumber: Int,
    val seasonName: String,
    val episodes: List<VideoItem>
)

data class MediaSeries(
    val id: String,
    val title: String,
    val posterUri: Uri? = null,
    val isMovie: Boolean = false,
    val seasons: List<Season> = emptyList(),
    val movieVideo: VideoItem? = null
)
