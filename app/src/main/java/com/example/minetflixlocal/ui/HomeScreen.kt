package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minetflixlocal.model.MediaSeries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    seriesList: List<MediaSeries>,
    moviesList: List<MediaSeries>,
    onMediaSelected: (MediaSeries) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MiNetflix",
                        color = Color(0xFFE50914), // Rojo Netflix
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
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
                MediaSection(title = "Series", items = seriesList, onItemClick = onMediaSelected)
            }
            item {
                MediaSection(title = "Películas", items = moviesList, onItemClick = onMediaSelected)
            }
        }
    }
}

@Composable
fun MediaSection(
    title: String,
    items: List<MediaSeries>,
    onItemClick: (MediaSeries) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { media ->
                MediaCard(media = media, onClick = { onItemClick(media) })
            }
        }
    }
}

@Composable
fun MediaCard(media: MediaSeries, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(170.dp)
            .background(Color.DarkGray, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = media.title,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(8.dp)
        )
    }
}
