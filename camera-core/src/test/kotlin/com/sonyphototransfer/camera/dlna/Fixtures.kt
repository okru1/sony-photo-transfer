package com.sonyphototransfer.camera.dlna

/**
 * Responses shaped like those published by open-source projects that captured
 * them from real Sony bodies in "Send to Smartphone" mode (ImagingEdge4Linux,
 * SonnyLink). They are not captures from an ILCE-7M3.
 */
object Fixtures {

    fun deviceDescription(controlPrefix: String = "") = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
            <friendlyName>ILCE-7M3</friendlyName>
            <manufacturer>Sony Corporation</manufacturer>
            <modelName>SonyImagingDevice</modelName>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>
                <controlURL>$controlPrefix/upnp/control/ContentDirectory</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-sony-com:service:XPushList:1</serviceType>
                <serviceId>urn:schemas-sony-com:serviceId:XPushList</serviceId>
                <controlURL>$controlPrefix/upnp/control/XPushList</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private const val DIDL_OPEN = "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
        "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"

    fun container(id: String, title: String, parent: String) =
        "<container id=\"$id\" parentID=\"$parent\" restricted=\"1\" childCount=\"1\">" +
            "<dc:title>$title</dc:title><upnp:class>object.container</upnp:class></container>"

    fun jpegItem(id: String, name: String, date: String, base: String, size: Long = 13_729_792) =
        "<item id=\"$id\" parentID=\"x\" restricted=\"1\">" +
            "<dc:title>$name</dc:title><upnp:class>object.item.imageItem.photo</upnp:class>" +
            "<dc:date>$date</dc:date>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_CI=1\">$base/LRG_$name?q</res>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM;DLNA.ORG_CI=1\">$base/SM_$name?q</res>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN;DLNA.ORG_CI=1\">$base/TN_$name?q</res>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:*\" size=\"$size\" resolution=\"6000x4000\">$base/ORG_$name?q</res>" +
            "</item>"

    fun rawItem(id: String, name: String, date: String, base: String) =
        "<item id=\"$id\" parentID=\"x\" restricted=\"1\">" +
            "<dc:title>$name</dc:title><upnp:class>object.item.imageItem.photo</upnp:class>" +
            "<dc:date>$date</dc:date>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM;DLNA.ORG_CI=1\">$base/SM_$name?q</res>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_CI=1\">$base/LRG_$name?q</res>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN;DLNA.ORG_CI=1\">$base/TN_$name?q</res>" +
            "</item>"

    fun videoItem(id: String) =
        "<item id=\"$id\" parentID=\"x\" restricted=\"1\">" +
            "<dc:title>C0001.MP4</dc:title><upnp:class>object.item.videoItem</upnp:class></item>"

    /** A BrowseResponse whose DIDL is XML-escaped inside <Result>, exactly as the camera sends it. */
    fun browseResponse(entries: String, returned: Int, total: Int): String {
        val escaped = (DIDL_OPEN + entries + "</DIDL-Lite>")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:BrowseResponse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\">" +
            "<Result>$escaped</Result>" +
            "<NumberReturned>$returned</NumberReturned><TotalMatches>$total</TotalMatches>" +
            "<UpdateID>1</UpdateID></u:BrowseResponse></s:Body></s:Envelope>"
    }
}
