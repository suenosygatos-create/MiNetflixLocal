package com.example.minetflixlocal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedEngine: String,
    onEngineChanged: (String) -> Unit,
    onBack: () -> Unit,
    onChangeProfile: () -> Unit,
    onRescan: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", color = Color.White) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(text = "Motor de Reproducción", color = Color.Gray, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEngineChanged("EXOPLAYER") },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedEngine == "EXOPLAYER",
                    onClick = { onEngineChanged("EXOPLAYER") }
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
                    onClick = { onEngineChanged("VLC") }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("VLC Media Player", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Text("Para archivos con audio AC3 / DTS o contenedores MKV", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

            Divider(color = Color.DarkGray)

            Button(
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reescanear Archivos Locales")
            }

            OutlinedButton(
                onClick = onChangeProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cambiar de Perfil", color = Color.White)
            }
        }
    }
}
