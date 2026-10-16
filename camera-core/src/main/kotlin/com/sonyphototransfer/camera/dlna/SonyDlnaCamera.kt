package com.sonyphototransfer.camera.dlna

import com.sonyphototransfer.camera.CameraException
import com.sonyphototransfer.camera.CameraGateway
import com.sonyphototransfer.camera.CameraInfo
import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoResource
import com.sonyphototransfer.camera.RemoteStream
import com.sonyphototransfer.camera.http.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Talks to the media server a Sony camera starts in "Send to Smartphone" mode.
 *
 * This protocol is not documented by Sony. What is implemented here is limited to
 * what open-source projects have observed on real bodies (ImagingEdge4Linux,
 * SonnyLink): a UPnP ContentDirectory:1 service on the camera's own access point,
 * usually at http://192.168.122.1:64321, which serves original JPEGs ("ORG_")
 * plus camera-made JPEG previews, but never the original RAW (.ARW) file.
 */
class SonyDlnaCamera(
    private val http: HttpTransport,
    /** Hosts to try before SSDP, e.g. the Wi-Fi gateway; 192.168.122.1 is always tried. */
    candidateHosts: List<String> = emptyList(),
    private val ssdp: SsdpDiscovery? = null,
    private val port: Int = DEFAULT_PORT,
) : CameraGateway {

    private val hosts = (candidateHosts + DEFAULT_HOST).distinct()

    private var contentDirectoryUrl: String? = null
    private var pushListUrl: String? = null

    override suspend fun connect(): CameraInfo = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        for (url in descriptionCandidates()) {
            val info = tryDescription(url, errors)
            if (info != null) return@withContext info
        }
        if (ssdp != null) {
            val locations = try {
                ssdp.search(ContentDirectory.SERVICE_TYPE).ifEmpty { ssdp.search(MEDIA_SERVER) }
            } catch (e: Exception) {
                errors += "SSDP: ${e.message}"
                emptyList()
            }
            for (url in locations) {
                val info = tryDescription(url, errors)
                if (info != null) return@withContext info
            }
        }
        throw CameraException(
            "No se encontró el servidor de imágenes de la cámara. Comprueba que la cámara " +
                "está en «Enviar a smartphone» y que el móvil está en su red Wi-Fi.\n" +
                errors.joinToString("\n"),
        )
    }

    private fun descriptionCandidates(): List<String> =
        hosts.flatMap { host -> DESCRIPTION_PATHS.map { "http://$host:$port$it" } }

    private fun tryDescription(url: String, errors: MutableList<String>): CameraInfo? {
        val description = try {
            DeviceDescription.parse(http.getText(url), url)
        } catch (e: Exception) {
            errors += "$url: ${e.message ?: e.javaClass.simpleName}"
            return null
        } ?: return null
        val cd = description.controlUrlFor(ContentDirectory.SERVICE_TYPE) ?: return null
        contentDirectoryUrl = cd
        pushListUrl = description.controlUrlFor(PUSH_LIST_SERVICE)
        // Shows "transferring" on the camera when the user picked images on the camera. Optional.
        pushList("X_TransferStart", "")
        return CameraInfo(
            friendlyName = description.friendlyName,
            modelName = description.modelName,
            manufacturer = description.manufacturer,
            descriptionUrl = url,
        )
    }

    override fun photos(): Flow<List<CameraPhoto>> = flow {
        val controlUrl = contentDirectoryUrl ?: throw CameraException("La cámara no está conectada")
        val found = LinkedHashMap<String, CameraPhoto>()
        val visited = HashSet<String>()
        // Root "0" normally lists PhotoRoot ("select on smartphone") or PushRoot
        // ("select on camera"); fall back to browsing those ids directly.
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += ROOT_ID to 0
        var browsedAny = false
        var lastError: Exception? = null
        while (queue.isNotEmpty()) {
            val (objectId, depth) = queue.removeFirst()
            if (!visited.add(objectId) || depth > MAX_DEPTH) continue
            var start = 0
            while (true) {
                val page = try {
                    browse(controlUrl, objectId, start)
                } catch (e: CameraException) {
                    lastError = e
                    null
                } catch (e: java.io.IOException) {
                    lastError = e
                    null
                }
                if (page == null) {
                    if (objectId == ROOT_ID) FALLBACK_ROOTS.forEach { queue += it to 1 }
                    break
                }
                browsedAny = true
                page.containers.forEach { queue += it.id to depth + 1 }
                var added = false
                for (photo in page.photos) {
                    if (found.putIfAbsent(photo.stableKey, photo) == null) added = true
                }
                if (added) emit(found.values.toList())
                start += page.numberReturned
                if (page.numberReturned == 0 || start >= page.totalMatches) break
            }
        }
        if (!browsedAny) throw CameraException("La cámara no devolvió ningún listado de imágenes", lastError)
        emit(found.values.toList())
    }.flowOn(Dispatchers.IO)

    private fun browse(controlUrl: String, objectId: String, start: Int): ContentDirectory.BrowseResult {
        val body = ContentDirectory.browseRequest(objectId, start, PAGE_SIZE)
        val response = http.soap(controlUrl, ContentDirectory.SERVICE_TYPE, "Browse", body)
        return ContentDirectory.parseBrowseResponse(response)
            ?: throw CameraException("Respuesta de la cámara no válida al listar $objectId")
    }

    override suspend fun open(resource: PhotoResource): RemoteStream = withContext(Dispatchers.IO) {
        val conn = http.openStream(resource.url)
        try {
            RemoteStream(
                input = conn.inputStream,
                contentLength = conn.contentLengthLong.takeIf { it >= 0 },
                mimeType = conn.contentType,
                onClose = conn::disconnect,
            )
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            // Ends "Send to Smartphone" on the camera when it supports it.
            pushList("X_TransferEnd", "<ErrCode>0</ErrCode>")
        }
        contentDirectoryUrl = null
        pushListUrl = null
    }

    private fun pushList(action: String, args: String) {
        val url = pushListUrl ?: return
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$PUSH_LIST_SERVICE\">$args</u:$action></s:Body></s:Envelope>"
        try {
            http.soap(url, PUSH_LIST_SERVICE, action, body)
        } catch (_: Exception) {
            // Only meaningful in "select on camera" mode; harmless to ignore otherwise.
        }
    }

    companion object {
        const val DEFAULT_HOST = "192.168.122.1"
        const val DEFAULT_PORT = 64321
        val DESCRIPTION_PATHS = listOf("/DmsDescPush.xml", "/dd.xml", "/DmsDesc.xml")
        const val MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1"
        const val PUSH_LIST_SERVICE = "urn:schemas-sony-com:service:XPushList:1"
        const val ROOT_ID = "0"
        val FALLBACK_ROOTS = listOf("PhotoRoot", "PushRoot")
        private const val PAGE_SIZE = 100
        private const val MAX_DEPTH = 6
    }
}
