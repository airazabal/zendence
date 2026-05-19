package com.alex.zendence

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import java.util.Calendar

class MeditationRepository(private val dao: MeditationDao, private val sharedPrefs: SharedPreferences) {

    val history: Flow<List<Meditation>> = dao.getAll()
    val presets: Flow<List<Preset>> = dao.getAllPresets()

    // Reactive flow for all settings changes
    val settingsFlow: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(Unit) }

    suspend fun getMeditationById(id: Int) = dao.getById(id)
    suspend fun getAllMeditations() = dao.getAllList()
    suspend fun getRecentMeditations(limit: Int) = dao.getRecent(limit)
    suspend fun insertMeditation(meditation: Meditation) = dao.insert(meditation)
    suspend fun updateMeditation(meditation: Meditation) = dao.update(meditation)
    suspend fun deleteMeditation(meditation: Meditation) = dao.delete(meditation)
    suspend fun clearHistory() = dao.deleteAll()

    fun calculateStreak(history: List<Meditation>): Int {
        if (history.isEmpty()) return 0
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)

        val sessionDays = history.map {
            calendar.timeInMillis = it.timestamp
            calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
        }.distinct()

        var currentStreak = 0
        val checkCal = Calendar.getInstance()
        
        val isTodayPresent = sessionDays.any { it.first == year && it.second == today }
        checkCal.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterdayPresent = sessionDays.any { it.first == checkCal.get(Calendar.YEAR) && it.second == checkCal.get(Calendar.DAY_OF_YEAR) }

        if (isTodayPresent || isYesterdayPresent) {
            if (isTodayPresent) {
                checkCal.timeInMillis = System.currentTimeMillis()
            }
            while (sessionDays.any { it.first == checkCal.get(Calendar.YEAR) && it.second == checkCal.get(Calendar.DAY_OF_YEAR) }) {
                currentStreak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        return currentStreak
    }

    fun calculateWeeklyMinutes(history: List<Meditation>): Int {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        calendar.timeInMillis = now
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = calendar.timeInMillis
        
        return history.filter { it.timestamp >= sevenDaysAgo }.sumOf { it.durationMinutes }
    }

    suspend fun insertPreset(preset: Preset) = dao.insertPreset(preset)
    suspend fun deletePreset(preset: Preset) = dao.deletePreset(preset)

    fun getInitialDuration() = sharedPrefs.getInt("initial_duration_sec", 2700)
    fun getVolume() = sharedPrefs.getFloat("volume", 0.5f)
    fun getStartingBellEnabled() = sharedPrefs.getBoolean("starting_bell_enabled", true)
    fun getStartingBellVolume() = sharedPrefs.getFloat("starting_bell_volume", 0.7f)
    fun getStartingBellUri() = sharedPrefs.getString("starting_bell_uri", "android.resource://com.alex.zendence/raw/starting_bell")
    fun getIntervalBellsJson() = sharedPrefs.getString("interval_bells", null)
    fun getInitialSilence() = sharedPrefs.getInt("initial_silence_sec", 30)
    fun getMeditationReading() = sharedPrefs.getString("meditation_reading", null)
    fun getBackgroundSoundUri() = sharedPrefs.getString("background_sound_uri", "android.resource://com.alex.zendence/raw/nature_stream")

    fun saveMeditationReading(text: String) {
        sharedPrefs.edit().putString("meditation_reading", text).apply()
    }

    fun saveBackgroundSoundUri(uri: String) {
        sharedPrefs.edit().putString("background_sound_uri", uri).apply()
    }

    fun getGeminiApiKey() = sharedPrefs.getString("gemini_api_key", null)
    fun saveGeminiApiKey(key: String) {
        sharedPrefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun saveSettings(
        duration: Int,
        volume: Float,
        startBell: Boolean,
        startBellVol: Float,
        startBellUri: String,
        bellsJson: String,
        silenceSec: Int
    ) {
        sharedPrefs.edit()
            .putInt("initial_duration_sec", duration)
            .putFloat("volume", volume)
            .putBoolean("starting_bell_enabled", startBell)
            .putFloat("starting_bell_volume", startBellVol)
            .putString("starting_bell_uri", startBellUri)
            .putString("interval_bells", bellsJson)
            .putInt("initial_silence_sec", silenceSec)
            .apply()
    }
}
