package io.github.soundtouchradio.soundtouch

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.player.RequestLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * API web local del SoundTouch (HTTP, puerto 8090, XML).
 * Documentación: «SoundTouch API WebServices v1.1.0» de Bose.
 */
class SoundTouchApi private constructor(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val log: RequestLog,
    private val io: CoroutineDispatcher,
) {
    init { require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "baseUrl sin esquema: ${baseUrl}" } }

    suspend fun info(): DeviceInfo = SoundTouchXml.parseInfo(get("/info"))
    suspend fun nowPlaying(): NowPlaying = SoundTouchXml.parseNowPlaying(get("/now_playing"))
    suspend fun volume(): Volume = SoundTouchXml.parseVolume(get("/volume"))
    suspend fun setVolume(level: Int) { post("/volume", SoundTouchXml.volumeBody(level)) }
    suspend fun setName(name: String) { post("/name", SoundTouchXml.nameBody(name)) }
    suspend fun zone(): Zone = SoundTouchXml.parseZone(get("/getZone"))

    /** Simula una pulsación completa (press + release), como recomienda la documentación. */
    suspend fun pressKey(key: String) {
        post("/key", SoundTouchXml.keyBody(key, "press"))
        post("/key", SoundTouchXml.keyBody(key, "release"))
    }

    suspend fun setZone(masterMac: String, senderIp: String?, members: List<ZoneMember>) {
        post("/setZone", SoundTouchXml.zoneBody(masterMac, senderIp, members))
    }

    suspend fun removeZoneSlaves(masterMac: String, members: List<ZoneMember>) {
        post("/removeZoneSlave", SoundTouchXml.zoneBody(masterMac, null, members))
    }

    private suspend fun get(path: String): String = call("GET", path, null)
    private suspend fun post(path: String, body: String): String = call("POST", path, body)

    private suspend fun call(method: String, path: String, body: String?): String = withContext(io) {
        val started = log.now()
        val builder = Request.Builder().url(baseUrl + path).header("User-Agent", Http.USER_AGENT)
        if (body != null) builder.post(body.toRequestBody(XML))
        try {
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val err = SoundTouchXml.parseError(text)
                if (!resp.isSuccessful || err != null) {
                    val msg = err?.let { "${it.name ?: "ERROR"} ${it.value ?: ""} ${it.message ?: ""}".trim() }
                        ?: "HTTP ${resp.code}"
                    log.add(method, path, msg, false, started)
                    throw SoundTouchException("$path: $msg", err)
                }
                log.add(method, path, "HTTP ${resp.code}", true, started)
                text
            }
        } catch (e: IOException) {
            log.add(method, path, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""), false, started)
            throw SoundTouchException(Txt.s(R.string.ui_sin_respuesta, path, e.message ?: e.javaClass.simpleName), cause = e)
        }
    }

    companion object {
        /** Altavoz por IP o nombre: http://IP:8090. (Antes había dos constructores ambiguos; ver CHANGELOG 1.0.2.) */
        fun forHost(host: String, client: OkHttpClient, log: RequestLog, port: Int = 8090, io: CoroutineDispatcher = Dispatchers.IO) =
            SoundTouchApi("http://${hostForUrl(host)}:$port", client, log, io)

        /** URL base completa (tests con servidor simulado). */
        fun forBaseUrl(baseUrl: String, client: OkHttpClient, log: RequestLog, io: CoroutineDispatcher = Dispatchers.IO) =
            SoundTouchApi(baseUrl.trimEnd('/'), client, log, io)

        private val XML = "application/xml; charset=utf-8".toMediaType()
        fun hostForUrl(host: String): String = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
    }
}
