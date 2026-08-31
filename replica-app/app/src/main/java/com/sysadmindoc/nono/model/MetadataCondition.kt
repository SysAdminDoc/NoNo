package com.sysadmindoc.nono.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A notification metadata property that can narrow a rule. */
enum class MetadataField(val label: String) {
    CHANNEL("Channel"),
    IMPORTANCE("Importance"),
    CATEGORY("Category"),
    CONVERSATION("Conversation"),
    ONGOING("Ongoing"),
    GROUP_SUMMARY("Group summary"),
}

/**
 * A typed, persistable condition over metadata Android exposes to the listener.
 *
 * Each subtype carries only the value its field can accept. This avoids the old extras list,
 * where arbitrary display strings looked configurable but could never be evaluated.
 */
@Serializable
sealed interface MetadataCondition

@Serializable
@SerialName("channel")
data class ChannelCondition(val channelPseudonym: String) : MetadataCondition

@Serializable
@SerialName("importance")
data class ImportanceCondition(val level: Int) : MetadataCondition

@Serializable
@SerialName("category")
data class CategoryCondition(val category: String) : MetadataCondition

@Serializable
@SerialName("conversation")
data class ConversationCondition(val required: Boolean) : MetadataCondition

@Serializable
@SerialName("ongoing")
data class OngoingCondition(val required: Boolean) : MetadataCondition

@Serializable
@SerialName("group_summary")
data class SummaryCondition(val required: Boolean) : MetadataCondition

val MetadataCondition.field: MetadataField
    get() = when (this) {
        is ChannelCondition -> MetadataField.CHANNEL
        is ImportanceCondition -> MetadataField.IMPORTANCE
        is CategoryCondition -> MetadataField.CATEGORY
        is ConversationCondition -> MetadataField.CONVERSATION
        is OngoingCondition -> MetadataField.ONGOING
        is SummaryCondition -> MetadataField.GROUP_SUMMARY
    }

/** A short value suitable for a rule row or picker. */
fun MetadataCondition.displayValue(): String = when (this) {
    is ChannelCondition -> channelPseudonym
    is ImportanceCondition -> importanceLabel(level) ?: "Unknown ($level)"
    is CategoryCondition -> categoryLabel(category)
    is ConversationCondition -> required.yesNo()
    is OngoingCondition -> required.yesNo()
    is SummaryCondition -> required.yesNo()
}

fun MetadataCondition.describe(): String = "${field.label}: ${displayValue()}"

fun SignalRule.metadataCondition(field: MetadataField): MetadataCondition? =
    metadataConditions.firstOrNull { it.field == field }

/** Replaces the one condition for [field], or removes it when [condition] is null. */
fun SignalRule.withMetadataCondition(
    field: MetadataField,
    condition: MetadataCondition?,
): SignalRule {
    require(condition == null || condition.field == field)
    val retained = metadataConditions.filterNot { it.field == field }
    return copy(metadataConditions = if (condition == null) retained else retained + condition)
}

/** Android notification categories that can be authored without free-form input. */
val notificationCategoryCatalog: List<Pair<String, String>> = listOf(
    "alarm" to "Alarm",
    "call" to "Call",
    "email" to "Email",
    "err" to "Error",
    "event" to "Event",
    "location_sharing" to "Location sharing",
    "msg" to "Message",
    "missed_call" to "Missed call",
    "navigation" to "Navigation",
    "progress" to "Progress",
    "promo" to "Promotion",
    "recommendation" to "Recommendation",
    "reminder" to "Reminder",
    "service" to "Service",
    "social" to "Social",
    "status" to "Status",
    "stopwatch" to "Stopwatch",
    "sys" to "System",
    "transport" to "Transport",
    "voicemail" to "Voicemail",
    "workout" to "Workout",
)

fun categoryLabel(category: String): String =
    notificationCategoryCatalog.firstOrNull { it.first == category }?.second ?: category

private fun Boolean.yesNo(): String = if (this) "Yes" else "No"
