package com.example.barnameh12

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "study_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "یادآوری مطالعه", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subject = intent.getStringExtra("subject") ?: "زمان مطالعه"
        val duration = intent.getIntExtra("duration", 60)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("وقت مطالعه است 📚")
            .setContentText("$subject • مدت برنامه‌ریزی‌شده: $duration دقیقه")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(intent.getIntExtra("notificationId", 1), notification)
    }
}
