package com.example.minetflixlocal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.ui.VideoPlayerScreen
import com.example.minetflixlocal.util.LocalVideo
import com.example.minetflixlocal.util.VideoScanner

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // El escaneo se realiza desde la interfaz
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestVideoPermission()

        setContent {
            NetflixLocalApp()
        }
    }

    private fun requestVideoPermission() {

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        if (
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(permission)
        }
    }
}


/* =========================================================
 * ESTADO PRINCIPAL DE LA APLICACIÓN
 * ========================================================= */

@Composable
private fun NetflixLocalApp() {

    val context = androidx.compose.ui.platform.LocalContext.current

    var currentScreen by remember {
        mutableStateOf("HOME")
    }

    /*
     * Motor seleccionado.
     *
     * EXOPLAYER queda como predeterminado.
     */
    var selectedEngine by remember {
        mutableStateOf("EXOPLAYER")
    }

    /*
     * Videos detectados.
     */
    var localVideos by remember {
        mutableStateOf<List<LocalVideo>>(
            emptyList()
        )
    }

    /*
     * Series construidas por VideoScanner.
     */
    var mediaSeries by remember {
        mutableStateOf<List<MediaSeries>>(
            emptyList()
        )
    }

    /*
     * Películas:
     * vídeos que no pertenecen a una carpeta
     * con múltiples elementos.
     */
    var movies by remember {
        mutableStateOf<List<LocalVideo>>(
            emptyList()
        )
    }

    /*
     * Serie actualmente seleccionada.
     */
    var currentSeries by remember {
        mutableStateOf<MediaSeries?>(null)
    }

    /*
     * Episodio actualmente seleccionado.
     */
    var currentEpisode by remember {
        mutableStateOf<Episode?>(null)
    }

    /*
     * Posición de reproducción guardada.
     */
    var playbackPosition by remember {
        mutableLongStateOf(0L)
    }

    var playbackDuration by remember {
        mutableLongStateOf(0L)
    }

    /*
     * Cargar vídeos.
     */
    fun scanLibrary() {

        try {

            val scanner =
                VideoScanner(context)

            val videos =
                scanner.scanVideos()

            localVideos =
                videos

            /*
             * Construimos las series.
             */
            mediaSeries =
                scanner.buildMediaSeries(
                    videos
                )

            /*
             * Detectamos películas:
             *
             * Una carpeta con varios vídeos
             * se interpreta como serie.
             *
             * Un vídeo individual se interpreta
             * como película.
             */
            val grouped =
                scanner.groupVideosByFolder(
                    videos
                )

            movies =
                grouped.second

        } catch (e: Exception) {

            e.printStackTrace()

            localVideos =
                emptyList()

            mediaSeries =
                emptyList()

            movies =
                emptyList()
        }
    }

    /*
     * Escaneo inicial.
     */
    LaunchedEffect(Unit) {

        scanLibrary()
    }

    /*
     * =====================================================
     * PLAYER
     * =====================================================
     */

    if (
        currentScreen == "PLAYER" &&
        currentEpisode != null
    ) {

        val episode =
            currentEpisode!!

        /*
         * Buscar siguiente episodio.
         */
        val nextEpisode =
            findNextEpisode(
                series = currentSeries,
                currentEpisode = episode
            )

        VideoPlayerScreen(

            videoUriString =
                episode.videoPath,

            title =
                episode.title,

            engine =
                selectedEngine,

            startPositionMs =
                playbackPosition,

            nextEpisodeTitle =
                nextEpisode?.title,

            nextEpisodePosterUri =
                currentSeries?.posterUri,

            onProgressUpdate = {
                    position,
                    duration ->

                playbackPosition =
                    position

                playbackDuration =
                    duration
            },

            onNextEpisode = {

                if (nextEpisode != null) {

                    currentEpisode =
                        nextEpisode

                    playbackPosition =
                        0L

                    playbackDuration =
                        0L
                }
            },

            onBack = {

                currentScreen =
                    "HOME"

                playbackPosition =
                    0L

                playbackDuration =
                    0L
            }
        )

        return
    }

    /*
     * =====================================================
     * RESTO DE LA APLICACIÓN
     * =====================================================
     *
     * Acá se mantiene la navegación de la versión actual.
     *
     * Si tu HomeScreen/ProfileScreen/SettingsScreen ya existen,
     * se conectan desde este bloque.
     */

    when (currentScreen) {

        "PROFILE_SELECTION" -> {

            /*
             * Mantener tu pantalla actual
             * de selección de perfil.
             *
             * Después de seleccionar:
             */
            currentScreen =
                "HOME"
        }

        "HOME" -> {

            HomeContent(

                series =
                    mediaSeries,

                movies =
                    movies,

                onRefresh = {
                    scanLibrary()
                },

                onSeriesSelected = { series ->

                    currentSeries =
                        series

                    /*
                     * Si tu Home actualmente abre
                     * directamente el primer episodio,
                     * usamos el primero.
                     *
                     * Si ya tenés una pantalla de temporadas,
                     * este callback debe navegar hacia ella.
                     */
                    val firstEpisode =
                        series
                            .seasons
                            .firstOrNull()
                            ?.episodes
                            ?.firstOrNull()

                    if (firstEpisode != null) {

                        currentEpisode =
                            firstEpisode

                        playbackPosition =
                            0L

                        currentScreen =
                            "PLAYER"
                    }
                },

                onMovieSelected = { movie ->

                    /*
                     * Las películas se convierten
                     * temporalmente en un Episode
                     * para reutilizar VideoPlayerScreen.
                     */
                    currentSeries =
                        null

                    currentEpisode =
                        Episode(
                            id =
                                movie.id.toString(),

                            title =
                                movie.title,

                            videoPath =
                                movie.uri.toString(),

                            episodeNumber =
                                0
                        )

                    playbackPosition =
                        0L

                    currentScreen =
                        "PLAYER"
                },

                onSettings = {

                    currentScreen =
                        "SETTINGS"
                }
            )
        }

        "SETTINGS" -> {

            SettingsContent(

                selectedEngine =
                    selectedEngine,

                onEngineChanged = {
                    engine ->

                    selectedEngine =
                        engine
                },

                onBack = {

                    currentScreen =
                        "HOME"
                }
            )
        }
    }
}


/* =========================================================
 * SIGUIENTE EPISODIO
 * ========================================================= */

private fun findNextEpisode(
    series: MediaSeries?,
    currentEpisode: Episode
): Episode? {

    if (series == null) {
        return null
    }

    val allEpisodes =
        series.seasons
            .sortedBy {
                it.seasonNumber
            }
            .flatMap { season ->

                season.episodes
                    .sortedBy {
                        it.episodeNumber
                    }
            }

    val currentIndex =
        allEpisodes.indexOfFirst {
            it.id == currentEpisode.id
        }

    if (currentIndex == -1) {
        return null
    }

    return allEpisodes
        .getOrNull(
            currentIndex + 1
        )
}


/* =========================================================
 * HOME
 *
 * Esta función sirve como puente con tu interfaz actual.
 * ========================================================= */

@Composable
private fun HomeContent(
    series: List<MediaSeries>,
    movies: List<LocalVideo>,
    onRefresh: () -> Unit,
    onSeriesSelected: (MediaSeries) -> Unit,
    onMovieSelected: (LocalVideo) -> Unit,
    onSettings: () -> Unit
) {

    /*
     * IMPORTANTE:
     *
     * Acá tenés que colocar el contenido de tu
     * HomeScreen actual.
     *
     * Los datos nuevos ya están preparados:
     *
     * series
     * movies
     *
     * y los callbacks:
     *
     * onSeriesSelected()
     * onMovieSelected()
     * onRefresh()
     * onSettings()
     */

    androidx.compose.material3.Text(
        text =
            "Netflix Local"
    )
}


/* =========================================================
 * SETTINGS
 * ========================================================= */

@Composable
private fun SettingsContent(
    selectedEngine: String,
    onEngineChanged: (String) -> Unit,
    onBack: () -> Unit
) {

    /*
     * Conectá acá tu SettingsScreen actual.
     *
     * El motor se mantiene compatible con
     * VideoPlayerScreen:
     *
     * EXOPLAYER
     * VLC
     */

    androidx.compose.material3.Text(
        text =
            "Motor actual: $selectedEngine"
    )
}
