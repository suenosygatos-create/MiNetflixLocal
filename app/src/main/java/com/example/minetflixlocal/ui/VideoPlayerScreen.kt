package com.example.minetflixlocal.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.Season
import java.io.File

object LocalVideoScanner {

    fun scanLocalVideos(context: Context): Pair<List<MediaSeries>, List<MediaSeries>> {
        val seriesList = mutableListOf<MediaSeries>()
        val moviesList = mutableListOf<MediaSeries>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )

        val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val folderMap = mutableMapOf<String, MutableList<Pair<String, Uri>>>()

        context.contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val path = cursor.getString(dataColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                val file = File(path)
                val folderName = file.parentFile?.name ?: "Otros"

                folderMap.getOrPut(folderName) { mutableListOf() }.add(Pair(name, contentUri))
            }
        }

        folderMap.forEach { (folderName, videoFiles) ->
            if (videoFiles.size == 1) {
                // Caso 1: Película (Un solo video en la carpeta)
                val (videoName, videoUri) = videoFiles.first()
                val cleanTitle = videoName.substringBeforeLast(".")
                moviesList.add(
                    MediaSeries(
                        id = folderName + "_movie",
                        title = cleanTitle,
                        isMovie = true,
                        posterUri = videoUri,
                        seasons = listOf(
                            Season(
                                seasonNumber = 1,
                                title = "Película",
                                episodes = listOf(Episode("1", cleanTitle, videoUri.toString()))
                            )
                        )
                    )
                )
            } else {
                // Caso 2: Serie (Más de un video en la carpeta)
                val episodes = videoFiles.mapIndexed { index, pair ->
                    Episode(
                        id = index.toString(),
                        title = pair.first.substringBeforeLast("."),
                        videoPath = pair.second.toString()
                    )
                }

                seriesList.add(
                    MediaSeries(
                        id = folderName + "_series",
                        title = folderName,
                        isMovie = false,
                        posterUri = videoFiles.first().second,
                        seasons = listOf(Season(seasonNumber = 1, title = "Temporada 1", episodes = episodes))
                    )
                )
            }
        }

        return Pair(seriesList, moviesList)
    }
}
