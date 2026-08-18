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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.io.File

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folderName: String,
    val customImageUri: Uri? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF121212),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
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
    var videosByFolder by remember { mutableStateOf<Map<String, List<VideoFile>>>(emptyMap()) }
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
            videosByFolder = loadLocalVideosGroupedByFolder(context)
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
            NetflixMainLayout(
                groupedVideos = videosByFolder,
                onVideoSelect = { selectedVideoUri = it.uri }
            )
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
    onVideoSelect: (VideoFile) -> Unit
) {
    Scaffold(
        bottomBar = { NetflixBottomNavigation() },
        containerColor = Color.Black
    ) { innerPadding ->
        if (groupedVideos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No se encontraron videos locales.", color = Color.Gray)
            }
        } else {
            val allVideos = groupedVideos.values.flatten()
            val featuredVideo = remember(allVideos) { allVideos.firstOrNull() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Banner Destacado (Hero Item)
                if (featuredVideo != null) {
                    item {
                        NetflixHeroBanner(
                            video = featuredVideo,
                            onPlayClick = { onVideoSelect(featuredVideo) }
                        )
                    }
                }

                // Selector de Categorías Superiores
                item {
                    NetflixCategoryTabs()
                }

                // Filas de Categorías (Carretes)
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
            .height(420.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = video.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Sombreado Degradado estilo Netflix (Top y Bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    )
                )
        )

        // Logo 'N' Superior
        Text(
            text = "N",
            color = Color(0xFFE50914),
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        )

        // Información y Botón de Reproducción
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = video.name,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "• Local • HD • Reproducción Inmediata",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .width(180.dp)
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
fun NetflixCategoryTabs() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Text("Series", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("Películas", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("Categorías ▾", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NetflixFolderRow(
    folderName: String,
    videos: List<VideoFile>,
    onVideoSelect: (VideoFile) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = folderName,
            color = Color.White,
            fontSize = 18.sp,
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
            .width(115.dp)
            .height(165.dp)
            .clip(RoundedCornerShape(4.dp))
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
fun NetflixBottomNavigation() {
    NavigationBar(
        containerColor = Color(0xFF121212),
        contentColor = Color.White
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
            label = { Text("Inicio", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFFE50914)
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Novedades") },
            label = { Text("Próximamente", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            icon = { Icon(Icons.Default.Download, contentDescription = "Descargas") },
            label = { Text("Descargas", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
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

            var customImageUri: Uri? = null
            if (dataColumn != -1) {
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
