package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeProfile: () -> Unit,
    onRescan: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes y Configuración", color = Color.White) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("General", color = Color(0xFFE50914), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            
            Button(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
            ) {
                Text("Re-escanear videos locales", color = Color.White)
            }

            Button(
                onClick = onChangeProfile,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
            ) {
                Text("Cambiar de Usuario / Perfil", color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Información de la App", color = Color(0xFFE50914), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Versión: 1.0.0 Local", color = Color.Gray, fontSize = 14.sp)
            Text("Formatos soportados: MKV, MP4, AVI, WEBM, TS, M4V, FLV", color = Color.Gray, fontSize = 14.sp)
        }
    }
}
