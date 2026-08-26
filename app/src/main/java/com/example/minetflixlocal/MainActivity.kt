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

    // Intercepción de teclas físicas
    // TV Remote / D-Pad / Botones multimedia
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

enum class Screen {
    PROFILE_SELECTION,
    HOME,
    SETTINGS,
    PLAYER
}

@Composable
fun MainApp() {

    val context = LocalContext.current

    val progressManager = remember {
        WatchProgressManager(context)
    }

    val videoScanner = remember {
        VideoScanner(context)
    }

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
    // VIDEOS ESCANEADOS
    // ---------------------------------------------------------

    var scannedVideos by remember {
        mutableStateOf<List<LocalVideo>>(emptyList())
    }

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
    // VIDEOS OCULTOS
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

    // ---------------------------------------------------------
    // PERMISOS
    // ---------------------------------------------------------

    val permissionToRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            Manifest.permission.READ_MEDIA_VIDEO

        } else {

            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {

                scannedVideos =
                    videoScanner.scanVideos()
            }
        }

    // ---------------------------------------------------------
    // ESCANEO INICIAL
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // CARGAR PROGRESO DEL PERFIL
    // ---------------------------------------------------------

    LaunchedEffect(activeProfile?.id) {

        activeProfile?.let { profile ->

            currentProfileProgress =
                progressManager.getProgressForProfile(
                    profile.id
                )
        }
    }

    // ---------------------------------------------------------
    // FILTRAR VIDEOS OCULTOS
    // ---------------------------------------------------------

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
    // NUEVA ESTRUCTURA DE SERIES
    // =========================================================
    //
    // ANTES:
    //
    // folder.videos
    //     ↓
    // groupBy(...)
    //     ↓
    // seasonNumber = 1
    //
    // AHORA:
    //
    // VideoScanner
    //     ↓
    // detecta S01E01
    // detecta S01E02
    // detecta S02E01
    //     ↓
    // MediaSeries
    //     ├── Season 1
    //     │      ├── Episode 1
    //     │      └── Episode 2
    //     │
    //     └── Season 2
    //            └── Episode 1
    //
    // =========================================================

    val seriesList =
        remember(visibleVideos) {

            videoScanner.buildMediaSeries(
                visibleVideos
            )
        }

    // ---------------------------------------------------------
    // PELÍCULAS
    // ---------------------------------------------------------
    //
    // Las películas siguen utilizando la lógica existente:
    // un vídeo individual se presenta como película.
    //
    // Las películas que estén solas dentro de una carpeta
    // también siguen funcionando.
    // ---------------------------------------------------------

    val (_, singleMovies) =
        remember(visibleVideos) {

            videoScanner.groupVideosByFolder(
                visibleVideos
            )
        }

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
                    posterUri = null,
                    seasons = listOf(
                        season
                    ),
                    isMovie = true
                )
            }
        }

    // =========================================================
    // SIGUIENTE EPISODIO
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

    val allEpisodes =
        remember(currentSeries) {

            currentSeries
                ?.seasons
                ?.flatMap {
                    it.episodes
                }
                ?: emptyList()
        }

    val currentEpisodeIndex =
        remember(
            allEpisodes,
            playingEpisodeId
        ) {

            allEpisodes.indexOfFirst {
                it.id == playingEpisodeId
            }
        }

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
    // NAVEGACIÓN PRINCIPAL
    // =========================================================

    when (currentScreen) {

        // -----------------------------------------------------
        // PERFIL
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // HOME
        // -----------------------------------------------------

        Screen.HOME -> {

            HomeScreen(

                activeProfile = activeProfile,

                seriesList = seriesList,

                moviesList = moviesList,

                continueWatchingMap =
                    currentProfileProgress,

                onMediaSelected = { media ->

                    /*
                     * IMPORTANTE:
                     *
                     * Por ahora mantenemos exactamente
                     * el comportamiento de la interfaz actual.
                     *
                     * La selección de temporada/episodio
                     * se incorporará en DetailScreen.kt.
                     *
                     * No modificamos aquí la interfaz.
                     */

                    val season =
                        media.seasons.firstOrNull()

                    val episode =
                        season
                            ?.episodes
                            ?.firstOrNull()

                    if (episode != null) {

                        playingUri =
                            episode.videoPath

                        playingTitle =
                            episode.title

                        playingMediaId =
                            media.id

                        playingEpisodeId =
                            episode.id

                        playingStartPos =
                            currentProfileProgress[
                                media.id
                            ]?.positionMs ?: 0L

                        isMovie =
                            media.isMovie

                        currentScreen =
                            Screen.PLAYER
                    }
                },

                onResumePlayback = {
                        media,
                        episodeId ->

                    val episode =
                        media
                            .seasons
                            .flatMap {
                                it.episodes
                            }
                            .find {
                                it.id == episodeId
                            }
                            ?: media
                                .seasons
                                .firstOrNull()
                                ?.episodes
                                ?.firstOrNull()

                    if (episode != null) {

                        playingUri =
                            episode.videoPath

                        playingTitle =
                            episode.title

                        playingMediaId =
                            media.id

                        playingEpisodeId =
                            episode.id

                        playingStartPos =
                            currentProfileProgress[
                                media.id
                            ]?.positionMs ?: 0L

                        isMovie =
                            media.isMovie

                        currentScreen =
                            Screen.PLAYER
                    }
                },

                onOpenSettings = {

                    currentScreen =
                        Screen.SETTINGS
                },

                onHideMedia = { mediaId ->

                    onHideVideo(
                        mediaId
                    )
                }
            )
        }

        // -----------------------------------------------------
        // SETTINGS
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // REPRODUCTOR
        // -----------------------------------------------------

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

                    nextEpisodePosterUri =
                        nextEpisode
                            ?.videoPath
                            ?.let {
                                Uri.parse(it)
                            },

                    onNextEpisode =
                        nextEpisode?.let {
                            nextEp ->

                            {

                                playingUri =
                                    nextEp.videoPath

                                playingTitle =
                                    nextEp.title

                                playingEpisodeId =
                                    nextEp.id

                                playingStartPos =
                                    0L
                            }
                        },

                    onProgressUpdate = {
                            currentPos,
                            duration ->

                        activeProfile?.let {
                            profile ->

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

                    onBack = {

                        activeProfile?.let {
                            profile ->

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
