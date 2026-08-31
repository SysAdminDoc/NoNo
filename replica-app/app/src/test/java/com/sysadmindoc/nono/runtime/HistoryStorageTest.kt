package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.data.NotificationEntity
import com.sysadmindoc.nono.model.defaultSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class HistoryStorageTest {

    @Test
    fun `the dialog offers only choices the listener enforces`() {
        assertEquals(listOf("Metadata only", "Off"), historyStorageCatalog)
        historyStorageCatalog.forEach { label ->
            assertEquals("catalog entry $label does not resolve", label, historyStorage(label).label)
        }
    }

    @Test
    fun `the shipped default is one of the offered choices`() {
        val default = defaultSettings["Notification history"]
        assertTrue("default $default is not offered", default in historyStorageCatalog)
        assertEquals(HistoryStorage.METADATA_ONLY, historyStorage(default))
    }

    @Test
    fun `labels written by earlier builds resolve to what those builds actually did`() {
        listOf("Store notification metadata", "All notifications", "Store notification content").forEach { legacy ->
            assertEquals("legacy label $legacy", HistoryStorage.METADATA_ONLY, historyStorage(legacy))
        }
        assertEquals(HistoryStorage.METADATA_ONLY, historyStorage(null))
        assertEquals(HistoryStorage.METADATA_ONLY, historyStorage("whatever the user typed"))
    }

    @Test
    fun `off resolves case insensitively`() {
        assertEquals(HistoryStorage.OFF, historyStorage("Off"))
        assertEquals(HistoryStorage.OFF, historyStorage("off"))
    }

    @Test
    fun `no persisted shape can carry notification content`() {
        // The storage choice is only honest if the metadata mode really is metadata. Both the
        // ingestion payload and the stored row are checked, because content would have to pass
        // through one to reach the other.
        val forbidden = listOf("title", "text", "body", "message", "content", "subtext", "ticker")
        listOf(SanitizedNotification::class.java, NotificationEntity::class.java).forEach { type ->
            type.declaredFields.forEach { field ->
                val name = field.name.lowercase()
                // contentState names the provenance enum, not anything the notification said.
                if (name == "contentstate") return@forEach
                forbidden.forEach { word ->
                    assertTrue(
                        "${type.simpleName}.${field.name} looks like notification content",
                        !name.contains(word),
                    )
                }
            }
        }
    }
}
