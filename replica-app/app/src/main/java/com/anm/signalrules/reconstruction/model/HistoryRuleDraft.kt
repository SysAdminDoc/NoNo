package com.anm.signalrules.reconstruction.model

data class HistoryRuleDraft(
    val app: String,
    val phrase: String,
    val provenanceMessage: String,
)

/**
 * Derives only fields that are explicitly safe to use in a new rule. Metadata-only and
 * platform-redacted records intentionally produce the neutral phrase rather than guessing.
 */
fun deriveRuleDraft(record: HistoryRecord): HistoryRuleDraft {
    val phrase = when {
        record.contentState != NotificationContentState.AVAILABLE -> "anything"
        record.title.isMeaningfulNotificationText() -> record.title.trim()
        record.body.isMeaningfulNotificationText() -> record.body.trim()
        else -> "anything"
    }
    val provenance = if (phrase == "anything") {
        when (record.contentState) {
            NotificationContentState.HIDDEN_BY_SYSTEM ->
                "Content hidden by system; no phrase was derived."
            NotificationContentState.NOT_STORED ->
                "Only notification metadata is stored; no phrase was derived."
            NotificationContentState.NOT_AVAILABLE ->
                "Notification content was unavailable; no phrase was derived."
            NotificationContentState.AVAILABLE ->
                "No meaningful content was available; no phrase was derived."
        }
    } else {
        "Phrase copied from the captured notification title."
    }
    return HistoryRuleDraft(app = record.app, phrase = phrase, provenanceMessage = provenance)
}

private fun String.isMeaningfulNotificationText(): Boolean =
    isNotBlank() && lowercase() !in setOf(
        "notification received",
        "content hidden by system",
        "metadata stored locally; notification content is not persisted.",
        "no notification content was supplied.",
    )
