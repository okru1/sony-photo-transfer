package com.sonyphototransfer.camera.dlna

import com.sonyphototransfer.camera.PhotoFormat
import com.sonyphototransfer.camera.ResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    private val base = "http://192.168.122.1:60151"

    @Test
    fun deviceDescriptionResolvesRelativeControlUrls() {
        val url = "http://192.168.122.1:64321/DmsDescPush.xml"
        val d = DeviceDescription.parse(Fixtures.deviceDescription(), url)!!
        assertEquals("ILCE-7M3", d.friendlyName)
        assertEquals("Sony Corporation", d.manufacturer)
        assertEquals(
            "http://192.168.122.1:64321/upnp/control/ContentDirectory",
            d.controlUrlFor(ContentDirectory.SERVICE_TYPE),
        )
        assertEquals(
            "http://192.168.122.1:64321/upnp/control/XPushList",
            d.controlUrlFor(SonyDlnaCamera.PUSH_LIST_SERVICE),
        )
    }

    @Test
    fun deviceDescriptionRejectsGarbage() {
        assertNull(DeviceDescription.parse("<html>no</html>", "http://x/"))
        assertNull(DeviceDescription.parse("", "http://x/"))
    }

    @Test
    fun jpegExposesOriginalWithAnnouncedSize() {
        val soap = Fixtures.browseResponse(Fixtures.jpegItem("1", "DSC01234.JPG", "2026-04-10T14:45:35", base), 1, 1)
        val photo = ContentDirectory.parseBrowseResponse(soap)!!.photos.single()
        assertEquals("DSC01234.JPG", photo.fileName)
        assertEquals(PhotoFormat.JPEG, photo.format)
        assertEquals("2026-04-10T14:45:35", photo.date)
        val original = photo.original!!
        assertEquals(ResourceKind.ORIGINAL, original.kind)
        assertEquals(13_729_792L, original.sizeBytes)
        assertEquals("6000x4000", original.resolution)
        assertTrue(original.url.contains("/ORG_"))
        assertTrue(photo.thumbnail!!.url.contains("/TN_"))
        assertTrue(photo.largePreview!!.url.contains("/LRG_"))
    }

    @Test
    fun rawHasPreviewsButNoOriginal() {
        val soap = Fixtures.browseResponse(Fixtures.rawItem("2", "DSC01235.ARW", "2026-04-10T14:46:00", base), 1, 1)
        val photo = ContentDirectory.parseBrowseResponse(soap)!!.photos.single()
        assertEquals(PhotoFormat.RAW, photo.format)
        assertFalse(photo.isOriginalAvailable)
        assertNull(photo.original)
        assertNotNull(photo.largePreview)
    }

    @Test
    fun cameraJpegIsNeverTreatedAsRawOriginal() {
        // A JPEG with a size attribute under a RAW title must not be offered as the RAW file.
        val item = "<item id=\"3\"><dc:title>DSC1.ARW</dc:title><upnp:class>object.item.imageItem.photo</upnp:class>" +
            "<res protocolInfo=\"http-get:*:image/jpeg:*\" size=\"100\">$base/ORG_DSC1.ARW</res></item>"
        val photo = ContentDirectory.parseBrowseResponse(Fixtures.browseResponse(item, 1, 1))!!.photos.single()
        assertNull(photo.original)
    }

    @Test
    fun containersAndPagingAreReported() {
        val entries = Fixtures.container("PhotoRoot", "PhotoRoot", "0") + Fixtures.videoItem("v1")
        val result = ContentDirectory.parseBrowseResponse(Fixtures.browseResponse(entries, 2, 5))!!
        assertEquals(listOf("PhotoRoot"), result.containers.map { it.id })
        assertEquals(0, result.photos.size)
        assertEquals(1, result.skippedItems)
        assertEquals(2, result.numberReturned)
        assertEquals(5, result.totalMatches)
    }

    @Test
    fun brokenResponsesDoNotCrash() {
        assertNull(ContentDirectory.parseBrowseResponse(""))
        assertNull(ContentDirectory.parseBrowseResponse("<html/>"))
    }

    @Test
    fun browseRequestEscapesObjectId() {
        val body = ContentDirectory.browseRequest("a<b", 0, 10)
        assertTrue(body.contains("<ObjectID>a&lt;b</ObjectID>"))
        assertTrue(body.contains("<BrowseFlag>BrowseDirectChildren</BrowseFlag>"))
    }

    @Test
    fun doctypeIsRejected() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
            "<root><device><friendlyName>&x;</friendlyName></device></root>"
        assertNull(DeviceDescription.parse(evil, "http://x/"))
    }

    @Test
    fun ssdpLocationIsParsedCaseInsensitively() {
        val response = "HTTP/1.1 200 OK\r\nlocation: http://192.168.122.1:64321/dd.xml\r\nST: x\r\n\r\n"
        assertEquals("http://192.168.122.1:64321/dd.xml", SsdpDiscovery.parseLocation(response))
    }
}
