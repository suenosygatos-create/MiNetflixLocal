package com.example.minetflixlocal.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.Season
import com.example.minetflixlocal.model.VideoItem
import java.io.File

object LocalVideoScanner {

    fun scanLocalVideos(context: Context): Pair<List<MediaSeries>, List<MediaSeries>> {
        val seriesMap = mutableMapOf<String, MutableMap<String, MutableList<VideoItem>>>()
        val seriesFolderMap = mutableMapOf<String, File>()
        val moviesList = mutableListOf<MediaSeries>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
        )

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val path = cursor.getString(dataColumn)
                val durationMs = cursor.getLong(durationColumn)

                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val durationMin = if (durationMs > 0) "${durationMs / (1000 * 60)} min" else "Video"

                val file = File(path)
                val parentFolder = file.parentFile
                val grandParentFolder = parentFolder?.parentFile

                if (parentFolder != null && grandParentFolder != null &&
                    (parentFolder.name.contains("temp", ignoreCase = true) || parentFolder.name.contains("season", ignoreCase = true))
                ) {
                    val seriesName = grandParentFolder.name
                    val seasonName = parentFolder.name

                    seriesFolderMap[seriesName] = grandParentFolder

                    val videoItem = VideoItem(id.toString(), name, uri.toString(), durationMin, uri)
                    seriesMap.getOrPut(seriesName) { mutableMapOf() }
                        .getOrPut(seasonName) { mutableListOf() }
                        .add(videoItem)
                } else if (parentFolder != null) {
                    val seriesName = parentFolder.name
                    seriesFolderMap[seriesName] = parentFolder

                    val videoItem = VideoItem(id.toString(), name, uri.toString(), durationMin, uri)
                    seriesMap.getOrPut(seriesName) { mutableMapOf() }
                        .getOrPut("Temporada 1") { mutableListOf() }
                        .add(videoItem)
                } else {
                    val posterUri = findFolderCover(file.parentFile, uri)
                    moviesList.add(
                        MediaSeries(
                            id = id.toString(),
                            title = name,
                            posterUri = posterUri,
                            isMovie = true,
                            movieVideo = VideoItem(id.toString(), name, uri.toString(), durationMin, uri)
                        )
                    )
                }
            }
        }

        val seriesList = seriesMap.map { (seriesTitle, seasonsMap) ->
            val folder = seriesFolderMap[seriesTitle]
            val firstVideoUri = seasonsMap.values.firstOrNull()?.firstOrNull()?.thumbnailUri
            val posterUri = findFolderCover(folder, firstVideoUri)

            val seasons = seasonsMap.entries.toList().mapIndexed { index, entry ->
                Season(
                    seasonNumber = index + 1,
                    seasonName = entry.key,
                    episodes = entry.value
                )
            }
            MediaSeries(
                id = seriesTitle,
                title = seriesTitle,
                posterUri = posterUri,
                isMovie = false,
                seasons = seasons
            )
        }

        return Pair(seriesList, moviesList)
    }

    private fun findFolderCover(folder: File?, fallbackUri: Uri?): Uri? {
        if (folder == null || !folder.exists()) return fallbackUri
        val coverNames = listOf("poster.jpg", "poster.png", "cover.jpg", "cover.png", "folder.jpg", "folder.png")
        for (name in coverNames) {
            val coverFile = File(folder, name)
            if (coverFile.exists()) {
                return Uri.fromFile(coverFile)
            }
        }
        return fallbackUri
    }
}
