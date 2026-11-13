package io.github.soundtouchradio.playlist

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class PlaylistKind { PLS, M3U }

/** Reglas puras sobre URLs de emisoras. Sin red. */
object UrlRules {

    sealed interface Validation {
        data class Ok(val url: String) : Validation
        data class Invalid(val reason: String) : Validation
    }

    fun validate(input: String): Validation {
        val s = input.trim()
        if (s.isEmpty()) return Validation.Invalid(Txt.s(R.string.ui_escribe_la_url_del_stream))
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true))
            return Validation.Invalid("La URL debe empezar por http:// o https://")
        val u = s.toHttpUrlOrNull() ?: return Validation.Invalid(Txt.s(R.string.ui_no_parece_una_url_valida))
        return Validation.Ok(u.toString())
    }

    fun isHttps(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

    /** Variante http de una URL https (quita el puerto 443 explícito). Null si no es https. */
    fun httpVariant(url: String): String? {
        val u = url.trim().toHttpUrlOrNull() ?: return null
        if (!u.isHttps) return null
        val b: HttpUrl.Builder = u.newBuilder().scheme("http")
        if (u.port == 443) b.port(80)
        return b.build().toString()
    }

    private fun path(url: String): String =
        url.toHttpUrlOrNull()?.encodedPath?.lowercase() ?: url.substringBefore('?').lowercase()

    fun looksLikeHls(url: String): Boolean = path(url).endsWith(".m3u8")

    fun playlistKind(url: String, contentType: String? = null): PlaylistKind? {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase()
        val p = path(url)
        if (p.endsWith(".m3u8") || ct == "application/vnd.apple.mpegurl") return null // HLS, no lista simple
        return when {
            p.endsWith(".pls") || ct == "audio/x-scpls" -> PlaylistKind.PLS
            p.endsWith(".m3u") || ct == "audio/x-mpegurl" || ct == "audio/mpegurl" -> PlaylistKind.M3U
            else -> null
        }
    }

    fun isHlsContentType(contentType: String?): Boolean {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return ct == "application/vnd.apple.mpegurl" || ct == "application/x-mpegurl"
    }

    /** MIME para el DIDL: el Content-Type real si es de audio; si no, por extensión; MP3 por defecto. */
    fun guessMime(url: String, contentType: String? = null): String {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (ct != null && ct.startsWith("audio/") && ct != "audio/x-scpls" && ct != "audio/x-mpegurl" && ct != "audio/mpegurl") {
            return if (ct == "audio/aacp") "audio/aac" else ct
        }
        val p = path(url)
        return when {
            p.endsWith(".aac") -> "audio/aac"
            p.endsWith(".m4a") || p.endsWith(".mp4") -> "audio/mp4"
            p.endsWith(".ogg") || p.endsWith(".oga") -> "audio/ogg"
            p.endsWith(".flac") -> "audio/flac"
            p.endsWith(".wma") || p.endsWith(".asf") -> "audio/x-ms-wma"
            else -> "audio/mpeg"
        }
    }

    /** Etiqueta corta del códec para mostrar («MP3», «AAC»…). */
    fun codecLabel(mime: String?): String? = when (mime?.lowercase()) {
        null -> null
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/aac", "audio/aacp", "audio/mp4" -> "AAC"
        "audio/ogg" -> "OGG"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/x-ms-wma" -> "WMA"
        else -> mime.substringAfter('/').uppercase().take(6)
    }
}
