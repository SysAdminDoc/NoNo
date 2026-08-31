package com.sysadmindoc.nono.model

import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The removal reason codes, checked against the platform that is actually running.
 *
 * `RemovalReason` writes the numbers out rather than referencing the constants, because most of
 * them arrived after this app's minimum API and are not compilable at minSdk 24. That leaves the
 * numbers unchecked, and a wrong one would silently mislabel every record carrying it. This test
 * compiles against the current SDK and compares the two on a device, so a value that never matched
 * the platform, or one the platform later changes, fails here.
 */
@RunWith(AndroidJUnit4::class)
class RemovalReasonPlatformTest {

    @Test
    fun everyMappedCodeMatchesTheRunningPlatformsOwnConstant() {
        val expected = mapOf(
            NotificationListenerService.REASON_CLICK to RemovalReason.CLICKED,
            NotificationListenerService.REASON_CANCEL to RemovalReason.DISMISSED,
            NotificationListenerService.REASON_CANCEL_ALL to RemovalReason.DISMISSED_ALL,
            NotificationListenerService.REASON_ERROR to RemovalReason.PLATFORM_ERROR,
            NotificationListenerService.REASON_APP_CANCEL to RemovalReason.WITHDRAWN_BY_APP,
            NotificationListenerService.REASON_APP_CANCEL_ALL to RemovalReason.WITHDRAWN_BY_APP,
            NotificationListenerService.REASON_LISTENER_CANCEL to RemovalReason.CANCELLED_BY_LISTENER,
            NotificationListenerService.REASON_LISTENER_CANCEL_ALL to RemovalReason.CANCELLED_BY_LISTENER,
            NotificationListenerService.REASON_TIMEOUT to RemovalReason.TIMED_OUT,
            NotificationListenerService.REASON_SNOOZED to RemovalReason.SNOOZED,
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED to RemovalReason.GROUP_MANAGEMENT,
            NotificationListenerService.REASON_GROUP_OPTIMIZATION to RemovalReason.GROUP_MANAGEMENT,
            NotificationListenerService.REASON_UNAUTOBUNDLED to RemovalReason.GROUP_MANAGEMENT,
            NotificationListenerService.REASON_PACKAGE_CHANGED to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_USER_STOPPED to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_PACKAGE_BANNED to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_PACKAGE_SUSPENDED to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_PROFILE_TURNED_OFF to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_CHANNEL_BANNED to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_CHANNEL_REMOVED to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_CLEAR_DATA to RemovalReason.APP_STATE_CHANGED,
            NotificationListenerService.REASON_ASSISTANT_CANCEL to RemovalReason.ASSISTANT,
        )

        expected.forEach { (code, reason) ->
            assertEquals(
                "platform code $code",
                reason,
                RemovalReason.fromPlatform(code, Build.VERSION.SDK_INT),
            )
        }
    }

    @Test
    fun lockdownStaysUnknownBecauseTheNotificationIsHiddenRatherThanGone() {
        // Recording it would put "this device was locked down at this time" into a history the
        // user can export, and it would be wrong about the notification as well.
        assertEquals(
            RemovalReason.UNKNOWN,
            RemovalReason.fromPlatform(NotificationListenerService.REASON_LOCKDOWN, Build.VERSION.SDK_INT),
        )
    }

    @Test
    fun theBundleDismissalCodeIsTwentyFourOnThisPlatform() {
        // The whole point of the version gate: the number is only trusted from API 37 up.
        assertEquals(24, NotificationListenerService.REASON_BUNDLE_DISMISSED)
    }

    @Test
    fun aBundleDismissalIsLabelledOnlyWhereAndroidDefinesIt() {
        assumeTrue("Android 17 or newer only", Build.VERSION.SDK_INT >= 37)

        val reason = RemovalReason.fromPlatform(
            NotificationListenerService.REASON_BUNDLE_DISMISSED,
            Build.VERSION.SDK_INT,
        )

        assertEquals(RemovalReason.BUNDLE_DISMISSED, reason)
        assertEquals("Cleared with its bundle", reason.label)
        // Android does not say who dismissed the bundle, so this is not a user dismissal and must
        // not reach the Dismissed filter.
        assertEquals(false, reason.userDismissed)
        assertEquals(
            RemovalReason.UNKNOWN,
            RemovalReason.fromPlatform(NotificationListenerService.REASON_BUNDLE_DISMISSED, 36),
        )
    }
}
