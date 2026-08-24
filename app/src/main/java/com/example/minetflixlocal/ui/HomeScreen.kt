package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.util.WatchProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activeProfile: UserProfile?,
    seriesList: List<MediaSeries>,
    moviesList: List<MediaSeries>,
    continueWatchingMap: Map<String, WatchProgress>,
    onMediaSelected: (MediaSeries) -> Unit,
    onResumePlayback: (MediaSeries, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val allMedia = remember(seriesList, moviesList) { seriesList + moviesList }

    val filteredMovies = remember(searchQuery, moviesList) {
        if (searchQuery.isBlank()) moviesList
        else moviesList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val filteredSeries = remember(searchQuery, seriesList) {
        if (searchQuery.isBlank()) seriesList
        else seriesList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val continueWatchingList = remember(continueWatchingMap, allMedia) {
        continueWatchingMap.values.mapNotNull { progress ->
            val media = allMedia.find { it.id == progress.mediaId }
            if (media != null) Pair(media, progress) else null
        }.sortedByDescending { it.second.lastUpdated }
    }

    val recommendedList = remember(allMedia) {
        allMedia.shuffled().take(6)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar películas o series...", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFE50914)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NETFLIX",
                                color = Color(0xFFE50914),
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            activeProfile?.let {
                                Text(
                                    text = "${it.avatarIcon} ${it.name}",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
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
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Sección: Seguir viendo
            if (continueWatchingList.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                        Text(
                            text = "Seguir viendo",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(continueWatchingList) { (media, progress) ->
                                ContinueWatchingCard(
                                    media = media,
                                    progress = progress,
                                    onClick = { onResumePlayback(media, progress.episodeId) }
                                )
                            }
                        }
                    }
                }
            }

            // Sección: Recomendaciones
            if (recommendedList.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    MediaSectionRow(
                        title = "Recomendados para ti",
                        items = recommendedList,
                        onMediaSelected = onMediaSelected
                    )
                }
            }

            // Sección: Películas
            if (filteredMovies.isNotEmpty()) {
                item {
                    MediaSectionRow(
                        title = "Películas",
                        items = filteredMovies,
                        onMediaSelected = onMediaSelected
                    )
                }
            }

            // Sección: Series
            if (filteredSeries.isNotEmpty()) {
                item {
                    MediaSectionRow(
                        title = "Series de TV",
                        items = filteredSeries,
                        onMediaSelected = onMediaSelected
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    media: MediaSeries,
    progress: WatchProgress,
    onClick: () -> Unit
) {
    val progressPercent = if (progress.totalDurationMs > 0) {
        progress.positionMs.toFloat() / progress.totalDurationMs.toFloat()
    } else 0f

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B)),
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(160.dp)
            ) {
                if (media.posterUri != null) {
                    AsyncImage(
                        model = media.posterUri,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Continuar",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFFE50914),
                trackColor = Color.Gray
            )
        }
    }
}
