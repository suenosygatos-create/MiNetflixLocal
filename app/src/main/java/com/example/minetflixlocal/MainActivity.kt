package com.example.minetflixlocal

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.ui.DetailScreen
import com.example.minetflixlocal.ui.HomeScreen
import com.example.minetflixlocal.util.LocalVideoScanner

class MainActivity : ComponentActivity() {

    private var seriesList by mutableStateOf<List<MediaSeries>>(emptyList())
    private var moviesList by mutableStateOf<List<MediaSeries>>(emptyList())

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
            var selectedMedia by remember { mutableStateOf<MediaSeries?>(null) }

            if (selectedMedia == null) {
                HomeScreen(
                    seriesList = seriesList,
                    moviesList = moviesList,
                    onMediaSelected = { selectedMedia = it }
                )
            } else {
                DetailScreen(
                    media = selectedMedia!!,
                    onBack = { selectedMedia = null },
                    onEpisodeClick = { episode ->
                        playVideo(episode.videoPath)
                    }
                )
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

    private fun playVideo(videoUriString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(videoUriString), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el reproductor de video", Toast.LENGTH_SHORT).show()
        }
    }
}
