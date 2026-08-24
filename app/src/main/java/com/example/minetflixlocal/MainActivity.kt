package com.example.minetflixlocal

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

// --- MODELOS DE DATOS ---
data class UserProfile(val name: String, val color: Color)

data class Episode(
    val id: String,
    val title: String,
    val duration: String,
    val description: String,
    val uri: Uri? = null
)

data class Season(
    val number: Int,
    val episodes: List<Episode>
)

data class MediaItemShow(
    val id: String,
    val title: String,
    val description: String,
    val bannerColor: Color,
    val seasons: List<Season>
)

data class LocalVideo(
    val id: Long,
    val title: String,
    val uri: Uri,
    val duration: String
)

sealed class Screen {
    object Splash : Screen()
    object Profiles : Screen()
    object Home : Screen()
    data class ShowDetail(val show: MediaItemShow) : Screen()
    data class Player(val videoUri: Uri) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF141414))) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF141414)
                ) {
                    NetflixApp()
                }
            }
        }
    }
}

// --- APP PRINCIPAL CON CONTROLADOR DE NAVEGACIÓN ---
@Composable
fun NetflixApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var selectedProfile by remember { mutableStateOf<UserProfile?>(null) }
    var localVideos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    val context = LocalContext.current

    // Datos estáticos estructurados para la serie "Los Simpson"
    val simpsonsShow = remember {
        MediaItemShow(
            id = "simpsons",
            title = "Los Simpson",
            description = "Las aventuras de una familia típica estadounidense en la ciudad de Springfield.",
            bannerColor = Color(0xFFFED41D),
            seasons = (1..5).map { seasonNum ->
                Season(
                    number = seasonNum,
                    episodes = (1..6).map { epNum ->
                        Episode(
                            id = "s${seasonNum}e${epNum}",
                            title = "Capítulo $epNum - Temp. $seasonNum",
                            duration = "22 min",
                            description = "Episodio $epNum de la temporada $seasonNum de la serie animada."
                        )
                    }
                )
            }
        )
    }

    when (val screen = currentScreen) {
        is Screen.Splash -> SplashScreen { currentScreen = Screen.Profiles }
        is Screen.Profiles -> ProfileSelectionScreen { profile ->
            selectedProfile = profile
            currentScreen = Screen.Home
        }
        is Screen.Home -> HomeScreen(
            profile = selectedProfile,
            simpsonsShow = simpsonsShow,
            localVideos = localVideos,
            onShowSelected = { show -> currentScreen = Screen.ShowDetail(show) },
            onVideoPlay = { uri -> currentScreen = Screen.Player(uri) },
            onScanVideos = {
                localVideos = fetchLocalVideos(context)
            },
            onDeleteLocalVideo = { videoToDelete ->
                localVideos = localVideos.filter { it.id != videoToDelete.id }
            }
        )
        is Screen.ShowDetail -> ShowDetailScreen(
            show = screen.show,
            onBack = { currentScreen = Screen.Home },
            onPlayEpisode = { uri -> currentScreen = Screen.Player(uri) }
        )
        is Screen.Player -> VideoPlayerScreen(
            videoUri = screen.videoUri,
            onBack = { currentScreen = Screen.Home }
        )
    }
}

// --- 1. PANTALLA SPLASH ---
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "NETFLIX",
            color = Color(0xFFE50914),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
    }
}

// --- 2. PANTALLA SELECCIÓN DE PERFILES ---
@Composable
fun ProfileSelectionScreen(onProfileSelected: (UserProfile) -> Unit) {
    val profiles = listOf(
        UserProfile("Usuario 1", Color(0xFFE50914)),
        UserProfile("Familia", Color(0xFF1B82D0)),
        UserProfile("Infantil", Color(0xFF2BBE63))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "¿Quién está viendo ahora?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEach { profile ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onProfileSelected(profile) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(profile.color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.name,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// --- 3. CATÁLOGO / CATÁLOGO ESTILO NETFLIX ---
@Composable
fun HomeScreen(
    profile: UserProfile?,
    simpsonsShow: MediaItemShow,
    localVideos: List<LocalVideo>,
    onShowSelected: (MediaItemShow) -> Unit,
    onVideoPlay: (Uri) -> Unit,
    onScanVideos: () -> Unit,
    onDeleteLocalVideo: (LocalVideo) -> Unit
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onScanVideos()
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onVideoPlay(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        // Barra Superior
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETFLIX",
                    color = Color(0xFFE50914),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            android.Manifest.permission.READ_MEDIA_VIDEO
                        } else {
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(perm)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Escanear Videos", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(profile?.color ?: Color.Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile?.name?.take(1) ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Feature Banner (Destacado Principal: Los Simpson)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clickable { onShowSelected(simpsonsShow) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFED41D),
                                    Color(0xFF141414)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(70.dp)
                        )
                        Text(
                            text = "LOS SIMPSON",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = "5 Temporadas Disponibles",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onShowSelected(simpsonsShow) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Temporadas", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color.White, Color.White))),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Abrir Archivo", color = Color.White)
                    }
                }
            }
        }

        // Fila 1: Colecciones y Series
        item {
            Text(
                text = "Series y Colecciones Locales",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ShowPosterCard(
                        title = simpsonsShow.title,
                        subtitle = "5 Temporadas",
                        backgroundColor = simpsonsShow.bannerColor,
                        onClick = { onShowSelected(simpsonsShow) }
                    )
                }
            }
        }

        // Fila 2: Videos Escaneados del Almacenamiento Local
        item {
            Text(
                text = "Videos en tu Dispositivo (${localVideos.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            if (localVideos.isEmpty()) {
                Text(
                    text = "Toca el ícono de recarga arriba para buscar videos en tu memoria o presiona 'Abrir Archivo'.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(localVideos) { video ->
                        LocalVideoCard(
                            video = video,
                            onPlay = { onVideoPlay(video.uri) },
                            onDelete = { onDeleteLocalVideo(video) }
                        )
                    }
                }
            }
        }
    }
}

// Tarjeta Estilo Poster de Serie
@Composable
fun ShowPosterCard(
    title: String,
    subtitle: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text(
                    text = title,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.DarkGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// Tarjeta de Video Local con Opción de Eliminar de la Lista
@Composable
fun LocalVideoCard(
    video: LocalVideo,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(130.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPlay() }
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFFE50914)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Quitar",
                            tint = Color.Gray
                        )
                    }
                }

                Column {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.duration,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// --- 4. DETALLE DE SERIE Y SELECTOR DE TEMPORADAS ---
@Composable
fun ShowDetailScreen(
    show: MediaItemShow,
    onBack: () -> Unit,
    onPlayEpisode: (Uri) -> Unit
) {
    var selectedSeasonNumber by remember { mutableStateOf(1) }
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPlayEpisode(it) }
    }

    val currentSeason = show.seasons.find { it.number == selectedSeasonNumber } ?: show.seasons.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        // Encabezado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(show.bannerColor)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = show.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = show.description,
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    maxLines = 2
                )
            }
        }

        // Selector de Temporadas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Temporada:",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp)
            )

            ScrollableTabRow(
                selectedTabIndex = selectedSeasonNumber - 1,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFE50914)
            ) {
                show.seasons.forEach { season ->
                    Tab(
                        selected = selectedSeasonNumber == season.number,
                        onClick = { selectedSeasonNumber = season.number },
                        text = {
                            Text(
                                text = "Temp. ${season.number}",
                                color = if (selectedSeasonNumber == season.number) Color(0xFFE50914) else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        }

        // Lista de Episodios
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(currentSeason.episodes) { episode ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Al tocar un episodio abre el selector de archivo para vincularlo o reproducir
                            videoPickerLauncher.launch("video/*")
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 40.dp)
                                .background(Color.DarkGray, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = episode.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = episode.duration,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 5. REPRODUCTOR DE VIDEO FULLSCREEN (EXOPLAYER) ---
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        activity?.let {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val windowInsetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val windowInsetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    keepScreenOn = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    controllerShowTimeoutMs = 3500
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White
            )
        }
    }
}

// Función auxiliar para consultar videos reales del almacenamiento
fun fetchLocalVideos(context: Context): List<LocalVideo> {
    val videos = mutableListOf<LocalVideo>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )

    val query = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )

    query?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val durationMs = cursor.getLong(durationColumn)
            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

            val minutes = (durationMs / 1000) / 60
            val seconds = (durationMs / 1000) % 60
            val durationStr = String.format("%d:%02d min", minutes, seconds)

            videos.add(LocalVideo(id, name, contentUri, durationStr))
        }
    }
    return videos
}
