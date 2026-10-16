package com.sonyphototransfer.camera.dlna

import com.sonyphototransfer.camera.dlna.Xml.child
import com.sonyphototransfer.camera.dlna.Xml.childText
import com.sonyphototransfer.camera.dlna.Xml.descendants
import java.net.URL

/** The parts of a UPnP device description (DmsDescPush.xml / dd.xml) that we use. */
data class DeviceDescription(
    val friendlyName: String,
    val modelName: String,
    val manufacturer: String,
    val services: List<Service>,
) {
    data class Service(val serviceType: String, val controlUrl: String)

    fun controlUrlFor(serviceTypePrefix: String): String? =
        services.firstOrNull { it.serviceType.startsWith(serviceTypePrefix) }?.controlUrl

    companion object {
        /**
         * Parses [xml] fetched from [descriptionUrl]. Relative control URLs are
         * resolved against URLBase when present, else against the description URL.
         */
        fun parse(xml: String, descriptionUrl: String): DeviceDescription? {
            val root = Xml.parse(xml)?.documentElement ?: return null
            val device = root.child("device") ?: return null
            val base = root.childText("URLBase") ?: descriptionUrl
            val services = root.descendants("service").mapNotNull { service ->
                val type = service.childText("serviceType") ?: return@mapNotNull null
                val control = service.childText("controlURL") ?: return@mapNotNull null
                Service(type, resolve(base, control))
            }
            return DeviceDescription(
                friendlyName = device.childText("friendlyName").orEmpty(),
                modelName = device.childText("modelName").orEmpty(),
                manufacturer = device.childText("manufacturer").orEmpty(),
                services = services,
            )
        }

        private fun resolve(base: String, path: String): String =
            try {
                URL(URL(base), path).toString()
            } catch (e: Exception) {
                path
            }
    }
}
