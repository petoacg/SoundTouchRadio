package io.github.soundtouchradio.soundtouch

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Utilidades XML mínimas sobre javax.xml (disponible en Android y en la JVM de los tests). */
object Xml {
    fun parse(text: String, namespaceAware: Boolean = false): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = namespaceAware
        // Endurecimiento frente a XXE donde el parser lo admita.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.isExpandEntityReferences = false }
        val bytes = text.trim().toByteArray(Charsets.UTF_8)
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

/** Primer hijo directo (o descendiente) con ese nombre local. */
internal fun Element.child(name: String): Element? {
    val list = getElementsByTagName(name)
    if (list.length > 0) return list.item(0) as Element
    // Con namespaces, buscar por nombre local.
    val all = getElementsByTagNameNS("*", name)
    return if (all.length > 0) all.item(0) as Element else null
}

internal fun Element.childText(name: String): String? = child(name)?.textContent?.trim()?.ifEmpty { null }

internal fun Element.children(name: String): List<Element> {
    val out = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType == Node.ELEMENT_NODE && (n.nodeName == name || n.localName == name)) out += n as Element
    }
    return out
}

internal fun Element.attr(name: String): String? = getAttribute(name).ifEmpty { null }
