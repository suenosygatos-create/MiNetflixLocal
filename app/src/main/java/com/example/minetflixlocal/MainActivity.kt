package com.example.minetflixlocal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.Season
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.ui.*
import com.example.minetflixlocal.ui.theme.MiNetflixLocalTheme
import com.example.minetflixlocal.util.LocalVideo
import com.example.minetflixlocal.util.MediaFolder
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
    val progressManager = remember { WatchProgressManager(context) }
    val videoScanner = remember { VideoScanner(context) }

    var currentScreen by remember { mutableStateOf(Screen.PROFILE_SELECTION) }
    var activeProfile by remember { mutableStateOf<UserProfile?>(null) }
    var selectedEngine by remember { mutableStateOf("EXOPLAYER") }

    var scannedVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var currentProfileProgress by remember { mutableStateOf<Map<String, WatchProgress>>(emptyMap()) }

    // Estado para reproducción activa
    var playingUri by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("Video") }
    var playingStartPos by remember { mutableStateOf(0L) }
    var playingMediaId by remember { mutableStateOf("") }
    var playingEpisodeId by remember { mutableStateOf("") }
    var isMovie by remember { mutableStateOf(false) 
    var scannedVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }

// 1. Guardar el estado de los IDs ocultos en memoria de Compose
var hiddenIds by remember { mutableStateOf(VideoPreferences.getHiddenVideoIds(context)) }

// 2. Función auxiliar para refrescar el estado cuando ocultes un video
val onHideVideo: (String) -> Unit = { videoId ->
    VideoPreferences.hideVideo(context, videoId)
    hiddenIds = VideoPreferences.getHiddenVideoIds(context)
    }

    // Solicitar permisos de almacenamiento
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scannedVideos = videoScanner.scanVideos()
        }
    }

    LaunchedEffect(Unit) {
        val permissionStatus = ContextCompat.checkSelfPermission(context, permissionToRequest)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            scannedVideos = videoScanner.scanVideos()
        } else {
            launcher.launch(permissionToRequest)
        }
    }

    LaunchedEffect(activeProfile?.id) {
        activeProfile?.let { profile ->
            currentProfileProgress = progressManager.getProgressForProfile(profile.id)
        }
    }

        // 3. Filtrar videos antes de agrupar
val visibleVideos = remember(scannedVideos, hiddenIds) {
    scannedVideos.filter { video -> video.id.toString() !in hiddenIds }
}

// 4. Usar 'visibleVideos' en lugar de 'scannedVideos'
val (seriesFolders, singleMovies) = remember(visibleVideos) {
    videoScanner.groupVideosByFolder(visibleVideos)
}

    // Convertir series (carpetas con múltiples videos) a MediaSeries
    val seriesList = remember(seriesFolders) {
        seriesFolders.map { folder ->
            val seasons = folder.videos
                .groupBy { it.title.substringBefore("S").substringBefore("s") }
                .map { (seasonName, seasonVideos) ->
                    Season(
                        seasonNumber = 1,
                        title = seasonName,
                        episodes = seasonVideos.mapIndexed { index, video ->
                            Episode(
                                id = video.id.toString(),
                                title = video.title,
                                videoPath = video.uri.toString()
                            )
                        }
                    )
                }

            MediaSeries(
                id = folder.id,
                title = folder.name,
                seasons = if (seasons.isEmpty()) {
                    listOf(
                        Season(
                            seasonNumber = 1,
                            title = folder.name,
                            episodes = folder.videos.map { video ->
                                Episode(
                                    id = video.id.toString(),
                                    title = video.title,
                                    videoPath = video.uri.toString()
                                )
                            }
                        )
                    )
                } else {
                    seasons
                }
            )
        }
    }

    // Convertir películas individuales a MediaSeries (cada una es su propia serie de 1 episodio)
    val moviesList = remember(singleMovies) {
        singleMovies.map { movie ->
            val episode = Episode(
                id = movie.id.toString(),
                title = movie.title,
                videoPath = movie.uri.toString()
            )
            val season = Season(
                seasonNumber = 1,
                title = "Película",
                episodes = listOf(episode)
            )
            MediaSeries(
                id = movie.id.toString(),
                title = movie.title,
                seasons = listOf(season),
                isMovie = true
            )
        }
    }

    when (currentScreen) {
        Screen.PROFILE_SELECTION -> {
            ProfileSelectionScreen(
                onProfileSelected = { profile ->
                    activeProfile = profile
                    currentScreen = Screen.HOME
                }
            )
        }

        Screen.HOME -> {
            HomeScreen(
                activeProfile = activeProfile,
                seriesList = seriesList,
                moviesList = moviesList,
                continueWatchingMap = currentProfileProgress,
                onMediaSelected = { media ->
                    val season = media.seasons.firstOrNull()
                    val ep = season?.episodes?.firstOrNull()
                    if (ep != null) {
                        playingUri = ep.videoPath
                        playingTitle = ep.title
                        playingMediaId = media.id
                        playingEpisodeId = ep.id
                        playingStartPos = currentProfileProgress[media.id]?.positionMs ?: 0L
                        isMovie = media.isMovie
                        currentScreen = Screen.PLAYER
                    }
                },
                onResumePlayback = { media, episodeId ->
                    val ep = media.seasons.flatMap { it.episodes }.find { it.id == episodeId }
                        ?: media.seasons.firstOrNull()?.episodes?.firstOrNull()
                    if (ep != null) {
                        playingUri = ep.videoPath
                        playingTitle = ep.title
                        playingMediaId = media.id
                        playingEpisodeId = ep.id
                        playingStartPos = currentProfileProgress[media.id]?.positionMs ?: 0L
                        isMovie = media.isMovie
                        currentScreen = Screen.PLAYER
                    }
                },
                onOpenSettings = {
                    currentScreen = Screen.SETTINGS
                }
            )
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                selectedEngine = selectedEngine,
                onEngineChanged = { engine -> selectedEngine = engine },
                onRescan = {
                    scannedVideos = videoScanner.scanVideos()
                },
                onBack = { currentScreen = Screen.HOME },
                onChangeProfile = {
                    activeProfile = null
                    currentScreen = Screen.PROFILE_SELECTION
                }
            )
        }

        Screen.PLAYER -> {
            if (playingUri != null && activeProfile != null) {
                VideoPlayerScreen(
                    videoUriString = playingUri!!,
                    title = playingTitle,
                    engine = selectedEngine,
                    startPositionMs = playingStartPos,
                    onProgressUpdate = { currentPos, duration ->
                        activeProfile?.let { profile ->
                            progressManager.saveProgress(
                                profileId = profile.id,
                                mediaId = playingMediaId,
                                episodeId = playingEpisodeId,
                                positionMs = currentPos,
                                totalDurationMs = duration
                            )
                        }
                    },
                    onBack = {
                        activeProfile?.let { profile ->
                            currentProfileProgress = progressManager.getProgressForProfile(profile.id)
                        }
                        currentScreen = Screen.HOME
                    }
                )
            } else {
                currentScreen = Screen.HOME
            }
        }
    }
}
