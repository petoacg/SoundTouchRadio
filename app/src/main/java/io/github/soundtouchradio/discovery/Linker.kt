package io.github.soundtouchradio.discovery

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.player.RequestLog
import io.github.soundtouchradio.soundtouch.DeviceInfo
import io.github.soundtouchradio.soundtouch.Http
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.upnp.AvTransport
import io.github.soundtouchradio.upnp.DeviceDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class StepState { PENDING, RUNNING, OK, WARNING, FAILED }

data class LinkStep(val title: String, val state: StepState = StepState.PENDING, val detail: String? = null)

data class LinkReport(
    val host: String,
    val steps: List<LinkStep>,
    val info: DeviceInfo? = null,
    val controlUrl: String? = null,
    val done: Boolean = false,
) {
    /** Se puede usar si la API y el renderer UPnP responden; el nombre es solo un aviso. */
    val usable: Boolean get() = done && steps.getOrNull(1)?.state == StepState.OK && steps.getOrNull(2)?.state == StepState.OK

    fun toRecord(manual: Boolean, now: Long): SpeakerRecord? {
        val i = info ?: return null
        return SpeakerRecord(host = host, name = i.name, type = i.type, mac = i.macAddress, firmware = i.softwareVersion,
            controlUrl = controlUrl, manual = manual, lastSeenMillis = now)
    }
}

/**
 * «Vincular» = verificar en tres pasos que en esa IP hay un SoundTouch utilizable.
 * Ninguna comprobación hace sonar el altavoz.
 */
class Linker(private val client: OkHttpClient, private val log: RequestLog, private val discovery: Discovery?) {

    suspend fun check(candidate: Candidate, onProgress: (LinkReport) -> Unit): LinkReport {
        val host = candidate.host
        var r = LinkReport(host, listOf(
            LinkStep(Txt.s(R.string.ui_encontrado_en_la_red), StepState.OK, "$host · ${viaLabel(candidate.via)}"),
            LinkStep(Txt.s(R.string.ui_api_del_altavoz_puerto_8090), StepState.RUNNING),
            LinkStep(Txt.s(R.string.ui_reproductor_upnp_puerto_8091)),
            LinkStep(Txt.s(R.string.ui_nombre_compatible)),
        ))
        onProgress(r)

        val info = try {
            SoundTouchApi.forHost(host, client, log).info()
        } catch (e: Exception) {
            r = r.step(1, StepState.FAILED, explain(e)).copy(done = true)
            onProgress(r); return r
        }
        r = r.copy(info = info).step(1, StepState.OK, listOfNotNull(info.type, info.softwareVersion?.let { "firmware ${it.substringBefore(' ')}" }).joinToString(" · "))
            .step(2, StepState.RUNNING, null)
        onProgress(r)

        val location = candidate.location ?: withContext(Dispatchers.IO) { discovery?.ssdpUnicast(host) }
        val fromDescription = location?.let { loc -> runCatching { fetchControlUrl(loc) }.getOrNull() }
        val controlUrl = fromDescription ?: DeviceDescription.defaultControlUrl(host)
        val upnp = runCatching { AvTransport(controlUrl, client, log).transportInfo() }
        val upnpError = upnp.exceptionOrNull()?.let { technical(it) }
        r = if (upnp.isSuccess) {
            val origin = if (fromDescription != null) Txt.s(R.string.ui_segun_la_descripcion_upnp) else Txt.s(R.string.ui_ruta_por_defecto)
            r.copy(controlUrl = controlUrl).step(2, StepState.OK, "${pathOf(controlUrl)} · $origin · estado ${upnp.getOrNull()?.state?.ifEmpty { "?" }}")
        } else {
            r.step(2, StepState.FAILED, Txt.s(R.string.ui_el_reproductor_no_responde_reinicia, upnpError))
        }
        onProgress(r)

        r = if (info.nameBreaksRenderer) {
            r.step(3, StepState.WARNING, Txt.s(R.string.ui_contiene_o_puede_impedir_la, info.name))
        } else {
            r.step(3, StepState.OK, "«${info.name}»")
        }
        r = r.copy(done = true)
        onProgress(r)
        return r
    }

    private suspend fun fetchControlUrl(location: String): String? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(location).header("User-Agent", Http.USER_AGENT).build()).execute().use { resp ->
            if (!resp.isSuccessful) null else DeviceDescription.parse(resp.body?.string().orEmpty(), location).avTransportControlUrl
        }
    }

    private fun LinkReport.step(i: Int, state: StepState, detail: String?) =
        copy(steps = steps.mapIndexed { idx, s -> if (idx == i) s.copy(state = state, detail = detail) else s })

    private fun pathOf(url: String) = url.substringAfter("://").substringAfter('/', "").let { "/$it" }

    companion object {
        /** Causa probable en lenguaje llano + detalle técnico real entre corchetes. */
        fun explain(e: Throwable): String {
            val chain = generateSequence(e) { it.cause }.toList()
            val text = chain.joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            val plain = when {
                text.contains("EPERM") || text.contains("Operation not permitted") || chain.any { it is SecurityException } ->
                    Txt.s(R.string.ui_android_impide_a_esta_app)
                text.contains("CLEARTEXT", ignoreCase = true) -> Txt.s(R.string.ui_android_bloquea_http_sin_cifrar)
                chain.any { it is java.net.ConnectException } -> Txt.s(R.string.ui_conexion_rechazada_en_el_puerto)
                chain.any { it is java.net.SocketTimeoutException } -> Txt.s(R.string.ui_no_responde_a_tiempo_mismo)
                chain.any { it is java.net.NoRouteToHostException } -> Txt.s(R.string.ui_no_hay_ruta_hasta_esa)
                text.contains("URL scheme") || text.contains("sin esquema") -> Txt.s(R.string.ui_error_interno_de_la_app)
                chain.any { it is org.xml.sax.SAXException || it is IllegalArgumentException } -> Txt.s(R.string.ui_responde_pero_la_respuesta_no)
                else -> Txt.s(R.string.ui_no_responde_es_un_soundtouch)
            }
            return "$plain [${technical(e)}]"
        }

        fun technical(e: Throwable): String {
            val root = generateSequence(e) { it.cause }.last()
            return "${root.javaClass.simpleName}: ${root.message ?: "sin mensaje"}".take(180)
        }

        fun viaLabel(v: FoundVia) = when (v) {
            FoundVia.SSDP -> "SSDP"
            FoundVia.MDNS -> "mDNS"
            FoundVia.MANUAL -> Txt.s(R.string.ui_ip_manual)
            FoundVia.REMEMBERED -> "guardado"
        }
    }
}
