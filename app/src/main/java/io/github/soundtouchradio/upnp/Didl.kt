package io.github.soundtouchradio.upnp

import io.github.soundtouchradio.soundtouch.Xml

object Didl {
    /**
     * DIDL-Lite de una emisora en directo. Algunos renderers rechazan una URI sin metadatos.
     * El resultado se pasa como argumento SOAP, que vuelve a escaparse al construir el sobre.
     */
    fun broadcast(url: String, title: String, mime: String): String = buildString {
        append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
        append("<item id=\"1\" parentID=\"0\" restricted=\"1\">")
        append("<dc:title>").append(Xml.escape(title)).append("</dc:title>")
        append("<upnp:class>object.item.audioItem.audioBroadcast</upnp:class>")
        append("<res protocolInfo=\"http-get:*:").append(Xml.escape(mime)).append(":*\">")
        append(Xml.escape(url)).append("</res>")
        append("</item></DIDL-Lite>")
    }
}
