package com.example.minetflixlocal

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

// ==========================================
// 1. MODELO DE DATOS
// ==========================================
data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folderName: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateAdded: Long = 0L,
    val filePath: String? = null,
    var customImageUri: Uri? = null,
    val subtitleUri: Uri? = null
)

// ==========================================
// 2. ACTIVIDAD PRINCIPAL
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0D0D0D),
                    surface = Color(0xFF161616),
                    primary = Color(0xFFE50914),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0D0D)
                ) {
                    VideoAppScreen()
                }
            }
        }
    }
}

// ==========================================
// 3. UTILIDADES DE ALMACENAMIENTO Y PREFERENCIAS
// ==========================================
fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences("app_netflix_data", Context.MODE_PRIVATE)
}

fun saveHiddenVideos(context: Context, hiddenIds: Set<Long>) {
    val prefs = getPrefs(context)
    val stringSet = hiddenIds.map { it.toString() }.toSet()
    prefs.edit().putStringSet("hidden_videos", stringSet).apply()
}

fun loadHiddenVideos(context: Context): Set<Long> {
    val prefs = getPrefs(context)
    val stringSet = prefs.getStringSet("hidden_videos", emptySet()) ?: emptySet()
    return stringSet.mapNotNull { it.toLongOrNull() }.toSet()
}

fun saveUserName(context: Context, name: String) {
    getPrefs(context).edit().putString("user_name", name).apply()
}

fun loadUserName(context: Context): String {
    return getPrefs(context).getString("user_name", "Usuario") ?: "Usuario"
}

// Configuración del motor de reproducción (VLC vs ExoPlayer)
fun savePlayerEngine(context: Context, engine: String) {
    getPrefs(context).edit().putString("player_engine", engine).apply()
}

fun loadPlayerEngine(context: Context): String {
    return getPrefs(context).getString("player_engine", "vlc") ?: "vlc"
}

fun saveVideoProgress(context: Context, videoId: Long, positionMs: Long, totalDurationMs: Long) {
    if (positionMs <= 0) return
    val prefs = getPrefs(context)
    prefs.edit()
        .putLong("prog_pos_$videoId", positionMs)
        .putLong("prog_dur_$videoId", totalDurationMs)
        .putLong("prog_time_$videoId", System.currentTimeMillis())
        .apply()
}

fun getVideoProgress(context: Context, videoId: Long): Long {
    return getPrefs(context).getLong("prog_pos_$videoId", 0L)
}

fun getVideoSavedDuration(context: Context, videoId: Long): Long {
    return getPrefs(context).getLong("prog_dur_$videoId", 0L)
}

fun clearVideoProgress(context: Context, videoId: Long) {
    getPrefs(context).edit()
        .remove("prog_pos_$videoId")
        .remove("prog_dur_$videoId")
        .remove("prog_time_$videoId")
        .apply()
}

fun saveImageLocally(context: Context, videoId: Long, sourceUri: Uri): Uri? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
        val file = File(context.filesDir, "custom_poster_$videoId.jpg")
        val outputStream = FileOutputStream(file)

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        val localUri = Uri.fromFile(file)
        getPrefs(context).edit().putString("custom_img_$videoId", localUri.toString()).apply()
        localUri
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun loadCustomImage(context: Context, videoId: Long): String? {
    return getPrefs(context).getString("custom_img_$videoId", null)
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d h %02d m", hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(Locale.getDefault(), "%.0f KB", kb)
    }
}

// ==========================================
// 4. PANTALLA PRINCIPAL
// ==========================================
@Composable
fun VideoAppScreen() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var allVideos by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var hiddenVideoIds by remember { mutableStateOf(loadHiddenVideos(context)) }
    var userName by remember { mutableStateOf(loadUserName(context)) }
    var playerEngine by remember { mutableStateOf(loadPlayerEngine(context)) }
    var selectedVideo by remember { mutableStateOf<VideoFile?>(null) }
    var playFromStart by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("home") }
    var detailVideo by remember { mutableStateOf<VideoFile?>(null) }
    var videoToEditImage by remember { mutableStateOf<VideoFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

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
            allVideos = loadAllLocalVideos(context)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { newImageUri ->
            videoToEditImage?.let { targetVideo ->
                val savedUri = saveImageLocally(context, targetVideo.id, newImageUri)
                if (savedUri != null) {
                    targetVideo.customImageUri = savedUri
                    allVideos = allVideos.map { if (it.id == targetVideo.id) it.copy(customImageUri = savedUri) else it }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionToRequest)
    }

    BackHandler(enabled = selectedVideo != null || detailVideo != null || currentTab != "home" || searchQuery.isNotEmpty()) {
        if (selectedVideo != null) {
            selectedVideo = null
            refreshTrigger++
        } else if (detailVideo != null) {
            detailVideo = null
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (currentTab != "home") {
            currentTab = "home"
        }
    }

    val visibleVideos = remember(allVideos, hiddenVideoIds) {
        allVideos.filter { it.id !in hiddenVideoIds }
    }

    val filteredVideos = remember(visibleVideos, searchQuery) {
        if (searchQuery.isBlank()) {
            visibleVideos
        } else {
            visibleVideos.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.folderName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedVideos = remember(filteredVideos) {
        filteredVideos.groupBy { it.folderName }
    }

    val continueWatchingVideos = remember(visibleVideos, refreshTrigger) {
        visibleVideos.mapNotNull { video ->
            val prog = getVideoProgress(context, video.id)
            val dur = if (video.durationMs > 0) video.durationMs else getVideoSavedDuration(context, video.id)
            if (prog > 5000L && (dur <= 0 || prog < dur * 0.95)) {
                video to (if (dur > 0) prog.toFloat() / dur else 0.5f)
            } else {
                null
            }
        }
    }

    val recentVideos = remember(visibleVideos) {
        visibleVideos.sortedByDescending { it.dateAdded }.take(15)
    }

    if (selectedVideo != null) {
        UnifiedVideoPlayerScreen(
            video = selectedVideo!!,
            startFromBeginning = playFromStart,
            engine = playerEngine,
            onBack = {
                selectedVideo = null
                refreshTrigger++
            }
        )
    } else {
        if (hasPermission) {
            Box(modifier = Modifier.fillMaxSize()) {
                NetflixMainLayout(
                    groupedVideos = groupedVideos,
                    allVideos = filteredVideos,
                    continueWatchingList = continueWatchingVideos,
                    recentVideos = recentVideos,
                    totalAllVideosCount = allVideos.size,
                    hiddenCount = hiddenVideoIds.size,
                    userName = userName,
                    onUserNameChange = { newName ->
                        userName = newName
                        saveUserName(context, newName)
                    },
                    playerEngine = playerEngine,
                    onPlayerEngineChange = { newEngine ->
                        playerEngine = newEngine
                        savePlayerEngine(context, newEngine)
                    },
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onVideoSelect = { video -> detailVideo = video },
                    onRestoreHidden = {
                        hiddenVideoIds = emptySet()
                        saveHiddenVideos(context, emptySet())
                    },
                    onRefreshVideos = {
                        allVideos = loadAllLocalVideos(context)
                    }
                )

                detailVideo?.let { video ->
                    val savedProgress = getVideoProgress(context, video.id)
                    val totalDuration = if (video.durationMs > 0) video.durationMs else getVideoSavedDuration(context, video.id)

                    VideoDetailModal(
                        video = video,
                        savedProgressMs = savedProgress,
                        totalDurationMs = totalDuration,
                        onDismiss = { detailVideo = null },
                        onResumePlay = {
                            playFromStart = false
                            selectedVideo = video
                            detailVideo = null
                        },
                        onPlayFromStart = {
                            playFromStart = true
                            selectedVideo = video
                            detailVideo = null
                        },
                        onChangeImage = {
                            videoToEditImage = video
                            imagePickerLauncher.launch("image/*")
                        },
                        onRemoveVideo = {
                            val newSet = hiddenVideoIds + video.id
                            hiddenVideoIds = newSet
                            saveHiddenVideos(context, newSet)
                            detailVideo = null
                        },
                        onClearProgress = {
                            clearVideoProgress(context, video.id)
                            refreshTrigger++
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Se requiere permiso para acceder a tus videos.",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { launcher.launch(permissionToRequest) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                    ) {
                        Text("Conceder Permiso", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. LAYOUT PRINCIPAL NETFLIX
// ==========================================
@Composable
fun NetflixMainLayout(
    groupedVideos: Map<String, List<VideoFile>>,
    allVideos: List<VideoFile>,
    continueWatchingList: List<Pair<VideoFile, Float>>,
    recentVideos: List<VideoFile>,
    totalAllVideosCount: Int,
    hiddenCount: Int,
    userName: String,
    onUserNameChange: (String) -> Unit,
    playerEngine: String,
    onPlayerEngineChange: (String) -> Unit,
    currentTab: String,
    onTabSelected: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVideoSelect: (VideoFile) -> Unit,
    onRestoreHidden: () -> Unit,
    onRefreshVideos: () -> Unit
) {
    Scaffold(
        bottomBar = {
            CompactBottomNavigation(
                currentTab = currentTab,
                onTabSelected = onTabSelected
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { innerPadding ->
        when (currentTab) {
            "home" -> {
                if (allVideos.isEmpty() && continueWatchingList.isEmpty()) {
                    EmptyStateView(innerPadding, onRefreshVideos)
                } else {
                    val featuredVideo = remember(allVideos) { allVideos.firstOrNull() }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        item {
                            NetflixTopBar(
                                userName = userName,
                                searchQuery = searchQuery,
                                onSearchQueryChange = onSearchQueryChange
                            )
                        }

                        if (searchQuery.isBlank() && featuredVideo != null) {
                            item {
                                NetflixHeroBanner(
                                    video = featuredVideo,
                                    onPlayClick = { onVideoSelect(featuredVideo) }
                                )
                            }
                        }

                        if (continueWatchingList.isNotEmpty() && searchQuery.isBlank()) {
                            item {
                                ContinueWatchingRow(
                                    items = continueWatchingList,
                                    onVideoSelect = onVideoSelect
                                )
                            }
                        }

                        if (recentVideos.isNotEmpty() && searchQuery.isBlank()) {
                            item {
                                NetflixFolderRow(
                                    title = "Añadidos Recientemente",
                                    videos = recentVideos,
                                    onVideoSelect = onVideoSelect
                                )
                            }
                        }

                        items(groupedVideos.keys.toList()) { folderName ->
                            val videosInFolder = groupedVideos[folderName] ?: emptyList()
                            NetflixFolderRow(
                                title = folderName,
                                videos = videosInFolder,
                                onVideoSelect = onVideoSelect
                            )
                        }
                    }
                }
            }
            "grid" -> {
                GridCatalogScreen(
                    allVideos = allVideos,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onVideoSelect = onVideoSelect,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            "settings" -> {
                SettingsScreen(
                    userName = userName,
                    onUserNameChange = onUserNameChange,
                    playerEngine = playerEngine,
                    onPlayerEngineChange = onPlayerEngineChange,
                    allVideosCount = totalAllVideosCount,
                    hiddenCount = hiddenCount,
                    onRestoreHidden = onRestoreHidden,
                    onRefreshVideos = onRefreshVideos,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun NetflixTopBar(
    userName: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "N",
                    color = Color(0xFFE50914),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Para $userName",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar video o carpeta...", color = Color.Gray, fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.LightGray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFE50914),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun EmptyStateView(padding: PaddingValues, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No se encontraron videos.", color = Color.Gray, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRefresh,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE50914))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Escanear Nuevamente")
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(
    items: List<Pair<VideoFile, Float>>,
    onVideoSelect: (VideoFile) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = "Continuar Viendo",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { (video, progressFraction) ->
                NetflixPosterCard(
                    video = video,
                    progressFraction = progressFraction,
                    onClick = { onVideoSelect(video) }
                )
            }
        }
    }
}

@Composable
fun NetflixFolderRow(
    title: String,
    videos: List<VideoFile>,
    onVideoSelect: (VideoFile) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                NetflixPosterCard(
                    video = video,
                    onClick = { onVideoSelect(video) }
                )
            }
        }
    }
}

@Composable
fun NetflixPosterCard(
    video: VideoFile,
    progressFraction: Float? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = video.customImageUri ?: video.uri

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageModel)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(2000)
            .crossfade(true)
            .build()
    )

    Column(
        modifier = Modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(115.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(BorderStroke(1.dp, Color(0xFF262626)), RoundedCornerShape(6.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            Image(
                painter = painter,
                contentDescription = video.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (video.durationMs > 0) {
                Text(
                    text = formatDuration(video.durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (video.subtitleUri != null) {
                Text(
                    text = "CC",
                    color = Color.Yellow,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }

            if (progressFraction != null && progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFFE50914))
                    )
                }
            }
        }

        Text(
            text = video.name,
            color = Color(0xFFE0E0E0),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
        )
    }
}

@Composable
fun GridCatalogScreen(
    allVideos: List<VideoFile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVideoSelect: (VideoFile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Buscar en todo el catálogo...", color = Color.Gray, fontSize = 14.sp) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE50914),
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF161616),
                unfocusedContainerColor = Color(0xFF161616)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Catálogo Completo",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${allVideos.size} videos",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(allVideos) { video ->
                NetflixPosterCard(video = video, onClick = { onVideoSelect(video) })
            }
        }
    }
}

@Composable
fun NetflixHeroBanner(
    video: VideoFile,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = video.customImageUri ?: video.uri
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageModel)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(2500)
            .crossfade(true)
            .build()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = video.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color(0xFF0D0D0D).copy(alpha = 0.85f),
                            Color(0xFF0D0D0D)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = video.name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (video.durationMs > 0) {
                Text(
                    text = "Duración: ${formatDuration(video.durationMs)} • ${video.folderName}",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .width(170.dp)
                    .height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VideoDetailModal(
    video: VideoFile,
    savedProgressMs: Long,
    totalDurationMs: Long,
    onDismiss: () -> Unit,
    onResumePlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onChangeImage: () -> Unit,
    onRemoveVideo: () -> Unit,
    onClearProgress: () -> Unit
) {
    val hasProgress = savedProgressMs > 5000L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = video.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = "Carpeta: ${video.folderName}",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                if (video.durationMs > 0) {
                    Text(
                        text = "Duración: ${formatDuration(video.durationMs)}",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }

                if (video.sizeBytes > 0) {
                    Text(
                        text = "Tamaño: ${formatFileSize(video.sizeBytes)}",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }

                if (video.subtitleUri != null) {
                    Text(
                        text = "Subtítulos locales detectados (.srt)",
                        color = Color(0xFF81C784),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (hasProgress) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Progreso guardado: ${formatDuration(savedProgressMs)}",
                        color = Color(0xFFE50914),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (hasProgress) {
                    Button(
                        onClick = onResumePlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Continuar (${formatDuration(savedProgressMs)})", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onPlayFromStart,
                        border = BorderStroke(1.dp, Color(0xFF555555)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Empezar desde el Inicio", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onResumePlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reproducir Ahora", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onChangeImage,
                    border = BorderStroke(1.dp, Color(0xFF444444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cambiar Miniatura", color = Color.White)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    if (hasProgress) {
                        TextButton(
                            onClick = onClearProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Borrar Progreso", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = onRemoveVideo,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ocultar", color = Color(0xFFFF4D4D), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

// ==========================================
// 8. PANTALLA DE AJUSTES (CON SELECTOR DE MOTOR)
// ==========================================
@Composable
fun SettingsScreen(
    userName: String,
    onUserNameChange: (String) -> Unit,
    playerEngine: String,
    onPlayerEngineChange: (String) -> Unit,
    allVideosCount: Int,
    hiddenCount: Int,
    onRestoreHidden: () -> Unit,
    onRefreshVideos: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember { mutableStateOf(userName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Ajustes de la Aplicación",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Card de Perfil
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Perfil de Usuario", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = textState,
                    onValueChange = {
                        textState = it
                        onUserNameChange(it)
                    },
                    label = { Text("Nombre en pantalla", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE50914),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Card de Motor de Reproducción
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Motor de Reproducción", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Elige el decodificador de video para reproducir tus archivos:",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Opción 1: LibVLC
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (playerEngine == "vlc") Color(0xFF262626) else Color.Transparent)
                        .clickable { onPlayerEngineChange("vlc") }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (playerEngine == "vlc"),
                        onClick = { onPlayerEngineChange("vlc") },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE50914), unselectedColor = Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("LibVLC (Recomendado)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Compatible con MKV, Dolby AC3, DTS y subtítulos avanzados.", color = Color.LightGray, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Opción 2: ExoPlayer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (playerEngine == "exoplayer") Color(0xFF262626) else Color.Transparent)
                        .clickable { onPlayerEngineChange("exoplayer") }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (playerEngine == "exoplayer"),
                        onClick = { onPlayerEngineChange("exoplayer") },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE50914), unselectedColor = Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Google ExoPlayer / Media3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Motor nativo de Android para videos estándar MP4/WebM.", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
        }

        // Card de Almacenamiento
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Almacenamiento y Biblioteca", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Total de videos detectados: $allVideosCount", color = Color.LightGray, fontSize = 13.sp)
                Text("• Videos ocultos del catálogo: $hiddenCount", color = Color.LightGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefreshVideos,
                        border = BorderStroke(1.dp, Color(0xFF444444)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reescanear", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRestoreHidden,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        enabled = hiddenCount > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Restablecer ($hiddenCount)", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Mi Netflix Local v2.5 • Dual Engine (LibVLC + ExoPlayer)",
            color = Color.DarkGray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// ==========================================
// 9. NAVEGACIÓN INFERIOR COMPACTA
// ==========================================
@Composable
fun CompactBottomNavigation(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        color = Color(0xFF121212),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onTabSelected("home") }) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Inicio",
                    tint = if (currentTab == "home") Color(0xFFE50914) else Color.Gray
                )
            }

            IconButton(onClick = { onTabSelected("grid") }) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Catálogo",
                    tint = if (currentTab == "grid") Color(0xFFE50914) else Color.Gray
                )
            }

            IconButton(onClick = { onTabSelected("settings") }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    tint = if (currentTab == "settings") Color(0xFFE50914) else Color.Gray
                )
            }
        }
    }
}

// ==========================================
// 10. REPRODUCTOR UNIFICADO (VLC O EXOPLAYER)
// ==========================================
@Composable
fun UnifiedVideoPlayerScreen(
    video: VideoFile,
    startFromBeginning: Boolean,
    engine: String,
    onBack: () -> Unit
) {
    if (engine == "vlc") {
        VlcVideoPlayerView(
            video = video,
            startFromBeginning = startFromBeginning,
            onBack = onBack
        )
    } else {
        ExoPlayerVideoPlayerView(
            video = video,
            startFromBeginning = startFromBeginning,
            onBack = onBack
        )
    }
}

// ------------------------------------------
// 10.1 MOTOR LIBVLC (VLC PLAYER OFICIAL)
// ------------------------------------------
@Composable
fun VlcVideoPlayerView(
    video: VideoFile,
    startFromBeginning: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isFillAspect by remember { mutableStateOf(false) }
    var seekNotice by remember { mutableStateOf<String?>(null) }

    val libVLC = remember {
        val options = ArrayList<String>().apply {
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("-vvv")
        }
        LibVLC(context, options)
    }

    val mediaPlayer = remember {
        MediaPlayer(libVLC).apply {
            val media = Media(libVLC, video.uri).apply {
                setHWDecoderEnabled(true, false)
            }
            this.media = media
            media.release()

            // Subtítulos si existen
            video.subtitleUri?.let { subUri ->
                addSlave(Media.Slave.Type.Subtitle, subUri, true)
            }

            play()

            if (!startFromBeginning) {
                val savedPos = getVideoProgress(context, video.id)
                if (savedPos > 0) {
                    time = savedPos
                }
            }
        }
    }

    // Guardado continuo de progreso cada 3 segundos
    LaunchedEffect(mediaPlayer) {
        while (true) {
            delay(3000)
            if (mediaPlayer.isPlaying) {
                val currentPos = mediaPlayer.time
                val totalDur = mediaPlayer.length
                if (currentPos > 0 && totalDur > 0) {
                    saveVideoProgress(context, video.id, currentPos, totalDur)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentPos = mediaPlayer.time
            val totalDur = mediaPlayer.length
            if (currentPos > 0) {
                saveVideoProgress(context, video.id, currentPos, if (totalDur > 0) totalDur else video.durationMs)
            }
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                val newPos = (mediaPlayer.time - 10000).coerceAtLeast(0)
                                mediaPlayer.time = newPos
                                seekNotice = "⏪ -10s"
                            } else {
                                val newPos = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
                                mediaPlayer.time = newPos
                                seekNotice = "⏩ +10s"
                            }
                        }
                    )
                }
        )

        LaunchedEffect(seekNotice) {
            if (seekNotice != null) {
                delay(800)
                seekNotice = null
            }
        }

        AnimatedVisibility(
            visible = seekNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = seekNotice ?: "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Barra Superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }

            Text(
                text = video.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle Aspecto
                IconButton(
                    onClick = {
                        isFillAspect = !isFillAspect
                        mediaPlayer.aspectRatio = if (isFillAspect) "16:9" else null
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                ) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Aspecto", tint = if (isFillAspect) Color(0xFFE50914) else Color.White)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Selector Velocidad
                Box {
                    IconButton(
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    ) {
                        Text(
                            text = "${currentSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${speed}x",
                                        color = if (currentSpeed == speed) Color(0xFFE50914) else Color.White,
                                        fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    currentSpeed = speed
                                    mediaPlayer.rate = speed
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Botón reproductor externo
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(video.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Abrir con reproductor externo"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC222222)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("VLC / Externo", color = Color.White, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------
// 10.2 MOTOR EXOPLAYER (MEDIA3)
// ------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerVideoPlayerView(
    video: VideoFile,
    startFromBeginning: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var seekNotice by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableAudioTrackPlaybackParams(true)
        }

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val mediaItemBuilder = MediaItem.Builder().setUri(video.uri)

            video.subtitleUri?.let { subUri ->
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("es")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }

            setMediaItem(mediaItemBuilder.build())
            prepare()
            playWhenReady = true

            if (!startFromBeginning) {
                val savedPos = getVideoProgress(context, video.id)
                if (savedPos > 0) {
                    seekTo(savedPos)
                }
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(3000)
            if (exoPlayer.isPlaying) {
                val currentPos = exoPlayer.currentPosition
                val totalDur = exoPlayer.duration
                if (currentPos > 0 && totalDur > 0) {
                    saveVideoProgress(context, video.id, currentPos, totalDur)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentPos = exoPlayer.currentPosition
            val totalDur = exoPlayer.duration
            if (currentPos > 0) {
                saveVideoProgress(context, video.id, currentPos, if (totalDur > 0) totalDur else video.durationMs)
            }
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
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                exoPlayer.seekTo(newPos)
                                seekNotice = "⏪ -10s"
                            } else {
                                val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                seekNotice = "⏩ +10s"
                            }
                        }
                    )
                }
        )

        LaunchedEffect(seekNotice) {
            if (seekNotice != null) {
                delay(800)
                seekNotice = null
            }
        }

        AnimatedVisibility(
            visible = seekNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = seekNotice ?: "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }

            Text(
                text = video.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                ) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Aspecto", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    IconButton(
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    ) {
                        Text(
                            text = "${currentSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${speed}x",
                                        color = if (currentSpeed == speed) Color(0xFFE50914) else Color.White,
                                        fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    currentSpeed = speed
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(video.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Abrir con reproductor externo"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC222222)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("VLC / Externo", color = Color.White, fontSize = 12.sp)
        }
    }
}

// ==========================================
// 11. ESCANEO LOCAL DE VIDEOS Y METADATOS
// ==========================================
fun loadAllLocalVideos(context: Context): List<VideoFile> {
    val videos = mutableListOf<VideoFile>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED
    )

    try {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawName = cursor.getString(nameColumn) ?: "Sin título"
                val folderName = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: "General" else "General"
                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val savedImageUriString = loadCustomImage(context, id)
                var customImageUri: Uri? = savedImageUriString?.let { Uri.parse(it) }
                var subtitleUri: Uri? = null
                var filePath: String? = null

                if (dataColumn != -1) {
                    filePath = cursor.getString(dataColumn)
                    if (filePath != null) {
                        val videoFile = File(filePath)
                        val parentDir = videoFile.parentFile
                        val baseName = videoFile.nameWithoutExtension

                        if (parentDir != null && parentDir.exists()) {
                            if (customImageUri == null) {
                                val jpgImage = File(parentDir, "$baseName.jpg")
                                val jpegImage = File(parentDir, "$baseName.jpeg")
                                val pngImage = File(parentDir, "$baseName.png")

                                when {
                                    jpgImage.exists() -> customImageUri = Uri.fromFile(jpgImage)
                                    jpegImage.exists() -> customImageUri = Uri.fromFile(jpegImage)
                                    pngImage.exists() -> customImageUri = Uri.fromFile(pngImage)
                                }
                            }

                            val srtFile = File(parentDir, "$baseName.srt")
                            val vttFile = File(parentDir, "$baseName.vtt")
                            when {
                                srtFile.exists() -> subtitleUri = Uri.fromFile(srtFile)
                                vttFile.exists() -> subtitleUri = Uri.fromFile(vttFile)
                            }
                        }
                    }
                }

                val displayName = rawName.substringBeforeLast(".")

                videos.add(
                    VideoFile(
                        id = id,
                        uri = contentUri,
                        name = displayName,
                        folderName = folderName,
                        durationMs = duration,
                        sizeBytes = size,
                        dateAdded = dateAdded,
                        filePath = filePath,
                        customImageUri = customImageUri,
                        subtitleUri = subtitleUri
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return videos
}
