package io.github.soundtouchradio.upnp

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.player.RequestLog
import io.github.soundtouchradio.soundtouch.Http
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class UpnpException(message: String, val fault: Soap.Fault? = null, cause: Throwable? = null) : Exception(message, cause)

data class TransportInfo(val state: String, val status: String)

/** Cliente AVTransport:1 sobre el MediaRenderer del altavoz (puerto 8091). */
class AvTransport(
    val controlUrl: String,
    private val client: OkHttpClient,
    private val log: RequestLog,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun setUri(url: String, metadata: String) {
        call("SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to metadata))
    }

    suspend fun play() { call("Play", listOf("InstanceID" to "0", "Speed" to "1")) }
    suspend fun pause() { call("Pause", listOf("InstanceID" to "0")) }
    suspend fun stop() { call("Stop", listOf("InstanceID" to "0")) }

    suspend fun transportInfo(): TransportInfo {
        val r = call("GetTransportInfo", listOf("InstanceID" to "0"))
        return TransportInfo(r["CurrentTransportState"].orEmpty(), r["CurrentTransportStatus"].orEmpty())
    }

    /** Acciones disponibles ahora mismo («Play,Stop,Pause…»). */
    suspend fun currentActions(): Set<String> {
        val r = call("GetCurrentTransportActions", listOf("InstanceID" to "0"))
        return r["Actions"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private suspend fun call(action: String, args: List<Pair<String, String>>): Map<String, String> = withContext(io) {
        val started = log.now()
        val body = Soap.envelope(action, Soap.AVT, args).toRequestBody(XML)
        val req = Request.Builder().url(controlUrl).post(body)
            .header("SOAPACTION", Soap.soapActionHeader(Soap.AVT, action))
            .header("User-Agent", Http.USER_AGENT)
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val fault = Soap.parseFault(text)
                    val msg = fault?.let { "UPnP ${it.code ?: "?"} ${it.description.orEmpty()}".trim() } ?: "HTTP ${resp.code}"
                    log.add("SOAP", action, msg, false, started)
                    throw UpnpException("$action: $msg", fault)
                }
                log.add("SOAP", action, "HTTP ${resp.code}", true, started)
                runCatching { Soap.parseResponse(text) }.getOrDefault(emptyMap())
            }
        } catch (e: IOException) {
            log.add("SOAP", action, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""), false, started)
            throw UpnpException(Txt.s(R.string.ui_sin_respuesta_del_renderer_upnp, action, e.message ?: e.javaClass.simpleName), cause = e)
        }
    }

    companion object { private val XML = "text/xml; charset=\"utf-8\"".toMediaType() }
}
