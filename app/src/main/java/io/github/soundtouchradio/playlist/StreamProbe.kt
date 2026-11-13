package io.github.soundtouchradio.playlist

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.soundtouch.Http
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class ProbeResult(
    val requestedUrl: String,
    val finalUrl: String,
    val httpCode: Int?,
    val contentType: String?,
    val icyName: String?,
    val icyBitrate: Int?,
    val error: String?,
) {
    val reachable: Boolean get() = error == null && httpCode != null && httpCode in 200..299
    val redirectedToHttps: Boolean get() = !UrlRules.isHttps(requestedUrl) && UrlRules.isHttps(finalUrl)
    val looksLikeAudio: Boolean get() = contentType?.lowercase()?.let {
        (it.startsWith("audio/") && UrlRules.playlistKind(finalUrl, it) == null) || it.startsWith("application/ogg")
    } ?: false
}

enum class StreamWarning { HTTPS, HLS, REDIRECTS_TO_HTTPS, NOT_VERIFIED }

data class ResolvedStream(
    val originalUrl: String,
    val streamUrl: String,
    val mime: String,
    val probe: ProbeResult?,
    val viaPlaylist: Boolean,
    val warnings: Set<StreamWarning>,
)

class ResolveException(message: String) : Exception(message)

/**
 * Comprueba y resuelve URLs de emisoras desde el móvil. Es el único tráfico fuera de la LAN,
 * y siempre va al servidor de la propia emisora.
 */
class StreamProbe(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Lee solo cabeceras (y corta) para no descargar el stream. */
    suspend fun probe(url: String): ProbeResult = withContext(io) {
        val req = Request.Builder().url(url)
            .header("Icy-MetaData", "1")
            .header("User-Agent", Http.USER_AGENT)
            .build()
        try {
            client.newCall(req).execute().use { r ->
                ProbeResult(
                    requestedUrl = url,
                    finalUrl = r.request.url.toString(),
                    httpCode = r.code,
                    contentType = r.header("Content-Type"),
                    icyName = r.header("icy-name")?.trim()?.ifEmpty { null },
                    icyBitrate = r.header("icy-br")?.substringBefore(',')?.trim()?.toIntOrNull(),
                    error = if (r.isSuccessful) null else "HTTP ${r.code}",
                )
            }
        } catch (e: IOException) {
            ProbeResult(url, url, null, null, null, null, e.message ?: e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            ProbeResult(url, url, null, null, null, null, Txt.s(R.string.ui_url_no_valida))
        }
    }

    private suspend fun fetchText(url: String): Pair<String, String> = withContext(io) {
        val req = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        try {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ResolveException(Txt.s(R.string.ui_la_lista_respondio_http, r.code))
                val src = r.body?.source() ?: throw ResolveException(Txt.s(R.string.ui_la_lista_esta_vacia))
                src.request(MAX_PLAYLIST_BYTES)
                val bytes = src.buffer.readByteArray(minOf(src.buffer.size, MAX_PLAYLIST_BYTES))
                PlaylistParser.decode(bytes) to r.request.url.toString()
            }
        } catch (e: IOException) {
            throw ResolveException(Txt.s(R.string.ui_no_se_pudo_descargar_la, e.message ?: e.javaClass.simpleName))
        }
    }

    /**
     * Devuelve la URL que debe recibir el altavoz. Resuelve .pls/.m3u (anidadas hasta 3 niveles)
     * y elige la primera entrada que responda con audio.
     */
    suspend fun resolve(url: String): ResolvedStream = resolve(url, url, 0)

    private suspend fun resolve(original: String, url: String, depth: Int): ResolvedStream {
        if (depth > MAX_DEPTH) throw ResolveException(Txt.s(R.string.ui_demasiadas_listas_anidadas))
        if (UrlRules.looksLikeHls(url)) return finish(original, url, null, depth > 0)

        val kindByName = UrlRules.playlistKind(url)
        if (kindByName != null) return resolvePlaylist(original, url, kindByName, depth)

        val probe = probe(url)
        if (UrlRules.isHlsContentType(probe.contentType)) return finish(original, probe.finalUrl, probe, depth > 0)
        val kindByType = UrlRules.playlistKind(probe.finalUrl, probe.contentType)
        if (probe.reachable && kindByType != null) return resolvePlaylist(original, probe.finalUrl, kindByType, depth)
        return finish(original, url, probe, depth > 0)
    }

    private suspend fun resolvePlaylist(original: String, url: String, kind: PlaylistKind, depth: Int): ResolvedStream {
        val (text, finalUrl) = fetchText(url)
        return when (val content = PlaylistParser.parse(kind, text, finalUrl)) {
            PlaylistContent.Hls -> finish(original, finalUrl, null, true)
            is PlaylistContent.Entries -> {
                if (content.urls.isEmpty()) throw ResolveException(Txt.s(R.string.ui_la_lista_no_contiene_ninguna))
                var fallback: ResolvedStream? = null
                for (entry in content.urls.take(MAX_ENTRIES)) {
                    val r = runCatching { resolve(original, entry, depth + 1) }.getOrNull() ?: continue
                    if (r.probe?.reachable == true) return r.copy(viaPlaylist = true)
                    if (fallback == null) fallback = r.copy(viaPlaylist = true)
                }
                fallback ?: throw ResolveException(Txt.s(R.string.ui_ninguna_entrada_de_la_lista))
            }
        }
    }

    private fun finish(original: String, url: String, probe: ProbeResult?, viaPlaylist: Boolean): ResolvedStream {
        val warnings = mutableSetOf<StreamWarning>()
        if (UrlRules.isHttps(url)) warnings += StreamWarning.HTTPS
        if (UrlRules.looksLikeHls(url) || UrlRules.isHlsContentType(probe?.contentType)) warnings += StreamWarning.HLS
        if (probe?.redirectedToHttps == true) warnings += StreamWarning.REDIRECTS_TO_HTTPS
        if (probe == null || !probe.reachable) warnings += StreamWarning.NOT_VERIFIED
        // Si el móvil siguió redirecciones y la URL final sigue siendo http, se envía esa:
        // así no dependemos de que el renderer del altavoz siga redirecciones.
        val target = if (probe?.reachable == true && !UrlRules.isHttps(probe.finalUrl)) probe.finalUrl else url
        return ResolvedStream(
            originalUrl = original,
            streamUrl = target,
            mime = UrlRules.guessMime(target, probe?.contentType),
            probe = probe,
            viaPlaylist = viaPlaylist,
            warnings = warnings,
        )
    }

    companion object {
        const val MAX_DEPTH = 3
        const val MAX_ENTRIES = 5
        const val MAX_PLAYLIST_BYTES = 64L * 1024
    }
}
