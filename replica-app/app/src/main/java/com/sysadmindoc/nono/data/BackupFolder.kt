package com.sysadmindoc.nono.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * The user's chosen backup folder, reached through the Storage Access Framework.
 *
 * Only what the user picked is reachable, the grant is theirs to withdraw at any time, and a
 * withdrawn grant has to surface as a reported failure rather than as a job that quietly stops.
 * Nothing here uses a filesystem path: there is no storage permission in this app and none is
 * wanted.
 */
object BackupFolder {

    /** Flags taken when the picker returns, and the ones checked before every later write. */
    const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Holds the tree grant across reboots so the job can write without the app being opened. */
    fun persist(context: Context, treeUri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(treeUri, GRANT_FLAGS)
        true
    }.getOrDefault(false)

    /** Releases a grant the app no longer needs, so a folder the user deselected stops being held. */
    fun release(context: Context, treeUri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(treeUri, GRANT_FLAGS) }
    }

    /**
     * @return true when this app still holds a writable persisted grant on [treeUri].
     *
     * Checked before every run. A user who revokes access in system settings, or a removable
     * volume that is gone, both look like this, and both must be reported.
     */
    fun hasWriteGrant(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }

    /** A short name for the chosen folder, for the Settings row. Falls back to the tree id. */
    fun describe(treeUri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.lastPathSegment.orEmpty()
        return documentId.substringAfterLast(':').ifBlank { documentId }
    }

    /**
     * Writes one document into the folder.
     *
     * @throws IOException when the provider refuses to create the document or closes the stream
     * early. The caller turns that into a reported failure.
     */
    @Throws(IOException::class)
    fun writeDocument(resolver: ContentResolver, treeUri: Uri, displayName: String, bytes: ByteArray) {
        // Nothing is removed before the write. A provider that will not create the document, or a
        // write that fails, must not have cost the user the backup that was already there under
        // this name. Providers do not overwrite, so a same-second collision comes back as
        // "name (1).json"; rotation's pattern accepts that shape rather than leaving it forever.
        val target = DocumentsContract.createDocument(resolver, directoryUri(treeUri), "application/json", displayName)
            ?: throw IOException("the folder refused a new file")
        try {
            val stream = resolver.openOutputStream(target) ?: throw IOException("the new file could not be opened")
            stream.use { it.write(bytes) }
        } catch (error: Throwable) {
            // The document already exists holding a truncated payload. Left there it would match
            // the rotation pattern, occupy one of the retained slots, and fail to decrypt when
            // somebody finally reached for it.
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw error
        }
    }

    /**
     * Every display name directly inside the folder. Subfolders are not descended into.
     *
     * @return null when the folder could not be read at all, which is not the same as a folder
     * holding nothing. Rotation has to be able to tell those apart, or an unreadable provider
     * looks like a folder with nothing to remove and the files build up unreported.
     */
    fun listNames(resolver: ContentResolver, treeUri: Uri): List<String>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val names = mutableListOf<String>()
        val cursor = resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        cursor.use {
            while (it.moveToNext()) {
                it.getString(0)?.let(names::add)
            }
        }
        return names
    }

    /**
     * @return true when the named document is gone, whether this call removed it or it was never
     * there. A concurrent run having already removed it is not a rotation failure, and reporting
     * one would put a warning in Settings after a run that did exactly what it should.
     */
    fun deleteByName(resolver: ContentResolver, treeUri: Uri, displayName: String): Boolean {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) != displayName) continue
                val document = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                return runCatching { DocumentsContract.deleteDocument(resolver, document) }.getOrDefault(false)
            }
        }
        return true
    }

    private fun directoryUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
}
