package io.github.soundtouchradio.data

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.playlist.UrlRules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Importación/exportación de emisoras. Formato JSON propio versionado y M3U extendido. */
object StationCodec {

    @Serializable
    data class ExportFile(
        val format: String = FORMAT,
        val version: Int = 1,
        val stations: List<ExportStation>,
    )

    @Serializable
    data class ExportStation(val name: String, val url: String)

    const val FORMAT = "soundtouch-radio-stations"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(stations: List<Station>): String =
        json.encodeToString(ExportFile.serializer(), ExportFile(stations = stations.map { ExportStation(it.name, it.url) }))

    fun toM3u(stations: List<Station>): String = buildString {
        append("#EXTM3U\n")
        for (s in stations) {
            append("#EXTINF:-1,").append(s.name.replace('\n', ' ')).append('\n')
            append(s.url.trim()).append('\n')
        }
    }

    sealed interface ImportResult {
        data class Ok(val stations: List<Station>, val skipped: Int) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    /** Detecta el formato por contenido: JSON (propio o lista simple) o M3U/PLS. */
    fun import(text: String): ImportResult {
        val t = text.removePrefix("﻿").trim()
        if (t.isEmpty()) return ImportResult.Error(Txt.s(R.string.ui_el_archivo_esta_vacio))
        return when {
            // .pls empieza por «[playlist]»: comprobarlo antes que JSON.
            t.startsWith("[playlist]", ignoreCase = true) -> fromPairs(plsPairs(t))
            t.startsWith("{") || t.startsWith("[") -> importJson(t)
            else -> importM3u(t)
        }
    }

    private fun importJson(t: String): ImportResult = try {
        val list = if (t.startsWith("[")) json.decodeFromString<List<ExportStation>>(t)
        else json.decodeFromString(ExportFile.serializer(), t).also {
            require(it.format == FORMAT) { Txt.s(R.string.ui_formato_desconocido, it.format) }
        }.stations
        fromPairs(list.map { it.name to it.url })
    } catch (e: Exception) {
        ImportResult.Error("JSON no válido: ${e.message?.lineSequence()?.firstOrNull() ?: "error de formato"}")
    }

    private fun importM3u(t: String): ImportResult {
        if (t.lineSequence().any { it.trim().startsWith("#EXT-X-") })
            return ImportResult.Error(Txt.s(R.string.ui_es_un_manifiesto_hls_de))
        val pairs = mutableListOf<Pair<String?, String>>()
        var pendingName: String? = null
        for (raw in t.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingName = line.substringAfter(',', "").trim().ifEmpty { null }
                line.startsWith("#") -> Unit
                else -> { pairs += pendingName to line; pendingName = null }
            }
        }
        return fromPairs(pairs)
    }

    private fun plsPairs(t: String): List<Pair<String?, String>> {
        val titles = Regex("""^\s*Title(\d+)\s*=\s*(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .findAll(t).associate { it.groupValues[1] to it.groupValues[2].trim() }
        return Regex("""^\s*File(\d+)\s*=\s*(.+?)\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .findAll(t).map { titles[it.groupValues[1]] to it.groupValues[2] }.toList()
    }

    private fun fromPairs(pairs: List<Pair<String?, String>>): ImportResult {
        var skipped = 0
        val out = pairs.mapNotNull { (name, url) ->
            when (val v = UrlRules.validate(url)) {
                is UrlRules.Validation.Ok -> Station(name = name?.takeIf { it.isNotBlank() } ?: defaultName(v.url), url = v.url)
                is UrlRules.Validation.Invalid -> { skipped++; null }
            }
        }
        return if (out.isEmpty()) ImportResult.Error(Txt.s(R.string.ui_no_se_encontro_ninguna_url))
        else ImportResult.Ok(out, skipped)
    }

    fun defaultName(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { Txt.s(R.string.ui_emisora_2) }

}
