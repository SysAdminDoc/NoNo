package com.sysadmindoc.nono.runtime

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sysadmindoc.nono.data.IdentifierPseudonyms
import com.sysadmindoc.nono.model.GroupSummaryOrigin
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

/**
 * @property notificationKey a per-install pseudonym, not Android's key. The key embeds the tag
 * the posting app chose, which is a string an app can put anything in.
 * @property packageName kept verbatim: rules match on it and it is not app-authored free text.
 * @property channelId a per-install pseudonym for the same reason as [notificationKey].
 * @property groupKey likewise.
 */
data class SanitizedNotification(
    val notificationKey: String,
    val packageName: String,
    val postedAtEpochMillis: Long,
    val contentState: NotificationContentState,
    val groupKey: String? = null,
    /** The platform's own group, pseudonymized like the rest. Null below API 26 or when unset. */
    val overrideGroupKey: String? = null,
    val isGroupSummary: Boolean = false,
    /** Never inferred from siblings. See [groupSummaryOrigin]. */
    val groupSummaryOrigin: GroupSummaryOrigin = GroupSummaryOrigin.UNKNOWN,
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
    pseudonyms: IdentifierPseudonyms,
    payload: NotificationPayload = notificationPayload(sbn),
    ranking: NotificationListenerService.Ranking? = null,
): SanitizedNotification {
    val notification = sbn.notification
    val isSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    // Public since API 26. Below that the platform did not reorganise groups at all.
    val overrideGroupKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sbn.overrideGroupKey else null
    return SanitizedNotification(
        // Pseudonymized here, while the raw values are still on the stack, so nothing downstream
        // has to remember to do it.
        notificationKey = pseudonyms.pseudonym(sbn.key).orEmpty(),
        packageName = sbn.packageName,
        postedAtEpochMillis = sbn.postTime,
        contentState = classifyNotificationContent(payload),
        channelId = pseudonyms.pseudonym(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else null,
        ),
        groupKey = pseudonyms.pseudonym(notification.group),
        overrideGroupKey = pseudonyms.pseudonym(overrideGroupKey),
        isGroupSummary = isSummary,
        groupSummaryOrigin = groupSummaryOrigin(
            isGroupSummary = isSummary,
            // Notification.getGroup is public on every level this app supports, and it says
            // exactly what the classification needs: whether the app declared a group of its own.
            // StatusBarNotification.isAppGroup would say the same thing but needs API 30, and it
            // also counts a bare sort key, which is not a group declaration.
            appDeclaredGroup = notification.group != null,
            overrideGroupKey = overrideGroupKey,
        ),
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
