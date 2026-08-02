package com.anm.signalrules.reconstruction.runtime

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Least-privilege reconstruction surface. It records only sanitized counters and package names;
 * it never logs or transmits notification content and performs no automatic side effects.
 *
 * The connection callbacks matter as much as the posting callback. The platform unbinds
 * listeners routinely - after an app update, a service crash, or an OEM background kill - and
 * a listener that never asks to be rebound simply stops working until the user toggles
 * notification access by hand. `requestRebind` is the one method documented as safe to call
 * outside the connected window, so it is the recovery path used here.
 */
class SignalNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerHealth.onConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerHealth.onDisconnected()
        // The platform ignores this when access has been revoked, in which case the health
        // state shown in the app is what tells the user why nothing is happening.
        requestRebindIfPossible(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        // In-memory only. This callback runs on the main thread from API 24 onward, so the
        // previous synchronous SharedPreferences write ran twice per notification from every
        // app on the device, into a file nothing ever read.
        ListenerHealth.recordEvent(SystemClock.elapsedRealtime())
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, SignalNotificationListener::class.java)

        /**
         * Asks the platform to rebind the listener. Safe at any time, and a no-op when access
         * was never granted. Available from API 24; below that the settings screen is the only
         * recovery.
         */
        fun requestRebindIfPossible(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            runCatching { requestRebind(componentName(context)) }
        }
    }
}
