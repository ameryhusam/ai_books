package com.example.data.workspace

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

/**
 * Transparent local I/O abstraction supporting both filesystem paths
 * (`/data/...`) and Android Storage Access Framework URIs (`content://...`)
 * without copying user workspace files into app-private storage.
 */
object UriIO {

    fun isContentUri(pathOrUri: String?): Boolean =
        !pathOrUri.isNullOrBlank() && pathOrUri.trim().startsWith("content://", ignoreCase = true)

    fun isTreeUri(pathOrUri: String?): Boolean {
        if (!isContentUri(pathOrUri)) return false
        val uri = runCatching { Uri.parse(pathOrUri) }.getOrNull() ?: return false
        return runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false)
    }

    fun openInputStream(context: Context, pathOrUri: String?): InputStream? {
        val target = pathOrUri?.trim().orEmpty()
        if (target.isBlank()) return null
        return if (isContentUri(target)) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(target)) }.getOrNull()
        } else {
            val file = File(target)
            if (file.isFile) runCatching { file.inputStream() }.getOrNull() else null
        }
    }

    fun openReadPfd(context: Context, pathOrUri: String?): ParcelFileDescriptor? {
        val target = pathOrUri?.trim().orEmpty()
        if (target.isBlank()) return null
        return if (isContentUri(target)) {
            runCatching { context.contentResolver.openFileDescriptor(Uri.parse(target), "r") }.getOrNull()
        } else {
            val file = File(target)
            if (file.isFile) {
                runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            } else null
        }
    }

    fun readText(context: Context, pathOrUri: String?, maxBytes: Int = 4 * 1024 * 1024): String? {
        return openInputStream(context, pathOrUri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > maxBytes) {
                bytes.copyOf(maxBytes).toString(Charsets.UTF_8)
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    fun readBytes(context: Context, pathOrUri: String?): ByteArray? {
        return openInputStream(context, pathOrUri)?.use { it.readBytes() }
    }

    fun fileLength(context: Context, pathOrUri: String?): Long {
        val target = pathOrUri?.trim().orEmpty()
        if (target.isBlank()) return 0L
        if (!isContentUri(target)) {
            val file = File(target)
            return if (file.isFile) file.length() else 0L
        }
        val uri = runCatching { Uri.parse(target) }.getOrNull() ?: return 0L
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else -1L
                } else -1L
            }
        }.getOrNull() ?: -1L
        if (queried >= 0L) return queried
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)
    }

    fun displayName(context: Context, pathOrUri: String?): String {
        val target = pathOrUri?.trim().orEmpty()
        if (target.isBlank()) return ""
        if (!isContentUri(target)) return File(target).name
        val uri = runCatching { Uri.parse(target) }.getOrNull() ?: return target
        val fromCursor = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
                } else null
            }
        }.getOrNull()
        return fromCursor?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: target)
    }

    fun exists(context: Context, pathOrUri: String?): Boolean {
        val target = pathOrUri?.trim().orEmpty()
        if (target.isBlank()) return false
        if (!isContentUri(target)) return File(target).isFile
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(target), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
