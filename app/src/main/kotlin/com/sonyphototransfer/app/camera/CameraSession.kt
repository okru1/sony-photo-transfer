package com.sonyphototransfer.app.camera

import android.net.Network
import com.sonyphototransfer.app.wifi.CameraWifi
import com.sonyphototransfer.app.wifi.WifiState
import com.sonyphototransfer.camera.CameraException
import com.sonyphototransfer.camera.CameraGateway
import com.sonyphototransfer.camera.CameraInfo
import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoResource
import com.sonyphototransfer.camera.RemoteStream
import com.sonyphototransfer.camera.dlna.SonyDlnaCamera
import com.sonyphototransfer.camera.dlna.SsdpDiscovery
import com.sonyphototransfer.camera.http.ConnectionFactory
import com.sonyphototransfer.camera.http.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object JoiningWifi : ConnectionState
    data object FindingCamera : ConnectionState
    data class Connected(val camera: CameraInfo) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class GalleryState(
    val photos: List<CameraPhoto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Owns the link to the camera: Wi-Fi network, protocol client and the photo catalogue. */
class CameraSession(
    private val wifi: CameraWifi,
    private val scope: CoroutineScope,
) {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _gallery = MutableStateFlow(GalleryState())
    val gallery: StateFlow<GalleryState> = _gallery.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** Human-readable connection log for the diagnostics screen. */
    val log: StateFlow<List<String>> = _log.asStateFlow()

    @Volatile
    private var gateway: CameraGateway? = null
    private var connectJob: Job? = null
    private var listJob: Job? = null

    val isConnected: Boolean get() = gateway != null

    init {
        scope.launch {
            wifi.state.collect { state ->
                if (state == WifiState.Lost && gateway != null) {
                    log("Wi-Fi de la cámara perdida")
                    dropGateway()
                    _connection.value = ConnectionState.Error(
                        "Se perdió la conexión Wi-Fi con la cámara. Puede que se haya apagado o salido " +
                            "del modo «Enviar a smartphone».",
                    )
                }
            }
        }
    }

    /** Joins the camera AP from the app (system dialog) and then looks for the camera. */
    fun connect(ssid: String, password: String) = startConnecting("Conectando a «$ssid»") {
        wifi.connect(ssid, password)
        awaitNetwork(WIFI_DIALOG_TIMEOUT_MS)
    }

    /** Uses the Wi-Fi network the phone is already on (joined manually in Android settings). */
    fun connectCurrentWifi() = startConnecting("Usando la red Wi-Fi actual") {
        wifi.useCurrentWifi()
        awaitNetwork(CURRENT_WIFI_TIMEOUT_MS)
    }

    private fun startConnecting(what: String, joinNetwork: suspend () -> Network?) {
        connectJob?.cancel()
        listJob?.cancel()
        connectJob = scope.launch {
            dropGateway()
            _log.value = emptyList()
            log(what)
            _connection.value = ConnectionState.JoiningWifi
            val net = joinNetwork() ?: return@launch
            log("Red disponible: $net")
            _connection.value = ConnectionState.FindingCamera
            val hosts = wifi.candidateHosts(net)
            log("Hosts candidatos: ${(hosts + SonyDlnaCamera.DEFAULT_HOST).distinct().joinToString()}")
            val camera = SonyDlnaCamera(
                http = HttpTransport(ConnectionFactory { url -> net.openConnection(url) as HttpURLConnection }),
                candidateHosts = hosts,
                ssdp = SsdpDiscovery { socket -> net.bindSocket(socket) },
            )
            val lock = wifi.multicastLock()
            lock.acquire()
            try {
                val info = camera.connect()
                gateway = camera
                log("Cámara encontrada: ${info.displayModel} (${info.manufacturer}) en ${info.descriptionUrl}")
                _connection.value = ConnectionState.Connected(info)
                refreshPhotos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Error: ${e.message}")
                wifi.release()
                _connection.value = ConnectionState.Error(
                    (e as? CameraException)?.message?.substringBefore('\n')
                        ?: "No se pudo comunicar con la cámara: ${e.message}",
                )
            } finally {
                lock.release()
            }
        }
    }

    private suspend fun awaitNetwork(timeoutMs: Long): Network? {
        val result = withTimeoutOrNull(timeoutMs) {
            wifi.state.first { it is WifiState.Available || it is WifiState.Failed }
        }
        return when (result) {
            is WifiState.Available -> result.network
            is WifiState.Failed -> {
                log("Wi-Fi: ${result.reason}")
                _connection.value = ConnectionState.Error(result.reason)
                null
            }
            else -> {
                val message = "No se obtuvo la red Wi-Fi de la cámara a tiempo."
                wifi.timeOut(message)
                log(message)
                _connection.value = ConnectionState.Error(message)
                null
            }
        }
    }

    fun refreshPhotos() {
        val camera = gateway ?: return
        listJob?.cancel()
        listJob = scope.launch {
            _gallery.value = GalleryState(loading = true)
            try {
                camera.photos().collect { photos ->
                    _gallery.update { it.copy(photos = photos) }
                }
                log("Imágenes listadas: ${_gallery.value.photos.size}")
                _gallery.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Error al listar: ${e.message}")
                _gallery.update { it.copy(loading = false, error = e.message ?: "Error al leer la tarjeta") }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        listJob?.cancel()
        val camera = gateway
        gateway = null
        scope.launch {
            camera?.close()
            wifi.release()
            _gallery.value = GalleryState()
            _connection.value = ConnectionState.Disconnected
            log("Desconectado")
        }
    }

    /** Opens a camera resource; throws [CameraException] when not connected. */
    suspend fun open(resource: PhotoResource): RemoteStream {
        val camera = gateway ?: throw CameraException("La cámara no está conectada")
        return camera.open(resource)
    }

    private fun dropGateway() {
        gateway = null
        listJob?.cancel()
        _gallery.update { it.copy(loading = false) }
    }

    private fun log(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _log.update { (it + "$time  $line").takeLast(200) }
    }

    private companion object {
        /** The user has to read the password on the camera and confirm the system dialog. */
        const val WIFI_DIALOG_TIMEOUT_MS = 120_000L
        const val CURRENT_WIFI_TIMEOUT_MS = 5_000L
    }
}
