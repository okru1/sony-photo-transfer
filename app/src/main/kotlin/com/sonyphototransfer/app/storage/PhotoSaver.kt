package com.sonyphototransfer.app.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/** A file being written. Exactly one of [commit] or [abort] must be called. */
class SaveTarget(
    val uri: Uri,
    val output: OutputStream,
    private val onCommit: () -> Unit,
    private val onAbort: () -> Unit,
) {
    fun commit() {
        output.close()
        onCommit()
    }

    fun abort() {
        runCatching { output.close() }
        runCatching { onAbort() }
    }
}

/**
 * Writes downloaded photos without any storage permission:
 *  - default: MediaStore, Pictures/SonyPhotoTransfer, visible in the Android gallery;
 *  - or a folder the user picked with the system picker (Storage Access Framework).
 *
 * Files never overwrite each other: MediaStore entries get a free name, and SAF
 * providers rename on conflict themselves. Partially written files are deleted.
 */
class PhotoSaver(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun create(fileName: String, mimeType: String, dateTakenMillis: Long?, tree: Uri?): SaveTarget =
        if (tree != null) createInTree(tree, fileName, mimeType) else createInMediaStore(fileName, mimeType, dateTakenMillis)

    /** Size of what actually landed in storage, used to verify a transfer. */
    fun storedSize(uri: Uri): Long? =
        runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull()

    fun isTreeWritable(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun createInMediaStore(fileName: String, mimeType: String, dateTakenMillis: Long?): SaveTarget {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, freeMediaStoreName(collection, fileName))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            dateTakenMillis?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Android no permitió crear el archivo")
        val output = try {
            resolver.openOutputStream(uri, "w") ?: throw IOException("No se pudo abrir el archivo de destino")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SaveTarget(
            uri = uri,
            output = output,
            onCommit = {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            },
            onAbort = { resolver.delete(uri, null, null) },
        )
    }

    /** Picks "name (n).ext" when the app already saved a file with that name. */
    private fun freeMediaStoreName(collection: Uri, fileName: String): String {
        val stem = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var candidate = fileName
        var n = 1
        while (mediaStoreHas(collection, candidate) && n < 1000) {
            candidate = "$stem ($n)$ext"
            n++
        }
        return candidate
    }

    private fun mediaStoreHas(collection: Uri, name: String): Boolean {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(name, "$RELATIVE_PATH/")
        // Include our own pending entries (e.g. left by a crash) so names never clash.
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), bundle, null)
        } else {
            @Suppress("DEPRECATION")
            val withPending = MediaStore.setIncludePending(collection)
            resolver.query(withPending, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)
        }
        return cursor?.use { it.count > 0 } ?: false
    }

    private fun createInTree(tree: Uri, fileName: String, mimeType: String): SaveTarget {
        if (!isTreeWritable(tree)) {
            throw IOException("La carpeta de destino ya no es accesible. Elígela de nuevo en Ajustes.")
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(resolver, parent, mimeType, fileName)
            ?: throw IOException("No se pudo crear el archivo en la carpeta elegida")
        val output = try {
            resolver.openOutputStream(uri, "w") ?: throw IOException("No se pudo abrir el archivo de destino")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
        return SaveTarget(
            uri = uri,
            output = output,
            onCommit = {},
            onAbort = { DocumentsContract.deleteDocument(resolver, uri) },
        )
    }

    companion object {
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/SonyPhotoTransfer"
    }
}
