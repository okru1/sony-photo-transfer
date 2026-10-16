package com.sonyphototransfer.camera.http

import com.sonyphototransfer.camera.CameraException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opens connections to the camera. On Android this must go through the camera's
 * Wi-Fi [android.net.Network] (`network.openConnection(url)`), because that network has
 * no internet and the system would otherwise route traffic over mobile data.
 */
fun interface ConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

class HttpTransport(
    private val connections: ConnectionFactory,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 20_000,
) {
    fun getText(url: String): String {
        val conn = openConnection(url)
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw CameraException("HTTP $code en $url")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    fun soap(url: String, serviceType: String, action: String, body: String): String {
        val conn = openConnection(url)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", "\"$serviceType#$action\"")
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw CameraException("La cámara respondió HTTP $code a $action")
            }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    /** Opens a streaming GET; the returned connection must be disconnected by the caller. */
    fun openStream(url: String): HttpURLConnection {
        val conn = openConnection(url)
        val code = try {
            conn.responseCode
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        }
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw CameraException("HTTP $code al descargar $url")
        }
        return conn
    }

    private fun openConnection(url: String): HttpURLConnection =
        connections.open(URL(url)).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            instanceFollowRedirects = false
        }
}
