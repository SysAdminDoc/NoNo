package com.sysadmindoc.nono.runtime

import java.util.concurrent.atomic.AtomicReference

/**
 * What the app is allowed to write about a captured notification.
 *
 * There is deliberately no content option. This build never persists a title or a body, so a
 * setting offering to store content would promise something no code path performs. The choice
 * that remains is a real one: keep the metadata record, or keep nothing at all.
 */
enum class HistoryStorage(val label: String) {
    /** Package, timestamps and the platform's own assessment. Never notification content. */
    METADATA_ONLY("Metadata only"),

    /** Nothing new is written. Records already stored stay until the user deletes them. */
    OFF("Off"),
}

/** Exactly what the storage dialog may offer. Every entry is enforced by the listener. */
val historyStorageCatalog: List<String> = HistoryStorage.entries.map { it.label }

/**
 * Resolves a stored label, including the labels earlier builds wrote.
 *
 * "Store notification metadata", "All notifications" and "Store notification content" all
 * resolve to [HistoryStorage.METADATA_ONLY]: that is what those builds actually did, whatever
 * the dialog claimed, so the migration changes no behaviour.
 */
fun historyStorage(label: String?): HistoryStorage =
    HistoryStorage.entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
        ?: HistoryStorage.METADATA_ONLY

/** Process-wide setting bridge shared by the ViewModel and listener service. */
object HistoryStorageSettings {
    private val current = AtomicReference(HistoryStorage.METADATA_ONLY)

    fun set(storage: HistoryStorage) {
        current.set(storage)
    }

    fun set(label: String?) = set(historyStorage(label))

    fun get(): HistoryStorage = current.get()
}
