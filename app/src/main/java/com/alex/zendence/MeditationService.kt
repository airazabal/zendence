package com.alex.zendence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class MeditationService : MediaSessionService() {
    private var backgroundPlayer: ExoPlayer? = null
    private var bellPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val CHANNEL_ID = "meditation_timer_channel"
    private val NOTIFICATION_ID = 1

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private val _timeLeftSec = MutableStateFlow(0)
    val timeLeftSec: StateFlow<Int> = _timeLeftSec

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var initialDurationSec = 0
    private var intervalBells = listOf<IntervalBell>()
    private var startingBellEnabled = true
    private var startingBellVolume = 0.7f
    private var backgroundVolume = 0.5f

    private lateinit var repository: MeditationRepository

    inner class LocalBinder : Binder() {
        fun getService(): MeditationService = this@MeditationService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MeditationService", "Service onCreate")
        
        repository = (application as ZendenceApp).repository

        backgroundPlayer = ExoPlayer.Builder(this).build().apply {
            val mediaItem = MediaItem.fromUri("android.resource://${packageName}/raw/nature_stream")
            setMediaItem(mediaItem)
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            prepare()
        }
        
        bellPlayer = ExoPlayer.Builder(this).build()

        mediaSession = MediaSession.Builder(this, backgroundPlayer!!).build()
        
        createNotificationChannel()
    }

    fun startMeditation(
        durationSec: Int,
        bells: List<IntervalBell>,
        startBellEnabled: Boolean,
        startBellVolume: Float,
        bgVolume: Float,
        silenceSec: Int
    ) {
        initialDurationSec = durationSec
        _timeLeftSec.value = durationSec
        intervalBells = bells
        startingBellEnabled = startBellEnabled
        startingBellVolume = startBellVolume
        backgroundVolume = bgVolume

        _isRunning.value = true
        backgroundPlayer?.volume = backgroundVolume
        // Removed backgroundPlayer?.play() from here

        startForeground(NOTIFICATION_ID, createNotification())

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            if (startingBellEnabled) {
                playBell(R.raw.starting_bell, 1, startingBellVolume)
            }

            // Start music on a delay without blocking the timer
            serviceScope.launch {
                if (silenceSec > 0) {
                    delay(silenceSec * 1000L)
                }
                if (_isRunning.value) {
                    backgroundPlayer?.play()
                }
            }

            while (_timeLeftSec.value > 0 && _isRunning.value) {
                delay(1000)
                _timeLeftSec.value -= 1

                val elapsedSec = initialDurationSec - _timeLeftSec.value
                intervalBells.forEach { bell ->
                    if (elapsedSec == bell.atSecFromStart) {
                        val resId = if (bell.soundType == "interval_bell") R.raw.interval_bell else R.raw.starting_bell
                        playBell(resId, bell.repeats, bell.volume)
                    }
                }
                updateNotification()
            }

            if (_timeLeftSec.value <= 0 && _isRunning.value) {
                playBell(R.raw.starting_bell, 1, startingBellVolume)
                saveMeditation(initialDurationSec / 60)
                stopMeditation()
            }
        }
    }

    fun stopMeditation() {
        val elapsedMinutes = (initialDurationSec - _timeLeftSec.value) / 60
        if (_isRunning.value && _timeLeftSec.value > 0 && elapsedMinutes > 0) {
            saveMeditation(elapsedMinutes)
        }
        
        _isRunning.value = false
        timerJob?.cancel()
        backgroundPlayer?.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val _lastSavedMeditationId = MutableStateFlow<Long?>(null)
    val lastSavedMeditationId: StateFlow<Long?> = _lastSavedMeditationId

    private fun saveMeditation(minutes: Int) {
        serviceScope.launch(Dispatchers.IO) {
            val id = repository.insertMeditation(Meditation(timestamp = System.currentTimeMillis(), durationMinutes = minutes))
            _lastSavedMeditationId.value = id
        }
    }

    fun updateVolume(volume: Float) {
        backgroundVolume = volume
        backgroundPlayer?.volume = volume
    }

    private fun playBell(resId: Int, repeats: Int, volume: Float) {
        val mediaItem = MediaItem.fromUri("android.resource://${packageName}/$resId")
        bellPlayer?.stop()
        bellPlayer?.clearMediaItems()
        bellPlayer?.volume = volume
        repeat(repeats) {
            bellPlayer?.addMediaItem(mediaItem)
        }
        bellPlayer?.prepare()
        bellPlayer?.play()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Meditation Timer Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", _timeLeftSec.value / 60, _timeLeftSec.value % 60)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zendence")
            .setContentText("Meditation in progress - $timeStr remaining")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        Log.d("MeditationService", "Service onDestroy")
        serviceScope.cancel()
        backgroundPlayer?.release()
        bellPlayer?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
