package com.example.minetflixlocal.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.Season
import java.io.File
import java.util.regex.Pattern

object LocalVideoScanner {

    private val SEASON_PATTERN = Pattern.compile("(?i)(season|temporada|temp|s)\\s*(\\d+)")

    fun scanLocalVideos(context: Context): Pair<List<MediaSeries>, List<MediaSeries>> {
        val seriesList = mutableListOf<MediaSeries>()
        val moviesList = mutableListOf<MediaSeries>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )

        val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        
        // Estructura: SeriesName -> Map<SeasonName, List<Pair<VideoName, VideoUri>>>
        val masterCatalog = mutableMapOf<String, MutableMap<String, MutableList<Pair<String, Uri>>>>()

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
                val parentFolder = file.parentFile
                val parentName = parentFolder?.name ?: "Otros"

                val matcher = SEASON_PATTERN.matcher(parentName)
                
                val seriesName: String
                val seasonName: String

                if (matcher.find() && parentFolder?.parentFile != null) {
                    // La carpeta actual es "Temporada X". La carpeta padre es el título de la serie.
                    seriesName = parentFolder.parentFile!!.name
                    seasonName = parentName
                } else {
                    seriesName = parentName
                    seasonName = "Temporada 1"
                }

                masterCatalog
                    .getOrPut(seriesName) { mutableMapOf() }
                    .getOrPut(seasonName) { mutableListOf() }
                    .add(Pair(name, contentUri))
            }
        }

        masterCatalog.forEach { (seriesTitle, seasonsMap) ->
            val totalVideos = seasonsMap.values.sumOf { it.size }

            if (totalVideos == 1 && seasonsMap.size == 1) {
                // Película
                val (videoName, videoUri) = seasonsMap.values.first().first()
                val cleanTitle = videoName.substringBeforeLast(".")
                moviesList.add(
                    MediaSeries(
                        id = seriesTitle + "_movie",
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
                // Serie con una o múltiples temporadas
                val seasonsList = seasonsMap.entries.mapIndexed { sIndex, (seasonTitle, videoFiles) ->
                    val episodes = videoFiles.mapIndexed { eIndex, (vName, vUri) ->
                        Episode(
                            id = "${sIndex}_$eIndex",
                            title = vName.substringBeforeLast("."),
                            videoPath = vUri.toString()
                        )
                    }
                    Season(seasonNumber = sIndex + 1, title = seasonTitle, episodes = episodes)
                }

                val firstUri = seasonsMap.values.firstOrNull()?.firstOrNull()?.second

                seriesList.add(
                    MediaSeries(
                        id = seriesTitle + "_series",
                        title = seriesTitle,
                        isMovie = false,
                        posterUri = firstUri,
                        seasons = seasonsList
                    )
                )
            }
        }

        return Pair(seriesList, moviesList)
    }
}
