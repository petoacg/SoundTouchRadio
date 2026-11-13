package io.github.soundtouchradio.upnp

import io.github.soundtouchradio.soundtouch.Xml
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.w3c.dom.Element

data class UpnpDevice(
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val avTransportControlUrl: String?,
    val renderingControlUrl: String?,
)

object DeviceDescription {
    /** Ruta por defecto observada por la comunidad en SoundTouch 10/20. */
    const val DEFAULT_CONTROL_PATH = "/AVTransport/Control"
    const val UPNP_PORT = 8091

    fun defaultControlUrl(host: String): String =
        "http://${io.github.soundtouchradio.soundtouch.SoundTouchApi.hostForUrl(host)}:$UPNP_PORT$DEFAULT_CONTROL_PATH"

    fun parse(xml: String, location: String): UpnpDevice {
        val doc = Xml.parse(xml, namespaceAware = true)
        fun first(name: String): String? = doc.getElementsByTagNameNS("*", name).let {
            if (it.length > 0) it.item(0).textContent.trim().ifEmpty { null } else null
        }
        val base = first("URLBase") ?: location
        var avt: String? = null
        var rc: String? = null
        val services = doc.getElementsByTagNameNS("*", "service")
        for (i in 0 until services.length) {
            val s = services.item(i) as Element
            val type = s.getElementsByTagNameNS("*", "serviceType").item(0)?.textContent?.trim().orEmpty()
            val ctrl = s.getElementsByTagNameNS("*", "controlURL").item(0)?.textContent?.trim() ?: continue
            when {
                type.contains(":AVTransport:") -> avt = resolve(base, ctrl)
                type.contains(":RenderingControl:") -> rc = resolve(base, ctrl)
            }
        }
        return UpnpDevice(first("friendlyName"), first("manufacturer"), first("modelName"), avt, rc)
    }

    fun resolve(base: String, ref: String): String? {
        val b = base.toHttpUrlOrNull() ?: return ref.toHttpUrlOrNull()?.toString()
        return b.resolve(ref)?.toString()
    }
}
