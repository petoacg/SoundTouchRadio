package io.github.soundtouchradio.soundtouch

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Canal de notificaciones del altavoz: ws://IP:8080 con subprotocolo «gabbo» (documento oficial).
 * Solo se usa como aviso de «algo cambió»; el estado se vuelve a leer por HTTP.
 */
class SoundTouchSocket(
    private val host: String,
    private val client: OkHttpClient,
    private val onEvent: (String) -> Unit,
    private val onClosed: (Throwable?) -> Unit,
    private val onOpen: () -> Unit = {},
) {
    private var socket: WebSocket? = null

    fun open() {
        if (socket != null) return
        val req = Request.Builder()
            .url("ws://${SoundTouchApi.hostForUrl(host)}:8080")
            .header("Sec-WebSocket-Protocol", "gabbo")
            .build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = onEvent(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { socket = null; onClosed(null) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { socket = null; onClosed(t) }
        })
    }

    fun close() {
        val s = socket
        socket = null
        s?.close(1000, null)
    }

    companion object {
        /** Eventos que justifican releer estado inmediatamente. */
        fun isRelevant(text: String): Boolean =
            text.contains("nowPlayingUpdated") || text.contains("volumeUpdated") ||
                text.contains("nowSelectionUpdated") || text.contains("zoneUpdated")
    }
}
