package com.sysadmindoc.nono.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's number stands for "notifications that arrived", which is not the same as "rows
 * stored". Leaving that difference silent made the widget disagree with History for no reason
 * the user could see.
 */
class WidgetCountLabelTest {

    @Test
    fun anEmptyStoreSaysSoRatherThanReportingZero() {
        assertEquals("No metadata captured", SignalWidgetProvider.countLabel(0, 0))
    }

    @Test
    fun withNoSummariesTheCountStandsAlone() {
        assertEquals("12 notifications", SignalWidgetProvider.countLabel(12, 0))
    }

    @Test
    fun storedSummariesAreNamedAndExcludedFromTheCount() {
        val label = SignalWidgetProvider.countLabel(12, 3)

        assertTrue(label.startsWith("12 notifications"))
        assertTrue("the label must say what it left out", label.contains("3 group summaries"))
        assertTrue(label.contains("not counted"))
    }

    @Test
    fun aStoreOfNothingButSummariesDoesNotReadAsEmpty() {
        // Otherwise the widget says "No metadata captured" while History shows three rows.
        val label = SignalWidgetProvider.countLabel(0, 3)

        assertTrue(label.contains("3 group summaries"))
        assertTrue("no notifications arrived, and that is what it should say", label.contains("no notifications"))
    }
}
