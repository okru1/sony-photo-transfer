package com.sonyphototransfer.camera.dlna

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal DOM helpers. Parsing is deliberately not namespace-aware and matches on
 * local names, because camera responses mix prefixed tags (dc:title, upnp:class)
 * and are not always strict about declaring namespaces.
 */
internal object Xml {

    fun parse(text: String): Document? {
        if (text.isBlank()) return null
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                // The camera is untrusted input: never resolve DTDs or external entities.
                trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(text.trim())))
        } catch (e: Exception) {
            null
        }
    }

    fun localName(node: Node): String = node.nodeName.substringAfter(':')

    fun Element.children(name: String): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) {
            if (n is Element && localName(n) == name) out += n
            n = n.nextSibling
        }
        return out
    }

    fun Element.child(name: String): Element? = children(name).firstOrNull()

    fun Element.childText(name: String): String? = child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** All descendants named [name], in document order. */
    fun Node.descendants(name: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(node: Node) {
            var n = node.firstChild
            while (n != null) {
                if (n is Element) {
                    if (localName(n) == name) out += n
                    walk(n)
                }
                n = n.nextSibling
            }
        }
        walk(this)
        return out
    }

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: Exception) {
            // Not every parser implementation (e.g. Android's) knows every feature.
        }
    }
}
