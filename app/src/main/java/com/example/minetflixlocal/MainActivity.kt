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

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        return when (keyCode) {

            KeyEvent.KEYCODE_BACK -> {
                super.onKeyDown(keyCode, event)
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                true
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
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

    // =========================================================
    // MANAGERS
    // =========================================================

    val progressManager = remember {
        WatchProgressManager(context)
    }

    val videoScanner = remember {
        VideoScanner(context)
    }

    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    var currentScreen by remember {
        mutableStateOf(Screen.PROFILE_SELECTION)
    }

    // =========================================================
    // PERFILES
    // =========================================================

    var profiles by remember {
        mutableStateOf(
            ProfileManager.loadProfiles(context)
        )
    }

    var activeProfile by remember {
        mutableStateOf<UserProfile?>(null)
    }

    // =========================================================
    // MOTOR DE REPRODUCCIÓN
    // =========================================================

    var selectedEngine by remember {
        mutableStateOf("EXOPLAYER")
    }

    // =========================================================
    // VIDEOS
    // =========================================================

    var scannedVideos by remember {
        mutableStateOf<List<LocalVideo>>(emptyList())
    }

    var currentProfileProgress by remember {
        mutableStateOf<Map<String, WatchProgress>>(emptyMap())
    }

    // =========================================================
    // REPRODUCCIÓN ACTUAL
    // =========================================================

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

    // =========================================================
    // IDENTIDAD DEL REPRODUCTOR
    // =========================================================
    //
    // Cada vez que se selecciona otro video/episodio
    // incrementamos este valor.
    //
    // Esto ayuda a que VideoPlayerScreen descarte
    // cualquier instancia anterior del reproductor.
    //
    // =========================================================

    var playerInstance by remember {
        mutableStateOf(0L)
    }

    // =========================================================
    // VIDEOS OCULTOS
    // =========================================================

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

        val profile =
            activeProfile
                ?: return@LaunchedEffect

        currentProfileProgress =
            progressManager.getProgressForProfile(
                profile.id
            )
    }

    // =========================================================
    // VIDEOS VISIBLES
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
    // SERIES
    // =========================================================

    val seriesList =
        remember(visibleVideos) {

            videoScanner.buildMediaSeries(
                visibleVideos
            )
        }

    // =========================================================
    // PELÍCULAS
    // =========================================================

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
    // BUSCAR SERIE ACTUAL
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
    // TODOS LOS EPISODIOS
    // =========================================================
    //
    // Importante:
    // se mantienen en el orden:
    //
    // Temporada 1
    //   Episodio 1
    //   Episodio 2
    //
    // Temporada 2
    //   Episodio 1
    //   Episodio 2
    //
    // =========================================================

    val allEpisodes =
        remember(currentSeries) {

            currentSeries
                ?.seasons
                ?.sortedBy {
                    it.seasonNumber
                }
                ?.flatMap { season ->

                    season.episodes
                        .sortedBy {
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
    // FUNCIÓN PARA OBTENER POSICIÓN GUARDADA
    // =========================================================

    fun getSavedPosition(
        mediaId: String,
        episodeId: String
    ): Long {

        // Primero buscamos por episodio.
        val episodeProgress =
            currentProfileProgress[
                episodeId
            ]

        if (episodeProgress != null) {

            return episodeProgress.positionMs
        }

        // Compatibilidad con progreso antiguo
        // que solamente utilizaba mediaId.

        val mediaProgress =
            currentProfileProgress[
                mediaId
            ]

        return mediaProgress?.positionMs ?: 0L
    }

    // =========================================================
    // ABRIR EPISODIO
    // =========================================================

    fun openEpisode(
        media: MediaSeries,
        episode: Episode,
        resumePosition: Long? = null
    ) {

        // -----------------------------------------------------
        // 1. Primero establecemos todos los estados.
        // -----------------------------------------------------

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
                ?: getSavedPosition(
                    media.id,
                    episode.id
                )

        isMovie =
            media.isMovie

        // -----------------------------------------------------
        // 2. Nueva instancia lógica del reproductor.
        // -----------------------------------------------------

        playerInstance++

        // -----------------------------------------------------
        // 3. Finalmente navegamos al reproductor.
        // -----------------------------------------------------

        currentScreen =
            Screen.PLAYER
    }

    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    when (currentScreen) {

        // =====================================================
        // PERFIL
        // =====================================================

        Screen.PROFILE_SELECTION -> {

            ProfileSelectionScreen(

                onProfileSelected = { profile ->

                    activeProfile =
                        profile

                    // Recargar progreso inmediatamente
                    // al seleccionar perfil.

                    currentProfileProgress =
                        progressManager
                            .getProgressForProfile(
                                profile.id
                            )

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
                // Selección normal
                // -------------------------------------------------

                onMediaSelected = { media ->

                    val season =
                        media.seasons
                            .sortedBy {
                                it.seasonNumber
                            }
                            .firstOrNull()

                    val episode =
                        season
                            ?.episodes
                            ?.sortedBy {
                                it.episodeNumber
                            }
                            ?.firstOrNull()

                    if (episode != null) {

                        openEpisode(
                            media = media,
                            episode = episode
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
                        media.seasons
                            .sortedBy {
                                it.seasonNumber
                            }
                            .flatMap { season ->

                                season.episodes
                                    .sortedBy {
                                        it.episodeNumber
                                    }
                            }
                            .find {
                                it.id == episodeId
                            }
                            ?: media.seasons
                                .sortedBy {
                                    it.seasonNumber
                                }
                                .flatMap { season ->

                                    season.episodes
                                        .sortedBy {
                                            it.episodeNumber
                                        }
                                }
                                .firstOrNull()

                    if (episode != null) {

                        openEpisode(
                            media = media,
                            episode = episode
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
                // Ocultar
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

                    currentProfileProgress =
                        progressManager
                            .getProgressForProfile(
                                profile.id
                            )
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
        // PLAYER
        // =====================================================

        Screen.PLAYER -> {

            val uri =
                playingUri

            val profile =
                activeProfile

            if (
                uri != null &&
                profile != null
            ) {

                /*
                 * Key importante:
                 *
                 * Cuando cambia playerInstance,
                 * Compose considera este reproductor
                 * como una instancia nueva.
                 *
                 * Esto evita reutilizar accidentalmente
                 * el ExoPlayer/VLC anterior.
                 */

                key(
                    playerInstance,
                    uri,
                    playingEpisodeId
                ) {

                    VideoPlayerScreen(

                        videoUriString =
                            uri,

                        title =
                            playingTitle,

                        engine =
                            selectedEngine,

                        startPositionMs =
                            playingStartPos,

                        nextEpisodeTitle =
                            nextEpisode?.title,

                        /*
                         * No usamos videoPath como poster.
                         *
                         * Si el modelo dispone de posterUri,
                         * lo usamos.
                         *
                         * Si no existe, queda null.
                         */

                        nextEpisodePosterUri =
                            currentSeries
                                ?.posterUri
                                ?.let {
                                    Uri.parse(it)
                                },

                        // -------------------------------------------------
                        // SIGUIENTE EPISODIO
                        // -------------------------------------------------

                        onNextEpisode =
                            nextEpisode?.let { nextEp ->

                                {

                                    // -----------------------------------------
                                    // Guardar referencia de la nueva serie
                                    // -----------------------------------------

                                    val media =
                                        currentSeries

                                    if (media != null) {

                                        openEpisode(
                                            media = media,
                                            episode = nextEp,
                                            resumePosition = 0L
                                        )
                                    }
                                }
                            },

                        // -------------------------------------------------
                        // PROGRESO
                        // -------------------------------------------------

                        onProgressUpdate = {
                                currentPos,
                                duration ->

                            val currentProfile =
                                activeProfile

                            if (
                                currentProfile != null &&
                                playingMediaId.isNotBlank() &&
                                playingEpisodeId.isNotBlank()
                            ) {

                                progressManager.saveProgress(

                                    profileId =
                                        currentProfile.id,

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
                        // VOLVER
                        // -------------------------------------------------

                        onBack = {

                            val currentProfile =
                                activeProfile

                            if (
                                currentProfile != null
                            ) {

                                currentProfileProgress =
                                    progressManager
                                        .getProgressForProfile(
                                            currentProfile.id
                                        )
                            }

                            currentScreen =
                                Screen.HOME
                        }
                    )
                }

            } else {

                currentScreen =
                    Screen.HOME
            }
        }
    }
}
