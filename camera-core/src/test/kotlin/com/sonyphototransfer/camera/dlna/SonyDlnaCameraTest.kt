package com.sonyphototransfer.camera.dlna

import com.sonyphototransfer.camera.CameraException
import com.sonyphototransfer.camera.http.HttpTransport
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Runs the client against a local HTTP server that behaves like the camera. */
class SonyDlnaCameraTest {

    private lateinit var server: HttpServer
    private val soapActions = CopyOnWriteArrayList<String>()
    private val original = ByteArray(300_000) { (it % 251).toByte() }

    private val port get() = server.address.port
    private val base get() = "http://127.0.0.1:$port"

    /** objectId -> list of pages (each page: DIDL entries, total). */
    private val catalogue = mutableMapOf<String, List<Pair<String, Int>>>()
    private var descriptionPath = "/DmsDescPush.xml"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex -> handle(ex) }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        when {
            path == descriptionPath -> respond(ex, 200, Fixtures.deviceDescription().toByteArray())
            path == "/upnp/control/ContentDirectory" -> {
                val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                soapActions += ex.requestHeaders.getFirst("SOAPACTION")
                val id = Regex("<ObjectID>(.*?)</ObjectID>").find(body)!!.groupValues[1]
                val start = Regex("<StartingIndex>(\\d+)</StartingIndex>").find(body)!!.groupValues[1].toInt()
                val pages = catalogue[id]
                if (pages == null) {
                    respond(ex, 500, "no such object".toByteArray())
                    return
                }
                // Pages are addressed by their starting index in steps of the page size.
                var offset = 0
                val page = pages.firstOrNull { (entries, _) ->
                    val count = entries.split("</item>", "</container>").size - 1
                    (offset == start).also { offset += count }
                }
                if (page == null) {
                    respond(ex, 200, Fixtures.browseResponse("", 0, pages.last().second).toByteArray())
                } else {
                    val count = page.first.split("</item>", "</container>").size - 1
                    respond(ex, 200, Fixtures.browseResponse(page.first, count, page.second).toByteArray())
                }
            }
            path == "/upnp/control/XPushList" -> {
                soapActions += ex.requestHeaders.getFirst("SOAPACTION")
                respond(ex, 200, "<ok/>".toByteArray())
            }
            path == "/ORG_DSC00001.JPG" -> respond(ex, 200, original)
            else -> respond(ex, 404, ByteArray(0))
        }
    }

    private fun respond(ex: HttpExchange, code: Int, body: ByteArray) {
        ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun camera() = SonyDlnaCamera(
        http = HttpTransport({ url -> url.openConnection() as HttpURLConnection }),
        candidateHosts = listOf("127.0.0.1"),
        port = port,
    )

    @Test
    fun connectsReadsModelAndStartsTransfer() = runBlocking {
        val info = camera().connect()
        assertEquals("ILCE-7M3", info.displayModel)
        assertTrue(soapActions.any { it.contains("X_TransferStart") })
    }

    @Test
    fun findsAlternativeDescriptionPath() = runBlocking {
        descriptionPath = "/dd.xml"
        assertEquals("ILCE-7M3", camera().connect().friendlyName)
    }

    @Test(expected = CameraException::class)
    fun failsClearlyWhenNoCameraAnswers() = runBlocking<Unit> {
        descriptionPath = "/nothing-here"
        camera().connect()
    }

    @Test
    fun walksFoldersAndPagesProgressively() = runBlocking {
        catalogue["0"] = listOf(Fixtures.container("PhotoRoot", "PhotoRoot", "0") to 1)
        catalogue["PhotoRoot"] = listOf(Fixtures.container("D1", "2026-10-01", "PhotoRoot") to 1)
        catalogue["D1"] = listOf(
            (Fixtures.jpegItem("a", "DSC00001.JPG", "2026-10-01T10:00:00", base) +
                Fixtures.rawItem("b", "DSC00002.ARW", "2026-10-01T10:00:05", base)) to 3,
            Fixtures.jpegItem("c", "DSC00003.JPG", "2026-10-01T10:00:09", base) to 3,
        )
        val cam = camera()
        cam.connect()
        val emissions = cam.photos().toList()
        assertTrue("gallery should fill progressively", emissions.size >= 2)
        val names = emissions.last().map { it.fileName }
        assertEquals(listOf("DSC00001.JPG", "DSC00002.ARW", "DSC00003.JPG"), names)
    }

    @Test
    fun fallsBackToPushRootWhenRootIsRejected() = runBlocking {
        catalogue["PushRoot"] = listOf(Fixtures.jpegItem("a", "DSC00001.JPG", "2026-10-01T10:00:00", base) to 1)
        val cam = camera()
        cam.connect()
        assertEquals(listOf("DSC00001.JPG"), cam.photos().last().map { it.fileName })
    }

    @Test
    fun streamsOriginalBytesWithLength() = runBlocking {
        catalogue["0"] = listOf(Fixtures.jpegItem("a", "DSC00001.JPG", "2026-10-01T10:00:00", base, original.size.toLong()) to 1)
        val cam = camera()
        cam.connect()
        val photo = cam.photos().last().single()
        val bytes = cam.open(photo.original!!).use { stream ->
            assertEquals(original.size.toLong(), stream.contentLength)
            stream.input.readBytes()
        }
        assertTrue(bytes.contentEquals(original))
    }

    @Test
    fun closeEndsTransferOnCamera() = runBlocking {
        val cam = camera()
        cam.connect()
        cam.close()
        assertTrue(soapActions.any { it.contains("X_TransferEnd") })
    }
}
