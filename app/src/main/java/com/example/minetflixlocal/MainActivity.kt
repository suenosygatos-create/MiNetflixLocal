package com.example.minetflixlocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.ui.*
import com.example.minetflixlocal.ui.theme.MiNetflixLocalTheme
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
    SETTINGS
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val progressManager = remember { WatchProgressManager(context) }

    var currentScreen by remember { mutableStateOf(Screen.PROFILE_SELECTION) }
    var activeProfile by remember { mutableStateOf<UserProfile?>(null) }
    
    var seriesList by remember { mutableStateOf<List<MediaSeries>>(emptyList()) }
    var moviesList by remember { mutableStateOf<List<MediaSeries>>(emptyList()) }
    var currentProfileProgress by remember { mutableStateOf<Map<String, WatchProgress>>(emptyMap()) }

    var selectedMediaForDetail by remember { mutableStateOf<MediaSeries?>(null) }

    // Recarga los datos de seguimiento del perfil activo de forma independiente
    LaunchedEffect(activeProfile?.id) {
        activeProfile?.let { profile ->
            currentProfileProgress = progressManager.getProgressForProfile(profile.id)
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
                    selectedMediaForDetail = media
                },
                onResumePlayback = { media, episodeId ->
                    // Lógica para reanudar la reproducción
                },
                onOpenSettings = {
                    currentScreen = Screen.SETTINGS
                }
            )
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                onBack = { currentScreen = Screen.HOME },
                onChangeProfile = {
                    activeProfile = null
                    currentScreen = Screen.PROFILE_SELECTION
                }
            )
        }
    }
}
