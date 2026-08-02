package com.anm.signalrules.reconstruction.runtime

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.anm.signalrules.reconstruction.data.SignalDatabase
import com.anm.signalrules.reconstruction.data.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: SignalDatabase
    private lateinit var ingestor: NotificationIngestor<SanitizedNotification>

    override fun onCreate() {
        super.onCreate()
        database = SignalDatabase.create(applicationContext)
        ingestor = NotificationIngestor(serviceScope) { notification ->
            database.notificationDao().insertAndPrune(
                notification.toEntity(),
                retentionCutoffEpochMillis(
                    HistoryRetentionSettings.get(),
                    System.currentTimeMillis(),
                ),
            )
        }
        serviceScope.launch {
            ingestor.metrics.collect(ListenerHealth::updateIngestionMetrics)
        }
    }

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
        // This callback runs on the main thread from API 24 onward. Sanitization is in-memory;
        // all Room I/O is performed by the bounded worker.
        val sanitized = sanitizeNotification(notification)
        ingestor.offer(sanitized)
        SignalObservability.emit(
            SignalEvent(
                type = SignalEventType.NOTIFICATION_CAPTURED,
                traceId = newTraceId(),
                contentState = sanitized.contentState,
            ),
        )
        ListenerHealth.recordEvent(SystemClock.elapsedRealtime())
    }

    override fun onDestroy() {
        if (::ingestor.isInitialized && ::database.isInitialized) serviceScope.launch {
            ingestor.close()
            database.close()
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, SignalNotificationListener::class.java)

        /**
         * Asks the platform to rebind the listener. Safe at any time, and a no-op when access
         * was never granted. Available since API 24, which is this app's minimum.
         */
        fun requestRebindIfPossible(context: Context) {
            runCatching { requestRebind(componentName(context)) }
        }
    }
}
