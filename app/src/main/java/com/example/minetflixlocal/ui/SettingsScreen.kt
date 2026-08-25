package com.example.minetflixlocal.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import coil.compose.AsyncImage
import com.example.minetflixlocal.model.UserProfile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedEngine: String,
    onEngineChanged: (String) -> Unit,
    profiles: List<UserProfile> = emptyList(),
    activeProfile: UserProfile? = null,
    onProfileSelected: (UserProfile) -> Unit = {},
    onUpdateProfileAvatar: (String, Uri?) -> Unit = { _, _ -> },
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedProfileIdForAvatar by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedProfileIdForAvatar?.let { profileId ->
                try {
                    val inputStream = context.contentResolver.openInputStream(selectedUri)
                    val localFile = File(context.filesDir, "profile_${profileId}.jpg")
                    inputStream?.use { input ->
                        localFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onUpdateProfileAvatar(profileId, Uri.fromFile(localFile))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SECCIÓN: Motor de Reproducción
            item {
                Text(text = "Motor de Reproducción", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineChanged("EXOPLAYER") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEngine == "EXOPLAYER",
                            onClick = { onEngineChanged("EXOPLAYER") },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE50914))
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Google ExoPlayer", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Text("Recomendado (Rápido y nativo de Android)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineChanged("VLC") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEngine == "VLC",
                            onClick = { onEngineChanged("VLC") },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE50914))
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("VLC Media Player", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Text("Para archivos con audio AC3 / DTS o contenedores MKV", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Divider(color = Color.DarkGray)
            }

            // SECCIÓN: Gestión de Perfiles y Fotos Personalizadas
            item {
                Column {
                    Text(text = "Perfiles y Fotos", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toca el icono de la cámara en cualquier perfil para cargar tu propia foto.",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(profiles, key = { it.id }) { profile ->
                val isActive = activeProfile?.id == profile.id

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF1F1F1F) else Color(0xFF121212)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2B2B2B))
                                .clickable {
                                    selectedProfileIdForAvatar = profile.id
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!profile.avatarUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = Uri.parse(profile.avatarUri),
                                    contentDescription = profile.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(text = profile.avatarIcon, fontSize = 24.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Cambiar foto",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isActive) {
                                Text(
                                    text = "Perfil activo",
                                    color = Color(0xFFE50914),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (!isActive) {
                            TextButton(onClick = { onProfileSelected(profile) }) {
                                Text("Usar", color = Color(0xFFE50914))
                            }
                        }
                    }
                }
            }

            item {
                Divider(color = Color.DarkGray)
            }

            // SECCIÓN: Acciones adicionales
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRescan,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reescanear Archivos Locales")
                    }
                }
            }
        }
    }
}
