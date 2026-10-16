package com.sonyphototransfer.app.transfer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sonyphototransfer.app.camera.CameraSession
import com.sonyphototransfer.app.data.FormatFilter
import com.sonyphototransfer.app.data.RawHandling
import com.sonyphototransfer.app.data.SettingsRepository
import com.sonyphototransfer.app.storage.DownloadIndex
import com.sonyphototransfer.app.storage.DownloadedPhoto
import com.sonyphototransfer.app.storage.PhotoSaver
import com.sonyphototransfer.app.storage.SaveTarget
import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoFormat
import com.sonyphototransfer.camera.PhotoResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

enum class TransferStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED, SKIPPED }

data class TransferItem(
    val id: Long,
    val photo: CameraPhoto,
    val status: TransferStatus = TransferStatus.QUEUED,
    val bytesDone: Long = 0,
    val bytesTotal: Long? = null,
    val message: String? = null,
    val savedAsPreview: Boolean = false,
) {
    val fraction: Float?
        get() = bytesTotal?.takeIf { it > 0 }?.let { (bytesDone.toFloat() / it).coerceIn(0f, 1f) }
}

data class TransferState(val items: List<TransferItem> = emptyList()) {
    val isActive get() = items.any { it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING }
    val pending get() = items.count { it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING }
    val done get() = items.count { it.status == TransferStatus.DONE }
    val failed get() = items.count { it.status == TransferStatus.FAILED }
    val bytesDone get() = items.sumOf { it.bytesDone }

    /** Sum of known sizes; null while any queued item has no announced size yet. */
    val bytesTotal: Long?
        get() {
            val relevant = items.filter { it.status != TransferStatus.SKIPPED && it.status != TransferStatus.CANCELLED }
            if (relevant.isEmpty()) return null
            var total = 0L
            for (item in relevant) total += item.bytesTotal ?: item.photo.original?.sizeBytes ?: return null
            return total
        }
}

/**
 * Downloads original files one at a time (the camera handles one stream best),
 * verifies each one and only then publishes it. Runs in the application scope and is
 * kept alive by [TransferService] while anything is queued.
 */
class TransferManager(
    private val context: Context,
    private val session: CameraSession,
    private val settings: SettingsRepository,
    private val saver: PhotoSaver,
    private val index: DownloadIndex,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val ids = AtomicLong()
    private var worker: Job? = null
    private var current: Pair<Long, Job>? = null

    fun enqueue(photos: List<CameraPhoto>) {
        val alreadyQueued = _state.value.items
            .filter { it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING }
            .map { it.photo.stableKey }
            .toSet()
        val fresh = photos.filter { it.stableKey !in alreadyQueued }.map { TransferItem(ids.incrementAndGet(), it) }
        if (fresh.isEmpty()) return
        _state.update { it.copy(items = it.items + fresh) }
        ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
        startWorker()
    }

    fun cancel(id: Long) {
        current?.takeIf { it.first == id }?.second?.cancel()
        setStatus(id, TransferStatus.CANCELLED, onlyIfQueued = true)
    }

    fun cancelAll() {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.status == TransferStatus.QUEUED) it.copy(status = TransferStatus.CANCELLED) else it })
        }
        current?.second?.cancel()
    }

    fun retry(id: Long) = requeue { it.id == id }

    fun retryFailed() = requeue { it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED }

    fun clearFinished() {
        _state.update { s ->
            s.copy(items = s.items.filter { it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING })
        }
    }

    private fun requeue(predicate: (TransferItem) -> Boolean) {
        var any = false
        _state.update { s ->
            s.copy(
                items = s.items.map {
                    val retryable = it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED
                    if (retryable && predicate(it)) {
                        any = true
                        it.copy(status = TransferStatus.QUEUED, bytesDone = 0, message = null)
                    } else {
                        it
                    }
                },
            )
        }
        if (any) {
            ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
            startWorker()
        }
    }

    @Synchronized
    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = _state.value.items.firstOrNull { it.status == TransferStatus.QUEUED } ?: break
                update(next.id) { it.copy(status = TransferStatus.RUNNING, bytesDone = 0, message = null) }
                val job = launch(Dispatchers.IO) { download(next) }
                current = next.id to job
                job.join()
                current = null
                if (job.isCancelled) {
                    // Cancelled by the user: anything left half-way was already removed.
                    update(next.id) {
                        if (it.status == TransferStatus.RUNNING) it.copy(status = TransferStatus.CANCELLED, bytesDone = 0) else it
                    }
                }
            }
        }
    }

    private suspend fun download(item: TransferItem) {
        val prefs = settings.settings.value
        // Object URLs can change between camera sessions; prefer the current listing.
        val photo = session.gallery.value.photos.firstOrNull { it.stableKey == item.photo.stableKey } ?: item.photo

        val excluded = when (prefs.downloadFormats) {
            FormatFilter.ALL -> false
            FormatFilter.JPEG_ONLY -> photo.format != PhotoFormat.JPEG
            FormatFilter.RAW_ONLY -> photo.format != PhotoFormat.RAW
        }
        if (excluded) return finish(item.id, TransferStatus.SKIPPED, "Excluida por la preferencia de formato (Ajustes)")
        if (index.contains(photo.stableKey)) return finish(item.id, TransferStatus.SKIPPED, "Ya estaba descargada")

        val original = photo.original
        val (resource, asPreview) = when {
            original != null -> original to false
            photo.format == PhotoFormat.RAW && prefs.rawHandling == RawHandling.SAVE_PREVIEW ->
                (photo.largePreview ?: return finish(item.id, TransferStatus.SKIPPED, "La cámara no ofrece vista previa")) to true
            photo.format == PhotoFormat.RAW -> return finish(
                item.id,
                TransferStatus.SKIPPED,
                "La cámara no envía archivos RAW (ARW) por Wi-Fi, solo vistas previas JPEG. " +
                    "Puedes guardar la vista previa (Ajustes) o copiar el RAW por USB.",
            )
            else -> return finish(item.id, TransferStatus.SKIPPED, "La cámara no ofrece el archivo original")
        }

        val fileName = if (asPreview) "${photo.fileName.substringBeforeLast('.')}_preview.JPG" else photo.fileName
        val mime = if (asPreview) "image/jpeg" else mimeFor(photo.format)
        var target: SaveTarget? = null
        try {
            val written = session.open(resource).use { stream ->
                val expected = stream.contentLength ?: resource.sizeBytes
                update(item.id) { it.copy(bytesTotal = expected) }
                target = saver.create(fileName, mime, parseDate(photo.date), prefs.destinationTree)
                val copied = copy(stream.input, target!!, item.id)
                verify(copied, expected, resource, asPreview || photo.format == PhotoFormat.JPEG)
                copied.bytes
            }
            val saved = target!!
            // Close before measuring so nothing is still buffered.
            saved.output.close()
            val stored = saver.storedSize(saved.uri)
            if (stored != null && stored != written) {
                throw IOException("El archivo guardado no coincide con lo recibido ($stored de $written bytes)")
            }
            saved.commit()
            target = null
            index.add(
                DownloadedPhoto(
                    key = photo.stableKey,
                    uri = saved.uri,
                    fileName = fileName,
                    sizeBytes = written,
                    savedAtMillis = System.currentTimeMillis(),
                    isPreview = asPreview,
                ),
            )
            update(item.id) {
                it.copy(
                    status = TransferStatus.DONE,
                    bytesDone = written,
                    bytesTotal = written,
                    savedAsPreview = asPreview,
                    message = if (asPreview) "Guardada la vista previa JPEG, no el RAW original" else null,
                )
            }
        } catch (e: CancellationException) {
            target?.abort()
            throw e
        } catch (e: Exception) {
            target?.abort()
            val reason = when (e) {
                is IOException -> "Conexión interrumpida o error de escritura: ${e.message}"
                else -> e.message ?: e.javaClass.simpleName
            }
            finish(item.id, TransferStatus.FAILED, reason)
        }
    }

    private class CopyResult(val bytes: Long, val startsWithJpegMarker: Boolean)

    private suspend fun copy(input: java.io.InputStream, target: SaveTarget, id: Long): CopyResult {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var lastReport = 0L
        val first = ByteArray(2)
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (total < 2) {
                for (i in 0 until minOf(read, (2 - total).toInt())) first[(total + i).toInt()] = buffer[i]
            }
            target.output.write(buffer, 0, read)
            total += read
            val now = System.currentTimeMillis()
            if (now - lastReport > PROGRESS_INTERVAL_MS) {
                lastReport = now
                val done = total
                update(id) { it.copy(bytesDone = done) }
            }
        }
        update(id) { it.copy(bytesDone = total) }
        return CopyResult(total, total >= 2 && first[0] == 0xFF.toByte() && first[1] == 0xD8.toByte())
    }

    private fun verify(copied: CopyResult, expected: Long?, resource: PhotoResource, isJpeg: Boolean) {
        if (copied.bytes == 0L) throw IOException("La cámara no envió datos")
        if (expected != null && copied.bytes != expected) {
            throw IOException("Transferencia incompleta: ${copied.bytes} de $expected bytes")
        }
        if (isJpeg && !copied.startsWithJpegMarker) {
            throw IOException("Los datos recibidos de ${resource.url.substringAfterLast('/')} no son un JPEG válido")
        }
    }

    private fun finish(id: Long, status: TransferStatus, message: String) =
        update(id) { it.copy(status = status, message = message) }

    private fun setStatus(id: Long, status: TransferStatus, onlyIfQueued: Boolean) = update(id) {
        if (!onlyIfQueued || it.status == TransferStatus.QUEUED) it.copy(status = status) else it
    }

    private fun update(id: Long, transform: (TransferItem) -> TransferItem) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) transform(it) else it }) }
    }

    private fun mimeFor(format: PhotoFormat) = when (format) {
        PhotoFormat.JPEG -> "image/jpeg"
        PhotoFormat.RAW -> "image/x-sony-arw"
        PhotoFormat.HEIF -> "image/heif"
        PhotoFormat.OTHER -> "application/octet-stream"
    }

    private fun parseDate(date: String?): Long? = date?.let {
        runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it.take(19))?.time }.getOrNull()
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
        const val PROGRESS_INTERVAL_MS = 200L
    }
}
