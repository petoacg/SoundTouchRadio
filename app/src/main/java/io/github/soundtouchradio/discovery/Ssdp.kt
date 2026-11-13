package io.github.soundtouchradio.discovery

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL

data class SsdpResponse(val host: String, val location: String, val server: String?, val st: String?)

/** SSDP M-SEARCH (UPnP Device Architecture). Multicast a 239.255.255.250:1900 o unicast a una IP. */
object Ssdp {
    private const val ADDR = "239.255.255.250"
    private const val PORT = 1900
    val TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1",
    )

    fun request(st: String, unicastHost: String? = null): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: ${unicastHost ?: ADDR}:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            (if (unicastHost == null) "MX: 2\r\n" else "") +
            "ST: $st\r\n" +
            "USER-AGENT: Android UPnP/1.1 SoundTouchRadio/1.0\r\n\r\n"

    fun parse(text: String): SsdpResponse? {
        val lines = text.split("\r\n", "\n")
        if (lines.isEmpty() || !lines[0].startsWith("HTTP/1.1 200", ignoreCase = true)) return null
        val headers = lines.drop(1).mapNotNull { l ->
            val i = l.indexOf(':'); if (i <= 0) null else l.substring(0, i).trim().uppercase() to l.substring(i + 1).trim()
        }.toMap()
        val location = headers["LOCATION"] ?: return null
        val host = runCatching { URL(location).host }.getOrNull() ?: return null
        return SsdpResponse(host, location, headers["SERVER"], headers["ST"])
    }

    /** Bloqueante: llamar desde Dispatchers.IO. */
    fun search(timeoutMs: Long, unicastHost: String? = null, onResponse: (SsdpResponse) -> Unit) {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = 400
            val target = InetAddress.getByName(unicastHost ?: ADDR)
            repeat(2) { // UDP puede perderse: dos rondas
                for (st in TARGETS) {
                    val data = request(st, unicastHost).toByteArray(Charsets.US_ASCII)
                    socket.send(DatagramPacket(data, data.size, target, PORT))
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                    parse(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let(onResponse)
                } catch (_: SocketTimeoutException) { }
            }
        }
    }
}
