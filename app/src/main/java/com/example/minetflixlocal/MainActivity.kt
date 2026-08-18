package com.example.minetflixlocal

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VideoAppScreen()
                }
            }
        }
    }
}

@Composable
fun VideoAppScreen() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var videoList by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

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
            videoList = loadLocalVideos(context)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionToRequest)
    }

    if (selectedVideoUri != null) {
        VideoPlayerScreen(
            videoUri = selectedVideoUri!!,
            onBack = { selectedVideoUri = null }
        )
    } else {
        if (hasPermission) {
            VideoGridScreen(
                videos = videoList,
                onVideoSelect = { selectedVideoUri = it.uri }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Se requiere permiso para leer los videos locales.")
            }
        }
    }
}

@Composable
fun VideoGridScreen(
    videos: List<VideoFile>,
    onVideoSelect: (VideoFile) -> Unit
) {
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No se encontraron videos locales.")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(videos) { video ->
                VideoItem(video = video, onClick = { onVideoSelect(video) })
            }
        }
    }
}

@Composable
fun VideoItem(
    video: VideoFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(video.uri)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(1000)
            .crossfade(true)
            .build()
    )

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Image(
                painter = painter,
                contentDescription = video.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = video.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onBack,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Volver")
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

fun loadLocalVideos(context: Context): List<VideoFile> {
    val videos = mutableListOf<VideoFile>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val duration = cursor.getInt(durationColumn)
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )

            videos.add(VideoFile(id, contentUri, name, duration))
        }
    }
    return videos
}
