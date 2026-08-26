package com.example.minetflixlocal.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.minetflixlocal.model.Episode
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.UserProfile
import com.example.minetflixlocal.util.WatchProgress
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activeProfile: UserProfile?,
    seriesList: List<MediaSeries>,
    moviesList: List<MediaSeries>,
    continueWatchingMap: Map<String, WatchProgress>,
    hiddenMediaIds: Set<String> = emptySet(),
    onMediaSelected: (MediaSeries) -> Unit,
    onResumePlayback: (MediaSeries, String) -> Unit,
    onOpenSettings: () -> Unit,
    onHideMedia: (String) -> Unit,
    onUpdateMediaPoster: (String, Uri) -> Unit = { _, _ -> }
) {

    val context = LocalContext.current

    var searchQuery by remember {
        mutableStateOf("")
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    /*
     * Serie seleccionada para abrir DetailScreen.
     *
     * IMPORTANTE:
     * Ya no mostramos un AlertDialog con todos los episodios.
     * La serie entra en su propia pantalla de detalles.
     */
    var selectedSeriesForDetails by remember {
        mutableStateOf<MediaSeries?>(null)
    }

    var selectedMediaIdForPoster by remember {
        mutableStateOf<String?>(null)
    }

    // =========================================================
    // SELECTOR DE PORTADAS
    // =========================================================

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            uri?.let { selectedUri ->

                selectedMediaIdForPoster?.let { mediaId ->

                    try {

                        val inputStream =
                            context.contentResolver
                                .openInputStream(selectedUri)

                        val localFile =
                            File(
                                context.filesDir,
                                "poster_${mediaId}.jpg"
                            )

                        inputStream?.use { input ->

                            localFile.outputStream().use { output ->

                                input.copyTo(output)
                            }
                        }

                        onUpdateMediaPoster(
                            mediaId,
                            Uri.fromFile(localFile)
                        )

                    } catch (e: Exception) {

                        e.printStackTrace()
                    }
                }
            }
        }

    // =========================================================
    // CONTENIDO VISIBLE
    // =========================================================

    val visibleMovies =
        remember(
            moviesList,
            hiddenMediaIds
        ) {

            moviesList.filterNot {
                it.id in hiddenMediaIds
            }
        }

    val visibleSeries =
        remember(
            seriesList,
            hiddenMediaIds
        ) {

            seriesList.filterNot {
                it.id in hiddenMediaIds
            }
        }

    val allVisibleMedia =
        remember(
            visibleSeries,
            visibleMovies
        ) {

            visibleSeries + visibleMovies
        }

    // =========================================================
    // BÚSQUEDA
    // =========================================================

    val filteredMovies =
        remember(
            searchQuery,
            visibleMovies
        ) {

            if (searchQuery.isBlank()) {

                visibleMovies

            } else {

                visibleMovies.filter {
                    it.title.contains(
                        searchQuery,
                        ignoreCase = true
                    )
                }
            }
        }

    val filteredSeries =
        remember(
            searchQuery,
            visibleSeries
        ) {

            if (searchQuery.isBlank()) {

                visibleSeries

            } else {

                visibleSeries.filter {
                    it.title.contains(
                        searchQuery,
                        ignoreCase = true
                    )
                }
            }
        }

    // =========================================================
    // CONTINUAR VIENDO
    // =========================================================

    val continueWatchingList =
        remember(
            continueWatchingMap,
            allVisibleMedia
        ) {

            continueWatchingMap.values
                .mapNotNull { progress ->

                    val media =
                        allVisibleMedia.find {
                            it.id == progress.mediaId
                        }

                    if (media != null) {

                        Pair(
                            media,
                            progress
                        )

                    } else {

                        null
                    }
                }
                .sortedByDescending {
                    it.second.lastUpdated
                }
        }

    // =========================================================
    // RECOMENDADOS
    // =========================================================

    val recommendedList =
        remember(allVisibleMedia) {

            allVisibleMedia
                .shuffled()
                .take(6)
        }

    // =========================================================
    // CLICK SOBRE CONTENIDO
    // =========================================================

    val handleItemClick:
            (MediaSeries) -> Unit = { media ->

        if (media.isMovie) {

            /*
             * Las películas siguen entrando
             * directamente al reproductor.
             */
            onMediaSelected(media)

        } else {

            /*
             * LAS SERIES NO SE REPRODUCEN DIRECTAMENTE.
             *
             * Primero abrimos:
             *
             * Serie
             *   ↓
             * Temporadas
             *   ↓
             * Episodios
             *   ↓
             * Reproductor
             */
            selectedSeriesForDetails = media
        }
    }

    // =========================================================
    // HOME
    // =========================================================

    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    if (isSearching) {

                        OutlinedTextField(

                            value = searchQuery,

                            onValueChange = {
                                searchQuery = it
                            },

                            placeholder = {

                                Text(
                                    "Buscar películas o series...",
                                    color = Color(0xFF8E8E93)
                                )
                            },

                            colors =
                                OutlinedTextFieldDefaults.colors(

                                    focusedTextColor =
                                        Color.White,

                                    unfocusedTextColor =
                                        Color.White,

                                    focusedBorderColor =
                                        Color(0xFFFF2E63),

                                    unfocusedBorderColor =
                                        Color(0xFF252538)
                                ),

                            singleLine = true,

                            shape =
                                RoundedCornerShape(12.dp),

                            modifier =
                                Modifier.fillMaxWidth()
                        )

                    } else {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(

                                text = "MOVIEBOX",

                                color =
                                    Color(0xFFFF2E63),

                                fontWeight =
                                    FontWeight.ExtraBold,

                                fontSize =
                                    22.sp,

                                letterSpacing =
                                    1.2.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(12.dp)
                            )

                            activeProfile?.let {

                                Surface(

                                    color =
                                        Color(0xFF181924),

                                    shape =
                                        RoundedCornerShape(20.dp),

                                    border =
                                        BorderStroke(
                                            1.dp,
                                            Color(0xFF2A2C3E)
                                        )
                                ) {

                                    Row(

                                        verticalAlignment =
                                            Alignment.CenterVertically,

                                        modifier =
                                            Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 4.dp
                                            )
                                    ) {

                                        Text(
                                            text =
                                                it.avatarIcon,
                                            fontSize =
                                                13.sp
                                        )

                                        Spacer(
                                            modifier =
                                                Modifier.width(6.dp)
                                        )

                                        Text(

                                            text =
                                                it.name,

                                            color =
                                                Color.White,

                                            fontSize =
                                                12.sp,

                                            fontWeight =
                                                FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                },

                actions = {

                    IconButton(

                        onClick = {

                            isSearching =
                                !isSearching

                            if (!isSearching) {
                                searchQuery = ""
                            }
                        }
                    ) {

                        Icon(

                            imageVector =
                                if (isSearching)
                                    Icons.Default.Close
                                else
                                    Icons.Default.Search,

                            contentDescription =
                                "Buscar",

                            tint =
                                Color.White
                        )
                    }

                    IconButton(
                        onClick =
                            onOpenSettings
                    ) {

                        Icon(

                            Icons.Default.Settings,

                            contentDescription =
                                "Ajustes",

                            tint =
                                Color.White
                        )
                    }
                },

                colors =
                    TopAppBarDefaults
                        .topAppBarColors(

                            containerColor =
                                Color(0xFF0D0E15)
                        )
            )
        },

        containerColor =
            Color(0xFF0D0E15)

    ) { padding ->

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),

            contentPadding =
                PaddingValues(
                    vertical = 12.dp
                )
        ) {

            // =================================================
            // CONTINUAR VIENDO
            // =================================================

            if (
                continueWatchingList.isNotEmpty() &&
                searchQuery.isBlank()
            ) {

                item {

                    Column(
                        modifier =
                            Modifier.padding(
                                bottom = 20.dp
                            )
                    ) {

                        Text(

                            text =
                                "Seguir viendo",

                            color =
                                Color.White,

                            fontSize =
                                19.sp,

                            fontWeight =
                                FontWeight.Bold,

                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                        )

                        LazyRow(

                            contentPadding =
                                PaddingValues(
                                    horizontal = 16.dp
                                ),

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    14.dp
                                )
                        ) {

                            items(

                                items =
                                    continueWatchingList,

                                key = {
                                    "cw_${it.first.id}"
                                }

                            ) { (media, progress) ->

                                ContinueWatchingCard(

                                    media =
                                        media,

                                    progress =
                                        progress,

                                    onClick = {

                                        onResumePlayback(
                                            media,
                                            progress.episodeId
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // =================================================
            // RECOMENDADOS
            // =================================================

            if (
                recommendedList.isNotEmpty() &&
                searchQuery.isBlank()
            ) {

                item {

                    MediaSectionRow(

                        title =
                            "Recomendados para ti",

                        items =
                            recommendedList,

                        onMediaSelected =
                            handleItemClick,

                        onHideMedia =
                            onHideMedia,

                        onChangePoster = { mediaId ->

                            selectedMediaIdForPoster =
                                mediaId

                            photoPickerLauncher.launch(

                                PickVisualMediaRequest(
                                    ActivityResultContracts
                                        .PickVisualMedia
                                        .ImageOnly
                                )
                            )
                        }
                    )
                }
            }

            // =================================================
            // PELÍCULAS
            // =================================================

            if (filteredMovies.isNotEmpty()) {

                item {

                    MediaSectionRow(

                        title =
                            "Películas",

                        items =
                            filteredMovies,

                        onMediaSelected =
                            handleItemClick,

                        onHideMedia =
                            onHideMedia,

                        onChangePoster = { mediaId ->

                            selectedMediaIdForPoster =
                                mediaId

                            photoPickerLauncher.launch(

                                PickVisualMediaRequest(
                                    ActivityResultContracts
                                        .PickVisualMedia
                                        .ImageOnly
                                )
                            )
                        }
                    )
                }
            }

            // =================================================
            // SERIES
            // =================================================

            if (filteredSeries.isNotEmpty()) {

                item {

                    MediaSectionRow(

                        title =
                            "Series de TV",

                        items =
                            filteredSeries,

                        onMediaSelected =
                            handleItemClick,

                        onHideMedia =
                            onHideMedia,

                        onChangePoster = { mediaId ->

                            selectedMediaIdForPoster =
                                mediaId

                            photoPickerLauncher.launch(

                                PickVisualMediaRequest(
                                    ActivityResultContracts
                                        .PickVisualMedia
                                        .ImageOnly
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    // =========================================================
    // DETALLE DE LA SERIE
    // =========================================================

    /*
     * IMPORTANTE:
     *
     * En lugar del AlertDialog anterior,
     * ahora utilizamos DetailScreen.
     *
     * La pantalla conserva el mismo tema oscuro.
     */

    selectedSeriesForDetails?.let { series ->

        DetailScreen(

            media =
                series,

            onBack = {

                selectedSeriesForDetails =
                    null
            },

            onEpisodeClick = { episode ->

                /*
                 * Al elegir un episodio:
                 *
                 * DetailScreen
                 *       ↓
                 *     Home
                 *       ↓
                 *   MainActivity
                 *       ↓
                 *   VideoPlayer
                 *
                 * Usamos el callback existente
                 * de reproducción.
                 */

                selectedSeriesForDetails =
                    null

                onResumePlayback(
                    series,
                    episode.id
                )
            },

            onHideMedia = {

                selectedSeriesForDetails =
                    null

                onHideMedia(
                    series.id
                )
            }
        )
    }
}

// =============================================================
// CONTINUE WATCHING CARD
// =============================================================

@Composable
fun ContinueWatchingCard(
    media: MediaSeries,
    progress: WatchProgress,
    onClick: () -> Unit
) {

    val context =
        LocalContext.current

    val progressPercent =
        if (progress.totalDurationMs > 0) {

            (
                progress.positionMs.toFloat() /
                    progress.totalDurationMs.toFloat()
            ).coerceIn(0f, 1f)

        } else {

            0f
        }

    Surface(

        shape =
            RoundedCornerShape(16.dp),

        color =
            Color(0xFF181924),

        modifier =
            Modifier
                .width(140.dp)
                .clickable {
                    onClick()
                }
    ) {

        Column {

            Box(

                modifier =
                    Modifier
                        .width(140.dp)
                        .height(165.dp)
            ) {

                if (media.posterUri != null) {

                    AsyncImage(

                        model =
                            ImageRequest.Builder(context)
                                .data(media.posterUri)
                                .crossfade(true)
                                .build(),

                        contentDescription =
                            media.title,

                        contentScale =
                            ContentScale.Crop,

                        modifier =
                            Modifier.fillMaxSize()
                    )

                } else {

                    Box(

                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Color(0xFF252538)
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(

                            imageVector =
                                Icons.Default.PlayArrow,

                            contentDescription =
                                null,

                            tint =
                                Color.Gray,

                            modifier =
                                Modifier.size(36.dp)
                        )
                    }
                }

                Box(

                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(

                                Brush.verticalGradient(

                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(
                                                alpha = 0.6f
                                            )
                                        )
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Box(

                        modifier =
                            Modifier
                                .size(42.dp)
                                .background(
                                    Color(0xFFFF2E63),
                                    CircleShape
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(

                            Icons.Default.PlayArrow,

                            contentDescription =
                                "Continuar",

                            tint =
                                Color.White,

                            modifier =
                                Modifier.size(24.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(

                progress = {
                    progressPercent
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp),

                color =
                    Color(0xFFFF2E63),

                trackColor =
                    Color(0xFF252538)
            )
        }
    }
}

// =============================================================
// FILA DE CONTENIDO
// =============================================================

@Composable
fun MediaSectionRow(
    title: String,
    items: List<MediaSeries>,
    onMediaSelected: (MediaSeries) -> Unit,
    onHideMedia: (String) -> Unit,
    onChangePoster: (String) -> Unit
) {

    val context =
        LocalContext.current

    Column(
        modifier =
            Modifier.padding(
                bottom = 20.dp
            )
    ) {

        Text(

            text =
                title,

            color =
                Color.White,

            fontSize =
                19.sp,

            fontWeight =
                FontWeight.Bold,

            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
        )

        LazyRow(

            contentPadding =
                PaddingValues(
                    horizontal = 16.dp
                ),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            items(

                items =
                    items,

                key = {
                    it.id
                }

            ) { media ->

                Column(

                    modifier =
                        Modifier
                            .width(125.dp)
                            .clickable {

                                onMediaSelected(
                                    media
                                )
                            }
                ) {

                    Surface(

                        shape =
                            RoundedCornerShape(16.dp),

                        color =
                            Color(0xFF181924),

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                    ) {

                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                        ) {

                            // ---------------------------------
                            // PORTADA
                            // ---------------------------------

                            if (media.posterUri != null) {

                                AsyncImage(

                                    model =
                                        ImageRequest
                                            .Builder(context)
                                            .data(
                                                media.posterUri
                                            )
                                            .crossfade(true)
                                            .build(),

                                    contentDescription =
                                        media.title,

                                    contentScale =
                                        ContentScale.Crop,

                                    modifier =
                                        Modifier.fillMaxSize()
                                )

                            } else {

                                Box(

                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color(0xFF252538)
                                            ),

                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    Icon(

                                        imageVector =
                                            Icons.Default.PlayArrow,

                                        contentDescription =
                                            null,

                                        tint =
                                            Color.Gray,

                                        modifier =
                                            Modifier.size(36.dp)
                                    )
                                }
                            }

                            // ---------------------------------
                            // GRADIENTE
                            // ---------------------------------

                            Box(

                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(

                                            Brush.verticalGradient(

                                                colors =
                                                    listOf(
                                                        Color.Black.copy(
                                                            alpha = 0.3f
                                                        ),
                                                        Color.Transparent,
                                                        Color.Black.copy(
                                                            alpha = 0.4f
                                                        )
                                                    )
                                            )
                                        )
                            )

                            // ---------------------------------
                            // MENÚ
                            // ---------------------------------

                            var showMenu by remember(
                                media.id
                            ) {
                                mutableStateOf(false)
                            }

                            IconButton(

                                onClick = {
                                    showMenu = true
                                },

                                modifier =
                                    Modifier
                                        .align(
                                            Alignment.TopEnd
                                        )
                                        .padding(4.dp)
                                        .background(
                                            Color.Black.copy(
                                                alpha = 0.5f
                                            ),
                                            CircleShape
                                        )
                                        .size(26.dp)
                            ) {

                                Icon(

                                    imageVector =
                                        Icons.Default.MoreVert,

                                    contentDescription =
                                        "Opciones",

                                    tint =
                                        Color.White,

                                    modifier =
                                        Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(

                                expanded =
                                    showMenu,

                                onDismissRequest = {
                                    showMenu = false
                                },

                                modifier =
                                    Modifier.background(
                                        Color(0xFF252538)
                                    )
                            ) {

                                DropdownMenuItem(

                                    text = {

                                        Text(
                                            "Cambiar portada",
                                            color =
                                                Color.White,
                                            fontSize =
                                                13.sp
                                        )
                                    },

                                    leadingIcon = {

                                        Icon(

                                            Icons.Default.Image,

                                            contentDescription =
                                                null,

                                            tint =
                                                Color.White,

                                            modifier =
                                                Modifier.size(18.dp)
                                        )
                                    },

                                    onClick = {

                                        showMenu = false

                                        onChangePoster(
                                            media.id
                                        )
                                    }
                                )

                                DropdownMenuItem(

                                    text = {

                                        Text(
                                            "Ocultar",
                                            color =
                                                Color.White,
                                            fontSize =
                                                13.sp
                                        )
                                    },

                                    leadingIcon = {

                                        Icon(

                                            Icons.Default.VisibilityOff,

                                            contentDescription =
                                                null,

                                            tint =
                                                Color.White,

                                            modifier =
                                                Modifier.size(18.dp)
                                        )
                                    },

                                    onClick = {

                                        showMenu = false

                                        onHideMedia(
                                            media.id
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(

                        text =
                            media.title,

                        color =
                            Color.White,

                        fontSize =
                            13.sp,

                        fontWeight =
                            FontWeight.Medium,

                        maxLines =
                            1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
