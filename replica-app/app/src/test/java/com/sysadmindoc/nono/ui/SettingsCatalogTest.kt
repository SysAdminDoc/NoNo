package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.DISMISS_FIXED_SETTING
import com.sysadmindoc.nono.model.defaultSettings
import com.sysadmindoc.nono.model.muteImportanceCatalog
import com.sysadmindoc.nono.model.muteModeCatalog
import com.sysadmindoc.nono.runtime.BackupCadence
import com.sysadmindoc.nono.runtime.WidgetScope
import com.sysadmindoc.nono.runtime.historyRetentionCatalog
import com.sysadmindoc.nono.runtime.historyStorageCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings row shows a stored value and its dialog offers a list of them. Nothing connects the
 * two, so they can drift apart silently, and one already had: the seeded "Mute importance" value
 * was not among the choices, so the dialog opened with nothing selected and the row displayed a
 * value that could not be picked again.
 */
class SettingsCatalogTest {

    /** Every setting whose row opens a list, and the list that row opens. */
    private val dialogBacked = mapOf(
        "Mute mode" to muteModeCatalog,
        "Mute importance" to muteImportanceCatalog,
        "Notification history" to historyStorageCatalog,
        "History retention" to historyRetentionCatalog,
        "Theme" to themeCatalog(sdkInt = 31),
        "Automatic backups" to BackupCadence.entries.map { it.label },
        "Widget count" to WidgetScope.entries.map { it.label },
    )

    @Test
    fun everySeededValueIsSomethingItsDialogOffers() {
        dialogBacked.forEach { (setting, choices) ->
            val seeded = defaultSettings[setting]
            assertTrue("$setting has no seeded value", seeded != null)
            assertTrue(
                "$setting is seeded \"$seeded\", which its dialog does not offer: $choices",
                seeded in choices,
            )
        }
    }

    @Test
    fun noDialogOffersTheSameChoiceTwice() {
        dialogBacked.forEach { (setting, choices) ->
            assertTrue("$setting repeats a choice: $choices", choices.size == choices.distinct().size)
        }
    }

    @Test
    fun theDismissRowsStoredKeyIsOneThatIsSeeded() {
        // The row is titled "Dismiss fixed notifications" and the seeded preference is
        // "Allow dismissing fixed notifications". Keying by the title read neither.
        assertTrue(DISMISS_FIXED_SETTING in defaultSettings)
    }
}
