package com.sysadmindoc.nono.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sysadmindoc.nono.R
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val SELF_TEST_CHANNEL_ID = "nono_capture_self_test"
private const val SELF_TEST_NOTIFICATION_ID = 0x4E4F
private const val SELF_TEST_TAG_PREFIX = "nono-self-test-"
const val CAPTURE_SELF_TEST_TIMEOUT_MILLIS = 8_000L

sealed interface CaptureSelfTestOutcome {
    data class Passed(val elapsedMillis: Long) : CaptureSelfTestOutcome
    data object TimedOut : CaptureSelfTestOutcome
    data object NotificationsBlocked : CaptureSelfTestOutcome
    data object AlreadyRunning : CaptureSelfTestOutcome
    data object PostFailed : CaptureSelfTestOutcome
}

/**
 * Posts one temporary notification and waits for the real listener callback to acknowledge it.
 * The listener consumes the matching callback before sanitization, storage, counters, or rules.
 */
object CaptureSelfTest {
    suspend fun run(
        context: Context,
        timeoutMillis: Long = CAPTURE_SELF_TEST_TIMEOUT_MILLIS,
    ): CaptureSelfTestOutcome {
        require(timeoutMillis > 0L)
        val app = context.applicationContext
        val notifications = NotificationManagerCompat.from(app)
        if (!runCatching { notifications.areNotificationsEnabled() }.getOrDefault(false)) {
            return CaptureSelfTestOutcome.NotificationsBlocked
        }

        val key = CaptureSelfTestKey(
            packageName = app.packageName,
            tag = SELF_TEST_TAG_PREFIX + UUID.randomUUID(),
            notificationId = SELF_TEST_NOTIFICATION_ID,
        )
        val observed = CaptureSelfTestProbe.arm(key)
            ?: return CaptureSelfTestOutcome.AlreadyRunning
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            createChannel(app)
            val notification = NotificationCompat.Builder(app, SELF_TEST_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setContentTitle("NoNo capture check")
                .setContentText("This temporary notification checks the listener connection.")
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .build()
            notifications.notify(key.tag, key.notificationId, notification)
            val received = withTimeoutOrNull(timeoutMillis) {
                observed.await()
                true
            } ?: false
            if (received) {
                CaptureSelfTestOutcome.Passed(SystemClock.elapsedRealtime() - startedAt)
            } else {
                CaptureSelfTestOutcome.TimedOut
            }
        } catch (_: SecurityException) {
            CaptureSelfTestOutcome.NotificationsBlocked
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            CaptureSelfTestOutcome.PostFailed
        } finally {
            runCatching { notifications.cancel(key.tag, key.notificationId) }
            CaptureSelfTestProbe.cancel(key)
        }
    }

    /** Returns true only once for the exact self-test notification currently armed. */
    fun consumeIfExpected(notification: StatusBarNotification, selfPackage: String): Boolean {
        if (notification.packageName != selfPackage) return false
        return CaptureSelfTestProbe.acknowledge(
            CaptureSelfTestKey(
                packageName = notification.packageName,
                tag = notification.tag.orEmpty(),
                notificationId = notification.id,
            ),
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SELF_TEST_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                SELF_TEST_CHANNEL_ID,
                "Capture self-test",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Temporary notifications used only when you run the capture check."
            },
        )
    }
}
