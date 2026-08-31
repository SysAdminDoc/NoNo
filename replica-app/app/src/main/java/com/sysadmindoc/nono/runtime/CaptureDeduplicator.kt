package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.RuleMatchState

/** How long two identical posts of one notification count as the same capture. */
const val CAPTURE_DEDUPLICATION_WINDOW_MILLIS = 2_000L

/**
 * Everything about a capture that a repost could meaningfully change.
 *
 * The posting time is deliberately absent: it changes on every repost, and a fingerprint that
 * included it would never match itself. Whether two posts arrived close together is the window's
 * job, not the fingerprint's.
 */
fun captureFingerprint(
    sanitized: SanitizedNotification,
    matchedRuleIds: List<Long>,
    matchState: RuleMatchState,
): String = listOf(
    sanitized.contentState.name,
    sanitized.channelId.orEmpty(),
    sanitized.groupKey.orEmpty(),
    sanitized.overrideGroupKey.orEmpty(),
    sanitized.isGroupSummary.toString(),
    sanitized.groupSummaryOrigin.name,
    sanitized.importance?.toString().orEmpty(),
    sanitized.isConversation?.toString().orEmpty(),
    sanitized.category.orEmpty(),
    sanitized.isOngoing.toString(),
    matchState.name,
    matchedRuleIds.sorted().joinToString(","),
    // Separated, so two different field splits cannot concatenate to the same string.
).joinToString(" ")

/**
 * Collapses a burst of identical posts into one capture.
 *
 * Apps repost a notification to update a progress bar, a chat count, or nothing at all, and the
 * platform delivers every one of them. Each was previously a separate diagnostic increment, a
 * separate activity-log entry, and a separate widget broadcast, so an app that reposts once a
 * second looked like a flood of new notifications.
 *
 * Only an unchanged post inside the window is dropped. A changed fingerprint always gets through,
 * because that is a real update to the stored row.
 *
 * Process-scoped and bounded: the map is capped so a device posting thousands of distinct
 * notifications cannot grow it without limit.
 */
class CaptureDeduplicator(
    private val windowMillis: Long = CAPTURE_DEDUPLICATION_WINDOW_MILLIS,
    private val maxEntries: Int = 256,
) {
    private data class Seen(val fingerprint: String, val atEpochMillis: Long)

    // Access-ordered so the eviction below drops the least recently seen key.
    private val seen = object : LinkedHashMap<String, Seen>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Seen>?): Boolean =
            size > maxEntries
    }

    /**
     * @return true when this post should be captured, false when it repeats one already taken.
     */
    fun shouldCapture(notificationKey: String, fingerprint: String, nowEpochMillis: Long): Boolean =
        synchronized(this) {
            val previous = seen[notificationKey]
            val repeat = previous != null &&
                previous.fingerprint == fingerprint &&
                nowEpochMillis - previous.atEpochMillis in 0 until windowMillis
            if (repeat) return false
            seen[notificationKey] = Seen(fingerprint, nowEpochMillis)
            true
        }

    fun clear() = synchronized(this) { seen.clear() }
}
