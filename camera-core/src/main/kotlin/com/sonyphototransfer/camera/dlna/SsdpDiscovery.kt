package com.sonyphototransfer.camera.dlna

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * SSDP M-SEARCH used as a fallback when the camera's description is not at the
 * usual address. [bindSocket] lets Android bind the socket to the camera network.
 */
class SsdpDiscovery(private val bindSocket: (DatagramSocket) -> Unit = {}) {

    /** Returns the distinct LOCATION URLs that answered for [searchTarget]. */
    fun search(searchTarget: String, timeoutMs: Int = 2_500): List<String> {
        val request = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $ADDRESS:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $searchTarget\r\n\r\n"
        val locations = LinkedHashSet<String>()
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            bindSocket(socket)
            socket.bind(null)
            socket.soTimeout = timeoutMs
            val payload = request.toByteArray(Charsets.US_ASCII)
            val target = InetAddress.getByName(ADDRESS)
            // UDP is lossy and the camera's radio may still be waking up: send twice.
            repeat(2) { socket.send(DatagramPacket(payload, payload.size, target, PORT)) }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                parseLocation(String(packet.data, 0, packet.length, Charsets.US_ASCII))?.let(locations::add)
            }
        }
        return locations.toList()
    }

    companion object {
        const val ADDRESS = "239.255.255.250"
        const val PORT = 1900

        fun parseLocation(response: String): String? =
            response.lineSequence()
                .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
