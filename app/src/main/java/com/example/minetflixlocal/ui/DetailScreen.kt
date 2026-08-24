package com.example.minetflixlocal.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    media: MediaSeries,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onUpdatePoster: (Uri) -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onUpdatePoster(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(media.title, color = Color.White) },
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
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.DarkGray)
                ) {
                    if (media.posterUri != null) {
                        AsyncImage(
                            model = media.posterUri,
                            contentDescription = media.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Botón flotante para cambiar la carátula desde la galería
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cambiar Carátula", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Text(
                    text = if (media.isMovie) "Película" else "Serie de TV",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Si es película, muestra un botón directo de reproducción
            if (media.isMovie) {
                item {
                    val movieEpisode = media.seasons.firstOrNull()?.episodes?.firstOrNull()
                    if (movieEpisode != null) {
                        Button(
                            onClick = { onEpisodeClick(movieEpisode) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reproducir Película")
                        }
                    }
                }
            } else {
                // Si es serie, lista las temporadas y episodios
                media.seasons.forEach { season ->
                    item {
                        Text(
                            text = season.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(season.episodes) { episode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEpisodeClick(episode) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = episode.title, color = Color.White, fontSize = 16.sp)
                        }
                        Divider(color = Color.DarkGray, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
