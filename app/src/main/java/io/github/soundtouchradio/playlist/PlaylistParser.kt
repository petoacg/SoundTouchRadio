package io.github.soundtouchradio.playlist

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

sealed interface PlaylistContent {
    data class Entries(val urls: List<String>) : PlaylistContent
    /** Es un manifiesto HLS (#EXT-X-…), no una lista de streams. */
    data object Hls : PlaylistContent
}

object PlaylistParser {

    /** Decodifica en UTF-8 si es válido; si no, ISO-8859-1 (frecuente en .pls antiguos). Quita BOM. */
    fun decode(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
        return text.removePrefix("﻿")
    }

    fun parse(kind: PlaylistKind, text: String, baseUrl: String? = null): PlaylistContent = when (kind) {
        PlaylistKind.PLS -> PlaylistContent.Entries(parsePls(text, baseUrl))
        PlaylistKind.M3U -> parseM3u(text, baseUrl)
    }

    fun parsePls(text: String, baseUrl: String? = null): List<String> {
        val re = Regex("""^\s*File(\d+)\s*=\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
        return text.lineSequence().mapNotNull { re.find(it) }
            .map { it.groupValues[1].toInt() to it.groupValues[2] }
            .sortedBy { it.first }
            .mapNotNull { absolutize(it.second, baseUrl) }
            .distinct().toList()
    }

    fun parseM3u(text: String, baseUrl: String? = null): PlaylistContent {
        if (text.lineSequence().any { it.trim().startsWith("#EXT-X-") }) return PlaylistContent.Hls
        val urls = text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { absolutize(it, baseUrl) }
            .distinct().toList()
        return PlaylistContent.Entries(urls)
    }

    private fun absolutize(ref: String, baseUrl: String?): String? {
        ref.toHttpUrlOrNull()?.let { return it.toString() }
        val base = baseUrl?.toHttpUrlOrNull() ?: return null
        return base.resolve(ref)?.toString()
    }
}
