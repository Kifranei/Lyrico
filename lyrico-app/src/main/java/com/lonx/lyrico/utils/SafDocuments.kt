package com.lonx.lyrico.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/** One child of a SAF tree, as returned by [SafDocuments.children]. */
data class SafDocumentRow(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val uri: Uri,
    val isDirectory: Boolean
)

/**
 * Reads SAF folders through [DocumentsContract] queries.
 *
 * Shared by the music folder scanner and the artist poster folders so every feature sees exactly
 * the same files - `DocumentFile` is deliberately avoided, it reports an empty folder instead of
 * failing when a provider does not like the request.
 */
object SafDocuments {
    private const val TAG = "SafDocuments"

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    /**
     * The direct children of [parentDocumentId], or of the tree root when it is `null`.
     *
     * Returns `null` when the provider could not be queried (deleted folder, revoked access),
     * which callers use to tell "folder unavailable" apart from "folder is empty".
     */
    fun children(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String? = null
    ): List<SafDocumentRow>? = try {
        val parentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val documentIdIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val displayNameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val sizeIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_SIZE
            )
            val lastModifiedIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            buildList {
                while (cursor.moveToNext()) {
                    val documentId = cursor.getStringOrNull(documentIdIndex) ?: continue
                    val displayName = cursor.getStringOrNull(displayNameIndex) ?: continue
                    val mimeType = cursor.getStringOrNull(mimeTypeIndex)

                    add(
                        SafDocumentRow(
                            documentId = documentId,
                            displayName = displayName,
                            mimeType = mimeType,
                            size = cursor.getLongOrZero(sizeIndex),
                            lastModified = cursor.getLongOrZero(lastModifiedIndex),
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "读取 SAF 子文件失败: parentDocumentId=$parentDocumentId", e)
        null
    }
}

private fun Cursor.getStringOrNull(index: Int): String? {
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.getLongOrZero(index: Int): Long {
    if (index < 0 || isNull(index)) return 0L
    return runCatching { getLong(index) }.getOrDefault(0L)
}
