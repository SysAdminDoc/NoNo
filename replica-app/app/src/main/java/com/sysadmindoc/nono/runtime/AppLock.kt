package com.sysadmindoc.nono.runtime

/** The Settings label the lock is stored under, read by the view model at startup. */
const val APP_LOCK_SETTING = "Lock the app"

/**
 * How long the app may sit in the background before it locks again.
 *
 * Long enough that checking a notification and coming straight back does not mean typing a PIN,
 * short enough that a phone left on a desk is not standing open.
 */
const val APP_LOCK_GRACE_MILLIS = 60_000L

/** Why the lock is not available, said in the UI instead of an unexplained disabled row. */
const val NO_DEVICE_CREDENTIAL =
    "Set a screen lock in Android settings first. NoNo uses that lock rather than one of its own."

/**
 * Whether the app should be locked right now.
 *
 * The state deliberately lives in memory only. Nothing persists "unlocked", so a process that has
 * been killed and restarted comes back locked, which is the safe direction and is also the whole
 * behaviour asked of it across process death.
 *
 * @param enabled the user's setting.
 * @param deviceSecure whether Android has a screen lock to check against. Without one there is
 * nothing to unlock with, and staying locked would shut the user out of their own app.
 * @param lastUnlockedElapsed uptime at the last successful unlock, or null if there has not been
 * one in this process.
 * @param leftForegroundElapsed uptime when the app was last backgrounded, or null while it is in
 * the foreground.
 * @param nowElapsed uptime now. Uptime rather than wall clock: the wall clock can be moved.
 */
fun shouldLock(
    enabled: Boolean,
    deviceSecure: Boolean,
    lastUnlockedElapsed: Long?,
    leftForegroundElapsed: Long?,
    nowElapsed: Long,
    graceMillis: Long = APP_LOCK_GRACE_MILLIS,
): Boolean {
    if (!enabled || !deviceSecure) return false
    if (lastUnlockedElapsed == null) return true
    if (leftForegroundElapsed == null) return false
    // A clock that appears to run backwards is not evidence the app was away for no time. Treat
    // anything that cannot be measured as long enough.
    val away = nowElapsed - leftForegroundElapsed
    return away < 0L || away >= graceMillis
}
