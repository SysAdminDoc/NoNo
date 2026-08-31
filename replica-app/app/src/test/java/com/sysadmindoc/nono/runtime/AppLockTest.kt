package com.sysadmindoc.nono.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app is locked.
 *
 * The dangerous direction is not "fails to lock", it is "locks and cannot be opened". A user who
 * removed their screen lock while this setting was on has no way to prove who they are, and a lock
 * that stayed on would take their rules with it.
 */
class AppLockTest {

    @Test
    fun anAppWithTheSettingOffIsNeverLocked() {
        assertFalse(shouldLock(enabled = false, deviceSecure = true, lastUnlockedElapsed = null, leftForegroundElapsed = null, nowElapsed = 0L))
    }

    @Test
    fun aDeviceWithNoScreenLockIsNeverLocked() {
        // There is nothing to unlock with. Locking here shuts the user out of their own rules with
        // nothing they can do about it.
        assertFalse(shouldLock(enabled = true, deviceSecure = false, lastUnlockedElapsed = null, leftForegroundElapsed = null, nowElapsed = 0L))
        assertFalse(shouldLock(enabled = true, deviceSecure = false, lastUnlockedElapsed = 1L, leftForegroundElapsed = 1L, nowElapsed = 10_000_000L))
    }

    @Test
    fun aFreshProcessStartsLocked() {
        // Nothing writes "unlocked" to disk, so a process that was killed has no record of one and
        // comes back locked. That is the state across process death, and it is the safe direction.
        assertTrue(shouldLock(enabled = true, deviceSecure = true, lastUnlockedElapsed = null, leftForegroundElapsed = null, nowElapsed = 5_000L))
    }

    @Test
    fun anUnlockedAppInTheForegroundStaysUnlocked() {
        assertFalse(shouldLock(enabled = true, deviceSecure = true, lastUnlockedElapsed = 1_000L, leftForegroundElapsed = null, nowElapsed = 9_999_999L))
    }

    @Test
    fun aShortTripAwayDoesNotAskAgain() {
        // Opening a notification and coming straight back should not mean typing a PIN.
        assertFalse(
            shouldLock(
                enabled = true,
                deviceSecure = true,
                lastUnlockedElapsed = 1_000L,
                leftForegroundElapsed = 2_000L,
                nowElapsed = 2_000L + APP_LOCK_GRACE_MILLIS - 1L,
            ),
        )
    }

    @Test
    fun theGracePeriodEndsExactlyWhenItSaysItDoes() {
        assertTrue(
            shouldLock(
                enabled = true,
                deviceSecure = true,
                lastUnlockedElapsed = 1_000L,
                leftForegroundElapsed = 2_000L,
                nowElapsed = 2_000L + APP_LOCK_GRACE_MILLIS,
            ),
        )
    }

    @Test
    fun aClockThatAppearsToRunBackwardsLocks() {
        // Uptime should not go backwards, and if the number says it did, the safe reading is that
        // the measurement is worthless rather than that no time passed.
        assertTrue(
            shouldLock(
                enabled = true,
                deviceSecure = true,
                lastUnlockedElapsed = 1_000L,
                leftForegroundElapsed = 9_000L,
                nowElapsed = 2_000L,
            ),
        )
    }

    @Test
    fun aLongerGraceIsHonouredWhenOneIsGiven() {
        assertFalse(
            shouldLock(
                enabled = true,
                deviceSecure = true,
                lastUnlockedElapsed = 0L,
                leftForegroundElapsed = 0L,
                nowElapsed = 120_000L,
                graceMillis = 300_000L,
            ),
        )
    }

    @Test
    fun theGracePeriodIsMeasuredInMinutesNotHours() {
        // A phone left on a desk should not stand open all afternoon.
        assertTrue(APP_LOCK_GRACE_MILLIS in 1_000L..300_000L)
    }
}
