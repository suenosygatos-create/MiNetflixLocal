package com.example.minetflixlocal

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.minetflixlocal.model.MediaSeries
import com.example.minetflixlocal.model.Season
import com.example.minetflixlocal.model.VideoItem
import com.example.minetflixlocal.ui.DetailScreen
import com.example.minetflixlocal.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Datos de prueba con 5 Temporadas de Los Simpson
        val simpsonsSeasons = (1..5).map { seasonNum ->
            Season(
                seasonNumber = seasonNum,
                seasonName = "Temporada $seasonNum",
                episodes = (1..4).map { epNum ->
                    VideoItem(
                        id = "simps_s${seasonNum}_e${epNum}",
                        title = "Episodio $epNum - Temp $seasonNum",
                        videoPath = ""
                    )
                }
            )
        }

        val dummySeries = listOf(
            MediaSeries(id = "1", title = "Los Simpson", seasons = simpsonsSeasons),
            MediaSeries(id = "2", title = "Futurama", seasons = emptyList())
        )

        val dummyMovies = listOf(
            MediaSeries(id = "3", title = "Los Simpson: La Película", isMovie = true)
        )

        setContent {
            var selectedMedia by remember { mutableStateOf<MediaSeries?>(null) }

            if (selectedMedia == null) {
                HomeScreen(
                    seriesList = dummySeries,
                    moviesList = dummyMovies,
                    onMediaSelected = { selectedMedia = it }
                )
            } else {
                DetailScreen(
                    media = selectedMedia!!,
                    onBack = { selectedMedia = null },
                    onEpisodeClick = { episode ->
                        Toast.makeText(this, "Reproduciendo: ${episode.title}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
