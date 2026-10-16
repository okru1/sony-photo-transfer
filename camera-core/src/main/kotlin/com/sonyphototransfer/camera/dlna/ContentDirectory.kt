package com.sonyphototransfer.camera.dlna

import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoFormat
import com.sonyphototransfer.camera.PhotoResource
import com.sonyphototransfer.camera.ResourceKind
import com.sonyphototransfer.camera.dlna.Xml.children
import com.sonyphototransfer.camera.dlna.Xml.childText
import com.sonyphototransfer.camera.dlna.Xml.descendants
import org.w3c.dom.Element

/** UPnP ContentDirectory:1 Browse — request building and DIDL-Lite parsing. */
object ContentDirectory {

    const val SERVICE_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1"

    data class Container(val id: String, val title: String, val childCount: Int?)

    data class BrowseResult(
        val containers: List<Container>,
        val photos: List<CameraPhoto>,
        /** Items that are not photos (e.g. videos); listed only so we can report them. */
        val skippedItems: Int,
        val numberReturned: Int,
        val totalMatches: Int,
    )

    fun browseRequest(objectId: String, startingIndex: Int, requestedCount: Int): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:Browse xmlns:u=\"$SERVICE_TYPE\">" +
            "<ObjectID>${Xml.escape(objectId)}</ObjectID>" +
            "<BrowseFlag>BrowseDirectChildren</BrowseFlag>" +
            "<Filter>*</Filter>" +
            "<StartingIndex>$startingIndex</StartingIndex>" +
            "<RequestedCount>$requestedCount</RequestedCount>" +
            "<SortCriteria></SortCriteria>" +
            "</u:Browse>" +
            "</s:Body>" +
            "</s:Envelope>"

    /** Parses a BrowseResponse SOAP envelope. Returns null if it is not one. */
    fun parseBrowseResponse(soap: String): BrowseResult? {
        val envelope = Xml.parse(soap) ?: return null
        val response = envelope.descendants("BrowseResponse").firstOrNull() ?: return null
        // <Result> holds a second, escaped XML document; textContent un-escapes it.
        val didl = response.childText("Result")
        val numberReturned = response.childText("NumberReturned")?.toIntOrNull() ?: 0
        val totalMatches = response.childText("TotalMatches")?.toIntOrNull() ?: 0
        val parsed = didl?.let { parseDidl(it) } ?: Didl(emptyList(), emptyList(), 0)
        return BrowseResult(parsed.containers, parsed.photos, parsed.skipped, numberReturned, totalMatches)
    }

    private class Didl(val containers: List<Container>, val photos: List<CameraPhoto>, val skipped: Int)

    private fun parseDidl(didl: String): Didl? {
        val root = Xml.parse(didl)?.documentElement ?: return null
        val containers = root.children("container").mapNotNull { c ->
            val id = c.getAttribute("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Container(id, c.childText("title").orEmpty(), c.getAttribute("childCount").toIntOrNull())
        }
        var skipped = 0
        val photos = root.children("item").mapNotNull { item ->
            parseItem(item).also { if (it == null) skipped++ }
        }
        return Didl(containers, photos, skipped)
    }

    private fun parseItem(item: Element): CameraPhoto? {
        val id = item.getAttribute("id").takeIf { it.isNotEmpty() } ?: return null
        val title = item.childText("title") ?: return null
        val upnpClass = item.childText("class").orEmpty()
        val format = PhotoFormat.fromFileName(title)
        val isImage = upnpClass.startsWith("object.item.imageItem") ||
            (upnpClass.isEmpty() && format != PhotoFormat.OTHER)
        if (!isImage) return null

        val resources = item.children("res").mapNotNull { parseResource(it, format) }
        return CameraPhoto(
            id = id,
            fileName = title,
            format = format,
            date = item.childText("date"),
            resources = resources,
        )
    }

    private fun parseResource(res: Element, format: PhotoFormat): PhotoResource? {
        val url = res.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val protocolInfo = res.getAttribute("protocolInfo")
        // protocolInfo = "<protocol>:<network>:<mime>:<additional info>"
        val parts = protocolInfo.split(':', limit = 4)
        val mime = parts.getOrNull(2).orEmpty()
        val extra = parts.getOrNull(3).orEmpty()
        val profile = extra.split(';')
            .firstOrNull { it.startsWith("DLNA.ORG_PN=") }
            ?.substringAfter('=')
            .orEmpty()
        val size = res.getAttribute("size").toLongOrNull()?.takeIf { it > 0 }
        val resolution = res.getAttribute("resolution").takeIf { it.isNotEmpty() }
        val fileName = url.substringBefore('?').substringAfterLast('/')

        var kind = when {
            profile == "JPEG_TN" || fileName.startsWith("TN_") -> ResourceKind.THUMBNAIL
            profile == "JPEG_SM" || profile == "JPEG_MED" || fileName.startsWith("SM_") -> ResourceKind.PREVIEW_SMALL
            profile == "JPEG_LRG" || fileName.startsWith("LRG_") -> ResourceKind.PREVIEW_LARGE
            fileName.startsWith("ORG_") -> ResourceKind.ORIGINAL
            profile.isEmpty() && size != null -> ResourceKind.ORIGINAL
            else -> ResourceKind.UNKNOWN
        }
        // Never present a camera-made JPEG as the original of a RAW/HEIF file.
        if (kind == ResourceKind.ORIGINAL && !mimeMatches(format, mime)) kind = ResourceKind.UNKNOWN
        return PhotoResource(url, kind, mime, size, resolution)
    }

    private fun mimeMatches(format: PhotoFormat, mime: String): Boolean {
        val m = mime.lowercase()
        return when (format) {
            PhotoFormat.JPEG -> m == "image/jpeg" || m == "*" || m.isEmpty()
            PhotoFormat.RAW, PhotoFormat.HEIF, PhotoFormat.OTHER -> m != "image/jpeg" && m.isNotEmpty() && m != "*"
        }
    }
}
