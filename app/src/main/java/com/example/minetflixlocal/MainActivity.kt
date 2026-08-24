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

enum class ScreenState {
    PROFILES, HOME, DETAIL, PLAYER, SETTINGS
}

class MainActivity : ComponentActivity() {

    private var seriesList by mutableStateOf<List<MediaSeries>>(emptyList())
    private var moviesList by mutableStateOf<List<MediaSeries>>(emptyList())
    private var activeProfile by mutableStateOf<UserProfile?>(null)
    private var playerEngine by mutableStateOf("EXOPLAYER")

    private var profilesState by mutableStateOf(
        listOf(
            UserProfile("1", "Usuario 1", "🐭", 0xFFE50914),
            UserProfile("2", "Familia", "🏰", 0xFF1E88E5),
            UserProfile("3", "Niños", "🦁", 0xFF43A047)
        )
    )

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
                            profilesState = profilesState.map {
                                if (it.id == updatedProfile.id) updatedProfile else it
                            }
                            if (activeProfile?.id == updatedProfile.id) {
                                activeProfile = updatedProfile
                            }
                        }
                    )
                }

                ScreenState.HOME -> {
                    HomeScreen(
                        activeProfile = activeProfile,
                        seriesList = seriesList,
                        moviesList = moviesList,
                        onMediaSelected = { media ->
                            selectedMedia = media
                            currentScreen = ScreenState.DETAIL
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
                            onUpdatePoster = { newUri ->
                                updateMediaPoster(media.id, newUri)
                                selectedMedia = selectedMedia?.copy(posterUri = newUri)
                            }
                        )
                    }
                }

                ScreenState.PLAYER -> {
                    val currentEpisode = currentEpisodeList.getOrNull(currentEpisodeIndex)
                    val nextEpisode = currentEpisodeList.getOrNull(currentEpisodeIndex + 1)

                    if (currentEpisode != null) {
                        VideoPlayerScreen(
                            videoUriString = currentEpisode.videoPath,
                            title = currentEpisode.title,
                            engine = playerEngine,
                            nextEpisodeTitle = nextEpisode?.title,
                            onNextEpisode = if (nextEpisode != null) {
                                { currentEpisodeIndex++ }
                            } else null,
                            onBack = { currentScreen = ScreenState.DETAIL }
                        )
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

    private fun updateMediaPoster(mediaId: String, newUri: Uri) {
        seriesList = seriesList.map { if (it.id == mediaId) it.copy(posterUri = newUri) else it }
        moviesList = moviesList.map { if (it.id == mediaId) it.copy(posterUri = newUri) else it }
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
        val (series, movies) = LocalVideoScanner.scanLocalVideos(this)
        seriesList = series
        moviesList = movies
    }
}
