package com.parapo.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val storageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * FR-16: Local JSON persistence for user settings (settings.json).
 */
object SettingsStore {
    private const val FILE_NAME = "settings.json"
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private var loaded = false

    fun load(context: Context): Settings {
        val file = File(context.filesDir, FILE_NAME)
        val parsed = if (file.exists()) {
            runCatching { storageJson.decodeFromString<Settings>(file.readText()) }.getOrNull()
        } else {
            null
        } ?: Settings()
        _settings.value = parsed
        loaded = true
        return parsed
    }

    fun current(context: Context): Settings = if (loaded) _settings.value else load(context)

    fun update(context: Context, transform: (Settings) -> Settings): Settings {
        val updated = transform(current(context))
        _settings.value = updated
        File(context.filesDir, FILE_NAME).writeText(storageJson.encodeToString(updated))
        return updated
    }

    fun reset(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        _settings.value = Settings()
        loaded = true
    }
}

/**
 * FR-15: Local JSON persistence for completed trip logs (trips.json).
 */
object TripLogStore {
    private const val FILE_NAME = "trips.json"
    private val _trips = MutableStateFlow<List<TripLog>>(emptyList())
    val trips: StateFlow<List<TripLog>> = _trips.asStateFlow()
    private var loaded = false

    fun load(context: Context): List<TripLog> {
        val file = File(context.filesDir, FILE_NAME)
        val parsed = if (file.exists()) {
            runCatching { storageJson.decodeFromString<List<TripLog>>(file.readText()) }.getOrNull()
        } else {
            null
        } ?: emptyList()
        _trips.value = parsed
        loaded = true
        return parsed
    }

    fun all(context: Context): List<TripLog> = if (loaded) _trips.value else load(context)

    fun append(context: Context, log: TripLog) {
        val updated = listOf(log) + all(context)
        _trips.value = updated
        File(context.filesDir, FILE_NAME).writeText(storageJson.encodeToString(updated))
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        _trips.value = emptyList()
        loaded = true
    }
}
