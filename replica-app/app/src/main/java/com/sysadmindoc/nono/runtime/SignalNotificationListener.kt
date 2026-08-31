package com.sysadmindoc.nono.runtime

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.sysadmindoc.nono.data.IdentifierPseudonyms
import com.sysadmindoc.nono.data.PseudonymKeyStore
import com.sysadmindoc.nono.data.SignalDatabase
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.data.decodeRules
import com.sysadmindoc.nono.data.toEntity
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    private val acceptingCallbacks = AtomicBoolean(false)
    private val shutdownStarted = AtomicBoolean(false)
    private lateinit var database: SignalDatabase
    private lateinit var ingestor: NotificationIngestor<CapturedNotification>
    private lateinit var pseudonyms: IdentifierPseudonyms

    /**
     * The worker waits on this before its first write, so a cold-started service cannot prune
     * with the process default retention or store a record the user switched off.
     */
    private val settings = ListenerSettingsGate()

    /** Collapses a burst of identical reposts into one capture. */
    private val deduplicator = CaptureDeduplicator()

    /**
     * Latest saved rules, kept here so evaluation stays on the callback thread with the payload.
     * Read on the platform's callback thread and written by the collector below.
     */
    @Volatile
    private var currentRules: List<SignalRule>? = null

    override fun onCreate() {
        super.onCreate()
        acceptingCallbacks.set(true)
        CaptureGate.load(applicationContext)
        database = SignalDatabase.get(applicationContext)
        pseudonyms = PseudonymKeyStore.get(applicationContext.noBackupFilesDir)
        serviceScope.launch {
            // One shot per install: rows written before the pseudonym scheme still hold the
            // identifiers the posting apps chose.
            runCatching { database.notificationDao().pseudonymizeStoredIdentifiers(pseudonyms) }
        }
        ingestor = NotificationIngestor(serviceScope) { captured ->
            // Off is a storage policy, not a capture pause: nothing new is written, and what is
            // already stored stays until the user deletes it.
            val written = persistCapture(settings.awaitSettings(), System.currentTimeMillis()) { cutoff ->
                database.notificationDao().insertAndPrune(
                    captured.sanitized.toEntity(captured.matchedRuleIds, captured.matchState),
                    cutoff,
                )
            }
            if (written) SignalWidgetProvider.requestUpdate(applicationContext)
            written
        }
        serviceScope.launch {
            // The platform can start this service with no Activity ever having run, so the rules
            // and the storage settings are read here rather than handed over by the view model.
            SignalPreferences.get(applicationContext).data
                .catch { emit(emptyPreferences()) }
                .collect { preferences ->
                    // Published first: a decode that throws must not leave the worker waiting on
                    // settings that will never arrive.
                    settings.publish(listenerSettings(preferences))
                    currentRules = decodeRules(preferences[SignalPreferences.RULES_KEY]).orEmpty()
                }
        }
        serviceScope.launch {
            var previous = IngestionMetrics()
            ingestor.metrics.drop(1).collect { current ->
                ListenerHealth.updateIngestionMetrics(current)
                val persistedDelta = current.persisted - previous.persisted
                val droppedDelta = current.dropped - previous.dropped
                val failedDelta = current.failed - previous.failed
                runCatching {
                    database.notificationDao().mergeIngestionMetrics(
                        persistedDelta = persistedDelta,
                        droppedDelta = droppedDelta,
                        failedDelta = failedDelta,
                        failureAtEpochMillis = if (failedDelta > 0L) System.currentTimeMillis() else null,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                }
                previous = current
            }
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) = onNotificationPosted(sbn, null)

    /**
     * The platform's ranking arrives alongside the notification, and carries what Android itself
     * thinks of it: channel importance and, from API 31, whether this is a conversation.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (!acceptingCallbacks.get()) return
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        if (CaptureGate.isPaused()) return
        // This callback runs on the main thread from API 24 onward. Sanitization is in-memory;
        // all Room I/O is performed by the bounded worker.
        val payload = notificationPayload(notification)
        val ranking = rankingMap?.let { map ->
            NotificationListenerService.Ranking().takeIf { map.getRanking(notification.key, it) }
        }
        val sanitized = sanitizeNotification(notification, pseudonyms, payload, ranking)
        // Evaluated here, while the payload is still in scope, and only rule ids are kept. The
        // payload itself goes no further than this stack frame.
        val rules = currentRules
        val evaluation = when {
            // One policy, shared with the counts: a summary stands for its group rather than
            // being an arrival, so no rule is tested against it and it is not counted as one.
            !groupingFor(sanitized).shouldEvaluate ->
                CaptureEvaluation(emptyList(), RuleMatchState.GROUP_SUMMARY)
            // The platform can deliver a notification before the store has been read.
            rules == null -> CaptureEvaluation(emptyList(), RuleMatchState.RULES_NOT_LOADED)
            else -> evaluateCapture(rules, payload)
        }
        // An app that reposts to move a progress bar delivers the same notification many times
        // over. Dropping the unchanged repeats here means one capture, one activity increment,
        // and one widget refresh, rather than one of each per post.
        val now = System.currentTimeMillis()
        val fingerprint = captureFingerprint(sanitized, evaluation.matchedRuleIds, evaluation.state)
        if (!deduplicator.shouldCapture(sanitized.notificationKey, fingerprint, now)) return

        ingestor.offer(CapturedNotification(sanitized, evaluation.matchedRuleIds, evaluation.state))
        SignalObservability.emit(
            SignalEvent(
                type = SignalEventType.NOTIFICATION_CAPTURED,
                traceId = newTraceId(),
                contentState = sanitized.contentState,
            ),
        )
        ListenerHealth.recordEvent(SystemClock.elapsedRealtime())
        // Durable, so silence can still be measured after the process or the phone restarts.
        ListenerActivityLog.recordEvent(applicationContext, System.currentTimeMillis())
    }

    override fun onDestroy() {
        acceptingCallbacks.set(false)
        if (shutdownStarted.compareAndSet(false, true)) {
            if (::ingestor.isInitialized) {
                serviceScope.launch {
                    try {
                        // The worker drains the closed queue so no write is lost. The database
                        // itself is process-shared and deliberately stays open: the view model and
                        // the widget read through the same instance after the service stops.
                        ingestor.close()
                    } finally {
                        serviceScope.cancel()
                    }
                }
            } else {
                serviceScope.cancel()
            }
        }
        super.onDestroy()
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, SignalNotificationListener::class.java)

        /**
         * Asks the platform to rebind the listener after a disconnected callback. Available since
         * API 24, which is this app's minimum.
         */
        fun requestRebindIfPossible(context: Context) {
            // UNKNOWN counts: the service has not called back in this process, which is what an
            // OEM background kill looks like. Only a listener that reported itself connected is
            // excluded, because it has nothing to rebind.
            if (ListenerHealth.connection.value == ListenerHealth.Connection.CONNECTED) return
            runCatching { requestRebind(componentName(context)) }
        }
    }
}
