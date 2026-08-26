package com.example.minetflixlocal.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.Season
import java.io.File
import java.util.Locale
import kotlin.math.max

data class LocalVideo(
    val id: Long,
    val title: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val folderPath: String? = null,

    // Información detectada automáticamente
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

class VideoScanner(private val context: Context) {

    /**
     * Escanea todos los vídeos disponibles en MediaStore.
     */
    fun scanVideos(): List<LocalVideo> {
        val videoList = mutableListOf<LocalVideo>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

            val idColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)

            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            val durationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            val sizeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            val dataColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idColumn)

                val name =
                    cursor.getString(nameColumn)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Video sin título"

                val duration = cursor.getLong(durationColumn)

                val size = cursor.getLong(sizeColumn)

                val data =
                    cursor.getString(dataColumn)
                        ?.trim()
                        ?: ""

                val contentUri = ContentUris.withAppendedId(
                    collection,
                    id
                )

                val folderPath = if (data.isNotEmpty()) {
                    try {
                        File(data).parentFile?.absolutePath
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }

                /*
                 * Intentamos detectar temporada y episodio
                 * directamente desde el nombre del archivo.
                 *
                 * Ejemplos:
                 * S01E01
                 * S01 E02
                 * 1x03
                 * T01E04
                 */
                val detected = detectSeasonAndEpisode(name)

                videoList.add(
                    LocalVideo(
                        id = id,
                        title = name,
                        uri = contentUri,
                        duration = duration,
                        size = size,
                        folderPath = folderPath,
                        seasonNumber = detected?.first,
                        episodeNumber = detected?.second
                    )
                )
            }
        }

        return videoList
    }

    /**
     * Agrupamiento compatible con la estructura anterior de la aplicación.
     *
     * Una carpeta con varios vídeos se considera una serie.
     * Un vídeo individual se considera película.
     *
     * Además, los vídeos de una serie se ordenan:
     *
     * Temporada → Episodio → Nombre
     */
    fun groupVideosByFolder(
        videos: List<LocalVideo>
    ): Pair<List<MediaFolder>, List<LocalVideo>> {

        val folderGroups = videos.groupBy {
            normalizeFolderKey(it.folderPath)
        }

        val folders = mutableListOf<MediaFolder>()
        val singleVideos = mutableListOf<LocalVideo>()

        folderGroups.forEach { (folderPath, folderVideos) ->

            when {

                folderVideos.size > 1 -> {

                    val folderName =
                        if (folderPath != UNCATEGORIZED) {
                            File(folderPath).name
                                .takeIf { it.isNotBlank() }
                                ?: "Serie"
                        } else {
                            "Otros"
                        }

                    val sortedVideos = folderVideos
                        .sortedWith(
                            compareBy<LocalVideo>(
                                { it.seasonNumber ?: Int.MAX_VALUE },
                                { it.episodeNumber ?: Int.MAX_VALUE },
                                { it.title.lowercase(Locale.getDefault()) }
                            )
                        )

                    folders.add(
                        MediaFolder(
                            id = folderPath,
                            name = folderName,
                            videos = sortedVideos,
                            isSeries = true,
                            posterUri = findPoster(folderVideos)
                        )
                    )
                }

                folderVideos.size == 1 -> {
                    singleVideos.add(folderVideos.first())
                }
            }
        }

        return Pair(
            folders.sortedBy {
                it.name.lowercase(Locale.getDefault())
            },
            singleVideos.sortedBy {
                it.title.lowercase(Locale.getDefault())
            }
        )
    }

    /**
     * NUEVO:
     *
     * Convierte directamente los vídeos escaneados en:
     *
     * MediaSeries
     *      └── Season
     *             └── Episode
     *
     * Esta es la estructura que queremos utilizar
     * para la navegación:
     *
     * Serie
     *   ↓
     * Temporada
     *   ↓
     * Episodio
     */
    fun buildMediaSeries(
        videos: List<LocalVideo>
    ): List<MediaSeries> {

        if (videos.isEmpty()) {
            return emptyList()
        }

        val series = mutableListOf<MediaSeries>()

        /*
         * Primero agrupamos por la carpeta de la serie.
         *
         * Para una estructura como:
         *
         * Los Simpson/
         *     Temporada 1/
         *     Temporada 2/
         *
         * usamos la carpeta "Los Simpson" como serie.
         */
        val seriesGroups = groupVideosIntoSeries(videos)

        seriesGroups.forEach { (seriesName, seriesVideos) ->

            if (seriesVideos.isEmpty()) {
                return@forEach
            }

            val seasons = buildSeasons(seriesVideos)

            val posterUri = findPoster(seriesVideos)

            val seriesId =
                seriesVideos
                    .mapNotNull { it.folderPath }
                    .firstOrNull()
                    ?: seriesName

            series.add(
                MediaSeries(
                    id = seriesId,
                    title = seriesName,
                    isMovie = false,
                    posterUri = posterUri,
                    seasons = seasons
                )
            )
        }

        return series.sortedBy {
            it.title.lowercase(Locale.getDefault())
        }
    }

    /**
     * Agrupa vídeos teniendo en cuenta la estructura de carpetas.
     *
     * Caso A:
     *
     * Los Simpson/
     *     S01E01.mp4
     *     S01E02.mp4
     *
     * Caso B:
     *
     * Los Simpson/
     *     Temporada 1/
     *         episodio1.mp4
     *
     *     Temporada 2/
     *         episodio1.mp4
     */
    private fun groupVideosIntoSeries(
        videos: List<LocalVideo>
    ): Map<String, List<LocalVideo>> {

        val result = linkedMapOf<String, MutableList<LocalVideo>>()

        videos.forEach { video ->

            val seriesName = determineSeriesName(video)

            result.getOrPut(seriesName) {
                mutableListOf()
            }.add(video)
        }

        return result
    }

    /**
     * Determina el nombre de la serie.
     *
     * Si el archivo está dentro de:
     *
     * /Los Simpson/Temporada 1/
     *
     * devuelve:
     *
     * Los Simpson
     */
    private fun determineSeriesName(
        video: LocalVideo
    ): String {

        val path = video.folderPath
            ?: return cleanVideoTitle(video.title)

        val folder = File(path)

        val folderName = folder.name.trim()

        if (folderName.isBlank()) {
            return cleanVideoTitle(video.title)
        }

        /*
         * Si la carpeta actual parece una temporada,
         * utilizamos su carpeta padre como nombre de serie.
         */
        if (isSeasonFolder(folderName)) {

            val parentName =
                folder.parentFile
                    ?.name
                    ?.trim()

            if (!parentName.isNullOrBlank()) {
                return parentName
            }
        }

        /*
         * Si el propio nombre del archivo contiene
         * S01E01, normalmente la carpeta actual
         * ya representa la serie.
         */
        return folderName
    }

    /**
     * Construye todas las temporadas de una serie.
     */
    private fun buildSeasons(
        videos: List<LocalVideo>
    ): List<Season> {

        val seasonGroups =
            videos.groupBy { video ->

                video.seasonNumber
                    ?: detectSeasonFromFolder(video.folderPath)
                    ?: 1
            }

        return seasonGroups
            .toSortedMap()
            .map { (seasonNumber, seasonVideos) ->

                val episodes =
                    seasonVideos
                        .sortedWith(
                            compareBy<LocalVideo>(
                                {
                                    it.episodeNumber
                                        ?: detectEpisodeNumber(it.title)
                                        ?: Int.MAX_VALUE
                                },
                                {
                                    it.title.lowercase(
                                        Locale.getDefault()
                                    )
                                }
                            )
                        )
                        .mapIndexed { index, video ->

                            val episodeNumber =
                                video.episodeNumber
                                    ?: detectEpisodeNumber(video.title)
                                    ?: (index + 1)

                            Episode(
                                id = video.id.toString(),
                                title = buildEpisodeTitle(
                                    video.title,
                                    episodeNumber
                                ),
                                videoPath = video.uri.toString(),
                                episodeNumber = episodeNumber
                            )
                        }

                Season(
                    seasonNumber = seasonNumber,
                    title = "Temporada $seasonNumber",
                    episodes = episodes
                )
            }
    }

    /**
     * Detecta:
     *
     * S01E01
     * S01 E01
     * S01-E01
     * S01_E01
     * T01E01
     */
    private fun detectSeasonAndEpisode(
        fileName: String
    ): Pair<Int, Int>? {

        val regex = Regex(
            pattern =
                """(?i)(?:S|T)\s*(\d{1,3})\s*[-_. ]?\s*E\s*(\d{1,4})"""
        )

        val match = regex.find(fileName)

        if (match != null) {

            val season =
                match.groupValues[1].toIntOrNull()

            val episode =
                match.groupValues[2].toIntOrNull()

            if (season != null && episode != null) {
                return Pair(season, episode)
            }
        }

        /*
         * También soportamos:
         *
         * 1x01
         * 2x05
         */
        val xRegex = Regex(
            pattern =
                """(?i)(\d{1,3})\s*x\s*(\d{1,4})"""
        )

        val xMatch = xRegex.find(fileName)

        if (xMatch != null) {

            val season =
                xMatch.groupValues[1].toIntOrNull()

            val episode =
                xMatch.groupValues[2].toIntOrNull()

            if (season != null && episode != null) {
                return Pair(season, episode)
            }
        }

        return null
    }

    /**
     * Detecta el número de temporada
     * desde el nombre de la carpeta.
     *
     * Temporada 1
     * Temporada 2
     * Season 3
     * T04
     * T5
     */
    private fun detectSeasonFromFolder(
        folderPath: String?
    ): Int? {

        if (folderPath.isNullOrBlank()) {
            return null
        }

        val folderName =
            File(folderPath).name

        val regex = Regex(
            pattern =
                """(?i)(?:temporada|season|temp|t)\s*[-_. ]?\s*(\d{1,3})"""
        )

        val match = regex.find(folderName)

        return match
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    /**
     * Detecta únicamente el episodio.
     */
    private fun detectEpisodeNumber(
        title: String
    ): Int? {

        val regex = Regex(
            pattern =
                """(?i)(?:S|T)\s*\d{1,3}\s*[-_. ]?\s*E\s*(\d{1,4})"""
        )

        val match = regex.find(title)

        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }

        val xRegex = Regex(
            pattern =
                """(?i)\d{1,3}\s*x\s*(\d{1,4})"""
        )

        val xMatch = xRegex.find(title)

        if (xMatch != null) {
            return xMatch.groupValues[1].toIntOrNull()
        }

        return null
    }

    /**
     * Determina si una carpeta representa una temporada.
     */
    private fun isSeasonFolder(
        folderName: String
    ): Boolean {

        val regex = Regex(
            pattern =
                """(?i)^(?:temporada|season|temp|t)\s*[-_. ]?\s*\d{1,3}$"""
        )

        return regex.matches(folderName.trim())
    }

    /**
     * Busca una imagen de portada junto a los vídeos.
     *
     * Por ahora solamente busca archivos locales.
     */
    private fun findPoster(
        videos: List<LocalVideo>
    ): Uri? {

        val posterNames = listOf(
            "poster.jpg",
            "poster.jpeg",
            "poster.png",
            "cover.jpg",
            "cover.jpeg",
            "cover.png",
            "folder.jpg",
            "folder.jpeg",
            "folder.png",
            "fanart.jpg",
            "fanart.jpeg",
            "fanart.png"
        )

        val folders =
            videos
                .mapNotNull { it.folderPath }
                .distinct()

        for (folderPath in folders) {

            for (name in posterNames) {

                val file =
                    File(folderPath, name)

                if (file.exists() && file.isFile) {
                    return Uri.fromFile(file)
                }
            }
        }

        /*
         * Si no existe una portada, utilizamos
         * el URI del primer vídeo.
         *
         * Esto permite que la interfaz siga teniendo
         * algo que mostrar mientras posteriormente
         * implementamos thumbnails.
         */
        return videos
            .firstOrNull()
            ?.uri
    }

    /**
     * Limpia ligeramente el nombre del archivo
     * cuando no existe una carpeta válida.
     */
    private fun cleanVideoTitle(
        title: String
    ): String {

        return title
            .substringBeforeLast(
                '.',
                missingDelimiterValue = title
            )
            .replace(
                Regex("""(?i)[._-]?S\d{1,3}E\d{1,4}"""),
                ""
            )
            .replace(
                Regex("""(?i)[._-]?\d{1,3}x\d{1,4}"""),
                ""
            )
            .replace(
                Regex("""[._]+"""),
                " "
            )
            .trim()
            .ifBlank {
                "Video"
            }
    }

    /**
     * Nombre del episodio que verá el usuario.
     *
     * Ejemplo:
     *
     * Episodio 1
     */
    private fun buildEpisodeTitle(
        originalTitle: String,
        episodeNumber: Int
    ): String {

        val cleaned =
            originalTitle
                .substringBeforeLast(
                    '.',
                    missingDelimiterValue = originalTitle
                )
                .replace(
                    Regex(
                        """(?i)[._-]?S\d{1,3}E\d{1,4}"""
                    ),
                    ""
                )
                .replace(
                    Regex(
                        """(?i)[._-]?\d{1,3}x\d{1,4}"""
                    ),
                    ""
                )
                .replace(
                    Regex(
                        """(?i)^(?:episodio|episode)\s*\d+\s*[-_.:]?\s*"""
                    ),
                    ""
                )
                .replace(
                    Regex("""[._]+"""),
                    " "
                )
                .trim()

        return if (cleaned.isBlank()) {
            "Episodio $episodeNumber"
        } else {
            "Episodio $episodeNumber · $cleaned"
        }
    }

    private fun normalizeFolderKey(
        folderPath: String?
    ): String {

        return folderPath
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: UNCATEGORIZED
    }

    companion object {
        private const val UNCATEGORIZED =
            "uncategorized"
    }
}

/**
 * Mantiene el modelo utilizado por la versión
 * actual de la interfaz.
 *
 * Más adelante podemos reemplazarlo gradualmente
 * por MediaSeries sin romper la pantalla existente.
 */
data class MediaFolder(
    val id: String,
    val name: String,
    val videos: List<LocalVideo>,
    val isSeries: Boolean,
    val posterUri: Uri? = null
)
