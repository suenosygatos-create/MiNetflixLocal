package com.example.minetflixlocal.util

import android.content.Context
import com.example.minetflixlocal.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

object ProfilePreferences {
    private const val PREF_NAME = "netflix_profiles_pref"
    private const val KEY_PROFILES = "user_profiles_list"

    // Obtener la lista de perfiles guardados (o devolver predeterminados)
    fun getProfiles(context: Context): List<UserProfile> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return getInitialProfiles()

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        avatarUrl = obj.optString("avatarUrl", "")
                    )
                )
            }
            if (list.isEmpty()) getInitialProfiles() else list
        } catch (e: Exception) {
            getInitialProfiles()
        }
    }

    // Agregar un nuevo perfil y guardarlo
    fun addProfile(context: Context, name: String): UserProfile {
        val currentProfiles = getProfiles(context).toMutableList()
        val newProfile = UserProfile(
            id = "user_${System.currentTimeMillis()}",
            name = name,
            avatarUrl = ""
        )
        currentProfiles.add(newProfile)
        saveProfiles(context, currentProfiles)
        return newProfile
    }

    private fun saveProfiles(context: Context, profiles: List<UserProfile>) {
        val jsonArray = JSONArray()
        profiles.forEach { profile ->
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("avatarUrl", profile.avatarUrl)
            }
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES, jsonArray.toString())
            .apply()
    }

    private fun getInitialProfiles(): List<UserProfile> {
        return listOf(
            UserProfile(id = "1", name = "Principal", avatarUrl = "")
        )
    }
}
