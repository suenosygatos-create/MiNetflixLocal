package com.example.minetflixlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minetflixlocal.model.DISNEY_AVATARS
import com.example.minetflixlocal.model.UserProfile

@Composable
fun ProfileScreen(
    profiles: List<UserProfile>,
    onProfileSelected: (UserProfile) -> Unit,
    onProfileUpdated: (UserProfile) -> Unit
) {
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0F1A), Color(0xFF050508))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "¿Quién está viendo?",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                items(profiles) { profile ->
                    ProfileCard(
                        profile = profile,
                        onSelect = { onProfileSelected(profile) },
                        onEdit = { editingProfile = profile }
                    )
                }
            }
        }

        // Modal para editar nombre e icono
        editingProfile?.let { profile ->
            EditProfileDialog(
                profile = profile,
                onDismiss = { editingProfile = null },
                onSave = { updated ->
                    onProfileUpdated(updated)
                    editingProfile = null
                }
            )
        }
    }
}

@Composable
fun ProfileCard(
    profile: UserProfile,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(profile.colorHex))
                .border(2.dp, Color(0xFF2B2B36), RoundedCornerShape(20.dp))
                .clickable { onSelect() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = profile.avatarIcon, fontSize = 54.sp)

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var selectedIcon by remember { mutableStateOf(profile.avatarIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F1F2C),
        title = { Text("Editar Perfil", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de usuario", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF3366),
                        unfocusedBorderColor = Color(0xFF333344),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Elige tu icono de avatar:", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(160.dp)
                ) {
                    items(DISNEY_AVATARS) { (icon, _) ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedIcon == icon) Color(0xFFFF3366) else Color(0xFF2B2B36)
                                )
                                .border(
                                    width = if (selectedIcon == icon) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = icon, fontSize = 22.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(profile.copy(name = name, avatarIcon = selectedIcon)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366))
            ) {
                Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}
