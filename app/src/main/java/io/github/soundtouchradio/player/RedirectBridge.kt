package io.github.soundtouchradio.player

import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Puente de redirección (1.9.5, experimental). El SoundTouch rechaza que se le envíe una dirección https
 * (UPnP 402), pero con Radio alHara sonó una dirección http que el servidor redirige a https. Si el altavoz sigue las
 * redirecciones, basta con darle una dirección http del móvil que responda «302 → la dirección https»: el altavoz
 * descarga el audio directamente de la emisora y el móvil solo interviene un instante al arrancar (y si el altavoz
 * reconecta). No pasa audio por el móvil.
 *
 * Servidor mínimo: solo GET/HEAD de /r/<id>; cualquier otra cosa, 404. Escucha en la Wi-Fi local.
 */
class RedirectBridge(
    private val localIp: () -> InetAddress?,
    private val port: Int = 8765,
    /** Cada petición del altavoz al puente, para el registro de Diagnóstico (1.9.6). */
    private val onRequest: (path: String, status: Int, startedAt: Long) -> Unit = { _, _, _ -> },
) {
    private val targets = ConcurrentHashMap<String, String>()
    @Volatile private var server: ServerSocket? = null

    /** Dirección http del móvil que redirige a [httpsUrl], o null si no hay Wi-Fi local o no se pudo abrir el puerto. */
    fun urlFor(httpsUrl: String): String? {
        val ip = localIp() ?: return null
        if (!ensureStarted()) return null
        val id = Integer.toHexString(httpsUrl.hashCode() and 0x7fffffff)
        targets[id] = httpsUrl
        return "http://${ip.hostAddress}:${server?.localPort ?: port}/r/$id"
    }

    @Synchronized private fun ensureStarted(): Boolean {
        if (server?.isClosed == false) return true
        val s = runCatching { ServerSocket(port) }.recoverCatching { ServerSocket(0) }.getOrNull() ?: return false
        server = s
        thread(name = "redirect-bridge", isDaemon = true) {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { handle(c) }
            }
        }
        return true
    }

    /**
     * 1.9.6: se leen todas las cabeceras antes de responder y se cierra con cuidado. Antes se leía solo la primera
     * línea y se cerraba con datos sin leer: eso puede provocar un reinicio de la conexión (RST) que le borra la
     * respuesta al altavoz, y explicaría que el puente funcionara unas veces y otras no.
     */
    private fun handle(c: Socket) = c.use { sock ->
        val started = System.currentTimeMillis()
        sock.soTimeout = 5_000
        val reader = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val line = runCatching { reader.readLine() }.getOrNull().orEmpty()
        runCatching { while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break } }
        val response = respond(line)
        runCatching {
            sock.getOutputStream().apply { write(response.toByteArray(Charsets.ISO_8859_1)); flush() }
            sock.shutdownOutput()
            sock.soTimeout = 1_000
            runCatching { while (reader.read() >= 0) { } }   // dejar que el altavoz cierre primero
        }
        onRequest(line.split(' ').getOrNull(1) ?: "?", response.substringAfter(' ').take(3).toIntOrNull() ?: 0, started)
    }

    /** Respuesta HTTP para la línea de petición (separado para poder probarlo sin red). */
    fun respond(requestLine: String): String {
        val m = Regex("""^(GET|HEAD) /r/([0-9a-f]+)(\?\S*)? HTTP/1\.[01]$""").find(requestLine.trim())
        val target = m?.let { targets[it.groupValues[2]] }
        return if (target == null) "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        else "HTTP/1.1 302 Found\r\nLocation: $target\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
    }

    fun stop() { runCatching { server?.close() }; server = null }

    companion object {
        /** IPv4 de la red Wi-Fi del móvil (la que comparte con el altavoz). */
        fun wifiIpv4(context: android.content.Context): InetAddress? {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return null
            @Suppress("DEPRECATION")
            val wifi = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }
                ?: return null
            return cm.getLinkProperties(wifi)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        }
    }
}
