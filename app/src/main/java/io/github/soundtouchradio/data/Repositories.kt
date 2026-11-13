package io.github.soundtouchradio.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

class StationRepository(dir: File) {
    // Primer arranque (sin fichero): emisoras precargadas.
    private val store = JsonFile(File(dir, "stations.json"), ListSerializer(Station.serializer())) { Seed.stations }
    private val _stations = MutableStateFlow(store.read())
    val stations: StateFlow<List<Station>> = _stations

    private fun commit(transform: (List<Station>) -> List<Station>) {
        _stations.update(transform)
        store.write(_stations.value)
    }

    fun upsert(station: Station) = commit { list ->
        if (list.any { it.id == station.id }) list.map { if (it.id == station.id) station else it } else list + station
    }

    fun delete(id: String): Pair<Int, Station>? {
        val idx = _stations.value.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val removed = _stations.value[idx]
        commit { it.filterNot { s -> s.id == id } }
        return idx to removed
    }

    fun restore(index: Int, station: Station) = commit { list ->
        list.toMutableList().apply { add(index.coerceIn(0, size), station) }
    }

    fun move(id: String, delta: Int) = commit { list ->
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** Añade las importadas; las de URL ya existente se ignoran. Devuelve cuántas se añadieron. */
    fun importAll(incoming: List<Station>): Int {
        val existing = _stations.value.map { it.url.trim() }.toMutableSet()
        val fresh = incoming.filter { existing.add(it.url.trim()) }
        if (fresh.isNotEmpty()) commit { it + fresh }
        return fresh.size
    }

    fun updateTech(id: String, codec: String?, bitrate: Int?) {
        val s = _stations.value.firstOrNull { it.id == id } ?: return
        if (s.codec == codec && s.bitrateKbps == bitrate) return
        upsert(s.copy(codec = codec ?: s.codec, bitrateKbps = bitrate ?: s.bitrateKbps))
    }
}

class SpeakerRepository(dir: File) {
    private val store = JsonFile(File(dir, "speakers.json"), ListSerializer(SpeakerRecord.serializer())) { emptyList() }
    private val _speakers = MutableStateFlow(store.read())
    val speakers: StateFlow<List<SpeakerRecord>> = _speakers

    fun remember(s: SpeakerRecord) {
        _speakers.update { list ->
            val i = list.indexOfFirst { it.id == s.id || it.host == s.host }
            if (i < 0) list + s else list.toMutableList().apply {
                val old = this[i]
                // Si cambió la IP, la URL de control guardada se reescribe al nuevo host.
                val control = s.controlUrl ?: old.controlUrl?.let { rebase(it, s.host) }
                this[i] = s.copy(controlUrl = control, manual = old.manual || s.manual)
            }
        }
        store.write(_speakers.value)
    }

    private fun rebase(url: String, host: String): String? =
        url.toHttpUrlOrNull()?.newBuilder()?.host(host)?.build()?.toString()

    fun forget(id: String) {
        _speakers.update { it.filterNot { s -> s.id == id } }
        store.write(_speakers.value)
    }
}

class SettingsRepository(dir: File) {
    private val store = JsonFile(File(dir, "settings.json"), Settings.serializer()) { Settings() }
    private val _settings = MutableStateFlow(store.read())
    val settings: StateFlow<Settings> = _settings

    fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
        store.write(_settings.value)
    }

    fun assignPreset(slot: Int, stationId: String?) = update { s ->
        val list = normalized(s.presets).toMutableList()
        if (slot in list.indices) list[slot] = stationId
        s.copy(presets = list)
    }

    /** Al borrar una emisora, su preset queda vacío. */
    fun clearPresetsFor(stationId: String) = update { s -> s.copy(presets = normalized(s.presets).map { if (it == stationId) null else it }) }

    companion object {
        fun normalized(p: List<String?>): List<String?> =
            (p + List(Seed.PRESET_COUNT) { null }).take(Seed.PRESET_COUNT)
    }
}
