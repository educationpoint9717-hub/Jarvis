package com.jarvis.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService

class JarvisNotificationListener : NotificationListenerService() {
    companion object { @Volatile var instance: JarvisNotificationListener? = null }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }

    fun snapshot(): List<String> {
        val pm = packageManager
        return (activeNotifications ?: emptyArray())
            .filter { it.packageName != packageName && !it.isOngoing }
            .sortedByDescending { it.postTime }
            .take(10)
            .map { n ->
                val e = n.notification.extras
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString() } catch (_: Exception) { n.packageName }
                "$app: ${e.getCharSequence(Notification.EXTRA_TITLE) ?: ""} - ${e.getCharSequence(Notification.EXTRA_TEXT) ?: ""}"
            }
    }
}
