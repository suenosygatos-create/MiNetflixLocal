package com.example.minetflixlocal.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VideoPlayerScreen(
    videoUriString: String,
    title: String = "Video",
    engine: String = "EXOPLAYER",
    startPositionMs: Long = 0L,
    nextEpisodeTitle: String? = null,
    nextEpisodePosterUri: Uri? = null,
    onProgressUpdate: (Long, Long) -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showNextOverlay by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (engine == "VLC") {
            VlcVideoPlayer(
                videoUriString = videoUriString,
                title = title,
                startPositionMs = startPositionMs,
                onProgressUpdate = onProgressUpdate,
                onBack = onBack,
                onVideoEnded = {
                    if (onNextEpisode != null) showNextOverlay = true else onBack()
                }
            )
        } else {
            ExoVideoPlayer(
                videoUriString = videoUriString,
                title = title,
                startPositionMs = startPositionMs,
                onProgressUpdate = onProgressUpdate,
                onBack = onBack,
                onVideoEnded = {
                    if (onNextEpisode != null) showNextOverlay = true else onBack()
                }
            )
        }

        if (showNextOverlay && onNextEpisode != null) {
            NextEpisodeOverlay(
                nextTitle = nextEpisodeTitle ?: "Siguiente episodio",
                posterUri = nextEpisodePosterUri,
                onPlayNow = onNextEpisode,
                onCancel = onBack
            )
        }
    }
}

@Composable
fun ExoVideoPlayer(
    videoUriString: String,
    title: String,
    startPositionMs: Long,
    onProgressUpdate: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onVideoEnded: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUriString)))
            prepare()
            if (startPositionMs > 0) seekTo(startPositionMs)
            playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            }
            delay(3000)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    onVideoEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
fun VlcVideoPlayer(
    videoUriString: String,
    title: String,
    startPositionMs: Long,
    onProgressUpdate: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onVideoEnded: () -> Unit
) {
    val context = LocalContext.current
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

        mediaPlayer.setEventListener { event ->
            if (event.type == MediaPlayer.Event.EndReached) {
                onVideoEnded()
            }
        }

        mediaPlayer.play()
        if (startPositionMs > 0) mediaPlayer.time = startPositionMs

        onDispose {
            onProgressUpdate(mediaPlayer.time, mediaPlayer.length)
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            if (mediaPlayer.isPlaying) {
                onProgressUpdate(mediaPlayer.time, mediaPlayer.length)
            }
            delay(3000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
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
fun NextEpisodeOverlay(
    nextTitle: String,
    posterUri: Uri?,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(10) }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        } else {
            onPlayNow()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .padding(24.dp)
                .width(360.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "El próximo episodio empieza en $secondsLeft s",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 60.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF333333))
                    ) {
                        if (posterUri != null) {
                            AsyncImage(
                                model = posterUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = nextTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancelar", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onPlayNow,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reproducir ya")
                    }
                }
            }
        }
    }
}
