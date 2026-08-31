package com.sysadmindoc.nono.runtime

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sysadmindoc.nono.model.NotificationContentState

/**
 * The system can replace OTP and similar sensitive notification fields before delivering a
 * notification to an untrusted listener on Android 15+. The replacement is still a normal
 * StatusBarNotification, and the platform publishes no supported way to tell one apart, so an
 * empty payload is reported as unavailable rather than attributed to Android.
 */
data class NotificationPayload(
    val title: CharSequence?,
    val text: CharSequence?,
    val appLabel: CharSequence?,
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
    val matchState: com.sysadmindoc.nono.model.RuleMatchState,
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

/**
 * This is intentionally conservative: an uncertain payload is never promoted to matchable
 * content, and it is never attributed to Android either.
 *
 * Android 15 redacts sensitive notifications for untrusted listeners, but as of API 37 there is
 * no public flag, ranking method, or documented extra that says so, and the placeholder text is
 * localized. An earlier revision guessed from an undocumented extra key and a list of English
 * strings, which claimed provenance the platform never supplied and would misread an app that
 * happened to post "content hidden". Missing content is now simply
 * [NotificationContentState.NOT_AVAILABLE].
 *
 * @param sdkInt kept in the signature because callers pass the device level and the
 * classification is deliberately the same on every one of them.
 */
@Suppress("UNUSED_PARAMETER")
fun classifyNotificationContent(
    payload: NotificationPayload,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationContentState {
    payload.contentStateOverride?.let { return it }

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

/** Testable bridge for a synthetic Android Bundle. */
fun notificationPayloadFromExtras(
    extras: Bundle,
    appLabel: CharSequence? = null,
    packageName: String? = null,
): NotificationPayload =
    NotificationPayload(
        title = extras.getCharSequence(Notification.EXTRA_TITLE),
        text = extras.getCharSequence(Notification.EXTRA_TEXT),
        appLabel = appLabel,
        packageName = packageName,
    )
