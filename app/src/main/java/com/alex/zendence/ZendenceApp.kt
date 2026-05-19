package com.alex.zendence

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ZendenceApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "meditation-db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository by lazy {
        val sharedPrefs = getSharedPreferences("zendence_prefs", Context.MODE_PRIVATE)
        MeditationRepository(database.meditationDao(), sharedPrefs)
    }

    override fun onCreate() {
        super.onCreate()
        scheduleDailyReminder()
    }

    private fun scheduleDailyReminder() {
        val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .addTag("daily_reminder")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun calculateInitialDelay(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis - now
    }
}
