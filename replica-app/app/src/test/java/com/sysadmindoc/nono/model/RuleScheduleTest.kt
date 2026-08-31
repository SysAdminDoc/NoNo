package com.sysadmindoc.nono.model

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schedule windows.
 *
 * The awkward cases are the point: a window that runs past midnight, and the two days a year when
 * a local clock does not have twenty-four hours in it. Both are handled by reading the local time
 * through a calendar rather than by dividing the epoch, and both are pinned here because the
 * arithmetic version is right for most of the year and quietly wrong for the rest.
 */
class RuleScheduleTest {

    private val london = TimeZone.getTimeZone("Europe/London")
    private val newYork = TimeZone.getTimeZone("America/New_York")
    private val kolkata = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone,
    ): Long = GregorianCalendar(zone, Locale.ROOT).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    private val nights = RuleSchedule(
        days = setOf(1, 2, 3, 4, 5),
        startMinute = 22 * 60,
        endMinute = 7 * 60,
    )

    @Test
    fun `no schedule means any time`() {
        assertTrue(matchesSchedule(null, at(2026, 6, 1, 3, 0, london), london))
        assertTrue(matchesSchedule(null, at(2026, 6, 1, 15, 0, london), london))
    }

    @Test
    fun `a window across midnight matches late evening and not midday`() {
        // The case named in the roadmap: 22:00 to 07:00 covers 23:30 and does not cover 12:00.
        val wednesday2330 = at(2026, 9, 2, 23, 30, london)
        val wednesdayNoon = at(2026, 9, 2, 12, 0, london)

        assertTrue(matchesSchedule(nights, wednesday2330, london))
        assertFalse(matchesSchedule(nights, wednesdayNoon, london))
    }

    @Test
    fun `the morning after a selected day belongs to that day`() {
        // 01:00 on Saturday is Friday night, and a weeknight schedule includes it. 01:00 on Monday
        // is Sunday night, and it does not. Getting this wrong is what makes people say a schedule
        // "sort of works".
        val saturday0100 = at(2026, 9, 5, 1, 0, london)
        val monday0100 = at(2026, 9, 7, 1, 0, london)

        assertTrue("Saturday 01:00 is Friday night", matchesSchedule(nights, saturday0100, london))
        assertFalse("Monday 01:00 is Sunday night", matchesSchedule(nights, monday0100, london))
    }

    @Test
    fun `the boundaries are start inclusive and end exclusive`() {
        val schedule = RuleSchedule(days = RuleSchedule.ALL_DAYS, startMinute = 9 * 60, endMinute = 17 * 60)
        assertTrue(matchesSchedule(schedule, at(2026, 9, 2, 9, 0, london), london))
        assertTrue(matchesSchedule(schedule, at(2026, 9, 2, 16, 59, london), london))
        assertFalse("17:00 is the end, not the last minute", matchesSchedule(schedule, at(2026, 9, 2, 17, 0, london), london))
        assertFalse(matchesSchedule(schedule, at(2026, 9, 2, 8, 59, london), london))
    }

    @Test
    fun `equal ends mean the whole day, on the selected days only`() {
        val allDay = RuleSchedule(days = setOf(6, 7), startMinute = 0, endMinute = 0)
        assertTrue(allDay.coversWholeDay)
        assertTrue(matchesSchedule(allDay, at(2026, 9, 5, 4, 0, london), london))
        assertTrue(matchesSchedule(allDay, at(2026, 9, 6, 23, 59, london), london))
        assertFalse(matchesSchedule(allDay, at(2026, 9, 4, 12, 0, london), london))
    }

    @Test
    fun `selecting no day is a rule that never matches, not one that always does`() {
        // An empty selection is something the user did. Reading it as "every day" would fire a
        // rule they had just switched off.
        val none = RuleSchedule(days = emptySet(), startMinute = 0, endMinute = 0)
        assertFalse(matchesSchedule(none, at(2026, 9, 2, 12, 0, london), london))
        assertFalse(matchesSchedule(none, at(2026, 9, 2, 3, 0, london), london))
    }

    @Test
    fun `the spring gap, where a local hour does not exist`() {
        // New York moves 02:00 to 03:00 on 8 March 2026. The window is the hour after the jump, so
        // it is only satisfied by reading the clock the user reads: shifting the epoch by the
        // zone's standard offset puts the same instant at 02:30, outside the window.
        val schedule = RuleSchedule(days = RuleSchedule.ALL_DAYS, startMinute = 3 * 60, endMinute = 4 * 60)
        val beforeJump = at(2026, 3, 8, 1, 30, newYork)
        val afterJump = at(2026, 3, 8, 3, 30, newYork)

        assertFalse("01:30 is before the window", matchesSchedule(schedule, beforeJump, newYork))
        assertTrue("03:30 is inside it", matchesSchedule(schedule, afterJump, newYork))
        // Two hours of wall clock apart, one hour of real time: the 02:00 hour never happens.
        assertEquals(60 * 60 * 1000L, afterJump - beforeJump)
    }

    @Test
    fun `the autumn overlap, where a local hour happens twice`() {
        // New York repeats 01:00 to 02:00 on 1 November 2026. Both passes read 01:30 on the wall
        // clock and both are inside a 01:00 to 02:00 window; shifting the epoch by the standard
        // offset puts the first of them at 00:30 and misses it.
        val schedule = RuleSchedule(days = RuleSchedule.ALL_DAYS, startMinute = 60, endMinute = 2 * 60)
        // Anchored at an unambiguous local time and stepped forward, because 01:30 itself is
        // ambiguous that morning and a calendar has to pick one of the two for you.
        val firstPass = at(2026, 11, 1, 0, 30, newYork) + 60 * 60 * 1000L
        val secondPass = firstPass + 60 * 60 * 1000L

        assertTrue(matchesSchedule(schedule, firstPass, newYork))
        assertTrue(matchesSchedule(schedule, secondPass, newYork))

        for (instant in listOf(firstPass, secondPass)) {
            val local = Calendar.getInstance(newYork).apply { timeInMillis = instant }
            assertEquals("the wall clock reads 01:30 twice", 1, local.get(Calendar.HOUR_OF_DAY))
            assertEquals(30, local.get(Calendar.MINUTE))
        }
    }

    @Test
    fun `a half hour zone is read from the clock, not from an offset in whole hours`() {
        // Kolkata is UTC+5:30. An implementation that rounded the offset would put every window
        // half an hour out for the whole country.
        val schedule = RuleSchedule(days = RuleSchedule.ALL_DAYS, startMinute = 9 * 60, endMinute = 10 * 60)
        assertTrue(matchesSchedule(schedule, at(2026, 6, 1, 9, 15, kolkata), kolkata))
        assertFalse(matchesSchedule(schedule, at(2026, 6, 1, 8, 45, kolkata), kolkata))
    }

    @Test
    fun `the same instant belongs to different windows in different places`() {
        // The policy is the device's local clock, so a rule set for 22:00 means 22:00 wherever the
        // phone is, and the same notification falls inside the window in one zone and not another.
        val schedule = RuleSchedule(days = RuleSchedule.ALL_DAYS, startMinute = 22 * 60, endMinute = 23 * 60)
        val instant = at(2026, 6, 1, 22, 30, london)

        assertTrue(matchesSchedule(schedule, instant, london))
        assertFalse(matchesSchedule(schedule, instant, newYork))
        assertEquals(ScheduleZonePolicy.DEVICE_LOCAL, schedule.zonePolicy)
    }

    @Test
    fun `calendar weekdays map onto iso weekdays`() {
        assertEquals(1, isoWeekday(Calendar.MONDAY))
        assertEquals(5, isoWeekday(Calendar.FRIDAY))
        assertEquals(6, isoWeekday(Calendar.SATURDAY))
        assertEquals(7, isoWeekday(Calendar.SUNDAY))
        assertEquals(7, previousIsoWeekday(1))
        assertEquals(1, previousIsoWeekday(2))
    }

    @Test
    fun `minutes render as a clock`() {
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("07:05", formatMinuteOfDay(7 * 60 + 5))
        assertEquals("22:00", formatMinuteOfDay(22 * 60))
        assertEquals("23:59", formatMinuteOfDay(MINUTES_PER_DAY - 1))
    }

    @Test
    fun `a schedule describes itself the way it was set`() {
        assertEquals("Any time", describeSchedule(null))
        assertEquals("Weekdays, 22:00 to 07:00 the next morning", describeSchedule(nights))
        assertEquals(
            "Weekends, all day",
            describeSchedule(RuleSchedule(days = setOf(6, 7), startMinute = 0, endMinute = 0)),
        )
        assertEquals(
            "Every day, 09:00 to 17:00",
            describeSchedule(RuleSchedule(days = RuleSchedule.ALL_DAYS, startMinute = 540, endMinute = 1020)),
        )
        assertEquals(
            "Never: no day is selected",
            describeSchedule(RuleSchedule(days = emptySet())),
        )
        assertEquals(
            "Tue, Thu, 06:00 to 08:00",
            describeSchedule(RuleSchedule(days = setOf(2, 4), startMinute = 360, endMinute = 480)),
        )
    }
}
