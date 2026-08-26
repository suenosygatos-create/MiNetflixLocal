package com.example.minetflixlocal

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.Season
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.ui.HomeScreen
import com.example.minetflixlocal.ui.ProfileSelectionScreen
import com.example.minetflixlocal.ui.SettingsScreen
import com.example.minetflixlocal.ui.VideoPlayerScreen
import com.example.minetflixlocal.ui.theme.MiNetflixLocalTheme
import com.example.minetflixlocal.util.LocalVideo
import com.example.minetflixlocal.util.ProfileManager
import com.example.minetflixlocal.util.VideoPreferences
import com.example.minetflixlocal.util.VideoScanner
import com.example.minetflixlocal.util.WatchProgress
import com.example.minetflixlocal.util.WatchProgressManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MiNetflixLocalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // TECLAS FÍSICAS
    // ---------------------------------------------------------

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        return when (keyCode) {

            KeyEvent.KEYCODE_BACK ->
                super.onKeyDown(keyCode, event)

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK ->
                true

            else ->
                super.onKeyDown(keyCode, event)
        }
    }
}

// =============================================================
// PANTALLAS PRINCIPALES
// =============================================================

enum class Screen {
    PROFILE_SELECTION,
    HOME,
    SETTINGS,
    PLAYER
}

// =============================================================
// APLICACIÓN PRINCIPAL
// =============================================================

@Composable
fun MainApp() {

    val context = LocalContext.current

    // ---------------------------------------------------------
    // MANAGERS
    // ---------------------------------------------------------

    val progressManager = remember {
        WatchProgressManager(context)
    }

    val videoScanner = remember {
        VideoScanner(context)
    }

    // ---------------------------------------------------------
    // NAVEGACIÓN
    // ---------------------------------------------------------

    var currentScreen by remember {
        mutableStateOf(Screen.PROFILE_SELECTION)
    }

    // ---------------------------------------------------------
    // PERFILES
    // ---------------------------------------------------------

    var profiles by remember {
        mutableStateOf(
            ProfileManager.loadProfiles(context)
        )
    }

    var activeProfile by remember {
        mutableStateOf<UserProfile?>(null)
    }

    // ---------------------------------------------------------
    // MOTOR DE REPRODUCCIÓN
    // ---------------------------------------------------------

    var selectedEngine by remember {
        mutableStateOf("EXOPLAYER")
    }

    // ---------------------------------------------------------
    // VÍDEOS ESCANEADOS
    // ---------------------------------------------------------

    var scannedVideos by remember {
        mutableStateOf<List<LocalVideo>>(emptyList())
    }

    // ---------------------------------------------------------
    // PROGRESO DEL PERFIL
    // ---------------------------------------------------------

    var currentProfileProgress by remember {
        mutableStateOf<Map<String, WatchProgress>>(emptyMap())
    }

    // ---------------------------------------------------------
    // REPRODUCCIÓN ACTUAL
    // ---------------------------------------------------------

    var playingUri by remember {
        mutableStateOf<String?>(null)
    }

    var playingTitle by remember {
        mutableStateOf("Video")
    }

    var playingStartPos by remember {
        mutableStateOf(0L)
    }

    var playingMediaId by remember {
        mutableStateOf("")
    }

    var playingEpisodeId by remember {
        mutableStateOf("")
    }

    var isMovie by remember {
        mutableStateOf(false)
    }

    // ---------------------------------------------------------
    // VÍDEOS OCULTOS
    // ---------------------------------------------------------

    var hiddenIds by remember {
        mutableStateOf(
            VideoPreferences.getHiddenVideoIds(context)
        )
    }

    val onHideVideo: (String) -> Unit = { videoId ->

        VideoPreferences.hideVideo(
            context,
            videoId
        )

        hiddenIds =
            VideoPreferences.getHiddenVideoIds(context)
    }

    // =========================================================
    // PERMISOS
    // =========================================================

    val permissionToRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            Manifest.permission.READ_MEDIA_VIDEO

        } else {

            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val launcher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {

                scannedVideos =
                    videoScanner.scanVideos()
            }
        }

    // =========================================================
    // ESCANEO INICIAL
    // =========================================================

    LaunchedEffect(Unit) {

        val permissionStatus =
            ContextCompat.checkSelfPermission(
                context,
                permissionToRequest
            )

        if (
            permissionStatus ==
            PackageManager.PERMISSION_GRANTED
        ) {

            scannedVideos =
                videoScanner.scanVideos()

        } else {

            launcher.launch(
                permissionToRequest
            )
        }
    }

    // =========================================================
    // CARGAR PROGRESO DEL PERFIL
    // =========================================================

    LaunchedEffect(activeProfile?.id) {

        activeProfile?.let { profile ->

            currentProfileProgress =
                progressManager.getProgressForProfile(
                    profile.id
                )
        }
    }

    // =========================================================
    // FILTRAR VÍDEOS OCULTOS
    // =========================================================

    val visibleVideos =
        remember(
            scannedVideos,
            hiddenIds
        ) {

            scannedVideos.filter { video ->

                video.id.toString() !in hiddenIds
            }
        }

    // =========================================================
    // AGRUPAMIENTO ORIGINAL
    // =========================================================
    //
    // Esto nos permite separar:
    //
    // 1. Carpetas con múltiples vídeos -> SERIES
    // 2. Vídeos individuales -> PELÍCULAS
    //
    // Después utilizamos buildMediaSeries() únicamente
    // con los vídeos que realmente pertenecen a series.
    // =========================================================

    val groupedMedia =
        remember(visibleVideos) {

            videoScanner.groupVideosByFolder(
                visibleVideos
            )
        }

    val seriesFolders =
        groupedMedia.first

    val singleMovies =
        groupedMedia.second

    // =========================================================
    // SERIES
    // =========================================================
    //
    // IMPORTANTE:
    //
    // No pasamos todos los vídeos a buildMediaSeries(),
    // porque eso convertiría también las películas individuales
    // en series.
    // =========================================================

    val seriesVideos =
        remember(seriesFolders) {

            seriesFolders
                .flatMap { folder ->
                    folder.videos
                }
        }

    val seriesList =
        remember(seriesVideos) {

            videoScanner.buildMediaSeries(
                seriesVideos
            )
        }

    // =========================================================
    // PELÍCULAS
    // =========================================================

    val moviesList =
        remember(singleMovies) {

            singleMovies.map { movie ->

                val episode =
                    Episode(
                        id = movie.id.toString(),
                        title = movie.title,
                        videoPath = movie.uri.toString(),
                        episodeNumber = 1
                    )

                val season =
                    Season(
                        seasonNumber = 1,
                        title = "Película",
                        episodes = listOf(
                            episode
                        )
                    )

                MediaSeries(
                    id = movie.id.toString(),
                    title = movie.title,
                    isMovie = true,
                    posterUri = null,
                    seasons = listOf(
                        season
                    )
                )
            }
        }

    // =========================================================
    // TODOS LOS MEDIOS
    // =========================================================

    val allMedia =
        remember(
            seriesList,
            moviesList
        ) {

            seriesList + moviesList
        }

    // =========================================================
    // SERIE ACTUAL
    // =========================================================
    //
    // Buscamos en todas las series, no en las películas.
    // =========================================================

    val currentSeries =
        remember(
            seriesList,
            playingMediaId
        ) {

            seriesList.find {
                it.id == playingMediaId
            }
        }

    // =========================================================
    // EPISODIOS DE LA SERIE ACTUAL
    // =========================================================

    val allEpisodes =
        remember(currentSeries) {

            currentSeries
                ?.seasons
                ?.sortedBy {
                    it.seasonNumber
                }
                ?.flatMap { season ->

                    season.episodes.sortedBy {
                        it.episodeNumber
                    }

                }
                ?: emptyList()
        }

    // =========================================================
    // ÍNDICE DEL EPISODIO ACTUAL
    // =========================================================

    val currentEpisodeIndex =
        remember(
            allEpisodes,
            playingEpisodeId
        ) {

            allEpisodes.indexOfFirst { episode ->

                episode.id ==
                    playingEpisodeId
            }
        }

    // =========================================================
    // SIGUIENTE EPISODIO
    // =========================================================

    val nextEpisode =
        remember(
            allEpisodes,
            currentEpisodeIndex,
            isMovie
        ) {

            if (
                !isMovie &&
                currentEpisodeIndex >= 0 &&
                currentEpisodeIndex + 1 <
                allEpisodes.size
            ) {

                allEpisodes[
                    currentEpisodeIndex + 1
                ]

            } else {

                null
            }
        }

    // =========================================================
    // FUNCIÓN PARA INICIAR UN EPISODIO
    // =========================================================

    fun playEpisode(
        media: MediaSeries,
        episode: Episode,
        resumePosition: Long = 0L
    ) {

        playingUri =
            episode.videoPath

        playingTitle =
            episode.title

        playingMediaId =
            media.id

        playingEpisodeId =
            episode.id

        playingStartPos =
            resumePosition

        isMovie =
            media.isMovie

        currentScreen =
            Screen.PLAYER
    }

    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    when (currentScreen) {

        // =====================================================
        // SELECCIÓN DE PERFIL
        // =====================================================

        Screen.PROFILE_SELECTION -> {

            ProfileSelectionScreen(

                onProfileSelected = { profile ->

                    activeProfile =
                        profile

                    currentScreen =
                        Screen.HOME
                }
            )
        }

        // =====================================================
        // HOME
        // =====================================================

        Screen.HOME -> {

            HomeScreen(

                activeProfile =
                    activeProfile,

                seriesList =
                    seriesList,

                moviesList =
                    moviesList,

                continueWatchingMap =
                    currentProfileProgress,

                // -------------------------------------------------
                // Seleccionar una película / serie
                // -------------------------------------------------

                onMediaSelected = { media ->

                    val firstSeason =
                        media.seasons
                            .minByOrNull {
                                it.seasonNumber
                            }

                    val firstEpisode =
                        firstSeason
                            ?.episodes
                            ?.minByOrNull {
                                it.episodeNumber
                            }

                    if (firstEpisode != null) {

                        val savedProgress =
                            currentProfileProgress[
                                media.id
                            ]

                        playEpisode(
                            media = media,
                            episode = firstEpisode,
                            resumePosition =
                                savedProgress
                                    ?.positionMs
                                    ?: 0L
                        )
                    }
                },

                // -------------------------------------------------
                // Continuar reproducción
                // -------------------------------------------------

                onResumePlayback = {
                        media,
                        episodeId ->

                    val episode =
                        media
                            .seasons
                            .flatMap { season ->
                                season.episodes
                            }
                            .find { episode ->

                                episode.id ==
                                    episodeId
                            }
                            ?: media
                                .seasons
                                .flatMap { season ->
                                    season.episodes
                                }
                                .minByOrNull { episode ->

                                    episode.episodeNumber
                                }

                    if (episode != null) {

                        val savedProgress =
                            currentProfileProgress[
                                media.id
                            ]

                        playEpisode(
                            media = media,
                            episode = episode,
                            resumePosition =
                                savedProgress
                                    ?.positionMs
                                    ?: 0L
                        )
                    }
                },

                // -------------------------------------------------
                // Configuración
                // -------------------------------------------------

                onOpenSettings = {

                    currentScreen =
                        Screen.SETTINGS
                },

                // -------------------------------------------------
                // Ocultar medio
                // -------------------------------------------------

                onHideMedia = { mediaId ->

                    onHideVideo(
                        mediaId
                    )
                }
            )
        }

        // =====================================================
        // SETTINGS
        // =====================================================

        Screen.SETTINGS -> {

            SettingsScreen(

                selectedEngine =
                    selectedEngine,

                onEngineChanged = { engine ->

                    selectedEngine =
                        engine
                },

                profiles =
                    profiles,

                activeProfile =
                    activeProfile,

                onProfileSelected = { profile ->

                    activeProfile =
                        profile
                },

                onUpdateProfileAvatar = {
                        profileId,
                        uri ->

                    profiles =
                        profiles.map { profile ->

                            if (
                                profile.id ==
                                profileId
                            ) {

                                profile.copy(
                                    avatarUri =
                                        uri?.toString()
                                )

                            } else {

                                profile
                            }
                        }

                    ProfileManager.saveProfiles(
                        context,
                        profiles
                    )

                    if (
                        activeProfile?.id ==
                        profileId
                    ) {

                        activeProfile =
                            activeProfile?.copy(
                                avatarUri =
                                    uri?.toString()
                            )
                    }
                },

                onRescan = {

                    scannedVideos =
                        videoScanner.scanVideos()
                },

                onBack = {

                    currentScreen =
                        Screen.HOME
                }
            )
        }

        // =====================================================
        // REPRODUCTOR
        // =====================================================

        Screen.PLAYER -> {

            if (
                playingUri != null &&
                activeProfile != null
            ) {

                VideoPlayerScreen(

                    videoUriString =
                        playingUri!!,

                    title =
                        playingTitle,

                    engine =
                        selectedEngine,

                    startPositionMs =
                        playingStartPos,

                    nextEpisodeTitle =
                        nextEpisode?.title,

                    /*
                     * La portada del siguiente episodio
                     * debe ser la portada de la serie.
                     *
                     * No usamos el vídeo como poster.
                     */
                    nextEpisodePosterUri =
                        currentSeries?.posterUri,

                    // -------------------------------------------------
                    // SIGUIENTE EPISODIO
                    // -------------------------------------------------

                    onNextEpisode =
                        nextEpisode?.let { nextEp ->

                            {

                                playingUri =
                                    nextEp.videoPath

                                playingTitle =
                                    nextEp.title

                                playingEpisodeId =
                                    nextEp.id

                                playingStartPos =
                                    0L

                                isMovie =
                                    false
                            }
                        },

                    // -------------------------------------------------
                    // GUARDAR PROGRESO
                    // -------------------------------------------------

                    onProgressUpdate = {
                            currentPos,
                            duration ->

                        activeProfile?.let { profile ->

                            progressManager.saveProgress(

                                profileId =
                                    profile.id,

                                mediaId =
                                    playingMediaId,

                                episodeId =
                                    playingEpisodeId,

                                positionMs =
                                    currentPos,

                                totalDurationMs =
                                    duration
                            )
                        }
                    },

                    // -------------------------------------------------
                    // VOLVER AL HOME
                    // -------------------------------------------------

                    onBack = {

                        activeProfile?.let { profile ->

                            currentProfileProgress =
                                progressManager
                                    .getProgressForProfile(
                                        profile.id
                                    )
                        }

                        currentScreen =
                            Screen.HOME
                    }
                )

            } else {

                currentScreen =
                    Screen.HOME
            }
        }
    }
}
