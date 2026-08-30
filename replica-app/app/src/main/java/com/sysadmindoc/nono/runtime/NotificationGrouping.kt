package com.sysadmindoc.nono.runtime

/**
 * Android 16 may synthesize group summaries. A summary represents the group, not another child
 * notification, so it must never become a second rule-evaluation event.
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
