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
 * `Notification.getGroup` is non-null when the app declared a group of its own;
 * `StatusBarNotification.getOverrideGroupKey` is non-null when the platform reorganised the
 * notification into a group it chose. Where those agree, the answer follows. Where they overlap,
 * or where neither says anything, the answer is [GroupSummaryOrigin.UNKNOWN], because guessing
 * here would be inventing authorship the platform never reported.
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
    return when {
        appDeclaredGroup && !overridden -> GroupSummaryOrigin.APP
        !appDeclaredGroup && overridden -> GroupSummaryOrigin.SYSTEM
        else -> GroupSummaryOrigin.UNKNOWN
    }
}
