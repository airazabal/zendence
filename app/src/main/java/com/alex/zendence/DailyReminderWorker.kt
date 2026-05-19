package com.alex.zendence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as ZendenceApp).repository
        val history = repository.getAllMeditations()
        
        val streak = repository.calculateStreak(history)
        val weeklyMinutes = repository.calculateWeeklyMinutes(history)
        val lastInsight = history.firstOrNull { it.insight != null }?.insight

        val message = StringBuilder("Ready for your session? ")
        if (streak > 0) message.append("You're on a $streak day streak! ")
        message.append("You've meditated for $weeklyMinutes mins this week. ")
        lastInsight?.let { message.append("\nLast insight: \"$it\"") }

        showNotification(message.toString())
        return Result.success()
    }

    private fun showNotification(content: String) {
        val channelId = "daily_reminders"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Daily Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Zendence")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()

        notificationManager.notify(1001, notification)
    }
}
