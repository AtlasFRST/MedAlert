package com.example.medalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat



class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the alarm fires.
        val time = intent.getStringExtra("ALARM_TIME") ?: "your medication"
        val requestCode = intent.getIntExtra("REQUEST_CODE", 0)
        Log.d("AlarmReceiver", "ALARM TRIGGERED for time: $time")

        val userId = intent.getStringExtra("USER_ID")

        if (userId == null) {
            Log.e("AlarmReceiver", "CRITICAL: User ID not passed in intent. Cannot create notification action.")
            return
        }

        Log.d("AlarmReceiver", "ALARM TRIGGERED for user: $userId, time: $time")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val contentIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


        // Create and show a notification
        val channelId = "alarm_channel"

        // Create a notification channel for Android 8.0 (API 26) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("REQUEST_CODE", requestCode)
        }

        val actionPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode, // Must be a unique code for the PendingIntent as well
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Medication Reminder")
            .setContentText("It's time to take your medication ($time).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // Set the intent to open the app on tap
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check_mark, "Mark as Taken", actionPendingIntent)
            .build()


        notificationManager.notify(requestCode, notification)
    }
}