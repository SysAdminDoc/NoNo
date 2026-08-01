package com.anm.signalrules.reconstruction.runtime

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Least-privilege reconstruction surface. It records only sanitized counters and package names;
 * it never logs or transmits notification content and performs no automatic side effects.
 */
class SignalNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        getSharedPreferences("runtime_history", MODE_PRIVATE).edit()
            .putString("last_package", notification.packageName)
            .putLong("last_seen_at", System.currentTimeMillis())
            .putInt("seen_count", getSharedPreferences("runtime_history", MODE_PRIVATE).getInt("seen_count", 0) + 1)
            .apply()
    }
}
