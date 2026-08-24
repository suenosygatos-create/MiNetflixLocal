package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    media: MediaSeries,
    onBack: () -> Unit,
    onEpisodeClick: (VideoItem) -> Unit
) {
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var expandedSeasonDropdown by remember { mutableStateOf(false) }

    val currentSeason = media.seasons.getOrNull(selectedSeasonIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = media.title, color = Color.White) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Cabecera / Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(text = media.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de Temporadas (Desplegable)
            if (media.seasons.isNotEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Button(
                        onClick = { expandedSeasonDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                    ) {
                        Text(text = currentSeason?.seasonName ?: "Temporadas", color = Color.White)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = expandedSeasonDropdown,
                        onDismissRequest = { expandedSeasonDropdown = false },
                        modifier = Modifier.background(Color(0xFF222222))
                    ) {
                        media.seasons.forEachIndexed { index, season ->
                            DropdownMenuItem(
                                text = { Text(season.seasonName, color = Color.White) },
                                onClick = {
                                    selectedSeasonIndex = index
                                    expandedSeasonDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de Episodios
            val episodes = currentSeason?.episodes ?: emptyList()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(episodes) { episode ->
                    EpisodeRow(episode = episode, onClick = { onEpisodeClick(episode) })
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(episode: VideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 60.dp)
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = Color.White)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(text = episode.title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(text = episode.duration, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}
