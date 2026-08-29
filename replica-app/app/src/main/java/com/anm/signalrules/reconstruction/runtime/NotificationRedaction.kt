package com.anm.signalrules.reconstruction.runtime

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
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
    val packageName: String? = null,
    /** Used by metadata-only previews to preserve provenance without supplying content. */
    val contentStateOverride: NotificationContentState? = null,
)

/**
 * A sanitized record plus the rules that matched it, which is what the worker persists.
 *
 * Kept separate from [SanitizedNotification] because matching is a property of the user's saved
 * rules rather than of redaction.
 */
data class CapturedNotification(
    val sanitized: SanitizedNotification,
    val matchedRuleIds: List<Long>,
    val matchState: com.anm.signalrules.reconstruction.model.RuleMatchState,
)

data class SanitizedNotification(
    val notificationKey: String,
    val packageName: String,
    val postedAtEpochMillis: Long,
    val contentState: NotificationContentState,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val channelId: String? = null,
    /** Channel importance the platform assigned, 0 to 5, or null below API 26. */
    val importance: Int? = null,
    /** Whether the platform treats this as a conversation. Null below API 31. */
    val isConversation: Boolean? = null,
    /** Platform category constant such as msg or email. A fixed vocabulary, never user text. */
    val category: String? = null,
    val isOngoing: Boolean = false,
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
    payload.contentStateOverride?.let { return it }
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

/**
 * Reads the fields a rule can be evaluated against.
 *
 * Deliberately separate from [sanitizeNotification] so a caller that needs to evaluate a rule can
 * hold the payload for exactly as long as that takes. Nothing returned here is ever persisted.
 */
fun notificationPayload(sbn: StatusBarNotification): NotificationPayload {
    val extras = sbn.notification.extras
    return NotificationPayload(
        title = extras.getCharSequence(Notification.EXTRA_TITLE),
        text = extras.getCharSequence(Notification.EXTRA_TEXT),
        appLabel = null,
        systemMarkedSensitive = extras.getBoolean(ANDROID_SENSITIVE_CONTENT_EXTRA, false),
        packageName = sbn.packageName,
    )
}

/**
 * Reads the platform's own assessment of a notification.
 *
 * Importance, conversation status, category and the ongoing flag all come from Android rather
 * than from anything the notification says, so they can be stored without holding content. They
 * are what tells a silent promotion apart from a priority conversation, which the app previously
 * could not distinguish at all.
 */
fun sanitizeNotification(
    sbn: StatusBarNotification,
    payload: NotificationPayload = notificationPayload(sbn),
    ranking: NotificationListenerService.Ranking? = null,
): SanitizedNotification {
    val notification = sbn.notification
    return SanitizedNotification(
        notificationKey = sbn.key,
        packageName = sbn.packageName,
        postedAtEpochMillis = sbn.postTime,
        contentState = classifyNotificationContent(payload),
        channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else null,
        groupKey = notification.group,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ranking?.importance else null,
        isConversation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ranking?.isConversation else null,
        category = notification.category,
        isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
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
fun notificationPayloadFromExtras(
    extras: Bundle,
    appLabel: CharSequence? = null,
    packageName: String? = null,
): NotificationPayload =
    NotificationPayload(
        title = extras.getCharSequence(Notification.EXTRA_TITLE),
        text = extras.getCharSequence(Notification.EXTRA_TEXT),
        appLabel = appLabel,
        systemMarkedSensitive = extras.getBoolean(ANDROID_SENSITIVE_CONTENT_EXTRA, false),
        packageName = packageName,
    )
