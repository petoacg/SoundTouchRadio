package io.github.soundtouchradio.directory

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.soundtouch.Http
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Emisora del directorio comunitario radio-browser.info (https://www.radio-browser.info): abierto, sin cuenta
 * ni clave. Solo los campos que usa la app; el resto se ignora.
 */
@Serializable
data class RbStation(
    val stationuuid: String,
    val name: String,
    val url: String = "",
    val url_resolved: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Int = 0,
    val lastcheckok: Int = 1,
    val countrycode: String = "",
    val tags: String = "",
    val clickcount: Int = 0,
) {
    /** La URL a usar: la http si el directorio da alguna (el altavoz no admite https); si no, la resuelta. */
    val streamUrl: String get() = listOf(url_resolved, url).map { it.trim() }.firstOrNull { it.startsWith("http://") }
        ?: url_resolved.ifBlank { url }.trim()
    /** Solo https: el altavoz no la reproduce tal cual; la app probará su versión http al probarla. */
    val httpsOnly: Boolean get() = streamUrl.startsWith("https://")
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Géneros de los chips. Cada uno busca su etiqueta en inglés y en español (el directorio mezcla ambas). */
enum class Genre(val labelRes: Int, val tags: List<String>) {
    NEWS(R.string.ui_noticias, listOf("news", "noticias")),
    SPORTS(R.string.ui_deportes, listOf("sports", "deportes")),
    MUSIC(R.string.ui_musica, listOf("music", "música")),
    TALK(R.string.ui_tertulia, listOf("talk", "información")),
}

/** Países del selector: código ISO y nombre. null = todo el mundo. */
val COUNTRIES: List<Pair<String?, Int>> = listOf(
    "ES" to R.string.ui_espana, null to R.string.ui_todo_el_mundo_2, "PT" to R.string.ui_portugal, "FR" to R.string.ui_francia, "IT" to R.string.ui_italia,
    "DE" to R.string.ui_alemania, "GB" to R.string.ui_reino_unido, "US" to R.string.ui_estados_unidos, "MX" to R.string.ui_mexico, "AR" to R.string.ui_argentina,
    "CO" to R.string.ui_colombia, "CL" to R.string.ui_chile,
)

data class RbQuery(val text: String = "", val country: String? = "ES", val genre: Genre? = null, val compatibleOnly: Boolean = true)

object RadioBrowserJson {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    fun parse(text: String): List<RbStation> = json.decodeFromString(ListSerializer(RbStation.serializer()), text)

    /** Formatos que reproduce el SoundTouch por UPnP. HLS no (la app ya lo detecta y lo explica en otras pantallas). */
    private val PLAYABLE = setOf("MP3", "AAC", "AAC+", "HE-AAC")

    /** ¿La puede reproducir el altavoz, según el directorio? Sin HLS, formato compatible, última comprobación correcta. */
    fun compatible(s: RbStation): Boolean =
        s.hls == 0 && s.lastcheckok == 1 && s.streamUrl.startsWith("http") && s.codec.uppercase() in PLAYABLE &&
            !s.streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    /**
     * Une resultados de varias consultas: sin duplicados (mismo uuid o misma URL). Primero las http (suenan tal cual),
     * después las que solo dan https (dependen de que exista su versión http); dentro de cada grupo, las más escuchadas.
     */
    fun merge(lists: List<List<RbStation>>, compatibleOnly: Boolean): List<RbStation> =
        lists.flatten().distinctBy { it.stationuuid }.distinctBy { it.streamUrl.lowercase() }
            .filter { !compatibleOnly || compatible(it) }
            .sortedWith(compareBy<RbStation> { it.httpsOnly }.thenByDescending { it.clickcount })
}

/**
 * Cliente del directorio. Solo se conecta cuando el usuario busca. No pide logotipos (imágenes de terceros)
 * ni registra clics: envía la búsqueda y la identificación «SoundTouchRadio». Si un servidor no responde, prueba el siguiente.
 */
class RadioBrowser(
    private val client: OkHttpClient,
    private val hosts: List<String> = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info"),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val limit: Int = 80,
) {
    @Volatile private var preferred: String? = null

    suspend fun search(q: RbQuery): List<RbStation> = coroutineScope {
        val tagSets: List<String?> = q.genre?.tags ?: listOf(null)
        val results = tagSets.map { tag -> async { request(q, tag) } }.map { it.await() }
        RadioBrowserJson.merge(results, q.compatibleOnly)
    }

    private suspend fun request(q: RbQuery, tag: String?): List<RbStation> = withContext(io) {
        val order = listOfNotNull(preferred) + hosts.filter { it != preferred }
        var last: Exception? = null
        for (host in order) {
            val url = "https://$host/json/stations/search".toHttpUrl().newBuilder().apply {
                if (q.text.isNotBlank()) addQueryParameter("name", q.text.trim())
                q.country?.let { addQueryParameter("countrycode", it) }
                tag?.let { addQueryParameter("tag", it) }
                addQueryParameter("hidebroken", "true")
                addQueryParameter("order", "clickcount")
                addQueryParameter("reverse", "true")
                addQueryParameter("limit", limit.toString())
            }.build()
            try {
                client.newCall(Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val list = RadioBrowserJson.parse(r.body?.string().orEmpty())
                    preferred = host
                    return@withContext list
                }
            } catch (e: Exception) { last = e }
        }
        throw IOException("El directorio de emisoras no responde (${last?.message ?: "sin conexión"}).")
    }
}
