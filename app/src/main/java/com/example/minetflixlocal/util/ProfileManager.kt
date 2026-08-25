package com.example.minetflixlocal.util

import android.content.Context
import com.example.minetflixlocal.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

object ProfileManager {
    private const val PREF_NAME = "netflix_local_profiles_pref"
    private const val KEY_PROFILES = "user_profiles"

    fun saveProfiles(context: Context, profiles: List<UserProfile>) {
        val array = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("avatarIcon", p.avatarIcon)
                put("avatarUri", p.avatarUri ?: "")
                put("colorHex", p.colorHex)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES, array.toString())
            .apply()
    }

    fun loadProfiles(context: Context): List<UserProfile> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROFILES, null) ?: return defaultProfiles()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uriStr = obj.optString("avatarUri", "")
                list.add(
                    UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        avatarIcon = obj.getString("avatarIcon"),
                        avatarUri = if (uriStr.isEmpty()) null else uriStr,
                        colorHex = obj.getLong("colorHex")
                    )
                )
            }
            if (list.isEmpty()) defaultProfiles() else list
        } catch (e: Exception) {
            defaultProfiles()
        }
    }

    private fun defaultProfiles() = listOf(
        UserProfile("1", "Usuario 1", "🐭", null, 0xFFE50914),
        UserProfile("2", "Familia", "🏰", null, 0xFF1E88E5),
        UserProfile("3", "Niños", "🦁", null, 0xFF43A047)
    )
}
