package com.example.minetflixlocal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.example.minetflixlocal.model.MediaEpisode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.ui.*
import com.example.minetflixlocal.ui.theme.MiNetflixLocalTheme
import com.example.minetflixlocal.util.LocalVideo
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
            scannedVideos = VideoScanner(context).scanVideos()
        }
    }

    LaunchedEffect(Unit) {
        val permissionStatus = ContextCompat.checkSelfPermission(context, permissionToRequest)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            scannedVideos = VideoScanner(context).scanVideos()
        } else {
            launcher.launch(permissionToRequest)
        }
    }

    LaunchedEffect(activeProfile?.id) {
        activeProfile?.let { profile ->
            currentProfileProgress = progressManager.getProgressForProfile(profile.id)
        }
    }

    // Adaptar los videos del celular a la estructura de la app
    val mediaList = remember(scannedVideos) {
        scannedVideos.map { video ->
            MediaSeries(
                id = video.id.toString(),
                title = video.title,
                episodes = listOf(
                    MediaEpisode(
                        id = video.id.toString(),
                        title = video.title,
                        videoUri = video.uri
                    )
                )
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
                seriesList = mediaList,
                moviesList = emptyList(),
                continueWatchingMap = currentProfileProgress,
                onMediaSelected = { media ->
                    val ep = media.episodes.firstOrNull()
                    if (ep != null) {
                        playingUri = ep.videoUri.toString()
                        playingTitle = ep.title
                        playingMediaId = media.id
                        playingEpisodeId = ep.id
                        playingStartPos = currentProfileProgress[media.id]?.positionMs ?: 0L
                        currentScreen = Screen.PLAYER
                    }
                },
                onResumePlayback = { media, episodeId ->
                    val ep = media.episodes.find { it.id == episodeId } ?: media.episodes.firstOrNull()
                    if (ep != null) {
                        playingUri = ep.videoUri.toString()
                        playingTitle = ep.title
                        playingMediaId = media.id
                        playingEpisodeId = ep.id
                        playingStartPos = currentProfileProgress[media.id]?.positionMs ?: 0L
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
                    scannedVideos = VideoScanner(context).scanVideos()
                },
                onBack = { currentScreen = Screen.HOME },
                onChangeProfile = {
                    activeProfile = null
                    currentScreen = Screen.PROFILE_SELECTION
                }
            )
        }

        Screen.PLAYER -> {
            playingUri?.let { uriStr ->
                VideoPlayerScreen(
                    videoUriString = uriStr,
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
            } ?: run {
                currentScreen = Screen.HOME
            }
        }
    }
}
