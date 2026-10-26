package com.hongxiang.kpilot.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class KindleProfile(val id: String, val name: String)

class KindleProfiles(private val context: Context) {
    private val preferences = context.getSharedPreferences("kindle_profiles",Context.MODE_PRIVATE)
    init {
        if (all().isEmpty()) {
            val profile = KindleProfile(UUID.randomUUID().toString(),"Kindle")
            write(listOf(profile),profile.id)
            SecureSettingsStore(context).load()?.let { credentials(profile.id).save(it) }
        }
    }
    fun all(): List<KindleProfile> {
        val array = JSONArray(preferences.getString("profiles","[]"))
        return (0 until array.length()).map { array.getJSONObject(it).let { value -> KindleProfile(value.getString("id"),value.getString("name")) } }
    }
    val activeId: String get() = preferences.getString("active",null)?.takeIf { id -> all().any { it.id == id } } ?: all().first().id
    fun settings(id: String = activeId) = credentials(id).load() ?: ConnectionSettings()
    fun saveActive(settings: ConnectionSettings) = credentials(activeId).save(settings)
    fun rename(name: String) = write(all().map { if (it.id == activeId) it.copy(name=name) else it },activeId)
    fun select(id: String) { require(all().any { it.id == id }); preferences.edit { putString("active",id) } }
    fun add() { val profile = KindleProfile(UUID.randomUUID().toString(),"Kindle ${all().size+1}"); write(all()+profile,profile.id) }
    fun remove() {
        val profiles = all(); if (profiles.size <= 1) return
        val id = activeId; credentials(id).clear(); val remaining = profiles.filterNot { it.id == id }; write(remaining,remaining.first().id)
    }
    fun reset() {
        all().forEach { credentials(it.id).clear() }
        val profile = KindleProfile(UUID.randomUUID().toString(),"Kindle")
        write(listOf(profile),profile.id)
    }
    private fun credentials(id: String) = SecureSettingsStore(context,"kindle_credentials_$id")
    private fun write(profiles: List<KindleProfile>, active: String) {
        val array = JSONArray(); profiles.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name)) }
        preferences.edit { putString("profiles",array.toString()); putString("active",active) }
    }
}
