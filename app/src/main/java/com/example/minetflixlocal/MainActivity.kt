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

// Asegúrate de importar tu tema y las pantallas desde donde estén ubicadas:
import com.example.minetflixlocal.ui.HomeScreen
import com.example.minetflixlocal.ui.ProfileSelectionScreen
import com.example.minetflixlocal.ui.SettingsScreen
import com.example.minetflixlocal.ui.theme.MiNetflixLocalTheme // Cambia esta ruta si Theme.kt está en otra carpeta
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
    var selectedEngine by remember { mutableStateOf("EXOPLAYER") }

    var seriesList by remember { mutableStateOf<List<MediaSeries>>(emptyList()) }
    var moviesList by remember { mutableStateOf<List<MediaSeries>>(emptyList()) }
    var currentProfileProgress by remember { mutableStateOf<Map<String, WatchProgress>>(emptyMap()) }

    var selectedMediaForDetail by remember { mutableStateOf<MediaSeries?>(null) }

    LaunchedEffect(activeProfile?.id) {
        activeProfile?.let { profile ->
            currentProfileProgress = progressManager.getProgressForProfile(profile.id)
        }
    }

    when (currentScreen) {
        Screen.PROFILE_SELECTION -> {
            ProfileSelectionScreen(
                onProfileSelected = { profile: UserProfile ->
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
                    // Lógica para reanudar reproducción
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
                onRescan = { /* Lógica de rescan */ },
                onBack = { currentScreen = Screen.HOME },
                onChangeProfile = {
                    activeProfile = null
                    currentScreen = Screen.PROFILE_SELECTION
                }
            )
        }
    }
}
