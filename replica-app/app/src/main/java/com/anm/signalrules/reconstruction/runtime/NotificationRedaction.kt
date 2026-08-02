package com.anm.signalrules.reconstruction.runtime

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.anm.signalrules.reconstruction.model.NotificationContentState

/**
 * The system can replace OTP and similar sensitive notification fields before delivering a
 * notification to an untrusted listener on Android 15+. The replacement is still a normal
 * StatusBarNotification, so treating its text as user content would create false rule matches.
 */
data class NotificationPayload(
    val title: CharSequence?,
    val text: CharSequence?,
    val appLabel: CharSequence?,
    val systemMarkedSensitive: Boolean = false,
)

data class SanitizedNotification(
    val notificationKey: String,
    val packageName: String,
    val postedAtEpochMillis: Long,
    val contentState: NotificationContentState,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
)

private const val ANDROID_SENSITIVE_CONTENT_EXTRA = "key_sensitive_content"

/**
 * This is intentionally conservative: an uncertain payload is never promoted to matchable
 * content. The Android marker is localized on some releases, so known English variants are
 * matched by shape and the explicit sensitive-content extra is preferred when available.
 */
fun classifyNotificationContent(
    payload: NotificationPayload,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationContentState {
    if (payload.systemMarkedSensitive ||
        (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            (isSystemRedactionMarker(payload.title) || isSystemRedactionMarker(payload.text)))
    ) {
        return NotificationContentState.HIDDEN_BY_SYSTEM
    }

    if (payload.title.isNullOrBlank() && payload.text.isNullOrBlank()) {
        return NotificationContentState.NOT_AVAILABLE
    }

    return NotificationContentState.AVAILABLE
}

/** Returns content eligible for a future matcher, or null for hidden/unavailable content. */
fun matchableNotificationText(
    payload: NotificationPayload,
    sdkInt: Int = Build.VERSION.SDK_INT,
): String? {
    if (classifyNotificationContent(payload, sdkInt) != NotificationContentState.AVAILABLE) return null
    return listOfNotNull(payload.title?.toString(), payload.text?.toString())
        .joinToString(" ")
        .trim()
        .takeIf(String::isNotBlank)
}

fun sanitizeNotification(sbn: StatusBarNotification): SanitizedNotification {
    val notification = sbn.notification
    val extras = notification.extras
    val payload = NotificationPayload(
        title = extras.getCharSequence(Notification.EXTRA_TITLE),
        text = extras.getCharSequence(Notification.EXTRA_TEXT),
        appLabel = null,
        systemMarkedSensitive = extras.getBoolean(ANDROID_SENSITIVE_CONTENT_EXTRA, false),
    )
    return SanitizedNotification(
        notificationKey = sbn.key,
        packageName = sbn.packageName,
        postedAtEpochMillis = sbn.postTime,
        contentState = classifyNotificationContent(payload),
        groupKey = notification.group,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
    )
}

private fun isSystemRedactionMarker(value: CharSequence?): Boolean {
    val normalized = value?.toString()?.trim()?.lowercase() ?: return false
    if (normalized.isBlank()) return false
    return normalized in REDACTION_MARKERS ||
        (normalized.contains("sensitive") && normalized.contains("hidden")) ||
        (normalized.contains("notification") && normalized.contains("content") && normalized.contains("hidden"))
}

private val REDACTION_MARKERS = setOf(
    "sensitive notification content hidden",
    "sensitive content hidden",
    "notification content hidden",
    "content hidden by the system",
)

/** Testable bridge for a synthetic Android Bundle without exposing platform placeholder text. */
fun notificationPayloadFromExtras(extras: Bundle, appLabel: CharSequence? = null): NotificationPayload =
    NotificationPayload(
        title = extras.getCharSequence(Notification.EXTRA_TITLE),
        text = extras.getCharSequence(Notification.EXTRA_TEXT),
        appLabel = appLabel,
        systemMarkedSensitive = extras.getBoolean(ANDROID_SENSITIVE_CONTENT_EXTRA, false),
    )
