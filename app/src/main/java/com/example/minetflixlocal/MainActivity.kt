package com.example.minetflixlocal

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.ui.*
import com.example.minetflixlocal.util.LocalVideoScanner
import com.example.minetflixlocal.util.PlaybackManager
import com.example.minetflixlocal.util.ProfileManager
import com.example.minetflixlocal.util.WatchProgress

enum class ScreenState {
    PROFILES, HOME, DETAIL, PLAYER, SETTINGS
}

class MainActivity : ComponentActivity() {

    private var seriesList by mutableStateOf<List<MediaSeries>>(emptyList())
    private var moviesList by mutableStateOf<List<MediaSeries>>(emptyList())
    private var activeProfile by mutableStateOf<UserProfile?>(null)
    private var playerEngine by mutableStateOf("EXOPLAYER")
    private var profilesState by mutableStateOf<List<UserProfile>>(emptyList())
    private var continueWatchingMap by mutableStateOf<Map<String, WatchProgress>>(emptyMap())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadLocalVideos()
        } else {
            Toast.makeText(this, "Se requiere permiso para leer tus videos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profilesState = ProfileManager.loadProfiles(this)
        continueWatchingMap = PlaybackManager.getAllProgress(this)
        checkAndRequestPermissions()

        setContent {
            var currentScreen by remember { mutableStateOf(ScreenState.PROFILES) }
            var selectedMedia by remember { mutableStateOf<MediaSeries?>(null) }
            var currentEpisodeList by remember { mutableStateOf<List<Episode>>(emptyList()) }
            var currentEpisodeIndex by remember { mutableIntStateOf(0) }

            BackHandler(enabled = currentScreen != ScreenState.PROFILES) {
                when (currentScreen) {
                    ScreenState.PLAYER -> currentScreen = ScreenState.DETAIL
                    ScreenState.DETAIL -> currentScreen = ScreenState.HOME
                    ScreenState.SETTINGS -> currentScreen = ScreenState.HOME
                    ScreenState.HOME -> currentScreen = ScreenState.PROFILES
                    else -> {}
                }
            }

            when (currentScreen) {
                ScreenState.PROFILES -> {
                    ProfileScreen(
                        profiles = profilesState,
                        onProfileSelected = { profile ->
                            activeProfile = profile
                            currentScreen = ScreenState.HOME
                        },
                        onProfileUpdated = { updatedProfile ->
                            val updatedList = profilesState.map {
                                if (it.id == updatedProfile.id) updatedProfile else it
                            }
                            profilesState = updatedList
                            ProfileManager.saveProfiles(this@MainActivity, updatedList)
                        }
                    )
                }

                ScreenState.HOME -> {
                    HomeScreen(
                        activeProfile = activeProfile,
                        seriesList = seriesList,
                        moviesList = moviesList,
                        continueWatchingMap = continueWatchingMap,
                        onMediaSelected = { media ->
                            selectedMedia = media
                            currentScreen = ScreenState.DETAIL
                        },
                        onResumePlayback = { media, episodeId ->
                            selectedMedia = media
                            val allEpisodes = media.seasons.flatMap { it.episodes }
                            val idx = allEpisodes.indexOfFirst { it.id == episodeId }
                            currentEpisodeList = allEpisodes
                            currentEpisodeIndex = if (idx >= 0) idx else 0
                            currentScreen = ScreenState.PLAYER
                        },
                        onOpenSettings = {
                            currentScreen = ScreenState.SETTINGS
                        }
                    )
                }

                ScreenState.DETAIL -> {
                    selectedMedia?.let { media ->
                        DetailScreen(
                            media = media,
                            onBack = { currentScreen = ScreenState.HOME },
                            onEpisodeClick = { episode ->
                                val allEpisodes = media.seasons.flatMap { it.episodes }
                                val index = allEpisodes.indexOf(episode)
                                currentEpisodeList = allEpisodes
                                currentEpisodeIndex = if (index >= 0) index else 0
                                currentScreen = ScreenState.PLAYER
                            },
                            onHideMedia = {
                                PlaybackManager.hideMedia(this@MainActivity, media.id)
                                loadLocalVideos()
                                currentScreen = ScreenState.HOME
                            }
                        )
                    }
                }

                ScreenState.PLAYER -> {
                    val currentEpisode = currentEpisodeList.getOrNull(currentEpisodeIndex)
                    val nextEpisode = currentEpisodeList.getOrNull(currentEpisodeIndex + 1)
                    val mediaId = selectedMedia?.id ?: ""

                    if (currentEpisode != null) {
                        key(currentEpisode.videoPath) {
                            val initialPos = PlaybackManager.getProgress(this, currentEpisode.id)
                            VideoPlayerScreen(
                                videoUriString = currentEpisode.videoPath,
                                title = currentEpisode.title,
                                engine = playerEngine,
                                startPositionMs = initialPos,
                                nextEpisodeTitle = nextEpisode?.title,
                                nextEpisodePosterUri = selectedMedia?.posterUri,
                                onProgressUpdate = { pos, total ->
                                    PlaybackManager.saveProgress(
                                        context = this@MainActivity,
                                        mediaId = mediaId,
                                        episodeId = currentEpisode.id,
                                        positionMs = pos,
                                        totalDurationMs = total
                                    )
                                    continueWatchingMap = PlaybackManager.getAllProgress(this@MainActivity)
                                },
                                onNextEpisode = if (nextEpisode != null) {
                                    { currentEpisodeIndex++ }
                                } else null,
                                onBack = { currentScreen = ScreenState.DETAIL }
                            )
                        }
                    }
                }

                ScreenState.SETTINGS -> {
                    SettingsScreen(
                        selectedEngine = playerEngine,
                        onEngineChanged = { playerEngine = it },
                        onBack = { currentScreen = ScreenState.HOME },
                        onChangeProfile = { currentScreen = ScreenState.PROFILES },
                        onRescan = {
                            loadLocalVideos()
                            Toast.makeText(this, "Escaneo completado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissionLauncher.launch(permission)
    }

    private fun loadLocalVideos() {
        val hiddenIds = PlaybackManager.getHiddenMedia(this)
        val (series, movies) = LocalVideoScanner.scanLocalVideos(this)
        seriesList = series.filterNot { hiddenIds.contains(it.id) }
        moviesList = movies.filterNot { hiddenIds.contains(it.id) }
    }
}
