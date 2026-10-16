package com.sonyphototransfer.camera

import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Boundary between the UI and whatever protocol talks to the camera.
 *
 * The only implementation today is [com.sonyphototransfer.camera.dlna.SonyDlnaCamera]
 * (the camera's "Send to Smartphone" UPnP media server). Another transport, e.g.
 * USB/MTP for RAW files, can be added without touching the screens.
 */
interface CameraGateway {
    /** Locates the camera on the current network and checks it answers. */
    suspend fun connect(): CameraInfo

    /**
     * Walks the camera's catalogue. Each emission is the full list found so far,
     * so the gallery can fill in progressively while the camera is still answering.
     */
    fun photos(): Flow<List<CameraPhoto>>

    /** Opens a resource for streaming; the caller must close the result. */
    suspend fun open(resource: PhotoResource): RemoteStream

    /** Tells the camera the session is over. Never throws. */
    suspend fun close()
}

class RemoteStream(
    val input: InputStream,
    /** Content-Length from the camera, or null when it was not sent. */
    val contentLength: Long?,
    val mimeType: String?,
    private val onClose: () -> Unit = {},
) : AutoCloseable {
    override fun close() {
        try {
            input.close()
        } finally {
            onClose()
        }
    }
}
