package com.anm.signalrules.reconstruction.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HistoryRetentionTest {

    private val reference = Instant.parse("2026-08-31T12:00:00Z").toEpochMilli()

    @Test
    fun `thirty day retention uses calendar day boundary`() {
        val cutoff = retentionCutoffEpochMillis(HistoryRetention.THIRTY_DAYS, reference)
        assertEquals(Instant.parse("2026-08-01T12:00:00Z").toEpochMilli(), cutoff)
    }

    @Test
    fun `three and six month retention use calendar months`() {
        assertEquals(
            Instant.parse("2026-05-31T12:00:00Z").toEpochMilli(),
            retentionCutoffEpochMillis(HistoryRetention.THREE_MONTHS, reference),
        )
        assertEquals(
            Instant.parse("2026-02-28T12:00:00Z").toEpochMilli(),
            retentionCutoffEpochMillis(HistoryRetention.SIX_MONTHS, reference),
        )
    }

    @Test
    fun `unknown setting safely falls back to thirty days`() {
        assertEquals(HistoryRetention.THIRTY_DAYS, historyRetention("forever"))
    }
}
