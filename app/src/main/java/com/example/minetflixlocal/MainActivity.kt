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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                    background = Color(0xFF141414),
                    surface = Color(0xFF1F1F1F),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
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
            NetflixHomeScreen(
                groupedVideos = videosByFolder,
                onVideoSelect = { selectedVideoUri = it.uri }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Se requiere permiso para acceder a tus videos.",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun NetflixHomeScreen(
    groupedVideos: Map<String, List<VideoFile>>,
    onVideoSelect: (VideoFile) -> Unit
) {
    if (groupedVideos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No se encontraron videos en el dispositivo.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    text = "Mi Netflix Local",
                    color = Color(0xFFE50914), // Rojo Netflix
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            items(groupedVideos.keys.toList()) { folderName ->
                val videosInFolder = groupedVideos[folderName] ?: emptyList()
                FolderSection(
                    folderName = folderName,
                    videos = videosInFolder,
                    onVideoSelect = onVideoSelect
                )
            }
        }
    }
}

@Composable
fun FolderSection(
    folderName: String,
    videos: List<VideoFile>,
    onVideoSelect: (VideoFile) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = folderName,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(videos) { video ->
                VideoPosterCard(video = video, onClick = { onVideoSelect(video) })
            }
        }
    }
}

@Composable
fun VideoPosterCard(
    video: VideoFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Si hay una imagen personalizada (.jpg/.png con el mismo nombre) usa esa, si no extrae el frame del video
    val imageModel = video.customImageUri ?: video.uri

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageModel)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(1000)
            .crossfade(true)
            .build()
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B)),
            modifier = Modifier
                .width(130.dp)
                .height(180.dp)
        ) {
            Image(
                painter = painter,
                contentDescription = video.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = video.name,
            color = Color.LightGray,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
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

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Text("← Volver", color = Color.White)
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

            // Buscar si existe una imagen con el mismo nombre en la carpeta
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

            // Nombre formateado para la UI (remueve extensión como .mp4)
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
