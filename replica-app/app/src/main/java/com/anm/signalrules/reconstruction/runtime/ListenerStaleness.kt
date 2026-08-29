package com.anm.signalrules.reconstruction.runtime

import android.content.Context

/**
 * "It quietly stopped working" is the standard failure of every app in this category, and the
 * standard cause is an OEM battery manager unbinding the listener without telling anyone. The
 * platform reports that as a disconnect only some of the time; the rest of the time the service
 * looks connected and simply never receives another callback.
 *
 * So the app watches the clock as well as the connection. Access granted, listener apparently
 * connected, and nothing seen for hours is worth saying out loud.
 */
object ListenerActivityLog {
    private const val PREFERENCES = "runtime_history"
    private const val LAST_EVENT_AT = "last_event_at_epoch_millis"

    /** Minimum gap between writes. The value is read against a threshold measured in hours. */
    private const val WRITE_INTERVAL_MILLIS = 60_000L

    @Volatile
    private var lastWriteAt = 0L

    /**
     * Wall clock rather than elapsed realtime, because this has to survive a reboot.
     *
     * Throttled because this runs on the notification callback thread, and SharedPreferences
     * rewrites the whole file per commit: a burst would otherwise mean one full rewrite per
     * notification, with the queue drained on the main thread at the next pause.
     */
    fun recordEvent(context: Context, epochMillis: Long) {
        if (epochMillis - lastWriteAt < WRITE_INTERVAL_MILLIS && lastWriteAt != 0L) return
        lastWriteAt = epochMillis
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_EVENT_AT, epochMillis)
            .apply()
    }

    /** @return the last capture's wall clock, or null when this install has never captured one. */
    fun lastEventAt(context: Context): Long? = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getLong(LAST_EVENT_AT, 0L)
        .takeIf { it > 0L }
}

/** Hours of silence before a listener that claims to be healthy is treated as suspect. */
const val DEFAULT_LISTENER_STALE_AFTER_MILLIS = 12L * 60L * 60L * 1000L

enum class ListenerActivity {
    /** Seen a notification recently enough, or not in a state where silence means anything. */
    HEALTHY,

    /** Access is granted and the listener looks connected, but nothing has arrived in a long time. */
    STALE,
}

/**
 * Decides whether silence from a connected listener has gone on long enough to report.
 *
 * Silence only means something when the listener is supposed to be working: access granted and
 * not already reporting itself disconnected, since that case has its own message. Capture paused
 * by the user is silence they asked for. An install that has never captured anything is not stale
 * either, because there is no evidence it ever worked.
 */
fun listenerActivity(
    accessGranted: Boolean,
    connection: ListenerHealth.Connection,
    capturePaused: Boolean,
    lastEventAtEpochMillis: Long?,
    nowEpochMillis: Long,
    staleAfterMillis: Long = DEFAULT_LISTENER_STALE_AFTER_MILLIS,
): ListenerActivity {
    if (!accessGranted) return ListenerActivity.HEALTHY
    if (connection == ListenerHealth.Connection.DISCONNECTED) return ListenerActivity.HEALTHY
    if (capturePaused) return ListenerActivity.HEALTHY
    val lastEvent = lastEventAtEpochMillis ?: return ListenerActivity.HEALTHY
    // A clock that has moved backwards says nothing useful about the listener.
    val silence = nowEpochMillis - lastEvent
    if (silence < 0L) return ListenerActivity.HEALTHY
    return if (silence >= staleAfterMillis) ListenerActivity.STALE else ListenerActivity.HEALTHY
}

/**
 * Per-manufacturer steps for keeping the listener bound.
 *
 * Ordered by how often each one is the actual cause, so a user who stops after the first two
 * steps has still done the things most likely to help.
 */
fun oemListenerChecklist(manufacturer: String, sdkInt: Int): List<String> {
    val steps = mutableListOf<String>()
    steps += "Allow unrestricted battery use for Signal Rules in Android's battery settings."
    when (manufacturer.lowercase()) {
        "samsung" -> {
            steps += "Open Device care, then Battery, and add Signal Rules to Apps that won't be put to sleep."
            steps += "Turn off Put unused apps to sleep and Auto disable unused apps."
        }
        "xiaomi", "redmi", "poco" -> {
            steps += "Turn on Autostart for Signal Rules in Security, then Permissions."
            steps += "Set Battery saver for Signal Rules to No restrictions."
            steps += "Lock Signal Rules in Recents so the memory cleaner leaves it alone."
        }
        "huawei", "honor" -> {
            steps += "Set Signal Rules to Manage manually in Battery, then App launch."
            steps += "Enable Auto-launch, Secondary launch and Run in background."
        }
        "oneplus", "oppo", "realme" -> {
            steps += "Allow Auto-launch for Signal Rules."
            steps += "Set background power use to Allow, and turn off Deep optimisation and Sleep standby optimisation."
        }
        "vivo", "iqoo" -> {
            steps += "Allow Auto-start and High background power consumption for Signal Rules."
        }
        else -> {
            steps += "Look for an autostart, protected apps, or background restriction list in your battery settings and allow Signal Rules there."
        }
    }
    steps += "Lock Signal Rules in the Recents view if your launcher offers it."
    if (sdkInt >= 33) {
        // Android 13 blocks notification access for a sideloaded app until the user clears this.
        steps += "If notification access will not turn on, open App info, tap the menu, and choose Allow restricted settings."
    }
    steps += "Restart the phone if the listener still never connects."
    return steps
}
