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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

// ==========================================
// 1. MODELOS Y ESTADOS
// ==========================================

enum class AppState { SPLASH, PROFILES, MAIN }

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

data class UserProfile(
    val id: Int,
    val name: String,
    val colorIndex: Int
)

val profileColors = listOf(
    Color(0xFFE50914), // Rojo Netflix
    Color(0xFF0071EB), // Azul
    Color(0xFF54B135), // Verde
    Color(0xFFE87C03), // Naranja
    Color(0xFF8C54FB)  // Violeta
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
                    background = Color(0xFF080808),
                    surface = Color(0xFF141414),
                    primary = Color(0xFFE50914),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF080808)
                ) {
                    AppEntryPoint()
                }
            }
        }
    }
}

// ==========================================
// 3. PREFERENCIAS Y UTILIDADES
// ==========================================
fun getPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences("app_netflix_data", Context.MODE_PRIVATE)

fun saveHiddenVideos(context: Context, hiddenIds: Set<Long>) {
    getPrefs(context).edit()
        .putStringSet("hidden_videos", hiddenIds.map { it.toString() }.toSet())
        .apply()
}

fun loadHiddenVideos(context: Context): Set<Long> =
    (getPrefs(context).getStringSet("hidden_videos", emptySet()) ?: emptySet())
        .mapNotNull { it.toLongOrNull() }.toSet()

fun savePlayerEngine(context: Context, engine: String) =
    getPrefs(context).edit().putString("player_engine", engine).apply()

fun loadPlayerEngine(context: Context): String =
    getPrefs(context).getString("player_engine", "vlc") ?: "vlc"

fun saveProfile(context: Context, profile: UserProfile) {
    getPrefs(context).edit()
        .putString("profile_name_${profile.id}", profile.name)
        .putInt("profile_color_${profile.id}", profile.colorIndex)
        .putBoolean("profile_exists_${profile.id}", true)
        .apply()
}

fun loadProfiles(context: Context): List<UserProfile> {
    val prefs = getPrefs(context)
    val profiles = mutableListOf<UserProfile>()
    if (!prefs.getBoolean("profile_exists_0", false)) {
        saveProfile(context, UserProfile(0, "Usuario", 0))
    }
    for (i in 0..2) {
        if (prefs.getBoolean("profile_exists_$i", false)) {
            profiles.add(
                UserProfile(
                    id = i,
                    name = prefs.getString("profile_name_$i", "Perfil ${i + 1}") ?: "Perfil ${i + 1}",
                    colorIndex = prefs.getInt("profile_color_$i", i % profileColors.size)
                )
            )
        }
    }
    return profiles
}

fun deleteProfile(context: Context, profileId: Int) {
    getPrefs(context).edit()
        .remove("profile_name_$profileId")
        .remove("profile_color_$profileId")
        .putBoolean("profile_exists_$profileId", false)
        .apply()
}

fun saveVideoProgress(context: Context, videoId: Long, positionMs: Long, totalDurationMs: Long) {
    if (positionMs <= 0) return
    getPrefs(context).edit()
        .putLong("prog_pos_$videoId", positionMs)
        .putLong("prog_dur_$videoId", totalDurationMs)
        .apply()
}

fun getVideoProgress(context: Context, videoId: Long): Long =
    getPrefs(context).getLong("prog_pos_$videoId", 0L)

fun getVideoSavedDuration(context: Context, videoId: Long): Long =
    getPrefs(context).getLong("prog_dur_$videoId", 0L)

fun clearVideoProgress(context: Context, videoId: Long) {
    getPrefs(context).edit()
        .remove("prog_pos_$videoId")
        .remove("prog_dur_$videoId")
        .apply()
}

fun saveImageLocally(context: Context, videoId: Long, sourceUri: Uri): Uri? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
        val file = File(context.filesDir, "custom_poster_$videoId.jpg")
        inputStream?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        val localUri = Uri.fromFile(file)
        getPrefs(context).edit().putString("custom_img_$videoId", localUri.toString()).apply()
        localUri
    } catch (e: Exception) { null }
}

fun loadCustomImage(context: Context, videoId: Long): String? =
    getPrefs(context).getString("custom_img_$videoId", null)

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val s = durationMs / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d h %02d m", h, m)
    else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1024.0 / 1024.0
    val gb = mb / 1024.0
    return if (gb >= 1.0) String.format(Locale.getDefault(), "%.1f GB", gb)
    else String.format(Locale.getDefault(), "%.1f MB", mb)
}

// ==========================================
// 4. LOGO ESTILO NETFLIX CON LA LETRA 'M'
// ==========================================
@Composable
fun NetflixLogoM(
    sizeSp: Int = 36,
    modifier: Modifier = Modifier
) {
    Text(
        text = "M",
        color = Color(0xFFE50914),
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
    )
}

// ==========================================
// 5. PUNTO DE ENTRADA (SPLASH -> PERFILES -> MAIN)
// ==========================================
@Composable
fun AppEntryPoint() {
    val context = LocalContext.current
    var appState by remember { mutableStateOf(AppState.SPLASH) }
    var activeProfile by remember { mutableStateOf<UserProfile?>(null) }

    when (appState) {
        AppState.SPLASH -> SplashScreen(
            onFinished = { appState = AppState.PROFILES }
        )
        AppState.PROFILES -> ProfileSelectionScreen(
            context = context,
            onProfileSelected = { profile ->
                activeProfile = profile
                appState = AppState.MAIN
            }
        )
        AppState.MAIN -> VideoAppScreen(
            activeProfile = activeProfile ?: UserProfile(0, "Usuario", 0),
            onChangeProfile = { appState = AppState.PROFILES }
        )
    }
}

// ==========================================
// 6. SPLASH SCREEN (INTRO ESTILO NETFLIX CON "M")
// ==========================================
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
        delay(700)
        showSubtitle = true
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // El logo "M" estilo Netflix animado
            Text(
                text = "M",
                color = Color(0xFFE50914),
                fontSize = 130.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha)
            )

            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(tween(400)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(400)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mi Netflix Local",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tu cine personal",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 7. PANTALLA DE SELECCIÓN DE PERFIL (SIEMPRE AL ABRIR)
// ==========================================
@Composable
fun ProfileSelectionScreen(
    context: Context,
    onProfileSelected: (UserProfile) -> Unit
) {
    var profiles by remember { mutableStateOf(loadProfiles(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<UserProfile?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingColorIndex by remember { mutableStateOf(0) }
    var managingMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080808), Color(0xFF141414), Color(0xFF080808))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            NetflixLogoM(sizeSp = 48)

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "¿Quién está viendo ahora?",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Grid de perfiles
            val cols = if (profiles.size < 3) (profiles.size + 1).coerceAtMost(3) else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(profiles) { profile ->
                    ProfileAvatarItem(
                        profile = profile,
                        managingMode = managingMode,
                        onClick = {
                            if (managingMode) {
                                showEditDialog = profile
                                editingName = profile.name
                                editingColorIndex = profile.colorIndex
                            } else {
                                onProfileSelected(profile)
                            }
                        },
                        onDelete = {
                            if (profiles.size > 1) {
                                deleteProfile(context, profile.id)
                                profiles = loadProfiles(context)
                            }
                        }
                    )
                }

                if (profiles.size < 3) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showAddDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(2.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1A1A1A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Agregar",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Agregar", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = { managingMode = !managingMode },
                modifier = Modifier
                    .border(1.dp, if (managingMode) Color(0xFFE50914) else Color(0xFF555555), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = if (managingMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (managingMode) Color(0xFFE50914) else Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (managingMode) "Listo" else "Administrar perfiles",
                    color = if (managingMode) Color(0xFFE50914) else Color.LightGray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Modal para agregar perfil
        if (showAddDialog) {
            var newName by remember { mutableStateOf("") }
            var newColor by remember { mutableStateOf(profiles.size % profileColors.size) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(16.dp),
                title = { Text("Nuevo perfil", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(profileColors[newColor]),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (newName.isNotEmpty()) newName.first().toString().uppercase() else "?",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nombre", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFE50914),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Color de avatar:", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            profileColors.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            3.dp,
                                            if (newColor == index) Color.White else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { newColor = index }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                val newId = (0..2).first { id ->
                                    !getPrefs(context).getBoolean("profile_exists_$id", false)
                                }
                                saveProfile(context, UserProfile(newId, newName.trim(), newColor))
                                profiles = loadProfiles(context)
                                showAddDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                    ) { Text("Crear", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }

        // Modal para editar perfil
        showEditDialog?.let { profile ->
            AlertDialog(
                onDismissRequest = { showEditDialog = null },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(16.dp),
                title = { Text("Editar perfil", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(profileColors[editingColorIndex]),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (editingName.isNotEmpty()) editingName.first().toString().uppercase() else "?",
                                color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = { editingName = it },
                            label = { Text("Nombre", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFE50914),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Color:", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            profileColors.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(3.dp, if (editingColorIndex == index) Color.White else Color.Transparent, CircleShape)
                                        .clickable { editingColorIndex = index }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editingName.isNotBlank()) {
                                saveProfile(context, UserProfile(profile.id, editingName.trim(), editingColorIndex))
                                profiles = loadProfiles(context)
                                showEditDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                    ) { Text("Guardar", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = null }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileAvatarItem(
    profile: UserProfile,
    managingMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "profileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(profileColors[profile.colorIndex % profileColors.size]),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.first().toString().uppercase(),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            }
            if (managingMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profile.name,
            color = if (managingMode) Color(0xFFE50914) else Color.LightGray,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================
// 8. PANTALLA PRINCIPAL DE VIDEOS
// ==========================================
@Composable
fun VideoAppScreen(
    activeProfile: UserProfile,
    onChangeProfile: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var allVideos by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var hiddenVideoIds by remember { mutableStateOf(loadHiddenVideos(context)) }
    var playerEngine by remember { mutableStateOf(loadPlayerEngine(context)) }
    var selectedVideo by remember { mutableStateOf<VideoFile?>(null) }
    var selectedVideoSeries by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var playFromStart by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("home") }
    var detailVideo by remember { mutableStateOf<VideoFile?>(null) }
    var videoToEditImage by remember { mutableStateOf<VideoFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) allVideos = loadAllLocalVideos(context)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { newUri ->
            videoToEditImage?.let { target ->
                val saved = saveImageLocally(context, target.id, newUri)
                if (saved != null) {
                    allVideos = allVideos.map { if (it.id == target.id) it.copy(customImageUri = saved) else it }
                }
            }
        }
    }

    LaunchedEffect(Unit) { launcher.launch(permissionToRequest) }

    BackHandler(enabled = selectedVideo != null || detailVideo != null || currentTab != "home" || searchQuery.isNotEmpty()) {
        when {
            selectedVideo != null -> { selectedVideo = null; refreshTrigger++ }
            detailVideo != null -> detailVideo = null
            searchQuery.isNotEmpty() -> searchQuery = ""
            currentTab != "home" -> currentTab = "home"
        }
    }

    val visibleVideos = remember(allVideos, hiddenVideoIds) {
        allVideos.filter { it.id !in hiddenVideoIds }
    }
    val filteredVideos = remember(visibleVideos, searchQuery) {
        if (searchQuery.isBlank()) visibleVideos
        else visibleVideos.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.folderName.contains(searchQuery, ignoreCase = true)
        }
    }
    val groupedVideos = remember(filteredVideos) { filteredVideos.groupBy { it.folderName } }
    val continueWatchingVideos = remember(visibleVideos, refreshTrigger) {
        visibleVideos.mapNotNull { video ->
            val prog = getVideoProgress(context, video.id)
            val dur = if (video.durationMs > 0) video.durationMs else getVideoSavedDuration(context, video.id)
            if (prog > 5000L && (dur <= 0 || prog < dur * 0.95)) video to (if (dur > 0) prog.toFloat() / dur else 0.5f) else null
        }
    }
    val recentVideos = remember(visibleVideos) { visibleVideos.sortedByDescending { it.dateAdded }.take(15) }

    if (selectedVideo != null) {
        UnifiedVideoPlayerScreen(
            video = selectedVideo!!,
            allVideosInSeries = selectedVideoSeries,
            startFromBeginning = playFromStart,
            engine = playerEngine,
            onBack = { selectedVideo = null; refreshTrigger++ },
            onPlayNext = { next ->
                selectedVideo = next
                playFromStart = true
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
                    activeProfile = activeProfile,
                    onChangeProfile = onChangeProfile,
                    playerEngine = playerEngine,
                    onPlayerEngineChange = { savePlayerEngine(context, it); playerEngine = it },
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onVideoSelect = { video ->
                        selectedVideoSeries = groupedVideos[video.folderName] ?: listOf(video)
                        detailVideo = video
                    },
                    onRestoreHidden = { hiddenVideoIds = emptySet(); saveHiddenVideos(context, emptySet()) },
                    onRefreshVideos = { allVideos = loadAllLocalVideos(context) }
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
                            selectedVideoSeries = groupedVideos[video.folderName] ?: listOf(video)
                            selectedVideo = video
                            detailVideo = null
                        },
                        onPlayFromStart = {
                            playFromStart = true
                            selectedVideoSeries = groupedVideos[video.folderName] ?: listOf(video)
                            selectedVideo = video
                            detailVideo = null
                        },
                        onChangeImage = { videoToEditImage = video; imagePickerLauncher.launch("image/*") },
                        onRemoveVideo = {
                            val newSet = hiddenVideoIds + video.id
                            hiddenVideoIds = newSet
                            saveHiddenVideos(context, newSet)
                            detailVideo = null
                        },
                        onClearProgress = { clearVideoProgress(context, video.id); refreshTrigger++ }
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Se requiere permiso para acceder a tus videos.", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(permissionToRequest) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))) {
                        Text("Conceder Permiso", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. LAYOUT PRINCIPAL
// ==========================================
@Composable
fun NetflixMainLayout(
    groupedVideos: Map<String, List<VideoFile>>,
    allVideos: List<VideoFile>,
    continueWatchingList: List<Pair<VideoFile, Float>>,
    recentVideos: List<VideoFile>,
    totalAllVideosCount: Int,
    hiddenCount: Int,
    activeProfile: UserProfile,
    onChangeProfile: () -> Unit,
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
        bottomBar = { CompactBottomNavigation(currentTab, onTabSelected) },
        containerColor = Color(0xFF080808)
    ) { innerPadding ->
        when (currentTab) {
            "home" -> {
                if (allVideos.isEmpty() && continueWatchingList.isEmpty()) {
                    EmptyStateView(innerPadding, onRefreshVideos)
                } else {
                    val featuredVideo = remember(allVideos) { allVideos.firstOrNull() }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        item { NetflixTopBar(activeProfile, searchQuery, onSearchQueryChange, onChangeProfile) }
                        if (searchQuery.isBlank() && featuredVideo != null) {
                            item { NetflixHeroBanner(featuredVideo, onPlayClick = { onVideoSelect(featuredVideo) }) }
                        }
                        if (continueWatchingList.isNotEmpty() && searchQuery.isBlank()) {
                            item { ContinueWatchingRow(continueWatchingList, onVideoSelect) }
                        }
                        if (recentVideos.isNotEmpty() && searchQuery.isBlank()) {
                            item { NetflixFolderRow("Añadidos Recientemente", recentVideos, onVideoSelect) }
                        }
                        items(groupedVideos.keys.toList()) { folder ->
                            NetflixFolderRow(folder, groupedVideos[folder] ?: emptyList(), onVideoSelect)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
            "grid" -> GridCatalogScreen(allVideos, searchQuery, onSearchQueryChange, onVideoSelect, Modifier.padding(innerPadding))
            "settings" -> SettingsScreen(
                activeProfile = activeProfile,
                playerEngine = playerEngine,
                onPlayerEngineChange = onPlayerEngineChange,
                allVideosCount = totalAllVideosCount,
                hiddenCount = hiddenCount,
                onRestoreHidden = onRestoreHidden,
                onRefreshVideos = onRefreshVideos,
                onChangeProfile = onChangeProfile,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

// ==========================================
// 10. COMPONENTES VISUALES CON LOGO 'M' Y MARCO ROJO/BLANCO
// ==========================================
@Composable
fun NetflixTopBar(
    activeProfile: UserProfile,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onChangeProfile: () -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF080808), Color(0xFF080808).copy(alpha = 0.9f), Color.Transparent)))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            NetflixLogoM(sizeSp = 32)

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                    Icon(if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                // Avatar del perfil activo
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(profileColors[activeProfile.colorIndex % profileColors.size])
                        .clickable { onChangeProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeProfile.name.first().toString().uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar video o carpeta...", color = Color.Gray, fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, null, tint = Color.LightGray)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFE50914), unfocusedBorderColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun EmptyStateView(padding: PaddingValues, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideoLibrary, null, tint = Color(0xFF333333), modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("No se encontraron videos.", color = Color.Gray, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onRefresh, border = BorderStroke(1.dp, Color(0xFFE50914))) {
                Icon(Icons.Default.Refresh, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Escanear Almacenamiento", color = Color.White)
            }
        }
    }
}

@Composable
fun NetflixHeroBanner(video: VideoFile, onPlayClick: () -> Unit) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(video.customImageUri ?: video.uri)
            .decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(2500).crossfade(true).build()
    )

    Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        Image(painter, video.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        // Gradiente multicapa cinematográfico
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f),
                    0.3f to Color.Black.copy(alpha = 0.1f),
                    0.65f to Color(0xFF080808).copy(alpha = 0.7f),
                    1f to Color(0xFF080808)
                )
            )
        )

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xFF080808).copy(alpha = 0.4f), Color.Transparent))
            )
        )

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp, bottom = 20.dp)) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFE50914).copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "  ${video.folderName.uppercase()}  ",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }

            Text(
                text = video.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (video.durationMs > 0) {
                Text(
                    text = formatDuration(video.durationMs),
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
            } else {
                Spacer(Modifier.height(14.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = onPlayClick,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver Detalles", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun NetflixFolderRow(title: String, videos: List<VideoFile>, onVideoSelect: (VideoFile) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(videos) { video ->
                NetflixPosterCard(video = video, onClick = { onVideoSelect(video) })
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(items: List<Pair<VideoFile, Float>>, onVideoSelect: (VideoFile) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Box(modifier = Modifier.size(4.dp, 18.dp).background(Color(0xFFE50914), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text("Continuar Viendo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { (video, progressFraction) ->
                NetflixPosterCard(video = video, progressFraction = progressFraction, onClick = { onVideoSelect(video) })
            }
        }
    }
}

// ==========================================
// 11. TARJETA DE POSTER CON MARCO ROJO Y BLANCO
// ==========================================
@Composable
fun NetflixPosterCard(
    video: VideoFile,
    progressFraction: Float? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "cardScale"
    )

    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(video.customImageUri ?: video.uri)
            .decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(2000).crossfade(true).build()
    )

    Column(
        modifier = Modifier
            .width(118.dp)
            .scale(cardScale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(118.dp)
                .height(170.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(10.dp), ambientColor = Color(0xFFE50914).copy(alpha = 0.35f))
                // Marco exterior blanco brillante
                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            Image(painter, video.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

            // Marco interior con degradado rojo Netflix
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Color(0xFFE50914).copy(alpha = 0.85f))
                            )
                        ),
                        RoundedCornerShape(10.dp)
                    )
            )

            // Gradiente oscuro inferior para legibilidad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            )

            // Duración del video
            if (video.durationMs > 0) {
                Text(
                    text = formatDuration(video.durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }

            // Badge de subtítulos CC
            if (video.subtitleUri != null) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                ) {
                    Text("CC", color = Color.Yellow, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }

            // Barra de progreso roja
            if (progressFraction != null && progressFraction > 0f) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter).background(Color(0xFF333333))) {
                    Box(modifier = Modifier.fillMaxWidth(progressFraction.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFE50914)))
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text = video.name,
            color = Color(0xFFDDDDDD),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
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
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = searchQuery, onSearchQueryChange = onSearchQueryChange,
            placeholder = { Text("Buscar en el catálogo...", color = Color.Gray, fontSize = 14.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE50914), unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF161616), unfocusedContainerColor = Color(0xFF161616)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Catálogo Completo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1A1A)) {
                Text(" ${allVideos.size} videos ", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(allVideos) { video -> NetflixPosterCard(video, onClick = { onVideoSelect(video) }) }
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
    val context = LocalContext.current

    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(video.customImageUri ?: video.uri)
            .decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(2000).crossfade(true).build()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        shape = RoundedCornerShape(16.dp),
        title = null,
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                ) {
                    Image(painter, video.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
                        )
                    )
                    Text(
                        video.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (video.durationMs > 0) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E1E1E)) {
                            Text(" ⏱ ${formatDuration(video.durationMs)} ", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    if (video.sizeBytes > 0) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E1E1E)) {
                            Text(" 💾 ${formatFileSize(video.sizeBytes)} ", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    if (video.subtitleUri != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E2E1E)) {
                            Text(" CC ", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }

                if (hasProgress) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { (savedProgressMs.toFloat() / totalDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFE50914),
                            trackColor = Color(0xFF333333)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatDuration(savedProgressMs), color = Color(0xFFE50914), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (hasProgress) {
                    Button(onClick = onResumePlay, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Continuar (${formatDuration(savedProgressMs)})", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onPlayFromStart, border = BorderStroke(1.dp, Color(0xFF555555)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Replay, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Desde el Inicio", color = Color.White)
                    }
                } else {
                    Button(onClick = onResumePlay, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Reproducir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(6.dp))

                OutlinedButton(onClick = onChangeImage, border = BorderStroke(1.dp, Color(0xFF333333)), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar Miniatura", color = Color.LightGray, fontSize = 13.sp)
                }

                Spacer(Modifier.height(4.dp))

                Row(Modifier.fillMaxWidth()) {
                    if (hasProgress) {
                        TextButton(onClick = onClearProgress, modifier = Modifier.weight(1f)) {
                            Text("Borrar Progreso", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onRemoveVideo, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ocultar", color = Color(0xFFFF4D4D), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

// ==========================================
// 12. PANTALLA DE AJUSTES
// ==========================================
@Composable
fun SettingsScreen(
    activeProfile: UserProfile,
    playerEngine: String,
    onPlayerEngineChange: (String) -> Unit,
    allVideosCount: Int,
    hiddenCount: Int,
    onRestoreHidden: () -> Unit,
    onRefreshVideos: () -> Unit,
    onChangeProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Ajustes", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 20.dp))
        }

        // Perfil activo
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(profileColors[activeProfile.colorIndex % profileColors.size]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(activeProfile.name.first().toString().uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Perfil Activo", color = Color.Gray, fontSize = 11.sp)
                        Text(activeProfile.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onChangeProfile, border = BorderStroke(1.dp, Color(0xFFE50914))) {
                        Text("Cambiar", color = Color(0xFFE50914), fontSize = 13.sp)
                    }
                }
            }
        }

        // Motor de reproducción
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Motor de Reproducción", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Afecta la compatibilidad con MKV y audio multicanal.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                    listOf(
                        "vlc" to Triple("LibVLC (Recomendado)", "Dolby, DTS, MKV, subtítulos integrados", Icons.Default.VolumeUp),
                        "exoplayer" to Triple("Google ExoPlayer", "Videos estándar MP4, WebM, H264", Icons.Default.SmartDisplay)
                    ).forEach { (id, info) ->
                        val (title, desc, _) = info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playerEngine == id) Color(0xFF222222) else Color.Transparent)
                                .clickable { onPlayerEngineChange(id) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = playerEngine == id, onClick = { onPlayerEngineChange(id) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE50914), unselectedColor = Color.Gray))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(desc, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Biblioteca
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Biblioteca", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$allVideosCount", color = Color(0xFFE50914), fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text("Videos totales", color = Color.Gray, fontSize = 11.sp)
                        }
                        Box(Modifier.width(1.dp).height(50.dp).background(Color(0xFF333333)).align(Alignment.CenterVertically))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$hiddenCount", color = Color.LightGray, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text("Ocultos", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefreshVideos, border = BorderStroke(1.dp, Color(0xFF444444)), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reescanear", color = Color.White, fontSize = 12.sp)
                        }
                        Button(onClick = onRestoreHidden, enabled = hiddenCount > 0, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.weight(1f)) {
                            Text("Restablecer ($hiddenCount)", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text("Mi Netflix Local v3.0 • LibVLC + ExoPlayer", color = Color(0xFF444444), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

// ==========================================
// 13. NAVEGACIÓN INFERIOR
// ==========================================
@Composable
fun CompactBottomNavigation(currentTab: String, onTabSelected: (String) -> Unit) {
    Surface(color = Color(0xFF0C0C0C), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple("home", Icons.Default.Home, "Inicio"),
                Triple("grid", Icons.Default.GridView, "Catálogo"),
                Triple("settings", Icons.Default.Settings, "Ajustes")
            ).forEach { (tab, icon, label) ->
                val isSelected = currentTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(icon, label, tint = if (isSelected) Color(0xFFE50914) else Color(0xFF777777), modifier = Modifier.size(24.dp))
                    if (isSelected) {
                        Spacer(Modifier.height(3.dp))
                        Box(modifier = Modifier.width(20.dp).height(2.dp).background(Color(0xFFE50914), RoundedCornerShape(1.dp)))
                    }
                }
            }
        }
    }
}

// ==========================================
// 14. REPRODUCTOR UNIFICADO
// ==========================================
@Composable
fun UnifiedVideoPlayerScreen(
    video: VideoFile,
    allVideosInSeries: List<VideoFile>,
    startFromBeginning: Boolean,
    engine: String,
    onBack: () -> Unit,
    onPlayNext: (VideoFile) -> Unit
) {
    val currentIndex = allVideosInSeries.indexOfFirst { it.id == video.id }
    val nextVideo = if (currentIndex >= 0 && currentIndex < allVideosInSeries.size - 1)
        allVideosInSeries[currentIndex + 1] else null

    if (engine == "vlc") {
        VlcVideoPlayerView(video, nextVideo, startFromBeginning, onBack, onPlayNext)
    } else {
        ExoPlayerVideoPlayerView(video, nextVideo, startFromBeginning, onBack, onPlayNext)
    }
}

// ==========================================
// 15. REPRODUCTOR LIBVLC (PRINCIPAL - FIX MKV)
// ==========================================
@Composable
fun VlcVideoPlayerView(
    video: VideoFile,
    nextVideo: VideoFile?,
    startFromBeginning: Boolean,
    onBack: () -> Unit,
    onPlayNext: (VideoFile) -> Unit
) {
    val context = LocalContext.current
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isFillAspect by remember { mutableStateOf(false) }
    var seekNotice by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var showNextEpisode by remember { mutableStateOf(false) }
    var nextEpisodeCountdown by remember { mutableStateOf(10) }

    val libVLC = remember {
        LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--network-caching=1500", "-vvv"))
    }

    val mediaPlayer = remember {
        MediaPlayer(libVLC).apply {
            val media = Media(libVLC, video.uri).apply {
                setHWDecoderEnabled(true, false)
                addOption(":file-caching=1500")
                addOption(":network-caching=1500")
            }
            this.media = media
            media.release()
            video.subtitleUri?.let { addSlave(IMedia.Slave.Type.Subtitle, it, true) }
        }
    }

    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> isLoading = false
                MediaPlayer.Event.Buffering -> if (event.buffering == 100f) isLoading = false
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose { mediaPlayer.setEventListener(null) }
    }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            delay(3000)
            if (mediaPlayer.isPlaying) {
                val pos = mediaPlayer.time
                val dur = mediaPlayer.length
                if (pos > 0 && dur > 0) {
                    saveVideoProgress(context, video.id, pos, dur)
                    if (nextVideo != null && !showNextEpisode && pos.toFloat() / dur >= 0.95f) {
                        showNextEpisode = true
                        nextEpisodeCountdown = 10
                    }
                }
            }
        }
    }

    LaunchedEffect(showNextEpisode) {
        if (showNextEpisode && nextVideo != null) {
            while (nextEpisodeCountdown > 0) {
                delay(1000)
                nextEpisodeCountdown--
            }
            onPlayNext(nextVideo)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = mediaPlayer.time
            val dur = mediaPlayer.length
            if (pos > 0) saveVideoProgress(context, video.id, pos, if (dur > 0) dur else video.durationMs)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { VLCVideoLayout(it) },
            update = { vlcLayout ->
                if (!mediaPlayer.vlcVout.areViewsAttached()) {
                    mediaPlayer.attachViews(vlcLayout, null, false, false)
                    mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    if (!startFromBeginning) {
                        val savedPos = getVideoProgress(context, video.id)
                        if (savedPos > 0) mediaPlayer.time = savedPos
                    }
                    mediaPlayer.play()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) {
                                mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0)
                                seekNotice = "⏪ -10s"
                            } else {
                                mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
                                seekNotice = "⏩ +10s"
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFE50914), strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Iniciando reproductor...", color = Color.Gray, fontSize = 13.sp)
            }
        }

        LaunchedEffect(seekNotice) { if (seekNotice != null) { delay(800); seekNotice = null } }
        AnimatedVisibility(visible = seekNotice != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(seekNotice ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
            }
            Text(video.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        isFillAspect = !isFillAspect
                        mediaPlayer.videoScale = if (isFillAspect) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(Icons.Default.AspectRatio, null, tint = if (isFillAspect) Color(0xFFE50914) else Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    IconButton(onClick = { showSpeedMenu = true }, modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)) {
                        Text("${currentSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x", color = if (currentSpeed == speed) Color(0xFFE50914) else Color.White, fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { currentSpeed = speed; mediaPlayer.rate = speed; showSpeedMenu = false }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNextEpisode && nextVideo != null,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            if (nextVideo != null) {
                NextEpisodeCard(
                    nextVideo = nextVideo,
                    countdown = nextEpisodeCountdown,
                    onPlay = { onPlayNext(nextVideo) },
                    onDismiss = { showNextEpisode = false }
                )
            }
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(video.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Abrir con..."))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xBB141414)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            Icon(Icons.Default.OpenInNew, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("Reproductor externo", color = Color.White, fontSize = 11.sp)
        }
    }
}

// ==========================================
// 16. CARD DE SIGUIENTE EPISODIO
// ==========================================
@Composable
fun NextEpisodeCard(
    nextVideo: VideoFile,
    countdown: Int,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(nextVideo.customImageUri ?: nextVideo.uri)
            .decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(2000).crossfade(true).build()
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEE141414)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(240.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Siguiente episodio", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text("${countdown}s", color = Color(0xFFE50914), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
            ) {
                Image(painter, nextVideo.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(36.dp))
            }

            Spacer(Modifier.height(8.dp))

            Text(nextVideo.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Reproducir", color = Color.White, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, Color(0xFF555555)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("X", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { countdown / 10f },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                color = Color(0xFFE50914),
                trackColor = Color(0xFF333333)
            )
        }
    }
}

// ==========================================
// 17. REPRODUCTOR EXOPLAYER (ALTERNATIVO)
// ==========================================
@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerVideoPlayerView(
    video: VideoFile,
    nextVideo: VideoFile?,
    startFromBeginning: Boolean,
    onBack: () -> Unit,
    onPlayNext: (VideoFile) -> Unit
) {
    val context = LocalContext.current
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var seekNotice by remember { mutableStateOf<String?>(null) }
    var showNextEpisode by remember { mutableStateOf(false) }
    var nextEpisodeCountdown by remember { mutableStateOf(10) }

    val exoPlayer = remember {
        val rf = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableAudioTrackPlaybackParams(true)
        }
        ExoPlayer.Builder(context, rf).build().apply {
            val builder = MediaItem.Builder().setUri(video.uri)
            video.subtitleUri?.let { subUri ->
                builder.setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(subUri).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("es").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                ))
            }
            setMediaItem(builder.build())
            prepare()
            playWhenReady = true
            if (!startFromBeginning) { val p = getVideoProgress(context, video.id); if (p > 0) seekTo(p) }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(3000)
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                if (pos > 0 && dur > 0) {
                    saveVideoProgress(context, video.id, pos, dur)
                    if (nextVideo != null && !showNextEpisode && pos.toFloat() / dur >= 0.95f) {
                        showNextEpisode = true; nextEpisodeCountdown = 10
                    }
                }
            }
        }
    }

    LaunchedEffect(showNextEpisode) {
        if (showNextEpisode && nextVideo != null) {
            while (nextEpisodeCountdown > 0) { delay(1000); nextEpisodeCountdown-- }
            onPlayNext(nextVideo)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
            if (pos > 0) saveVideoProgress(context, video.id, pos, if (dur > 0) dur else video.durationMs)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); this.resizeMode = resizeMode } },
            update = { it.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { offset ->
                    if (offset.x < size.width / 2) { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)); seekNotice = "⏪ -10s" }
                    else { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)); seekNotice = "⏩ +10s" }
                })
            }
        )

        LaunchedEffect(seekNotice) { if (seekNotice != null) { delay(800); seekNotice = null } }
        AnimatedVisibility(visible = seekNotice != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(seekNotice ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(video.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { resizeMode = when(resizeMode) { AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) { Icon(Icons.Default.AspectRatio, null, tint = Color.White) }
                Spacer(Modifier.width(6.dp))
                Box {
                    IconButton(onClick = { showSpeedMenu = true }, modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)) {
                        Text("${currentSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x", color = if (currentSpeed == speed) Color(0xFFE50914) else Color.White) },
                                onClick = { currentSpeed = speed; exoPlayer.playbackParameters = PlaybackParameters(speed); showSpeedMenu = false }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showNextEpisode && nextVideo != null, enter = fadeIn(tween(300)), exit = fadeOut(tween(300)), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            if (nextVideo != null) NextEpisodeCard(nextVideo, nextEpisodeCountdown, { onPlayNext(nextVideo) }, { showNextEpisode = false })
        }
    }
}

// ==========================================
// 18. ESCANEO LOCAL DE VIDEOS
// ==========================================
fun loadAllLocalVideos(context: Context): List<VideoFile> {
    val videos = mutableListOf<VideoFile>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED
    )

    try {
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rawName = cursor.getString(nameCol) ?: "Sin título"
                val folder = if (bucketCol != -1) cursor.getString(bucketCol) ?: "General" else "General"
                val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                var customImageUri: Uri? = loadCustomImage(context, id)?.let { Uri.parse(it) }
                var subtitleUri: Uri? = null

                if (dataCol != -1) {
                    val filePath = cursor.getString(dataCol)
                    if (filePath != null) {
                        val vf = File(filePath)
                        val parent = vf.parentFile
                        val base = vf.nameWithoutExtension
                        if (parent != null && parent.exists()) {
                            if (customImageUri == null) {
                                customImageUri = listOf("$base.jpg", "$base.jpeg", "$base.png")
                                    .map { File(parent, it) }.firstOrNull { it.exists() }?.let { Uri.fromFile(it) }
                            }
                            subtitleUri = listOf("$base.srt", "$base.vtt")
                                .map { File(parent, it) }.firstOrNull { it.exists() }?.let { Uri.fromFile(it) }
                        }
                    }
                }

                videos.add(VideoFile(id, contentUri, rawName.substringBeforeLast("."), folder, duration, size, dateAdded, null, customImageUri, subtitleUri))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }

    return videos
}
