package com.sysadmindoc.nono.model

import java.util.Calendar
import java.util.TimeZone
import kotlinx.serialization.Serializable

/** Minutes in a day. A window is expressed in these, from local midnight. */
const val MINUTES_PER_DAY = 24 * 60

/**
 * Which clock a schedule is read against.
 *
 * Only one policy exists, and it is stated rather than assumed: a window the user set at 22:00
 * means 22:00 where they are, and it moves with them and with daylight saving. A fixed-offset or
 * UTC policy would mean the same rule fired an hour early for half the year, so if one is ever
 * added it has to be a deliberate choice with its own name, not a silent change to this one.
 */
enum class ScheduleZonePolicy {
    /** The device's current time zone, whatever it is when the notification arrives. */
    DEVICE_LOCAL,
}

/**
 * A recurring local-time window a rule is limited to.
 *
 * @property days ISO weekday numbers, Monday 1 through Sunday 7. Empty means no day is selected,
 * which is a schedule that can never match rather than one that always does: an empty selection is
 * something the user did, and reading it as "every day" would fire a rule they had switched off.
 * @property startMinute minutes from local midnight, inclusive.
 * @property endMinute minutes from local midnight, exclusive. A value at or below [startMinute]
 * crosses midnight, so 22:00 to 07:00 is start 1320, end 420.
 */
@Serializable
data class RuleSchedule(
    val days: Set<Int> = ALL_DAYS,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val zonePolicy: ScheduleZonePolicy = ScheduleZonePolicy.DEVICE_LOCAL,
) {
    /** True when the window covers the whole day, which is what equal ends mean. */
    val coversWholeDay: Boolean get() = startMinute == endMinute

    /** True when the window runs past local midnight into the next day. */
    val crossesMidnight: Boolean get() = endMinute < startMinute

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
    }
}

/**
 * Whether [epochMillis] falls inside [schedule].
 *
 * Read through [Calendar], not through arithmetic on the epoch value. The offset from UTC is not a
 * constant: it changes at each daylight-saving transition, and on some zones it has changed for
 * political reasons too. Dividing the epoch by 86,400,000 gives the right answer for about half
 * the year in half the world.
 *
 * The day a window belongs to is the day it *starts*, which is what makes a midnight-crossing
 * window mean what a person means by it. "Weeknights, 22:00 to 07:00" includes 01:00 on Saturday
 * morning, because that is Friday night, and excludes 01:00 on Monday morning, because that is
 * Sunday night.
 *
 * @param zone the device zone. Passed in rather than read here so a test can state one.
 */
fun matchesSchedule(
    schedule: RuleSchedule?,
    epochMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Boolean {
    if (schedule == null) return true
    if (schedule.days.isEmpty()) return false
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = epochMillis }
    val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val today = isoWeekday(calendar.get(Calendar.DAY_OF_WEEK))

    if (schedule.coversWholeDay) return today in schedule.days
    if (!schedule.crossesMidnight) {
        return today in schedule.days && minuteOfDay >= schedule.startMinute && minuteOfDay < schedule.endMinute
    }
    // Past midnight: either late on a selected day, or early on the morning after one.
    if (minuteOfDay >= schedule.startMinute) return today in schedule.days
    if (minuteOfDay < schedule.endMinute) return previousIsoWeekday(today) in schedule.days
    return false
}

/** Calendar numbers Sunday 1 through Saturday 7. ISO numbers Monday 1 through Sunday 7. */
internal fun isoWeekday(calendarDayOfWeek: Int): Int =
    if (calendarDayOfWeek == Calendar.SUNDAY) 7 else calendarDayOfWeek - 1

internal fun previousIsoWeekday(isoDay: Int): Int = if (isoDay == 1) 7 else isoDay - 1

/** "22:00" for 1320. Used on the rule card and in the schedule editor. */
fun formatMinuteOfDay(minuteOfDay: Int): String {
    val normalized = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val hours = normalized / 60
    val minutes = normalized % 60
    return (if (hours < 10) "0$hours" else "$hours") + ":" + (if (minutes < 10) "0$minutes" else "$minutes")
}

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * What the rule card says about a schedule.
 *
 * Deliberately not localised, like every other string in this build: the app ships no translated
 * resources, and inventing a locale-aware day name here while the rest of the screen is English
 * would be worse than being consistent.
 */
fun describeSchedule(schedule: RuleSchedule?): String {
    if (schedule == null) return "Any time"
    if (schedule.days.isEmpty()) return "Never: no day is selected"
    val days = when {
        schedule.days == RuleSchedule.ALL_DAYS -> "Every day"
        schedule.days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        schedule.days == setOf(6, 7) -> "Weekends"
        else -> schedule.days.sorted().joinToString(", ") { dayNames[it - 1] }
    }
    if (schedule.coversWholeDay) return "$days, all day"
    val window = formatMinuteOfDay(schedule.startMinute) + " to " + formatMinuteOfDay(schedule.endMinute)
    return if (schedule.crossesMidnight) "$days, $window the next morning" else "$days, $window"
}
