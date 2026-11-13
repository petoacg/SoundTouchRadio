package io.github.soundtouchradio.discovery

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ApConnectException(message: String) : Exception(message)

/**
 * Repite [attempt] cada [intervalMs] hasta que devuelva algo o pase [timeoutMs]. Para detectar la red del altavoz:
 * Android tarda en darle IP («Obteniendo dirección IP…», «Comprobando el acceso…»), así que una sola prueba al
 * volver a la app llega casi siempre a destiempo.
 */
suspend fun <T : Any> pollUntil(timeoutMs: Long, intervalMs: Long, attempt: suspend () -> T?): T? {
    var waited = 0L
    while (true) {
        attempt()?.let { return it }
        if (waited + intervalMs > timeoutMs) return null
        kotlinx.coroutines.delay(intervalMs)
        waited += intervalMs
    }
}

/** La conexión automática (WifiNetworkSpecifier) no llegó a mantenerse: en el Pixel 10 con el SoundTouch 10 entra en bucle. */
internal val AUTO_FAILED: String get() =
    Txt.s(R.string.ui_la_conexion_automatica_no_se) +
        Txt.s(R.string.ui_usa_abrir_wi_fi_es)

/** Mensaje en español según el permiso que Android dice que falta (nunca el texto técnico en inglés a secas). */
internal fun explainSecurity(e: SecurityException): String {
    val m = e.message.orEmpty()
    return when {
        "NEARBY_WIFI_DEVICES" in m || "ACCESS_FINE_LOCATION" in m ->
            Txt.s(R.string.ui_sin_permiso_de_dispositivos_cercanos_2)
        else -> Txt.s(R.string.ui_android_no_dejo_a_la, m.take(120))
    }
}

/**
 * Conecta la app a la red Wi-Fi abierta que crea el SoundTouch en modo configuración («Bose SoundTouch …»).
 * Vía principal (desde la 1.6.0): el usuario se conecta en el panel de Wi-Fi de Android, como con la app de Bose, y
 * responde «Sí» al aviso «sin acceso a Internet»; la app lo detecta con [findJoinedSpeakerNetwork].
 * Vía secundaria, [connect]: WifiNetworkSpecifier («Conectar con dispositivo»). No muestra ese aviso y, con el
 * SoundTouch 10 y un Pixel 10, entra en bucle en «Obteniendo dirección IP…» (capturas del usuario, noviembre de 2026).
 */
class SpeakerApConnector(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    val supportsInAppConnect: Boolean get() = Build.VERSION.SDK_INT >= 29

    suspend fun connect(timeoutMs: Int = 90_000): Network {
        release()
        if (!supportsInAppConnect) {
            @Suppress("DEPRECATION")
            return cm.allNetworks.firstOrNull { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                ?: throw ApConnectException(Txt.s(R.string.ui_conectate_en_ajustes_a_la))
        }
        val spec = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher("Bose", PatternMatcher.PATTERN_PREFIX))
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        return suspendCancellableCoroutine { cont ->
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { if (cont.isActive) cont.resume(network) }
                override fun onUnavailable() {
                    if (cont.isActive) cont.resumeWith(Result.failure(ApConnectException(
                        AUTO_FAILED)))
                }
            }
            callback = cb
            try {
                cm.requestNetwork(request, cb, timeoutMs)
            } catch (e: SecurityException) {
                callback = null
                cont.resumeWith(Result.failure(ApConnectException(explainSecurity(e))))
            }
            cont.invokeOnCancellation { release() }
        }
    }

    /**
     * ¿Está el móvil ya conectado (desde Ajustes) a la red del altavoz? Se prueba cada red Wi-Fi
     * preguntando a 192.0.2.1:8090 por esa red concreta: el SSID no hace falta (y leerlo exige ubicación).
     */
    suspend fun findJoinedSpeakerNetwork(probe: suspend (Network) -> Boolean): Network? {
        @Suppress("DEPRECATION")
        val wifi = cm.allNetworks.filter { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        return wifi.firstOrNull { runCatching { probe(it) }.getOrDefault(false) }
    }

    /**
     * Redes que ve el móvil (último escaneo del sistema; el panel de Wi-Fi acaba de hacer uno). Necesita
     * «Dispositivos cercanos» (Android 13+) o ubicación (10–12): sin permiso devuelve una lista vacía.
     */
    fun phoneScan(): List<io.github.soundtouchradio.soundtouch.PhoneScan> {
        val wm = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION") runCatching { wm.startScan() }
        return runCatching {
            wm.scanResults.map { r ->
                @Suppress("DEPRECATION")
                io.github.soundtouchradio.soundtouch.PhoneScan(r.SSID.orEmpty().removeSurrounding("\""), r.frequency, r.level, r.capabilities.orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    /** Suelta la red del altavoz: el móvil vuelve a la Wi-Fi de casa. */
    fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }
}
