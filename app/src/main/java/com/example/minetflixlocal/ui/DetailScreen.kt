package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // ---------------------------------------------------------
    // TEMPORADA SELECCIONADA
    // ---------------------------------------------------------

    var selectedSeasonIndex by remember(media.id) {
        mutableIntStateOf(0)
    }

    var seasonDropdownExpanded by remember {
        mutableStateOf(false)
    }

    /*
     * Si la serie cambia y la cantidad de temporadas
     * es diferente, aseguramos que el índice siga siendo válido.
     */
    LaunchedEffect(media.id, media.seasons.size) {

        if (
            selectedSeasonIndex >=
            media.seasons.size
        ) {
            selectedSeasonIndex = 0
        }
    }

    // ---------------------------------------------------------
    // TEMPORADA ACTUAL
    // ---------------------------------------------------------

    val currentSeason =
        media.seasons.getOrNull(
            selectedSeasonIndex
        )

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.ArrowBack,

                            contentDescription =
                                "Volver",

                            tint =
                                Color.White
                        )
                    }
                },

                actions = {

                    IconButton(
                        onClick = onHideMedia
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.VisibilityOff,

                            contentDescription =
                                "Ocultar contenido",

                            tint =
                                Color.Gray
                        )
                    }
                },

                colors =
                    TopAppBarDefaults
                        .topAppBarColors(
                            containerColor =
                                Color(0xFF141414)
                        )
            )
        },

        containerColor =
            Color(0xFF141414)

    ) { paddingValues ->

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {

            // =================================================
            // PORTADA
            // =================================================

            item {

                Box(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                Color(0xFF222222)
                            )
                ) {

                    if (media.posterUri != null) {

                        AsyncImage(

                            model =
                                media.posterUri,

                            contentDescription =
                                media.title,

                            contentScale =
                                ContentScale.Crop,

                            modifier =
                                Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // =================================================
            // SELECTOR DE TEMPORADAS
            // =================================================

            /*
             * Las películas NO muestran selector.
             *
             * Una serie con una sola temporada tampoco
             * necesita desplegable.
             */

            if (
                !media.isMovie &&
                media.seasons.size > 1
            ) {

                item {

                    Box(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                    ) {

                        Button(

                            onClick = {
                                seasonDropdownExpanded =
                                    true
                            },

                            shape =
                                RoundedCornerShape(8.dp),

                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            Color(0xFF2B2B2B)
                                    )
                        ) {

                            Text(

                                text =
                                    currentSeason
                                        ?.title
                                        ?: "Seleccionar Temporada",

                                color =
                                    Color.White,

                                fontWeight =
                                    FontWeight.Medium
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(8.dp)
                            )

                            Icon(

                                imageVector =
                                    Icons.Default.ArrowDropDown,

                                contentDescription =
                                    "Seleccionar temporada",

                                tint =
                                    Color.White
                            )
                        }

                        // -------------------------------------
                        // MENÚ DE TEMPORADAS
                        // -------------------------------------

                        DropdownMenu(

                            expanded =
                                seasonDropdownExpanded,

                            onDismissRequest = {
                                seasonDropdownExpanded =
                                    false
                            },

                            modifier =
                                Modifier.background(
                                    Color(0xFF2B2B2B)
                                )
                        ) {

                            media.seasons
                                .forEachIndexed {
                                    index,
                                    season ->

                                    DropdownMenuItem(

                                        text = {

                                            Text(

                                                text =
                                                    season.title,

                                                color =
                                                    if (
                                                        index ==
                                                        selectedSeasonIndex
                                                    ) {
                                                        Color(
                                                            0xFFE50914
                                                        )
                                                    } else {
                                                        Color.White
                                                    },

                                                fontWeight =
                                                    if (
                                                        index ==
                                                        selectedSeasonIndex
                                                    ) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Normal
                                                    }
                                            )
                                        },

                                        onClick = {

                                            selectedSeasonIndex =
                                                index

                                            seasonDropdownExpanded =
                                                false
                                        }
                                    )
                                }
                        }
                    }
                }
            }

            // =================================================
            // INFORMACIÓN DE LA TEMPORADA
            // =================================================

            if (
                !media.isMovie &&
                currentSeason != null
            ) {

                item {

                    Row(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 4.dp
                                ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(

                            text =
                                currentSeason.title,

                            color =
                                Color.White,

                            fontSize =
                                20.sp,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Text(

                            text =
                                "· ${currentSeason.episodes.size} episodios",

                            color =
                                Color.Gray,

                            fontSize =
                                14.sp
                        )
                    }
                }
            }

            // =================================================
            // EPISODIOS
            // =================================================

            currentSeason?.let { season ->

                items(

                    items =
                        season.episodes,

                    key = {
                        it.id
                    }

                ) { episode ->

                    EpisodeCard(

                        episode =
                            episode,

                        onClick = {
                            onEpisodeClick(
                                episode
                            )
                        }
                    )
                }
            }
        }
    }
}

// =============================================================
// TARJETA DE EPISODIO
// =============================================================

@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit
) {

    Card(

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFF1F1F1F)
            ),

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 6.dp
                )
                .clickable(
                    onClick = onClick
                ),

        shape =
            RoundedCornerShape(8.dp)
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // -------------------------------------------------
            // ICONO PLAY
            // -------------------------------------------------

            Icon(

                imageVector =
                    Icons.Default.PlayCircleOutline,

                contentDescription =
                    "Reproducir",

                tint =
                    Color(0xFFE50914),

                modifier =
                    Modifier.size(36.dp)
            )

            Spacer(
                modifier =
                    Modifier.width(16.dp)
            )

            // -------------------------------------------------
            // INFORMACIÓN
            // -------------------------------------------------

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(

                    text =
                        episode.title,

                    color =
                        Color.White,

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.Medium
                )
            }
        }
    }
}
