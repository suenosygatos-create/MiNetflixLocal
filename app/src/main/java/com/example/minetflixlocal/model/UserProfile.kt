package com.example.minetflixlocal.model

data class UserProfile(
    val id: String,
    var name: String,
    var avatarIcon: String = "🏰", // Emoji / Icono predeterminado
    var avatarUri: String? = null, // Nueva propiedad para foto propia de galería
    var colorHex: Long = 0xFFE50914
)

// Lista de iconos de estilo Disney disponibles para elegir
val DISNEY_AVATARS = listOf(
    "🏰" to "Castillo Mágico",
    "🐭" to "Ratón Mágico",
    "🦁" to "Rey León",
    "🧜‍♀️" to "Sirenita",
    "🧞‍♂️" to "Genio",
    "❄️" to "Reina de Hielo",
    "🚀" to "Guardián Espacial",
    "🤖" to "Robot Wall-E",
    "🎈" to "Casa con Globos",
    "⭐" to "Estrella Mágica"
)
