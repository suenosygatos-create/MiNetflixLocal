package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    onHideMedia: () -> Unit
) {
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var seasonDropdownExpanded by remember { mutableStateOf(false) }

    val currentSeason = media.seasons.getOrNull(selectedSeasonIndex) ?: media.seasons.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(media.title, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onHideMedia) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Ocultar contenido", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141414))
            )
        },
        containerColor = Color(0xFF141414)
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
                        .height(220.dp)
                        .background(Color(0xFF222222))
                ) {
                    if (media.posterUri != null) {
                        AsyncImage(
                            model = media.posterUri,
                            contentDescription = media.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Desplegable de Temporadas (si hay más de 1 temporada)
            if (!media.isMovie && media.seasons.size > 1) {
                item {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { seasonDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B))
                        ) {
                            Text(currentSeason?.title ?: "Seleccionar Temporada", color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = seasonDropdownExpanded,
                            onDismissRequest = { seasonDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF2B2B2B))
                        ) {
                            media.seasons.forEachIndexed { index, season ->
                                DropdownMenuItem(
                                    text = { Text(season.title, color = Color.White) },
                                    onClick = {
                                        selectedSeasonIndex = index
                                        seasonDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Lista de Episodios
            currentSeason?.let { season ->
                items(season.episodes) { episode ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onEpisodeClick(episode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = episode.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
