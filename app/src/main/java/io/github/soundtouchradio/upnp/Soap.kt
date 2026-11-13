package io.github.soundtouchradio.upnp

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.soundtouch.Xml
import org.w3c.dom.Element

object Soap {
    const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"

    /** Sobre SOAP 1.1 para una acción UPnP. Los argumentos se escapan aquí. */
    fun envelope(action: String, service: String, args: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:").append(action).append(" xmlns:u=\"").append(service).append("\">")
        for ((k, v) in args) append('<').append(k).append('>').append(Xml.escape(v)).append("</").append(k).append('>')
        append("</u:").append(action).append("></s:Body></s:Envelope>")
    }

    fun soapActionHeader(service: String, action: String) = "\"$service#$action\""

    data class Fault(val code: String?, val description: String?)

    fun parseFault(xml: String): Fault? {
        val doc = runCatching { Xml.parse(xml, namespaceAware = true) }.getOrNull() ?: return null
        val faults = doc.getElementsByTagNameNS("*", "Fault")
        if (faults.length == 0) return null
        val f = faults.item(0) as Element
        fun find(name: String) = f.getElementsByTagNameNS("*", name).let { if (it.length > 0) it.item(0).textContent.trim() else null }
        return Fault(find("errorCode"), find("errorDescription") ?: find("faultstring"))
    }

    /** Valores de salida de una respuesta de acción, por nombre de argumento. */
    fun parseResponse(xml: String): Map<String, String> {
        val doc = Xml.parse(xml, namespaceAware = true)
        val body = doc.getElementsByTagNameNS("*", "Body").item(0) as? Element ?: return emptyMap()
        val resp = (0 until body.childNodes.length).map { body.childNodes.item(it) }.firstOrNull { it is Element } as? Element
            ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        for (i in 0 until resp.childNodes.length) {
            val n = resp.childNodes.item(i)
            if (n is Element) out[n.localName ?: n.nodeName] = n.textContent.trim()
        }
        return out
    }
}
