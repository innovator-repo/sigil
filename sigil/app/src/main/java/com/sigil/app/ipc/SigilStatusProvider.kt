package com.sigil.app.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.sigil.app.SigilApplication
import com.sigil.app.logic.SigilStatus
import kotlinx.coroutines.runBlocking

/**
 * Read-only `content://com.sigil.app.provider/status`: one row with noteCount, sealedCount,
 * clipArmed (0/1), lastTitle and lastEditedAt. lastTitle only ever comes from a note with no
 * sealed chip. Guarded in the manifest by the ecosystem signature permission, for reads and
 * writes, exactly like the other Lume apps' providers.
 */
class SigilStatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.lastPathSegment != "status") return null
        val app = context?.applicationContext as? SigilApplication ?: return null
        val status = runCatching {
            runBlocking { SigilStatus.compute(app.repository.allItems(), app.clipboard.armed) }
        }.getOrNull() ?: SigilStatus.EMPTY
        return MatrixCursor(SigilStatus.COLUMNS, 1).apply { addRow(status.toRow()) }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.sigil.app.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
