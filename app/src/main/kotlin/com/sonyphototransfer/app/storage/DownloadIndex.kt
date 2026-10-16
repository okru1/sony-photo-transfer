package com.sonyphototransfer.app.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DownloadedPhoto(
    /** [com.sonyphototransfer.camera.CameraPhoto.stableKey] of the source shot. */
    val key: String,
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val savedAtMillis: Long,
    /** True when this is the camera's JPEG preview of a RAW, not the original file. */
    val isPreview: Boolean,
)

/**
 * Remembers what has already been downloaded so the gallery can mark it and the
 * transfer queue can skip duplicates. Stored as a small JSON file in app storage.
 */
class DownloadIndex(context: Context) {
    private val file = File(context.filesDir, "downloads.json")
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<Map<String, DownloadedPhoto>> = _entries.asStateFlow()

    fun contains(key: String) = _entries.value.containsKey(key)

    @Synchronized
    fun add(entry: DownloadedPhoto) {
        _entries.value = _entries.value + (entry.key to entry)
        save()
    }

    @Synchronized
    fun remove(key: String) {
        _entries.value = _entries.value - key
        save()
    }

    private fun load(): Map<String, DownloadedPhoto> = runCatching {
        if (!file.exists()) return emptyMap()
        val array = JSONArray(file.readText())
        (0 until array.length()).associate { i ->
            val o = array.getJSONObject(i)
            val entry = DownloadedPhoto(
                key = o.getString("key"),
                uri = Uri.parse(o.getString("uri")),
                fileName = o.getString("fileName"),
                sizeBytes = o.getLong("sizeBytes"),
                savedAtMillis = o.getLong("savedAt"),
                isPreview = o.optBoolean("isPreview", false),
            )
            entry.key to entry
        }
    }.getOrDefault(emptyMap())

    private fun save() {
        val array = JSONArray()
        _entries.value.values.forEach { e ->
            array.put(
                JSONObject()
                    .put("key", e.key)
                    .put("uri", e.uri.toString())
                    .put("fileName", e.fileName)
                    .put("sizeBytes", e.sizeBytes)
                    .put("savedAt", e.savedAtMillis)
                    .put("isPreview", e.isPreview),
            )
        }
        // Write-then-rename so a crash never leaves a half-written index.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(file)
    }
}
