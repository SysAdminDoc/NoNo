package com.sysadmindoc.nono.runtime

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
    fun `seven day retention uses calendar day boundary`() {
        assertEquals(
            Instant.parse("2026-08-24T12:00:00Z").toEpochMilli(),
            retentionCutoffEpochMillis(HistoryRetention.SEVEN_DAYS, reference),
        )
    }

    @Test
    fun `forever prunes nothing`() {
        // The delete runs with this cutoff on every insert, so it has to exclude every record.
        assertEquals(Long.MIN_VALUE, retentionCutoffEpochMillis(HistoryRetention.FOREVER, reference))
    }

    @Test
    fun `every option the dialog offers is honoured`() {
        // Offering a period the cutoff cannot express deletes the user's history behind their back.
        historyRetentionCatalog.forEach { label ->
            val retention = historyRetention(label)
            assertEquals("catalog entry $label does not resolve", label, retention.label)
        }
        assertEquals(HistoryRetention.entries.size, historyRetentionCatalog.size)
    }

    @Test
    fun `unknown setting safely falls back to thirty days`() {
        // "forever" used to be the example here, back when the dialog offered it and the code did
        // not implement it. It resolves now, so an actually unknown label is needed to test this.
        assertEquals(HistoryRetention.THIRTY_DAYS, historyRetention("until the heat death"))
        assertEquals(HistoryRetention.THIRTY_DAYS, historyRetention(null))
        assertEquals(HistoryRetention.FOREVER, historyRetention("forever"))
    }
}
