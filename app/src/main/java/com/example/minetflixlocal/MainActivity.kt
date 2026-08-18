package com.example.minetflixlocal

import android.Manifest
import android.content.ContentUris
import android.content.Context
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folderName: String,
    var customImageUri: Uri? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0D0D0D),
                    surface = Color(0xFF161616),
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

// Control de datos con SharedPreferences
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

// Guarda una copia local permanente de la imagen
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

@Composable
fun VideoAppScreen() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var videosByFolder by remember { mutableStateOf<Map<String, List<VideoFile>>>(emptyMap()) }
    var hiddenVideoIds by remember { mutableStateOf(loadHiddenVideos(context)) }
    var userName by remember { mutableStateOf(loadUserName(context)) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var currentTab by remember { mutableStateOf("home") }
    var detailVideo by remember { mutableStateOf<VideoFile?>(null) }
    var videoToEditImage by remember { mutableStateOf<VideoFile?>(null) }

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
            videosByFolder = loadLocalVideosGroupedByFolder(context)
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
                    videosByFolder = videosByFolder.toMap()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionToRequest)
    }

    BackHandler(enabled = selectedVideoUri != null || detailVideo != null || currentTab != "home") {
        if (selectedVideoUri != null) {
            selectedVideoUri = null
        } else if (detailVideo != null) {
            detailVideo = null
        } else if (currentTab != "home") {
            currentTab = "home"
        }
    }

    val visibleGroupedVideos = remember(videosByFolder, hiddenVideoIds) {
        videosByFolder.mapValues { entry ->
            entry.value.filter { it.id !in hiddenVideoIds }
        }.filterValues { it.isNotEmpty() }
    }

    if (selectedVideoUri != null) {
        VideoPlayerScreen(
            videoUri = selectedVideoUri!!,
            onBack = { selectedVideoUri = null }
        )
    } else {
        if (hasPermission) {
            Box(modifier = Modifier.fillMaxSize()) {
                NetflixMainLayout(
                    groupedVideos = visibleGroupedVideos,
                    allVideosCount = videosByFolder.values.flatten().size,
                    hiddenCount = hiddenVideoIds.size,
                    userName = userName,
                    onUserNameChange = { newName ->
                        userName = newName
                        saveUserName(context, newName)
                    },
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    onVideoSelect = { video -> detailVideo = video },
                    onRestoreHidden = {
                        hiddenVideoIds = emptySet()
                        saveHiddenVideos(context, emptySet())
                    }
                )

                detailVideo?.let { video ->
                    VideoDetailModal(
                        video = video,
                        onDismiss = { detailVideo = null },
                        onPlay = {
                            selectedVideoUri = video.uri
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
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Se requiere permiso de acceso a videos.", color = Color.White)
            }
        }
    }
}

@Composable
fun NetflixMainLayout(
    groupedVideos: Map<String, List<VideoFile>>,
    allVideosCount: Int,
    hiddenCount: Int,
    userName: String,
    onUserNameChange: (String) -> Unit,
    currentTab: String,
    onTabSelected: (String) -> Unit,
    onVideoSelect: (VideoFile) -> Unit,
    onRestoreHidden: () -> Unit
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
                if (groupedVideos.isEmpty()) {
                    EmptyStateView(innerPadding)
                } else {
                    val allVideos = groupedVideos.values.flatten()
                    val featuredVideo = remember(allVideos) { allVideos.firstOrNull() }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        item {
                            UserGreetingHeader(userName = userName)
                        }

                        if (featuredVideo != null) {
                            item {
                                NetflixHeroBanner(
                                    video = featuredVideo,
                                    onPlayClick = { onVideoSelect(featuredVideo) }
                                )
                            }
                        }

                        items(groupedVideos.keys.toList()) { folderName ->
                            val videosInFolder = groupedVideos[folderName] ?: emptyList()
                            NetflixFolderRow(
                                folderName = folderName,
                                videos = videosInFolder,
                                onVideoSelect = onVideoSelect
                            )
                        }
                    }
                }
            }
            "grid" -> {
                GridCatalogScreen(
                    allVideos = groupedVideos.values.flatten(),
                    onVideoSelect = onVideoSelect,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            "settings" -> {
                SettingsScreen(
                    userName = userName,
                    onUserNameChange = onUserNameChange,
                    allVideosCount = allVideosCount,
                    hiddenCount = hiddenCount,
                    onRestoreHidden = onRestoreHidden,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun UserGreetingHeader(userName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFE50914)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Para $userName",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyStateView(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text("No hay videos disponibles en la biblioteca.", color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
fun GridCatalogScreen(
    allVideos: List<VideoFile>,
    onVideoSelect: (VideoFile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "Películas y Series",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(allVideos) { video ->
                NetflixPosterCard(video = video, onClick = { onVideoSelect(video) })
            }
        }
    }
}

@Composable
fun SettingsScreen(
    userName: String,
    onUserNameChange: (String) -> Unit,
    allVideosCount: Int,
    hiddenCount: Int,
    onRestoreHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember { mutableStateOf(userName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Ajustes de Perfil",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Perfil del Usuario", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = textState,
                    onValueChange = { 
                        textState = it
                        onUserNameChange(it)
                    },
                    label = { Text("Nombre de Perfil", color = Color.Gray) },
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

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Resumen del Almacenamiento", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Total de videos detectados: $allVideosCount", color = Color.LightGray, fontSize = 13.sp)
                Text("• Videos ocultos del catálogo: $hiddenCount", color = Color.LightGray, fontSize = 13.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gestión de Contenido", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Restaura todos los videos eliminados previamente.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                
                Button(
                    onClick = onRestoreHidden,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    enabled = hiddenCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restablecer Videos Ocultos ($hiddenCount)", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Mi Netflix Local v1.4 • Guardado Permanente",
            color = Color.DarkGray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun NetflixFolderRow(
    folderName: String,
    videos: List<VideoFile>,
    onVideoSelect: (VideoFile) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = folderName,
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
                NetflixPosterCard(video = video, onClick = { onVideoSelect(video) })
            }
        }
    }
}

@Composable
fun NetflixPosterCard(
    video: VideoFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = video.customImageUri ?: video.uri

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageModel)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(1000)
            .crossfade(true)
            .build()
    )

    Box(
        modifier = Modifier
            .width(110.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, Color(0xFFE50914)), RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Image(
            painter = painter,
            contentDescription = video.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun VideoDetailModal(
    video: VideoFile,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onChangeImage: () -> Unit,
    onRemoveVideo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = video.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = "Carpeta: ${video.folderName}",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reproducir Ahora", color = Color.White, fontWeight = FontWeight.Bold)
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

                TextButton(
                    onClick = onRemoveVideo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF4D4D))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Quitar del Catálogo", color = Color(0xFFFF4D4D))
                }
            }
        },
        confirmButton = {}
    )
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
            .videoFrameMillis(2000)
            .crossfade(true)
            .build()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
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
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color(0xFF0D0D0D).copy(alpha = 0.8f),
                            Color(0xFF0D0D0D)
                        )
                    )
                )
        )

        Text(
            text = "N",
            color = Color(0xFFE50914),
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .width(160.dp)
                    .height(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
                    contentDescription = "Películas y Series",
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

import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val libVLC = remember {
        val options = arrayListOf(
            "--no-time-stretch",
            "--avcodec-hw=any"
        )
        LibVLC(context, options)
    }

    val mediaPlayer = remember { MediaPlayer(libVLC) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                    val media = Media(libVLC, videoUri)
                    mediaPlayer.media = media
                    media.release()
                    mediaPlayer.play()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White
            )
        }
    }
}

fun loadLocalVideosGroupedByFolder(context: Context): Map<String, List<VideoFile>> {
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
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
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
        val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
        val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val rawName = cursor.getString(nameColumn) ?: "Sin título"
            val folderName = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: "General" else "General"
            
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )

            val savedImageUriString = loadCustomImage(context, id)
            var customImageUri: Uri? = savedImageUriString?.let { Uri.parse(it) }

            if (customImageUri == null && dataColumn != -1) {
                val filePath = cursor.getString(dataColumn)
                if (filePath != null) {
                    val videoFile = File(filePath)
                    val parentDir = videoFile.parentFile
                    val baseName = videoFile.nameWithoutExtension

                    if (parentDir != null && parentDir.exists()) {
                        val jpgImage = File(parentDir, "$baseName.jpg")
                        val pngImage = File(parentDir, "$baseName.png")

                        if (jpgImage.exists()) {
                            customImageUri = Uri.fromFile(jpgImage)
                        } else if (pngImage.exists()) {
                            customImageUri = Uri.fromFile(pngImage)
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
                    customImageUri = customImageUri
                )
            )
        }
    }

    return videos.groupBy { it.folderName }
}
