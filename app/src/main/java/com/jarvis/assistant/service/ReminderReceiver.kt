package com.jarvis.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis_reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(context, "jarvis_reminders")
            .setSmallIcon(R.drawable.ic_jarvis).setContentTitle("JARVIS reminder").setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        JarvisService.instance?.speakExternal("Sir, a reminder: $text")
    }
}
