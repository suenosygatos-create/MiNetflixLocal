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
import com.example.minetflixlocal.ui.ProfileSelectionScreen
import com.example.minetflixlocal.ui.theme.MiNetflixLocalTheme
import com.example.minetflixlocal.util.LocalVideo
import com.example.minetflixlocal.util.VideoScanner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiNetflixLocalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppWithPermissions()
                }
            }
        }
    }
}

@Composable
fun MainAppWithPermissions() {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    // Determina el permiso adecuado según la versión de Android
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            videos = VideoScanner(context).scanVideos()
        }
    }

    LaunchedEffect(Unit) {
        val permissionStatus = ContextCompat.checkSelfPermission(context, permissionToRequest)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            videos = VideoScanner(context).scanVideos()
        } else {
            launcher.launch(permissionToRequest)
        }
    }

    // Aquí continúas con la navegación normal de tu app
    ProfileSelectionScreen(
        onProfileSelected = { profile ->
            // Iniciar pantalla principal cargando la lista de 'videos'
        }
    )
}
