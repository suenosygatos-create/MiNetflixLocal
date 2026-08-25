package com.example.minetflixlocal.util

import android.content.Context
import com.example.minetflixlocal.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

object ProfilePreferences {
    private const val PREFS_NAME = "user_profiles_prefs"
    private const val KEY_PROFILES = "profiles_json"

    fun getProfiles(context: Context): List<UserProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return getDefaultProfiles()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        avatarResId = obj.optInt("avatarResId", 0)
                    )
                )
            }
            list.ifEmpty { getDefaultProfiles() }
        } catch (e: Exception) {
            getDefaultProfiles()
        }
    }

    fun saveProfiles(context: Context, profiles: List<UserProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("avatarResId", profile.avatarResId)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES, array.toString())
            .apply()
    }

    fun addProfile(context: Context, profile: UserProfile) {
        val current = getProfiles(context).toMutableList()
        current.add(profile)
        saveProfiles(context, current)
    }

    private fun getDefaultProfiles(): List<UserProfile> {
        return listOf(
            UserProfile("1", "Usuario 1", 0),
            UserProfile("2", "Niños", 0)
        )
    }
}
