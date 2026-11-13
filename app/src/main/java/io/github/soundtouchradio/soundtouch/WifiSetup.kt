package io.github.soundtouchradio.soundtouch

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.player.RequestLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Red Wi-Fi para el paso «Tu red». Sale del escaneo del altavoz (solo redes que puede usar, 2,4 GHz) y/o del escaneo
 * del móvil. [only5GHz]: el móvil solo la ve en 5 GHz y el SoundTouch 10 no podrá unirse (se muestra deshabilitada).
 */
data class WifiNetwork(
    val ssid: String,
    val signalDbm: Int?,
    val secure: Boolean,
    val securityTypes: List<String>,
    val only5GHz: Boolean = false,
) {
    /** 0–4 barras a partir de dBm. */
    val bars: Int get() = when {
        signalDbm == null -> 2
        signalDbm >= -55 -> 4
        signalDbm >= -65 -> 3
        signalDbm >= -75 -> 2
        signalDbm >= -85 -> 1
        else -> 0
    }
}

/** Una entrada del escaneo Wi-Fi del móvil (ScanResult reducido, para poder probarlo sin Android). */
data class PhoneScan(val ssid: String, val frequencyMhz: Int, val levelDbm: Int, val capabilities: String)

/** Resultado del envío de credenciales. NO_ANSWER no es un fallo: el altavoz suele cortar su red al probar la tuya. */
enum class SendOutcome { ACCEPTED, NO_ANSWER }

/**
 * Configuración de Wi-Fi en modo punto de acceso (AP). Fuente: documentación comunitaria
 * gesellix/Bose-SoundTouch (DEVICE-INITIAL-SETUP, verificado en SoundTouch 10).
 */
object WifiSetupXml {
    /** Dirección del altavoz en modo configuración (el móvil recibe 192.0.2.2 por DHCP). */
    const val AP_HOST = "192.0.2.1"
    val SECURITY_TYPES = listOf("none", "wep", "wpatkip", "wpaaes", "wpa2tkip", "wpa2aes", "wpa_or_wpa2")

    fun surveyBody(timeoutSeconds: Int = 5) = "<PerformWirelessSiteSurvey timeout=\"$timeoutSeconds\"/>"

    /** Tolerante: busca cualquier <item ssid=…>; sin duplicados (se queda la señal más fuerte), ordenadas por señal. */
    fun parseSurvey(xml: String): List<WifiNetwork> {
        val doc = Xml.parse(xml)
        val items = doc.getElementsByTagName("item")
        val out = mutableListOf<WifiNetwork>()
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            val ssid = e.getAttribute("ssid").trim()
            if (ssid.isEmpty()) continue
            val types = e.getElementsByTagName("type").let { t -> (0 until t.length).map { t.item(it).textContent.trim() }.filter { it.isNotEmpty() } }
            val secureAttr = e.getAttribute("secure")
            out += WifiNetwork(
                ssid = ssid,
                signalDbm = e.getAttribute("signalStrength").trim().toIntOrNull(),
                secure = if (secureAttr.isNotEmpty()) secureAttr.equals("true", true) else types.any { it != "none" },
                securityTypes = types,
            )
        }
        return out.groupBy { it.ssid }.values.map { same -> same.maxBy { it.signalDbm ?: -200 } }
            .sortedByDescending { it.signalDbm ?: -200 }
    }

    /**
     * Une el escaneo del altavoz y el del móvil en una sola lista. El del altavoz manda (es lo que el altavoz ve);
     * del móvil se añaden las que falten, sin redes ocultas ni las del propio altavoz («Bose …»).
     * Las que el móvil solo ve en 5 GHz se marcan: el SoundTouch 10 trabaja en 2,4 GHz.
     */
    fun mergeNetworks(speaker: List<WifiNetwork>, phone: List<PhoneScan>): List<WifiNetwork> {
        val known = speaker.map { it.ssid }.toSet()
        val fromPhone = phone.filter { it.ssid.isNotBlank() && !it.ssid.startsWith("Bose") && it.ssid !in known }
            .groupBy { it.ssid }
            .map { (ssid, seen) ->
                val best = seen.maxBy { it.levelDbm }
                val caps = seen.joinToString(" ") { it.capabilities }.uppercase()
                val types = when {
                    "WEP" in caps -> listOf("wep")
                    "PSK" in caps || "WPA" in caps || "SAE" in caps || "EAP" in caps -> listOf("wpa_or_wpa2")
                    else -> listOf("none")
                }
                WifiNetwork(ssid, best.levelDbm, secure = types != listOf("none"), securityTypes = types,
                    only5GHz = seen.none { it.frequencyMhz in 2400..2500 })
            }
        return (speaker + fromPhone).sortedWith(compareBy<WifiNetwork> { it.only5GHz }.thenByDescending { it.signalDbm ?: -200 })
    }

    /** Tipo de seguridad a enviar: «none» si es abierta; si no, el recomendado si el altavoz lo anuncia. */
    fun chooseSecurity(net: WifiNetwork?): String = when {
        net == null -> "wpa_or_wpa2"
        !net.secure -> "none"
        "wpa_or_wpa2" in net.securityTypes -> "wpa_or_wpa2"
        else -> net.securityTypes.firstOrNull { it in SECURITY_TYPES && it != "none" } ?: "wpa_or_wpa2"
    }

    fun profileBody(ssid: String, password: String, security: String): String =
        "<AddWirelessProfile timeout=\"30\"><profile ssid=\"${Xml.escape(ssid)}\" password=\"${Xml.escape(password)}\" " +
            "securityType=\"${Xml.escape(security)}\"/></AddWirelessProfile>"

    const val LEAVE_BODY = "<setupState state=\"SETUP_WIFI_LEAVE\" />"

    /** ¿Es el mismo altavoz? Compara deviceID y MAC (sin separadores, en mayúsculas). */
    fun sameSpeaker(a: DeviceInfo, b: DeviceInfo): Boolean {
        fun norm(s: String?) = s?.replace(":", "")?.replace("-", "")?.uppercase()?.takeIf { it.isNotBlank() }
        val left = setOfNotNull(norm(a.deviceId), norm(a.macAddress))
        val right = setOfNotNull(norm(b.deviceId), norm(b.macAddress))
        return left.intersect(right).isNotEmpty()
    }

    /**
     * Nombre propuesto al terminar, como hace la app de Bose. Tras un reset el altavoz se llama
     * «Bose SoundTouch 1A2B3C»: se propone su modelo («SoundTouch 10»). Un nombre puesto por el usuario se respeta.
     */
    fun suggestedName(info: DeviceInfo): String {
        val n = info.name.trim()
        val factory = n.isEmpty() || Regex("""^Bose SoundTouch( \d+)? [0-9A-Fa-f]{4,}$""").matches(n)
        return if (factory) info.type.trim().ifEmpty { "SoundTouch" } else n
    }
}

/** Cliente del altavoz en modo AP. [client] debe ir atado a la red del altavoz (socketFactory de esa Network). */
class SpeakerSetupApi(
    private val client: OkHttpClient,
    private val log: RequestLog,
    private val baseUrl: String = "http://${WifiSetupXml.AP_HOST}:8090",
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val perAttemptTimeoutMs: Long = 40_000,
    private val surveyTimeoutMs: Long = 25_000,
    private val retryDelayMs: Long = 2_000,
) {
    suspend fun info(): DeviceInfo = SoundTouchApi.forBaseUrl(baseUrl, client, log, io).info()

    /** Escaneo del altavoz. Dos intentos con margen: el altavoz tarda en escanear y la primera petición a veces se pierde. */
    suspend fun survey(): List<WifiNetwork> {
        var last: Exception? = null
        repeat(2) { attempt ->
            if (attempt > 0) delay(retryDelayMs)
            try { return WifiSetupXml.parseSurvey(post("/performWirelessSiteSurvey", WifiSetupXml.surveyBody(), surveyTimeoutMs)) }
            catch (e: Exception) { last = e }
        }
        throw last ?: SoundTouchException(Txt.s(R.string.ui_el_altavoz_no_devolvio_la))
    }

    /**
     * Envía las credenciales. La petición le da al altavoz 30 s para probar la red (`timeout="30"`) y la app espera más
     * que eso. Si aun así no contesta, es lo esperable: al probar tu red, el altavoz puede apagar la suya y la respuesta
     * se pierde. Por eso «sin respuesta» devuelve [SendOutcome.NO_ANSWER] y el asistente sigue buscándolo en tu red.
     * Si no se pudo ni conectar (la petición no llegó), se reintenta una vez a los 2 s. Un HTTP de error es un rechazo.
     */
    suspend fun sendProfile(ssid: String, password: String, security: String): SendOutcome {
        repeat(2) { attempt ->
            if (attempt > 0) delay(retryDelayMs)
            try {
                post("/addWirelessProfile", WifiSetupXml.profileBody(ssid, password, security), perAttemptTimeoutMs)
                return SendOutcome.ACCEPTED
            } catch (e: SoundTouchException) {
                val io = e.cause ?: throw e                                   // HTTP de error: rechazo real
                if (io is java.io.InterruptedIOException) return SendOutcome.NO_ANSWER   // llegó y no contestó a tiempo
                if (attempt == 1) throw e                                     // no se pudo conectar dos veces
            }
        }
        return SendOutcome.NO_ANSWER
    }

    /** Cierra el modo configuración. Puede fallar si el altavoz ya ha dejado su red: no es un error. */
    suspend fun leaveSetup() { runCatching { post("/setup", WifiSetupXml.LEAVE_BODY, 5_000) } }

    private suspend fun post(path: String, body: String, timeoutMs: Long): String = withContext(io) {
        val started = log.now()
        // El cliente LAN base corta la lectura a los 4 s: aquí hay que esperar más (el altavoz escanea ~5 s y prueba tu
        // red hasta 30 s antes de contestar). Sin esto, el escaneo y el envío fallaban siempre (1.6.0, Pixel 10).
        val c = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val req = Request.Builder().url(baseUrl + path).post(body.toRequestBody(XML)).header("User-Agent", Http.USER_AGENT).build()
        try {
            c.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                // Nunca se registra el cuerpo: lleva la contraseña.
                log.add("POST", path, "HTTP ${r.code}", r.isSuccessful, started)
                if (!r.isSuccessful) throw SoundTouchException(Txt.s(R.string.ui_http, path, r.code))
                text
            }
        } catch (e: IOException) {
            log.add("POST", path, e.javaClass.simpleName, false, started)
            throw SoundTouchException(Txt.s(R.string.ui_sin_respuesta, path, e.message ?: e.javaClass.simpleName), cause = e)
        }
    }

    companion object { private val XML = "text/xml; charset=utf-8".toMediaType() }
}
