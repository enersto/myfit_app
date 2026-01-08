package com.example.myfit.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myfit.MainActivity
import com.example.myfit.R

object NotificationHelper {
    private const val CHANNEL_ID = "timer_channel"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Workout Timer"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Shows active workout timer"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateTimerNotification(context: Context, taskName: String?, endTimeMillis: Long?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)

        if (endTimeMillis != null && taskName != null) {
            val title = context.getString(R.string.notify_training_title, taskName)

            val remainingSeconds = (endTimeMillis - System.currentTimeMillis()) / 1000
            val timeString = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)

            builder.setContentTitle(title)
                .setWhen(endTimeMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setContentText(context.getString(R.string.notify_time_remaining, timeString))

            builder.addAction(R.drawable.ic_launcher_foreground, context.getString(R.string.timer_stop), pendingIntent)

        } else {
            builder.setContentTitle(context.getString(R.string.notify_paused_title))
                .setContentText(context.getString(R.string.notify_click_resume))
                .setOngoing(false)
                .setUsesChronometer(false)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}