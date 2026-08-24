package com.example.minetflixlocal

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
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

    private val defaultProfiles = listOf(
        UserProfile("1", "Usuario 1", 0xFFE50914),
        UserProfile("2", "Familia", 0xFF1E88E5),
        UserProfile("3", "Niños", 0xFF43A047)
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
            var playingVideoUri by remember { mutableStateOf<String?>(null) }
            var playingVideoTitle by remember { mutableStateOf("Video") }

            // Soporte de navegación para los botones físicos/gestos del celular
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
                        profiles = defaultProfiles,
                        onProfileSelected = { profile ->
                            activeProfile = profile
                            currentScreen = ScreenState.HOME
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
                                playingVideoUri = episode.videoPath
                                playingVideoTitle = episode.title
                                currentScreen = ScreenState.PLAYER
                            }
                        )
                    }
                }

                ScreenState.PLAYER -> {
                    playingVideoUri?.let { uri ->
                        VideoPlayerScreen(
                            videoUriString = uri,
                            title = playingVideoTitle,
                            onBack = { currentScreen = ScreenState.DETAIL }
                        )
                    }
                }

                ScreenState.SETTINGS -> {
                    SettingsScreen(
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
        val (series, movies) = LocalVideoScanner.scanLocalVideos(this)
        seriesList = series
        moviesList = movies
    }
}
