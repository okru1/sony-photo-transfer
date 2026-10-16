package com.sonyphototransfer.app.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface WifiState {
    data object Idle : WifiState
    data object Requesting : WifiState
    data class Available(val network: Network) : WifiState
    data class Failed(val reason: String) : WifiState
    data object Lost : WifiState
}

/**
 * Gets a handle on the camera's Wi-Fi network.
 *
 * Two routes, both official Android APIs:
 *  - [connect]: WifiNetworkSpecifier (Android 10+). The system shows its own dialog,
 *    the phone joins the camera AP for this app only and keeps mobile data for the rest.
 *  - [useCurrentWifi]: the user already joined the camera network in system settings.
 *
 * Either way the camera network has no internet, so all camera traffic must be sent
 * through the returned [Network] (see [Network.openConnection] / [Network.bindSocket]).
 */
class CameraWifi(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    private val _state = MutableStateFlow<WifiState>(WifiState.Idle)
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun connect(ssid: String, password: String) {
        release()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply { if (password.isNotEmpty()) setWpa2Passphrase(password) }
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val cb = callbackFor(unavailableMessage = "No se pudo conectar a «$ssid». Revisa el nombre de red y la " +
            "contraseña que muestra la cámara, o si cancelaste el diálogo del sistema.")
        _state.value = WifiState.Requesting
        callback = cb
        // Keeping this request registered is what keeps the phone on the camera network.
        connectivity.requestNetwork(request, cb)
    }

    fun useCurrentWifi() {
        release()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = callbackFor(unavailableMessage = "El teléfono no está conectado a ninguna red Wi-Fi.")
        _state.value = WifiState.Requesting
        callback = cb
        connectivity.registerNetworkCallback(request, cb)
    }

    /** Called by the session when nothing arrived in time (registerNetworkCallback never reports "unavailable"). */
    fun timeOut(message: String) {
        if (_state.value == WifiState.Requesting) {
            release()
            _state.value = WifiState.Failed(message)
        }
    }

    fun release() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        _state.value = WifiState.Idle
    }

    /** Hosts worth probing for the camera: the network's gateway / DHCP server. */
    fun candidateHosts(network: Network): List<String> {
        val link = connectivity.getLinkProperties(network) ?: return emptyList()
        val hosts = mutableListOf<String>()
        link.routes.filter { it.isDefaultRoute }.mapNotNullTo(hosts) { it.gateway?.hostAddress }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            link.dhcpServerAddress?.hostAddress?.let(hosts::add)
        }
        return hosts.filter { it.isNotBlank() && it != "0.0.0.0" }.distinct()
    }

    /** Multicast is filtered by default on many phones; SSDP needs this lock held while searching. */
    fun multicastLock(): WifiManager.MulticastLock =
        wifiManager.createMulticastLock("SonyPhotoTransfer:ssdp").apply { setReferenceCounted(false) }

    private fun callbackFor(unavailableMessage: String) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (callback === this) _state.value = WifiState.Available(network)
        }

        override fun onLost(network: Network) {
            val current = _state.value
            if (callback === this && current is WifiState.Available && current.network == network) {
                _state.value = WifiState.Lost
            }
        }

        override fun onUnavailable() {
            if (callback === this) {
                callback = null
                _state.value = WifiState.Failed(unavailableMessage)
            }
        }
    }
}
