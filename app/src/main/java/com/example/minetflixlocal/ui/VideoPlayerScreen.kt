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

    val libVLC = remember {
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

    val mediaPlayer = remember {
        MediaPlayer(libVLC)
    }

    var isPlaying by remember(videoUriString) {
        mutableStateOf(false)
    }

    var currentTimeMs by remember(videoUriString) {
        mutableLongStateOf(0L)
    }

    var totalDurationMs by remember(videoUriString) {
        mutableLongStateOf(0L)
    }

    var showControls by remember(videoUriString) {
        mutableStateOf(true)
    }

    var isFullscreen by remember {
        mutableStateOf(false)
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
        mutableStateOf<List<Pair<Int, String>>>(emptyList())
    }

    var subtitleTracks by remember {
        mutableStateOf<List<Pair<Int, String>>>(emptyList())
    }

    var videoLayoutReady by remember {
        mutableStateOf(false)
    }

    val interactionSource = remember {
        MutableInteractionSource()
    }

    // =========================================================
    // EVENTOS VLC
    // =========================================================

    DisposableEffect(mediaPlayer) {

        val mainHandler = Handler(Looper.getMainLooper())

        val listener = MediaPlayer.EventListener { event ->

            mainHandler.post {

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
                                mediaPlayer.length.coerceAtLeast(0L)
                        } catch (_: Exception) {
                        }
                    }

                    MediaPlayer.Event.TimeChanged -> {

                        try {
                            currentTimeMs =
                                mediaPlayer.time.coerceAtLeast(0L)

                            totalDurationMs =
                                mediaPlayer.length.coerceAtLeast(0L)
                        } catch (_: Exception) {
                        }
                    }

                    MediaPlayer.Event.Vout -> {
                        // Salida de video disponible.
                    }
                }
            }
        }

        mediaPlayer.setEventListener(listener)

        onDispose {

            try {
                onProgressUpdate(
                    mediaPlayer.time.coerceAtLeast(0L),
                    mediaPlayer.length.coerceAtLeast(0L)
                )
            } catch (_: Exception) {
            }

            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            } catch (_: Exception) {
            }

            try {
                if (mediaPlayer.vlcVout.areViewsAttached()) {
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

    // =========================================================
    // CREAR Y REPRODUCIR MEDIA
    // =========================================================

    LaunchedEffect(videoUriString, videoLayoutReady) {

        if (!videoLayoutReady) {
            return@LaunchedEffect
        }

        if (videoUriString.isBlank()) {
            return@LaunchedEffect
        }

        try {

            // Detener reproducción anterior
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }

            // Limpiar media anterior
            mediaPlayer.media = null

            delay(150L)

            val uri = Uri.parse(videoUriString)

            val media = Media(
                libVLC,
                uri
            )

            // Parámetros útiles para archivos locales / MKV
            media.setHWDecoderEnabled(
                true,
                false
            )

            media.addOption(
                ":network-caching=1000"
            )

            mediaPlayer.media = media

            media.release()

            // Esperamos a que VLC tenga preparada la media
            delay(150L)

            mediaPlayer.play()

            // Restaurar posición
            if (startPositionMs > 0L) {

                delay(500L)

                try {
                    mediaPlayer.time =
                        startPositionMs.coerceAtLeast(0L)
                } catch (_: Exception) {
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =========================================================
    // ACTUALIZACIÓN DE PROGRESO
    // =========================================================

    LaunchedEffect(mediaPlayer) {

        while (true) {

            try {

                if (mediaPlayer.isPlaying) {

                    currentTimeMs =
                        mediaPlayer.time.coerceAtLeast(0L)

                    totalDurationMs =
                        mediaPlayer.length.coerceAtLeast(0L)

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

    // =========================================================
    // OCULTAR CONTROLES
    // =========================================================

    LaunchedEffect(showControls, isPlaying) {

        if (showControls && isPlaying) {

            delay(5000L)

            showControls = false
        }
    }

    // =========================================================
    // OBTENER PISTAS
    // =========================================================

    LaunchedEffect(mediaPlayer, isPlaying) {

        if (isPlaying) {

            delay(800L)

            try {

                val tracks =
                    mediaPlayer.audioTracks

                if (tracks != null) {

                    audioTracks =
                        tracks.mapNotNull { track ->

                            if (track != null) {

                                Pair(
                                    track.id,
                                    track.name ?: "Audio"
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
                                    track.name ?: "Subtítulos"
                                )

                            } else {
                                null
                            }
                        }
                }

            } catch (_: Exception) {
            }
        }
    }

    // =========================================================
    // FULLSCREEN
    // =========================================================

    fun setFullscreen(fullscreen: Boolean) {

        val activity =
            context as? Activity
                ?: return

        val window =
            activity.window

        if (fullscreen) {

            if (android.os.Build.VERSION.SDK_INT >= 30) {

                window.insetsController?.let { controller ->

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

        } else {

            if (android.os.Build.VERSION.SDK_INT >= 30) {

                window.insetsController?.show(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
                )

            } else {

                @Suppress("DEPRECATION")

                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }

        isFullscreen = fullscreen
    }

    // =========================================================
    // UI
    // =========================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                showControls = !showControls
            }
    ) {

        AndroidView(

            factory = { ctx ->

                VLCVideoLayout(ctx).apply {

                    keepScreenOn = true

                    try {

                        if (!mediaPlayer.vlcVout.areViewsAttached()) {

                            mediaPlayer.attachViews(
                                this,
                                null,
                                false,
                                false
                            )
                        }

                        videoLayoutReady = true

                    } catch (e: Exception) {

                        e.printStackTrace()
                    }
                }
            },

            update = { layout ->

                try {

                    if (!mediaPlayer.vlcVout.areViewsAttached()) {

                        mediaPlayer.attachViews(
                            layout,
                            null,
                            false,
                            false
                        )
                    }

                    videoLayoutReady = true

                } catch (e: Exception) {

                    e.printStackTrace()
                }
            },

            modifier = Modifier.fillMaxSize()
        )

        // =====================================================
        // VELOCIDAD
        // =====================================================

        DropdownMenu(

            expanded = showSpeedMenu,

            onDismissRequest = {
                showSpeedMenu = false
            },

            modifier = Modifier
                .background(Color(0xFF202020))
                .align(Alignment.TopEnd)

        ) {

            val speeds =
                listOf(
                    0.5f,
                    0.75f,
                    1.0f,
                    1.25f,
                    1.5f,
                    2.0f
                )

            speeds.forEach { selectedSpeed ->

                DropdownMenuItem(

                    text = {

                        Text(
                            text =
                                if (selectedSpeed == 1f) {
                                    "Normal"
                                } else {
                                    "${selectedSpeed}x"
                                },
                            color = Color.White
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

        // =====================================================
        // AUDIO
        // =====================================================

        DropdownMenu(

            expanded = showAudioMenu,

            onDismissRequest = {
                showAudioMenu = false
            },

            modifier = Modifier
                .background(Color(0xFF202020))
                .align(Alignment.TopEnd)

        ) {

            if (audioTracks.isEmpty()) {

                DropdownMenuItem(

                    text = {

                        Text(
                            "No hay pistas de audio",
                            color = Color.Gray
                        )
                    },

                    onClick = {
                        showAudioMenu = false
                    }
                )

            } else {

                audioTracks.forEach { track ->

                    DropdownMenuItem(

                        text = {

                            Text(
                                track.second,
                                color = Color.White
                            )
                        },

                        onClick = {

                            try {

                                mediaPlayer.setAudioTrack(
                                    track.first
                                )

                            } catch (_: Exception) {
                            }

                            showAudioMenu = false
                        }
                    )
                }
            }
        }

        // =====================================================
        // SUBTÍTULOS
        // =====================================================

        DropdownMenu(

            expanded = showSubtitleMenu,

            onDismissRequest = {
                showSubtitleMenu = false
            },

            modifier = Modifier
                .background(Color(0xFF202020))
                .align(Alignment.TopEnd)

        ) {

            if (subtitleTracks.isEmpty()) {

                DropdownMenuItem(

                    text = {

                        Text(
                            "No hay subtítulos",
                            color = Color.Gray
                        )
                    },

                    onClick = {
                        showSubtitleMenu = false
                    }
                )

            } else {

                subtitleTracks.forEach { track ->

                    DropdownMenuItem(

                        text = {

                            Text(
                                track.second,
                                color = Color.White
                            )
                        },

                        onClick = {

                            try {

                                mediaPlayer.setSpuTrack(
                                    track.first
                                )

                            } catch (_: Exception) {
                            }

                            showSubtitleMenu = false
                        }
                    )
                }
            }
        }

        // =====================================================
        // CONTROLES
        // =====================================================

        if (showControls) {

            PlayerControlsOverlay(

                title = title,

                isPlaying = isPlaying,

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

                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                        } else {
                            mediaPlayer.play()
                        }

                    } catch (_: Exception) {
                    }
                },

                onRewind = {

                    try {

                        val newTime =
                            (
                                mediaPlayer.time -
                                    10_000L
                            ).coerceAtLeast(0L)

                        mediaPlayer.time =
                            newTime

                        currentTimeMs =
                            newTime

                    } catch (_: Exception) {
                    }
                },

                onForward = {

                    try {

                        val duration =
                            mediaPlayer.length
                                .coerceAtLeast(0L)

                        val newTime =
                            (
                                mediaPlayer.time +
                                    10_000L
                            ).coerceAtMost(
                                duration
                            )

                        mediaPlayer.time =
                            newTime

                        currentTimeMs =
                            newTime

                    } catch (_: Exception) {
                    }
                },

                onSeek = { newPosition ->

                    try {

                        mediaPlayer.time =
                            newPosition

                        currentTimeMs =
                            newPosition

                    } catch (_: Exception) {
                    }
                },

                onVolumeChange = { newVolume ->

                    val audioManager =
                        context.getSystemService(
                            Context.AUDIO_SERVICE
                        ) as AudioManager

                    val maxVolume =
                        audioManager.getStreamMaxVolume(
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
                        audioManager.getStreamMaxVolume(
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
                                restored.toFloat() /
                                    maxVolume *
                                    100f
                            )
                                .toInt()

                        isMuted =
                            false

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
                    showSpeedMenu = true
                },

                onAudioClick = {

                    if (audioTracks.isNotEmpty()) {
                        showAudioMenu = true
                    }
                },

                onSubtitleClick = {

                    if (subtitleTracks.isNotEmpty()) {
                        showSubtitleMenu = true
                    }
                },

                onFullscreenToggle = {
                    setFullscreen(!isFullscreen)
                },

                onBack = onBack
            )
        }
    }
}
