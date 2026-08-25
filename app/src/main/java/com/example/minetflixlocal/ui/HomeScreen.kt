package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onOpenSettings: () -> Unit,
    onHideMedia: (String) -> Unit,
    onChangePoster: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedSeriesForDetails by remember { mutableStateOf<MediaSeries?>(null) }

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

    val handleItemClick: (MediaSeries) -> Unit = { media ->
        if (media.isMovie) {
            onMediaSelected(media)
        } else {
            selectedSeriesForDetails = media
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar en MovieBox...", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF3366),
                                unfocusedBorderColor = Color(0xFF2B2B36)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MOVIEBOX",
                                color = Color(0xFFFF3366),
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            activeProfile?.let {
                                Surface(
                                    color = Color(0xFF1F1F2C),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B2B36))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = it.avatarIcon, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = it.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F1A))
            )
        },
        containerColor = Color(0xFF0F0F1A)
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
                        onMediaSelected = handleItemClick,
                        onHideMedia = onHideMedia,
                        onChangePoster = onChangePoster
                    )
                }
            }

            // Sección: Películas
            if (filteredMovies.isNotEmpty()) {
                item {
                    MediaSectionRow(
                        title = "Películas",
                        items = filteredMovies,
                        onMediaSelected = handleItemClick,
                        onHideMedia = onHideMedia,
                        onChangePoster = onChangePoster
                    )
                }
            }

            // Sección: Series
            if (filteredSeries.isNotEmpty()) {
                item {
                    MediaSectionRow(
                        title = "Series de TV",
                        items = filteredSeries,
                        onMediaSelected = handleItemClick,
                        onHideMedia = onHideMedia,
                        onChangePoster = onChangePoster
                    )
                }
            }
        }
    }

    // Diálogo de Selección de Temporadas y Episodios
    selectedSeriesForDetails?.let { series ->
        AlertDialog(
            onDismissRequest = { selectedSeriesForDetails = null },
            title = {
                Text(
                    text = series.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    LazyColumn {
                        series.seasons.forEach { season ->
                            item {
                                Text(
                                    text = season.title,
                                    color = Color(0xFFFF3366),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(season.episodes) { episode ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSeriesForDetails = null
                                            onResumePlayback(series, episode.id)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir",
                                        tint = Color(0xFFFF3366),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = episode.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF2B2B36))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedSeriesForDetails = null }) {
                    Text("Cerrar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1F1F2C)
        )
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F2C)),
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
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2B2B36)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFF3366), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Continuar",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFFFF3366),
                trackColor = Color(0xFF2B2B36)
            )
        }
    }
}

@Composable
fun MediaSectionRow(
    title: String,
    items: List<MediaSeries>,
    onMediaSelected: (MediaSeries) -> Unit,
    onHideMedia: (String) -> Unit,
    onChangePoster: (String) -> Unit = {}
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { media ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onMediaSelected(media) }
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F2C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (media.posterUri != null) {
                                AsyncImage(
                                    model = media.posterUri,
                                    contentDescription = media.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF2B2B36)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            var showMenu by remember { mutableStateOf(false) }

                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opciones",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF2B2B36))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Cambiar portada", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onChangePoster(media.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ocultar", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onHideMedia(media.id)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
