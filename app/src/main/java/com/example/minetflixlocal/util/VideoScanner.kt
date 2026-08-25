package com.example.minetflixlocal.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class LocalVideo(
    val id: Long,
    val title: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val folderPath: String? = null
)

class VideoScanner(private val context: Context) {

    fun scanVideos(): List<LocalVideo> {
        val videoList = mutableListOf<LocalVideo>()

        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Video sin título"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val data = cursor.getString(dataColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Obtener la carpeta del video
                val folderPath = if (data.isNotEmpty()) {
                    try {
                        File(data).parentFile?.absolutePath
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }

                videoList.add(LocalVideo(id, name, contentUri, duration, size, folderPath))
            }
        }

        return videoList
    }

    /**
     * Agrupa los videos por carpeta y determina si es serie o película
     * Serie: 2+ videos en la misma carpeta
     * Película: 1 video en la carpeta
     */
    fun groupVideosByFolder(videos: List<LocalVideo>): Pair<List<MediaFolder>, List<LocalVideo>> {
        // Agrupar por carpeta
        val folderGroups = videos.groupBy { it.folderPath ?: "uncategorized" }
        
        val folders = mutableListOf<MediaFolder>()
        val singleVideos = mutableListOf<LocalVideo>()

        folderGroups.forEach { (folderPath, folderVideos) ->
            when {
                folderVideos.size > 1 -> {
                    // Es una serie
                    val folderName = if (folderPath != "uncategorized") {
                        File(folderPath).name
                    } else {
                        "Otros"
                    }
                    
                    folders.add(
                        MediaFolder(
                            id = folderPath,
                            name = folderName,
                            videos = folderVideos.sortedBy { it.title },
                            isSeries = true,
                            posterUri = null
                        )
                    )
                }
                folderVideos.size == 1 -> {
                    // Es una película individual
                    singleVideos.add(folderVideos.first())
                }
            }
        }

        return Pair(folders.sortedBy { it.name }, singleVideos.sortedBy { it.title })
    }
}

data class MediaFolder(
    val id: String,
    val name: String,
    val videos: List<LocalVideo>,
    val isSeries: Boolean,
    val posterUri: Uri? = null
)
