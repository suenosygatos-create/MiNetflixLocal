package com.example.minetflixlocal.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    videoUriString: String,
    title: String = "Video",
    engine: String = "EXOPLAYER",
    onBack: () -> Unit
) {
    if (engine == "VLC") {
        VlcVideoPlayer(videoUriString = videoUriString, title = title, onBack = onBack)
    } else {
        ExoVideoPlayer(videoUriString = videoUriString, title = title, onBack = onBack)
    }
}

@Composable
fun ExoVideoPlayer(videoUriString: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUriString)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
    }
}

@Composable
fun VlcVideoPlayer(videoUriString: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var totalTimeMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    val libVLC = remember { LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames")) }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    DisposableEffect(Unit) {
        val uri = Uri.parse(videoUriString)
        val media = if (uri.scheme == "content") {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) Media(libVLC, pfd.fileDescriptor) else Media(libVLC, uri)
        } else {
            Media(libVLC, uri)
        }

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()

        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            if (mediaPlayer.isPlaying) {
                currentTimeMs = mediaPlayer.time
                totalTimeMs = mediaPlayer.length
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Text(text = title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(48.dp))
                    }

                    IconButton(onClick = {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.play()
                            isPlaying = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    IconButton(onClick = { mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(totalTimeMs) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Slider(
                        value = if (totalTimeMs > 0) currentTimeMs.toFloat() / totalTimeMs.toFloat() else 0f,
                        onValueChange = { percent ->
                            val targetTime = (percent * totalTimeMs).toLong()
                            mediaPlayer.time = targetTime
                            currentTimeMs = targetTime
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE50914),
                            activeTrackColor = Color(0xFFE50914)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatTime(currentTimeMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(text = formatTime(totalTimeMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
