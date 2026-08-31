package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.GroupSummaryOrigin

/**
 * Android may group an app's notifications itself and post a summary alongside the children.
 * A summary represents the group, not another arrival, so it must never become a second
 * rule-evaluation event or a second entry in a count of what arrived.
 */
data class NotificationGrouping(
    val notificationKey: String,
    val groupKey: String?,
    val isGroupSummary: Boolean,
) {
    val evaluationKey: String
        get() = groupKey ?: notificationKey

    val shouldEvaluate: Boolean
        get() = !isGroupSummary
}

fun groupingFor(notification: SanitizedNotification): NotificationGrouping =
    NotificationGrouping(notification.notificationKey, notification.groupKey, notification.isGroupSummary)

/**
 * Classifies a summary from the two grouping facts Android publishes.
 *
 * Only one case is decidable from public API. A summary whose app declared the group, with no
 * override from the platform, is the app's: `Notification.getGroup` is non-null and
 * `StatusBarNotification.getOverrideGroupKey` is null. Everything else is
 * [GroupSummaryOrigin.UNKNOWN].
 *
 * [GroupSummaryOrigin.SYSTEM] is deliberately never inferred. AOSP builds its auto-group summary
 * with `setGroup(GroupHelper.AUTOGROUP_KEY)` and posts it with the same value as the override
 * key, so the platform's own summary arrives with both signals set, exactly like an app summary
 * the platform regrouped. An earlier revision read "no app group, but overridden" as SYSTEM,
 * which fires only for an app-posted summary that never called setGroup: the label named the
 * wrong author in the one case it appeared. Telling those apart needs AUTOGROUP_KEY itself, which
 * is not public API, so the honest answer is that it is unknown.
 *
 * Nothing about siblings is consulted. An app's own summary normally has children and so does an
 * auto-generated one, so their presence is not evidence either way.
 *
 * @param appDeclaredGroup whether `Notification.getGroup()` was set by the posting app.
 * @param overrideGroupKey `StatusBarNotification.getOverrideGroupKey()`, null below API 26.
 */
fun groupSummaryOrigin(
    isGroupSummary: Boolean,
    appDeclaredGroup: Boolean,
    overrideGroupKey: String?,
): GroupSummaryOrigin {
    if (!isGroupSummary) return GroupSummaryOrigin.UNKNOWN
    val overridden = !overrideGroupKey.isNullOrBlank()
    return if (appDeclaredGroup && !overridden) GroupSummaryOrigin.APP else GroupSummaryOrigin.UNKNOWN
}
