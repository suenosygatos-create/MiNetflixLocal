package com.example.minetflixlocal.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Locale

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

    var showNextOverlay by remember(videoUriString) {
        mutableStateOf(false)
    }

    /*
     * IMPORTANTE:
     *
     * Android MediaStore normalmente entrega:
     *
     * content://...
     *
     * ExoPlayer trabaja muy bien con estas URI.
     *
     * VLC puede tener problemas dependiendo del proveedor
     * de contenido y de los permisos.
     *
     * Por eso, si recibimos content:// forzamos ExoPlayer.
     */

    val parsedUri = remember(videoUriString) {
        Uri.parse(videoUriString)
    }

    val effectiveEngine = remember(
        videoUriString,
        engine
    ) {
        if (parsedUri.scheme.equals("content", ignoreCase = true)) {
            "EXOPLAYER"
        } else {
            engine.uppercase(Locale.US)
        }
    }

    LaunchedEffect(videoUriString) {
        showNextOverlay = false
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window

        window?.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        onDispose {
            window?.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (videoUriString.isBlank()) {

            Text(
                text = "No se encontró el archivo de video.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )

        } else {

            if (effectiveEngine == "VLC") {

                VlcVideoPlayer(
                    videoUriString = videoUriString,
                    title = title,
                    startPositionMs = startPositionMs,
                    onProgressUpdate = onProgressUpdate,
                    onBack = onBack,
                    onVideoEnded = {
                        if (onNextEpisode != null) {
                            showNextOverlay = true
                        } else {
                            onBack()
                        }
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
                        if (onNextEpisode != null) {
                            showNextOverlay = true
                        } else {
                            onBack()
                        }
                    }
                )
            }
        }

        if (
            showNextOverlay &&
            onNextEpisode != null
        ) {

            NextEpisodeOverlay(
                nextTitle =
                    nextEpisodeTitle
                        ?: "Siguiente episodio",

                posterUri =
                    nextEpisodePosterUri,

                onPlayNow = {
                    showNextOverlay = false
                    onNextEpisode()
                },

                onCancel = {
                    showNextOverlay = false
                    onBack()
                }
            )
        }
    }
}

/* =========================================================
 * EXOPLAYER
 * ========================================================= */

@Composable
private fun ExoVideoPlayer(
    videoUriString: String,
    title: String,
    startPositionMs: Long,
    onProgressUpdate: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onVideoEnded: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUriString) {

        ExoPlayer
            .Builder(context)
            .build()
            .apply {

                val uri =
                    Uri.parse(videoUriString)

                val mediaItem =
                    MediaItem
                        .Builder()
                        .setUri(uri)
                        .build()

                setMediaItem(mediaItem)

                prepare()

                if (startPositionMs > 0L) {
                    seekTo(startPositionMs)
                }

                playWhenReady = true
            }
    }

    var isPlaying by remember {
        mutableStateOf(true)
    }

    var currentPosition by remember {
        mutableLongStateOf(
            startPositionMs
        )
    }

    var duration by remember {
        mutableLongStateOf(0L)
    }

    DisposableEffect(exoPlayer) {

        val listener =
            object : Player.Listener {

                override fun onIsPlayingChanged(
                    playing: Boolean
                ) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(
                    state: Int
                ) {

                    if (
                        state ==
                        Player.STATE_READY
                    ) {
                        duration =
                            exoPlayer.duration
                                .takeIf {
                                    it > 0L
                                }
                                ?: 0L
                    }

                    if (
                        state ==
                        Player.STATE_ENDED
                    ) {
                        onVideoEnded()
                    }
                }
            }

        exoPlayer.addListener(listener)

        onDispose {

            try {

                onProgressUpdate(
                    exoPlayer.currentPosition,
                    exoPlayer.duration
                        .takeIf { it > 0L }
                        ?: 0L
                )

            } catch (_: Exception) {
            }

            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {

        while (true) {

            try {

                currentPosition =
                    exoPlayer.currentPosition
                        .coerceAtLeast(0L)

                duration =
                    exoPlayer.duration
                        .takeIf { it > 0L }
                        ?: 0L

                onProgressUpdate(
                    currentPosition,
                    duration
                )

            } catch (_: Exception) {
            }

            delay(1000L)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(

            factory = { ctx ->

                PlayerView(ctx).apply {

                    player = exoPlayer

                    useController = true

                    controllerShowTimeoutMs =
                        4000

                    controllerHideOnTouch =
                        true

                    keepScreenOn = true

                    setShowBuffering(
                        PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                    )
                }
            },

            update = { view ->

                view.player =
                    exoPlayer
            },

            modifier =
                Modifier.fillMaxSize()
        )

        IconButton(

            onClick = onBack,

            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {

            Icon(
                imageVector =
                    Icons.Default.ArrowBack,

                contentDescription =
                    "Volver",

                tint =
                    Color.White
            )
        }
    }
}

/* =========================================================
 * VLC
 * ========================================================= */

@Composable
private fun VlcVideoPlayer(
    videoUriString: String,
    title: String,
    startPositionMs: Long,
    onProgressUpdate: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onVideoEnded: () -> Unit
) {
    val context = LocalContext.current

    val libVLC =
        remember {

            LibVLC(
                context,
                arrayListOf(
                    "--no-drop-late-frames",
                    "--no-skip-frames",
                    "--network-caching=1000",
                    "--clock-jitter=0",
                    "--clock-synchro=0"
                )
            )
        }

    val mediaPlayer =
        remember {

            MediaPlayer(libVLC)
        }

    var videoReady by remember {
        mutableStateOf(false)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var currentTimeMs by remember {
        mutableLongStateOf(0L)
    }

    var totalDurationMs by remember {
        mutableLongStateOf(0L)
    }

    var isMuted by remember {
        mutableStateOf(false)
    }

    var volume by remember {
        mutableIntStateOf(100)
    }

    var speed by remember {
        mutableFloatStateOf(1f)
    }

    var showControls by remember {
        mutableStateOf(true)
    }

    var isFullscreen by remember {
        mutableStateOf(false)
    }

    var showSpeedMenu by remember {
        mutableStateOf(false)
    }

    var showAudioMenu by remember {
        mutableStateOf(false)
    }

    var showSubtitleMenu by remember {
        mutableStateOf(false)
    }

    var audioTracks by remember {
        mutableStateOf<List<Pair<Int, String>>>(
            emptyList()
        )
    }

    var subtitleTracks by remember {
        mutableStateOf<List<Pair<Int, String>>>(
            emptyList()
        )
    }

    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    /*
     * Listener VLC
     */

    DisposableEffect(mediaPlayer) {

        val listener =
            MediaPlayer.EventListener { event ->

                when (event.type) {

                    MediaPlayer.Event.Playing -> {

                        isPlaying = true
                    }

                    MediaPlayer.Event.Paused -> {

                        isPlaying = false
                    }

                    MediaPlayer.Event.Stopped -> {

                        isPlaying = false
                    }

                    MediaPlayer.Event.EndReached -> {

                        isPlaying = false

                        onVideoEnded()
                    }

                    MediaPlayer.Event.LengthChanged -> {

                        try {

                            totalDurationMs =
                                mediaPlayer.length
                                    .coerceAtLeast(0L)

                        } catch (_: Exception) {
                        }
                    }

                    MediaPlayer.Event.TimeChanged -> {

                        try {

                            currentTimeMs =
                                mediaPlayer.time
                                    .coerceAtLeast(0L)

                            totalDurationMs =
                                mediaPlayer.length
                                    .coerceAtLeast(0L)

                        } catch (_: Exception) {
                        }
                    }
                }
            }

        mediaPlayer.setEventListener(listener)

        onDispose {

            try {

                onProgressUpdate(
                    mediaPlayer.time
                        .coerceAtLeast(0L),

                    mediaPlayer.length
                        .coerceAtLeast(0L)
                )

            } catch (_: Exception) {
            }

            try {

                if (
                    mediaPlayer.isPlaying
                ) {
                    mediaPlayer.stop()
                }

            } catch (_: Exception) {
            }

            try {

                if (
                    mediaPlayer.vlcVout
                        .areViewsAttached()
                ) {

                    mediaPlayer.detachViews()
                }

            } catch (_: Exception) {
            }

            try {
                mediaPlayer.release()
            } catch (_: Exception) {
            }

            try {
                libVLC.release()
            } catch (_: Exception) {
            }
        }
    }

    /*
     * Crear Media y reproducir
     */

    LaunchedEffect(
        videoUriString,
        videoReady
    ) {

        if (!videoReady) {
            return@LaunchedEffect
        }

        if (videoUriString.isBlank()) {
            return@LaunchedEffect
        }

        try {

            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }

            mediaPlayer.media = null

            delay(200L)

            val uri =
                Uri.parse(videoUriString)

            val media =
                Media(
                    libVLC,
                    uri
                )

            /*
             * Hardware decoding.
             */

            media.setHWDecoderEnabled(
                true,
                false
            )

            /*
             * Buffer de red.
             */

            media.addOption(
                ":network-caching=1000"
            )

            /*
             * Para archivos locales.
             */

            media.addOption(
                ":file-caching=500"
            )

            mediaPlayer.media =
                media

            media.release()

            delay(300L)

            mediaPlayer.play()

            /*
             * Restaurar posición después
             * de iniciar VLC.
             */

            if (startPositionMs > 0L) {

                delay(1000L)

                try {

                    mediaPlayer.time =
                        startPositionMs
                            .coerceAtLeast(0L)

                } catch (_: Exception) {
                }
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    /*
     * Progreso
     */

    LaunchedEffect(mediaPlayer) {

        while (true) {

            try {

                if (
                    mediaPlayer.isPlaying
                ) {

                    currentTimeMs =
                        mediaPlayer.time
                            .coerceAtLeast(0L)

                    totalDurationMs =
                        mediaPlayer.length
                            .coerceAtLeast(0L)

                    onProgressUpdate(
                        currentTimeMs,
                        totalDurationMs
                    )
                }

            } catch (_: Exception) {
            }

            delay(1000L)
        }
    }

    /*
     * Obtener pistas
     */

    LaunchedEffect(
        mediaPlayer,
        isPlaying
    ) {

        if (!isPlaying) {
            return@LaunchedEffect
        }

        delay(1000L)

        try {

            val tracks =
                mediaPlayer.audioTracks

            if (tracks != null) {

                audioTracks =
                    tracks.mapNotNull { track ->

                        if (track != null) {

                            Pair(
                                track.id,
                                track.name
                                    ?: "Audio"
                            )

                        } else {
                            null
                        }
                    }
            }

        } catch (_: Exception) {
        }

        try {

            val tracks =
                mediaPlayer.spuTracks

            if (tracks != null) {

                subtitleTracks =
                    tracks.mapNotNull { track ->

                        if (track != null) {

                            Pair(
                                track.id,
                                track.name
                                    ?: "Subtítulos"
                            )

                        } else {
                            null
                        }
                    }
            }

        } catch (_: Exception) {
        }
    }

    /*
     * Ocultar controles
     */

    LaunchedEffect(
        showControls,
        isPlaying
    ) {

        if (
            showControls &&
            isPlaying
        ) {

            delay(5000L)

            showControls = false
        }
    }

    /*
     * Pantalla completa
     */

    fun toggleFullscreen() {

        val activity =
            context as? Activity
                ?: return

        val window =
            activity.window

        if (!isFullscreen) {

            if (
                android.os.Build.VERSION.SDK_INT >= 30
            ) {

                window.insetsController?.let {

                    controller ->

                    controller.hide(
                        WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars()
                    )

                    controller.systemBarsBehavior =
                        WindowInsetsController
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

            } else {

                @Suppress("DEPRECATION")

                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }

            isFullscreen = true

        } else {

            if (
                android.os.Build.VERSION.SDK_INT >= 30
            ) {

                window.insetsController?.show(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
                )

            } else {

                @Suppress("DEPRECATION")

                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }

            isFullscreen = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource =
                    interactionSource,
                indication = null
            ) {

                showControls =
                    !showControls
            }
    ) {

        /*
         * VLC VIDEO
         */

        AndroidView(

            factory = { ctx ->

                VLCVideoLayout(ctx).apply {

                    keepScreenOn = true

                    try {

                        if (
                            !mediaPlayer
                                .vlcVout
                                .areViewsAttached()
                        ) {

                            mediaPlayer.attachViews(
                                this,
                                null,
                                false,
                                false
                            )
                        }

                        videoReady = true

                    } catch (e: Exception) {

                        e.printStackTrace()
                    }
                }
            },

            update = { layout ->

                try {

                    if (
                        !mediaPlayer
                            .vlcVout
                            .areViewsAttached()
                    ) {

                        mediaPlayer.attachViews(
                            layout,
                            null,
                            false,
                            false
                        )
                    }

                    videoReady = true

                } catch (e: Exception) {

                    e.printStackTrace()
                }
            },

            modifier =
                Modifier.fillMaxSize()
        )

        if (showControls) {

            PlayerControlsOverlay(
                title = title,

                isPlaying =
                    isPlaying,

                currentTimeMs =
                    currentTimeMs,

                totalDurationMs =
                    totalDurationMs,

                isMuted =
                    isMuted,

                volume =
                    volume,

                speed =
                    speed,

                isFullscreen =
                    isFullscreen,

                onPlayPauseToggle = {

                    try {

                        if (
                            mediaPlayer.isPlaying
                        ) {

                            mediaPlayer.pause()

                        } else {

                            mediaPlayer.play()
                        }

                    } catch (_: Exception) {
                    }
                },

                onRewind = {

                    try {

                        val position =
                            (
                                mediaPlayer.time -
                                    10_000L
                            ).coerceAtLeast(0L)

                        mediaPlayer.time =
                            position

                        currentTimeMs =
                            position

                    } catch (_: Exception) {
                    }
                },

                onForward = {

                    try {

                        val duration =
                            mediaPlayer.length
                                .coerceAtLeast(0L)

                        val position =
                            (
                                mediaPlayer.time +
                                    10_000L
                            ).coerceAtMost(
                                duration
                            )

                        mediaPlayer.time =
                            position

                        currentTimeMs =
                            position

                    } catch (_: Exception) {
                    }
                },

                onSeek = { position ->

                    try {

                        mediaPlayer.time =
                            position

                        currentTimeMs =
                            position

                    } catch (_: Exception) {
                    }
                },

                onVolumeChange = { newVolume ->

                    val audioManager =
                        context.getSystemService(
                            Context.AUDIO_SERVICE
                        ) as AudioManager

                    val maxVolume =
                        audioManager
                            .getStreamMaxVolume(
                                AudioManager.STREAM_MUSIC
                            )

                    val androidVolume =
                        (
                            newVolume / 100f *
                                maxVolume
                        )
                            .toInt()
                            .coerceIn(
                                0,
                                maxVolume
                            )

                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        androidVolume,
                        0
                    )

                    volume =
                        newVolume

                    isMuted =
                        newVolume == 0
                },

                onMuteToggle = {

                    val audioManager =
                        context.getSystemService(
                            Context.AUDIO_SERVICE
                        ) as AudioManager

                    val maxVolume =
                        audioManager
                            .getStreamMaxVolume(
                                AudioManager.STREAM_MUSIC
                            )

                    if (isMuted) {

                        val restored =
                            (
                                maxVolume *
                                    0.7f
                            )
                                .toInt()
                                .coerceAtLeast(1)

                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            restored,
                            0
                        )

                        volume =
                            (
                                restored
                                    .toFloat() /
                                    maxVolume *
                                    100f
                            )
                                .toInt()

                        isMuted = false

                    } else {

                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            0,
                            0
                        )

                        volume = 0

                        isMuted = true
                    }
                },

                onSpeedClick = {

                    showSpeedMenu =
                        true

                    showAudioMenu =
                        false

                    showSubtitleMenu =
                        false
                },

                onAudioClick = {

                    if (
                        audioTracks.isNotEmpty()
                    ) {

                        showAudioMenu =
                            true

                        showSpeedMenu =
                            false

                        showSubtitleMenu =
                            false
                    }
                },

                onSubtitleClick = {

                    if (
                        subtitleTracks.isNotEmpty()
                    ) {

                        showSubtitleMenu =
                            true

                        showSpeedMenu =
                            false

                        showAudioMenu =
                            false
                    }
                },

                onFullscreenToggle = {

                    toggleFullscreen()
                },

                onBack = onBack
            )
        }

        /*
         * MENÚ VELOCIDAD
         */

        DropdownMenu(

            expanded =
                showSpeedMenu,

            onDismissRequest = {
                showSpeedMenu = false
            },

            modifier =
                Modifier
                    .background(
                        Color(0xFF202020)
                    )
        ) {

            listOf(
                0.5f,
                0.75f,
                1f,
                1.25f,
                1.5f,
                2f
            ).forEach { selectedSpeed ->

                DropdownMenuItem(

                    text = {

                        Text(
                            text =
                                if (
                                    selectedSpeed == 1f
                                ) {
                                    "Normal"
                                } else {
                                    "${selectedSpeed}x"
                                },

                            color =
                                Color.White
                        )
                    },

                    onClick = {

                        try {

                            mediaPlayer.rate =
                                selectedSpeed

                        } catch (_: Exception) {
                        }

                        speed =
                            selectedSpeed

                        showSpeedMenu =
                            false
                    }
                )
            }
        }

        /*
         * MENÚ AUDIO
         */

        DropdownMenu(

            expanded =
                showAudioMenu,

            onDismissRequest = {
                showAudioMenu = false
            },

            modifier =
                Modifier
                    .background(
                        Color(0xFF202020)
                    )
        ) {

            audioTracks.forEach { track ->

                DropdownMenuItem(

                    text = {

                        Text(
                            text =
                                track.second,

                            color =
                                Color.White
                        )
                    },

                    onClick = {

                        try {

                            mediaPlayer
                                .setAudioTrack(
                                    track.first
                                )

                        } catch (_: Exception) {
                        }

                        showAudioMenu =
                            false
                    }
                )
            }
        }

        /*
         * MENÚ SUBTÍTULOS
         */

        DropdownMenu(

            expanded =
                showSubtitleMenu,

            onDismissRequest = {
                showSubtitleMenu = false
            },

            modifier =
                Modifier
                    .background(
                        Color(0xFF202020)
                    )
        ) {

            subtitleTracks.forEach { track ->

                DropdownMenuItem(

                    text = {

                        Text(
                            text =
                                track.second,

                            color =
                                Color.White
                        )
                    },

                    onClick = {

                        try {

                            mediaPlayer
                                .setSpuTrack(
                                    track.first
                                )

                        } catch (_: Exception) {
                        }

                        showSubtitleMenu =
                            false
                    }
                )
            }
        }
    }
}

/* =========================================================
 * CONTROLES VLC
 * ========================================================= */

@Composable
private fun PlayerControlsOverlay(
    title: String,
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    isMuted: Boolean,
    volume: Int,
    speed: Float,
    isFullscreen: Boolean,
    onPlayPauseToggle: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = 0.45f
                )
            )
            .padding(
                horizontal = 24.dp,
                vertical = 18.dp
            )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.TopCenter
                    )
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    imageVector =
                        Icons.Default.ArrowBack,

                    contentDescription =
                        "Volver",

                    tint =
                        Color.White
                )
            }

            Spacer(
                modifier =
                    Modifier.width(10.dp)
            )

            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.Center,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.Center
                    )
        ) {

            IconButton(
                onClick = onRewind,
                modifier =
                    Modifier.size(64.dp)
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Replay10,

                    contentDescription =
                        "Retroceder 10 segundos",

                    tint =
                        Color.White,

                    modifier =
                        Modifier.size(42.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.width(24.dp)
            )

            IconButton(

                onClick =
                    onPlayPauseToggle,

                modifier =
                    Modifier
                        .size(74.dp)
                        .background(
                            Color.White,
                            CircleShape
                        )
            ) {

                Icon(
                    imageVector =
                        if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },

                    contentDescription =
                        if (isPlaying) {
                            "Pausar"
                        } else {
                            "Reproducir"
                        },

                    tint =
                        Color.Black,

                    modifier =
                        Modifier.size(46.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.width(24.dp)
            )

            IconButton(
                onClick = onForward,
                modifier =
                    Modifier.size(64.dp)
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Forward10,

                    contentDescription =
                        "Adelantar 10 segundos",

                    tint =
                        Color.White,

                    modifier =
                        Modifier.size(42.dp)
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.BottomCenter
                    )
        ) {

            Slider(

                value =
                    if (
                        totalDurationMs > 0L
                    ) {

                        (
                            currentTimeMs
                                .toFloat() /
                                totalDurationMs
                                    .toFloat()
                        )
                            .coerceIn(
                                0f,
                                1f
                            )

                    } else {
                        0f
                    },

                onValueChange = { fraction ->

                    if (
                        totalDurationMs > 0L
                    ) {

                        onSeek(
                            (
                                fraction *
                                    totalDurationMs
                            ).toLong()
                        )
                    }
                },

                colors =
                    SliderDefaults.colors(
                        thumbColor =
                            Color(0xFFE50914),

                        activeTrackColor =
                            Color(0xFFE50914),

                        inactiveTrackColor =
                            Color.White.copy(
                                alpha = 0.35f
                            )
                    ),

                modifier =
                    Modifier.fillMaxWidth()
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        formatTime(
                            currentTimeMs
                        ),

                    color =
                        Color.White,

                    fontSize = 12.sp
                )

                Text(
                    text =
                        formatTime(
                            totalDurationMs
                        ),

                    color =
                        Color.White,

                    fontSize = 12.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.End,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                IconButton(
                    onClick =
                        onMuteToggle
                ) {

                    Icon(
                        imageVector =
                            when {

                                isMuted ->
                                    Icons.Default.VolumeOff

                                volume < 50 ->
                                    Icons.Default.VolumeDown

                                else ->
                                    Icons.Default.VolumeUp
                            },

                        contentDescription =
                            "Volumen",

                        tint =
                            Color.White
                    )
                }

                Text(
                    text =
                        "$volume%",

                    color =
                        Color.White,

                    fontSize =
                        12.sp
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                IconButton(
                    onClick =
                        onSpeedClick
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Speed,

                        contentDescription =
                            "Velocidad",

                        tint =
                            Color.White
                    )
                }

                Text(
                    text =
                        if (speed == 1f) {
                            "1x"
                        } else {
                            "${speed}x"
                        },

                    color =
                        Color.White,

                    fontSize =
                        12.sp
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                IconButton(
                    onClick =
                        onAudioClick
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.AudioFile,

                        contentDescription =
                            "Audio",

                        tint =
                            Color.White
                    )
                }

                IconButton(
                    onClick =
                        onSubtitleClick
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.ClosedCaption,

                        contentDescription =
                            "Subtítulos",

                        tint =
                            Color.White
                    )
                }

                IconButton(
                    onClick =
                        onFullscreenToggle
                ) {

                    Icon(
                        imageVector =
                            if (isFullscreen) {
                                Icons.Default.FullscreenExit
                            } else {
                                Icons.Default.Fullscreen
                            },

                        contentDescription =
                            "Pantalla completa",

                        tint =
                            Color.White
                    )
                }
            }
        }
    }
}

/* =========================================================
 * SIGUIENTE EPISODIO
 * ========================================================= */

@Composable
private fun NextEpisodeOverlay(
    nextTitle: String,
    posterUri: Uri?,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember {
        mutableIntStateOf(10)
    }

    LaunchedEffect(Unit) {

        while (secondsLeft > 0) {

            delay(1000L)

            secondsLeft--
        }

        onPlayNow()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.88f
                    )
                ),

        contentAlignment =
            Alignment.BottomEnd
    ) {

        Column(

            modifier =
                Modifier
                    .padding(24.dp)
                    .width(380.dp)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        Color(0xFF181818)
                    )
                    .padding(18.dp)
        ) {

            Text(
                text =
                    "Siguiente episodio",

                color =
                    Color.White,

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Comienza en ${secondsLeft}s",

                color =
                    Color.Gray,

                fontSize =
                    13.sp
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Box(
                    modifier =
                        Modifier
                            .width(110.dp)
                            .height(65.dp)
                            .clip(
                                RoundedCornerShape(8.dp)
                            )
                            .background(
                                Color(0xFF303030)
                            )
                ) {

                    if (posterUri != null) {

                        AsyncImage(
                            model =
                                posterUri,

                            contentDescription =
                                null,

                            contentScale =
                                ContentScale.Crop,

                            modifier =
                                Modifier.fillMaxSize()
                        )

                    } else {

                        Icon(
                            imageVector =
                                Icons.Default.PlayArrow,

                            contentDescription =
                                null,

                            tint =
                                Color.White,

                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .align(
                                        Alignment.Center
                                    )
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.width(14.dp)
                )

                Text(
                    text =
                        nextTitle,

                    color =
                        Color.White,

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.SemiBold,

                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End
            ) {

                Button(

                    onClick =
                        onCancel,

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                Color(0xFF333333)
                        )
                ) {

                    Text(
                        text =
                            "Cancelar",

                        color =
                            Color.White
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Button(

                    onClick =
                        onPlayNow,

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                Color(0xFFE50914)
                        )
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.PlayArrow,

                        contentDescription =
                            null
                    )

                    Spacer(
                        modifier =
                            Modifier.width(4.dp)
                    )

                    Text(
                        text =
                            "Reproducir"
                    )
                }
            }
        }
    }
}

/* =========================================================
 * FORMATO DE TIEMPO
 * ========================================================= */

private fun formatTime(
    ms: Long
): String {

    val totalSeconds =
        (ms / 1000L)
            .coerceAtLeast(0L)

    val seconds =
        totalSeconds % 60L

    val totalMinutes =
        totalSeconds / 60L

    val minutes =
        totalMinutes % 60L

    val hours =
        totalMinutes / 60L

    return if (hours > 0L) {

        String.format(
            Locale.US,
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        )

    } else {

        String.format(
            Locale.US,
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}
