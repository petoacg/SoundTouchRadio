package io.github.soundtouchradio.ui

import io.github.soundtouchradio.R

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.soundtouchradio.App
import io.github.soundtouchradio.data.Seed
import io.github.soundtouchradio.data.SettingsRepository
import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.data.Station
import io.github.soundtouchradio.data.StationCodec
import io.github.soundtouchradio.discovery.Candidate
import io.github.soundtouchradio.discovery.FoundVia
import io.github.soundtouchradio.discovery.LinkReport
import io.github.soundtouchradio.player.Phase
import io.github.soundtouchradio.player.PlayRequest
import io.github.soundtouchradio.player.SpeakerController
import io.github.soundtouchradio.player.SpeakerState
import io.github.soundtouchradio.playlist.ProbeResult
import io.github.soundtouchradio.playlist.UrlRules
import io.github.soundtouchradio.soundtouch.Zone
import io.github.soundtouchradio.soundtouch.ZoneMember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** WATCHING: el usuario está en el panel de Wi-Fi de Android y la app espera, comprobando cada 2 s, a que el móvil entre en la red del altavoz. */
enum class WifiStage { INTRO, WATCHING, CONNECTING, CHOOSE, SENDING, WAITING, FINDING, DONE, FAILED }

data class WifiSetupUi(
    val stage: WifiStage = WifiStage.INTRO,
    val steps: List<io.github.soundtouchradio.discovery.LinkStep> = WifiSetupSteps.initial(),
    val deviceInfo: io.github.soundtouchradio.soundtouch.DeviceInfo? = null,
    val networks: List<io.github.soundtouchradio.soundtouch.WifiNetwork> = emptyList(),
    val surveyFailed: Boolean = false,
    /** El altavoz devolvió su propia lista de redes: lo que no está en ella, no lo ve (1.9.3). */
    val speakerSurveyOk: Boolean = false,
    val chosenSsid: String? = null,
    val secondsLeft: Int = 0,
    val report: LinkReport? = null,
    /** Fallo por permiso «Dispositivos cercanos» denegado: la UI ofrece abrir los ajustes de la app. */
    val permissionDenied: Boolean = false,
    /** Segundos que lleva esperando a la red del altavoz (etapa WATCHING). */
    val watchedSeconds: Int = 0,
    /** Escaneando redes (altavoz + móvil) tras «Actualizar». */
    val refreshing: Boolean = false,
    /** Paso 5 falló y el altavoz sigue en su propia red: se puede corregir y volver a enviar. */
    val stillInSetup: Boolean = false,
)

object WifiSetupSteps {
    fun initial() = listOf(
        io.github.soundtouchradio.discovery.LinkStep(Txt.s(R.string.ui_modo_configuracion)),
        io.github.soundtouchradio.discovery.LinkStep(Txt.s(R.string.ui_conectar_con_el_altavoz)),
        io.github.soundtouchradio.discovery.LinkStep(Txt.s(R.string.ui_tu_red_wi_fi)),
        io.github.soundtouchradio.discovery.LinkStep(Txt.s(R.string.ui_enviar_y_esperar)),
        io.github.soundtouchradio.discovery.LinkStep(Txt.s(R.string.ui_encontrar_y_vincular)),
    )
}

/** Buscador de emisoras (directorio radio-browser.info). */
data class SearchUi(
    val query: io.github.soundtouchradio.directory.RbQuery = io.github.soundtouchradio.directory.RbQuery(),
    val loading: Boolean = false,
    val results: List<io.github.soundtouchradio.directory.RbStation> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
)

data class Snack(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class DiscoveryUi(val scanning: Boolean = false, val candidates: List<Candidate> = emptyList(), val scannedOnce: Boolean = false)

data class BenchRow(val url: String, val result: String, val mime: String?, val seconds: Double, val ok: Boolean)
data class BenchUi(val running: Boolean = false, val current: Int = 0, val total: Int = 0, val rows: List<BenchRow> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as App).container
    val log = c.log

    val stations = c.stations.stations
    val speakers = c.speakers.speakers
    val settings = c.settings.settings

    val activeSpeaker: StateFlow<SpeakerRecord?> = combine(speakers, settings) { list, s ->
        list.firstOrNull { it.id == s.activeSpeakerId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeController: StateFlow<SpeakerController?> = activeSpeaker
        .map { it?.let(c::controllerFor) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Destino «Este móvil» (1.10.0). */
    val onPhone: StateFlow<Boolean> = settings.map { it.playOnPhone }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Estado del destino elegido: el altavoz activo o el reproductor del móvil, con la misma forma. */
    val speakerState: StateFlow<SpeakerState> = combine(activeController, onPhone) { ctrl, phone -> ctrl to phone }
        .flatMapLatest { (ctrl, phone) -> if (phone) c.phone.state else ctrl?.state ?: flowOf(SpeakerState()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SpeakerState())

    private fun phoneMode() = settings.value.playOnPhone

    /** Lo que suena ahora en un destino, para pasarlo al otro al cambiar. */
    private fun nowPlayingRequest(st: SpeakerState): PlayRequest? {
        val playing = st.phase is io.github.soundtouchradio.player.Phase.Playing || st.phase is io.github.soundtouchradio.player.Phase.Buffering
        if (!playing) return null
        stations.value.firstOrNull { it.id == st.stationId }?.let { return PlayRequest(it.id, it.name, it.url) }
        val url = st.resolved?.originalUrl ?: return null
        return PlayRequest(st.stationId, st.stationName ?: Txt.s(R.string.ui_radio_3), url)
    }

    /** Cambiar de destino: lo que sonaba se para en el anterior y sigue en el nuevo. */
    private fun switchTo(phone: Boolean, speakerId: String? = null) {
        val before = speakerState.value
        val wasPhone = phoneMode()
        val req = nowPlayingRequest(before)
        if (wasPhone == phone && (speakerId == null || speakerId == activeSpeaker.value?.id)) return
        if (req != null) { if (wasPhone) c.phone.stop() else activeController.value?.stopPlayback() }
        c.settings.update { it.copy(playOnPhone = phone, activeSpeakerId = speakerId ?: it.activeSpeakerId) }
        if (req != null) viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            if (phone) c.phone.play(req) else activeSpeaker.value?.let { c.controllerFor(it).play(req) }
        }
    }

    fun selectPhone() = switchTo(phone = true)

    // --- Ecualizador de «Este móvil» (1.10.2) --------------------------------------------------

    val eqBands get() = c.phone.eqBands

    fun setEq(output: String, setting: io.github.soundtouchradio.player.EqSetting) {
        c.settings.update { it.copy(eq = it.eq + (output to setting)) }
        c.phone.applyEq()
    }

    val presets: StateFlow<List<Station?>> = combine(stations, settings) { st, s ->
        SettingsRepository.normalized(s.presets).map { id -> st.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, List(Seed.PRESET_COUNT) { null })

    private val _snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 4)
    val snacks = _snacks.asSharedFlow()
    private fun snack(s: Snack) { _snacks.tryEmit(s) }

    // --- Temporizador de apagado ---------------------------------------------------------

    val sleep: StateFlow<io.github.soundtouchradio.player.SleepState?> = onPhone
        .flatMapLatest { if (it) c.phone.sleep else c.sleep.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            c.sleep.events.collect { msg ->
                snack(Snack(msg))
                activeController.value?.nudge()
            }
        }
    }

    fun startSleep(minutes: Int, fade: Boolean) {
        if (phoneMode()) return c.phone.startSleep(minutes, fade)
        val s = activeSpeaker.value ?: return snack(Snack(Txt.s(R.string.ui_primero_vincula_un_altavoz)))
        c.sleep.start(s, minutes, fade)
        io.github.soundtouchradio.SleepTimerService.start(getApplication())
    }

    fun cancelSleep() { if (phoneMode()) c.phone.cancelSleep(null) else c.sleep.cancel() }

    // --- Despertador ------------------------------------------------------------------------

    /** Guarda y programa en el sistema. Al activarlo sin emisora/altavoz, se completan con los actuales. */
    fun saveAlarm(a: io.github.soundtouchradio.data.AlarmSettings, announce: Boolean = false) {
        val filled = a.copy(
            stationId = a.stationId ?: presets.value.firstOrNull { it != null }?.id ?: stations.value.firstOrNull()?.id,
            speakerId = a.speakerId ?: activeSpeaker.value?.id,
        )
        c.settings.update { it.copy(alarm = filled) }
        reschedule(announce && filled.enabled, io.github.soundtouchradio.player.AlarmPlan.nextTrigger(filled, java.time.ZonedDateTime.now()))
    }

    /**
     * Alarma «Una vez». Al activarla por primera vez toma la emisora y el volumen del despertador semanal
     * y, si no tiene fecha (o ya pasó), mañana.
     */
    fun saveOnce(o: io.github.soundtouchradio.data.OnceAlarm, announce: Boolean = false) {
        val now = java.time.ZonedDateTime.now()
        val weekly = settings.value.alarm
        val stale = o.date == null || runCatching { java.time.LocalDate.parse(o.date) < now.toLocalDate() }.getOrDefault(true)
        val filled = o.copy(
            date = if (stale) io.github.soundtouchradio.player.AlarmPlan.defaultOnceDate(now) else o.date,
            stationId = o.stationId ?: weekly.stationId ?: presets.value.firstOrNull { it != null }?.id ?: stations.value.firstOrNull()?.id,
        )
        c.settings.update { s ->
            s.copy(once = filled, alarm = if (s.alarm.speakerId == null) s.alarm.copy(speakerId = activeSpeaker.value?.id) else s.alarm)
        }
        val next = io.github.soundtouchradio.player.AlarmPlan.nextOnce(filled, now)
        reschedule(announce && filled.enabled, next)
        if (announce && filled.enabled && next == null) snack(Snack(Txt.s(R.string.ui_esa_hora_ya_ha_pasado_2)))
    }

    private fun reschedule(announce: Boolean, next: java.time.ZonedDateTime?) {
        io.github.soundtouchradio.AlarmScheduler.schedule(getApplication(), settings.value)
        if (announce && next != null) snack(Snack(io.github.soundtouchradio.player.AlarmPlan.nextLabel(next, java.time.ZonedDateTime.now()) + "."))
    }

    fun testAlarm(once: Boolean = false) {
        val id = if (once) settings.value.once.stationId ?: settings.value.alarm.stationId else settings.value.alarm.stationId
        if (id == null) return snack(Snack(Txt.s(R.string.ui_elige_una_emisora_para_el)))
        io.github.soundtouchradio.AlarmService.start(getApplication(), test = true,
            kind = if (once) io.github.soundtouchradio.player.AlarmPlan.Kind.ONCE else io.github.soundtouchradio.player.AlarmPlan.Kind.WEEKLY)
        snack(Snack(Txt.s(R.string.ui_probando_el_despertador_encender_poner)))
    }

    fun canScheduleExactAlarm(): Boolean = io.github.soundtouchradio.AlarmScheduler.canScheduleExact(getApplication())

    // --- Buscar emisoras (directorio) ----------------------------------------------

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search
    private var searchJob: Job? = null

    /** Solo aquí se conecta la app al directorio: cuando el usuario busca. */
    fun searchStations(q: io.github.soundtouchradio.directory.RbQuery) {
        searchJob?.cancel()
        _search.update { it.copy(query = q, loading = true, error = null) }
        searchJob = viewModelScope.launch {
            val r = runCatching { c.directory.search(q) }
            _search.update {
                it.copy(loading = false, searched = true,
                    results = r.getOrDefault(emptyList()),
                    error = r.exceptionOrNull()?.let { e -> e.message ?: Txt.s(R.string.ui_no_se_pudo_consultar_el) })
            }
        }
    }

    /** Identificador temporal para probar sin guardar: la luz piloto de la fila sigue a esta emisora. */
    fun directoryTrialId(s: io.github.soundtouchradio.directory.RbStation) = "rb:" + s.stationuuid

    fun tryDirectoryStation(s: io.github.soundtouchradio.directory.RbStation) =
        play(Station(id = directoryTrialId(s), name = s.name.trim(), url = s.streamUrl))

    /** Guarda la emisora (si ya existe esa URL, la reutiliza) y, si se pide, la pone en una tecla. */
    fun saveDirectoryStation(s: io.github.soundtouchradio.directory.RbStation, presetSlot: Int?, playedUrl: String? = null) {
        // Si la prueba sonó por la versión http, se guarda esa (la https no suena en el SoundTouch).
        val url = playedUrl ?: s.streamUrl
        val existing = stations.value.firstOrNull { it.url.equals(url, ignoreCase = true) || it.url.equals(s.streamUrl, ignoreCase = true) }
        val st = existing ?: Station(name = s.name.trim().take(60), url = url,
            codec = s.codec.uppercase().takeIf { it.isNotBlank() }, bitrateKbps = s.bitrate.takeIf { it > 0 })
        if (existing == null) c.stations.upsert(st)
        presetSlot?.let { c.settings.assignPreset(it, st.id) }
        snack(Snack(buildString {
            append(if (existing == null) Txt.s(R.string.ui_guardada_2, st.name) else Txt.s(R.string.ui_ya_estaba_guardada, st.name))
            presetSlot?.let { append(Txt.s(R.string.ui_tecla, it + 1)) }
            append(".")
        }))
    }

    // --- Reproducción ---------------------------------------------------------------

    fun play(station: Station) {
        if (phoneMode()) return c.phone.play(PlayRequest(station.id, station.name, station.url))
        val ctrl = activeController.value ?: return snack(Snack(Txt.s(R.string.ui_primero_vincula_un_altavoz)))
        ctrl.play(PlayRequest(station.id, station.name, station.url))
    }

    fun playPreset(slot: Int) { presets.value.getOrNull(slot)?.let(::play) }
    fun stop() { if (phoneMode()) c.phone.stop() else activeController.value?.stopPlayback() }
    fun pause() { if (phoneMode()) c.phone.pause() else activeController.value?.pause() }
    fun resume() { if (phoneMode()) c.phone.resume() else activeController.value?.resume() }
    fun setVolume(v: Int) { if (phoneMode()) c.phone.setVolume(v) else activeController.value?.setVolume(v) }
    fun stepVolume(delta: Int) { if (phoneMode()) c.phone.stepVolume(delta) else activeController.value?.stepVolume(delta * settings.value.volumeStep.coerceAtLeast(1)) }
    // --- Puente de redirección a https (1.9.5, experimental) ---------------------------------

    private val _bridgeTest = MutableStateFlow(false)
    val bridgeTestRunning: StateFlow<Boolean> = _bridgeTest

    /**
     * Prueba: ¿sigue el altavoz una redirección a https? Se le da una dirección http del móvil que solo redirige a
     * [httpsUrl]. Si el altavoz confirma que suena, se activa el puente para las emisoras que solo emiten por https.
     */
    fun testHttpsRedirect(httpsUrl: String) {
        val ctrl = activeController.value ?: return snack(Snack(Txt.s(R.string.ui_primero_vincula_un_altavoz)))
        val url = httpsUrl.trim()
        if (!url.startsWith("https://")) return snack(Snack(Txt.s(R.string.ui_bridge_need_https)))
        val bridgeUrl = c.redirect.urlFor(url) ?: return snack(Snack(Txt.s(R.string.ui_bridge_no_ip)))
        _bridgeTest.value = true
        viewModelScope.launch {
            ctrl.play(PlayRequest("bridge-test", Txt.s(R.string.ui_bridge_test_name), bridgeUrl)).join()
            val ok = ctrl.state.value.phase is io.github.soundtouchradio.player.Phase.Playing
            // Si falla, no se desactiva: puede haber fallado la emisora de prueba. Queda como estaba.
            if (ok) c.settings.update { it.copy(httpsBridge = true) }
            _bridgeTest.value = false
            snack(Snack(if (ok) Txt.s(R.string.ui_bridge_ok) else Txt.s(R.string.ui_bridge_fail)))
        }
    }

    fun setHttpsBridge(on: Boolean?) = c.settings.update { it.copy(httpsBridge = on) }

    fun markMediaNotifAsked() = c.settings.update { it.copy(mediaNotifAsked = true) }
    fun setVolumeStep(step: Int) = c.settings.update { it.copy(volumeStep = step.coerceIn(1, 10)) }

    /**
     * Botones físicos de volumen con la app abierta (1.8.0): mueven un punto el altavoz si está encendido y responde.
     * Devuelve false para que Android haga lo de siempre (volumen del móvil) si no hay altavoz que controlar.
     */
    fun hardwareVolume(delta: Int): Boolean {
        if (phoneMode()) return false   // «Este móvil»: los botones hacen lo de siempre (volumen multimedia)
        val c = activeController.value ?: return false
        val st = c.state.value
        if (!st.reachable || st.volume == null || st.speakerOn != true) return false
        c.stepVolume(delta * settings.value.volumeStep.coerceAtLeast(1))
        return true
    }
    fun toggleMute() { if (phoneMode()) c.phone.toggleMute() else activeController.value?.toggleMute() }
    /** Encender/apagar con confirmación del altavoz (ver SpeakerController.setPower). */
    fun setPower(on: Boolean) {
        val ctrl = activeController.value ?: return
        val name = activeSpeaker.value?.name ?: Txt.s(R.string.ui_el_altavoz)
        viewModelScope.launch {
            when (ctrl.setPower(on)) {
                io.github.soundtouchradio.player.PowerResult.DONE, io.github.soundtouchradio.player.PowerResult.ALREADY ->
                    snack(Snack(if (on) "$name encendido." else Txt.s(R.string.ui_en_reposo_2, name)))
                io.github.soundtouchradio.player.PowerResult.NO_CONFIRMATION ->
                    snack(Snack(Txt.s(R.string.ui_no_confirmo_el_cambio_en, name)))
                io.github.soundtouchradio.player.PowerResult.UNREACHABLE ->
                    snack(Snack(Txt.s(R.string.ui_no_se_encuentra_en_la, name)))
            }
        }
    }

    /** «Probar versión http» desde el panel de error: cambia la emisora si la variante responde con audio. */
    fun tryHttpVariant(stationId: String?) {
        val st = stations.value.firstOrNull { it.id == stationId } ?: return
        val http = UrlRules.httpVariant(st.url) ?: return
        viewModelScope.launch {
            val p = c.probe.probe(http)
            if (p.reachable && (p.looksLikeAudio || UrlRules.playlistKind(p.finalUrl, p.contentType) != null)) {
                val updated = st.copy(url = http)
                c.stations.upsert(updated)
                snack(Snack(Txt.s(R.string.ui_guardada_la_version_http_de, st.name)))
                play(updated)
            } else {
                snack(Snack(Txt.s(R.string.ui_version_http_de_no_responde, st.name, p.error ?: p.contentType ?: Txt.s(R.string.ui_sin_audio))))
            }
        }
    }

    fun clearFailure() { if (phoneMode()) c.phone.stop() else activeController.value?.clearFailure() }

    // --- Emisoras y presets -----------------------------------------------------------

    /**
     * Guardar (1.9.6): una dirección https se guarda en la forma que suena. Si su versión http responde, se guarda
     * esa (el altavoz la reproduce, redirija o no a https). Si no, se guarda la https y el puente la arranca al reproducir.
     */
    fun saveStation(s: Station) {
        c.stations.upsert(s)
        val http = UrlRules.httpVariant(s.url) ?: return
        viewModelScope.launch {
            val p = runCatching { c.probe.probe(http) }.getOrNull() ?: return@launch
            if (p.reachable && (p.looksLikeAudio || UrlRules.playlistKind(p.finalUrl, p.contentType) != null)) {
                c.stations.stations.value.firstOrNull { it.id == s.id }?.let { c.stations.upsert(it.copy(url = http)) }
                snack(Snack(Txt.s(R.string.ui_guardada_version_http, s.name)))
            }
        }
    }

    /** Activa el puente y vuelve a intentar la emisora que acaba de fallar. */
    fun enableBridgeAndRetry(stationId: String?) {
        c.settings.update { it.copy(httpsBridge = if (it.httpsBridge == false) null else it.httpsBridge) }
        stations.value.firstOrNull { it.id == stationId }?.let(::play)
    }

    fun deleteStation(id: String) {
        val presetSlots = SettingsRepository.normalized(settings.value.presets).withIndex().filter { it.value == id }.map { it.index }
        val removed = c.stations.delete(id) ?: return
        c.settings.clearPresetsFor(id)
        // Si un despertador usaba esta emisora, pasa a la primera tecla con emisora (1.10.2) y se avisa.
        val fallback = presets.value.firstOrNull { it != null && it.id != id }?.id ?: stations.value.firstOrNull { it.id != id }?.id
        val st = settings.value
        if (st.alarm.stationId == id || st.once.stationId == id) {
            c.settings.update { s -> s.copy(
                alarm = if (s.alarm.stationId == id) s.alarm.copy(stationId = fallback) else s.alarm,
                once = if (s.once.stationId == id) s.once.copy(stationId = fallback) else s.once) }
            val name = stations.value.firstOrNull { it.id == fallback }?.name
            if (name != null) snack(Snack(Txt.s(R.string.ui_despertador_pasa_a, name)))
        }
        snack(Snack(Txt.s(R.string.ui_borrada, removed.second.name), Txt.s(R.string.ui_deshacer)) {
            c.stations.restore(removed.first, removed.second)
            presetSlots.forEach { c.settings.assignPreset(it, id) }
        })
    }

    fun moveStation(id: String, delta: Int) = c.stations.move(id, delta)
    fun assignPreset(slot: Int, stationId: String?) = c.settings.assignPreset(slot, stationId)

    fun restoreSeedStations() {
        val n = c.stations.importAll(Seed.stations)
        snack(Snack(if (n == 0) Txt.s(R.string.ui_las_emisoras_precargadas_ya_estan) else Txt.s(R.string.ui_restauradas_emisoras_precargadas, n)))
    }

    suspend fun probe(url: String): ProbeResult = c.probe.probe(url)

    fun importText(text: String) {
        when (val r = StationCodec.import(text)) {
            is StationCodec.ImportResult.Error -> snack(Snack(r.message))
            is StationCodec.ImportResult.Ok -> {
                val added = c.stations.importAll(r.stations)
                val dup = r.stations.size - added
                snack(Snack(buildString {
                    append(Txt.s(R.string.ui_importadas_emisoras, added))
                    if (dup > 0) append(Txt.s(R.string.ui_ya_existian, dup))
                    if (r.skipped > 0) append(Txt.s(R.string.ui_url_no_validas, r.skipped))
                    append('.')
                }))
            }
        }
    }

    fun exportJson(): String = StationCodec.toJson(stations.value)
    fun exportM3u(): String = StationCodec.toM3u(stations.value)
    fun notify(message: String) = snack(Snack(message))

    // --- Altavoces: descubrir y vincular -------------------------------------------

    private val _discovery = MutableStateFlow(DiscoveryUi())
    val discovery: StateFlow<DiscoveryUi> = _discovery
    private var scanJob: Job? = null

    fun scan() {
        scanJob?.cancel()
        _discovery.value = DiscoveryUi(scanning = true)
        scanJob = viewModelScope.launch {
            try {
                c.discovery.discover().collect { cand ->
                    _discovery.update { d ->
                        if (d.candidates.any { it.host == cand.host }) d else d.copy(candidates = d.candidates + cand)
                    }
                }
            } finally {
                _discovery.update { it.copy(scanning = false, scannedOnce = true) }
            }
        }
    }

    private val _link = MutableStateFlow<LinkReport?>(null)
    val link: StateFlow<LinkReport?> = _link
    private var linkJob: Job? = null

    fun checkCandidate(cand: Candidate) {
        linkJob?.cancel()
        linkJob = viewModelScope.launch { c.linker.check(cand) { _link.value = it } }
    }

    fun checkManualIp(input: String): String? {
        val ip = input.trim()
        val ok = Regex("""^(\d{1,3})(\.\d{1,3}){3}$""").matches(ip) && ip.split('.').all { it.toInt() in 0..255 }
        if (!ok) return Txt.s(R.string.ui_escribe_una_ip_como_192)
        checkCandidate(Candidate(ip, FoundVia.MANUAL))
        return null
    }

    fun useLinked(manual: Boolean) {
        val r = _link.value ?: return
        val rec = r.toRecord(manual, System.currentTimeMillis()) ?: return
        c.speakers.remember(rec)
        c.settings.update { it.copy(activeSpeakerId = rec.id) }
        snack(Snack(Txt.s(R.string.ui_vinculado, rec.name)))
        _link.value = null
    }

    fun resetLink() { linkJob?.cancel(); _link.value = null }
    /** Renombra el altavoz en el propio dispositivo (API oficial /name) y en la app. Devuelve error o null. */
    fun renameSpeaker(id: String, newName: String, onDone: (String?) -> Unit) {
        io.github.soundtouchradio.soundtouch.SoundTouchXml.validateName(newName)?.let { return onDone(it) }
        val sp = speakers.value.firstOrNull { it.id == id } ?: return onDone(Txt.s(R.string.ui_altavoz_no_encontrado))
        viewModelScope.launch {
            val r = runCatching { io.github.soundtouchradio.soundtouch.SoundTouchApi.forHost(sp.host, c.lan, c.log).setName(newName.trim()) }
            if (r.isFailure) return@launch onDone(Txt.s(R.string.ui_el_altavoz_no_acepto_el, r.exceptionOrNull()?.message))
            val info = c.controllerFor(sp).reloadInfo()
            c.speakers.remember(sp.copy(name = info?.name ?: newName.trim()))
            snack(Snack(Txt.s(R.string.ui_ahora_se_llama, info?.name ?: newName.trim())))
            onDone(null)
        }
    }

    fun selectSpeaker(id: String) = switchTo(phone = false, speakerId = id)

    fun forgetSpeaker(id: String) {
        c.speakers.forget(id)
        if (settings.value.activeSpeakerId == id) c.settings.update { it.copy(activeSpeakerId = null) }
    }

    // --- Configurar la Wi-Fi del altavoz (modo AP) ----------------------------------------

    private val _wifi = MutableStateFlow(WifiSetupUi())
    val wifi: StateFlow<WifiSetupUi> = _wifi
    private var wifiJob: Job? = null
    private val WATCH_MS = 180_000L
    private var setupApi: io.github.soundtouchradio.soundtouch.SpeakerSetupApi? = null
    val wifiInAppConnect: Boolean get() = c.apConnector.supportsInAppConnect

    private fun step(i: Int, state: io.github.soundtouchradio.discovery.StepState, detail: String? = null) =
        _wifi.update { w -> w.copy(steps = w.steps.mapIndexed { idx, s -> if (idx == i) s.copy(state = state, detail = detail) else s }) }

    private fun wifiFail(i: Int, why: String) {
        step(i, io.github.soundtouchradio.discovery.StepState.FAILED, why)
        _wifi.update { it.copy(stage = WifiStage.FAILED) }
        c.apConnector.release()
    }

    fun wifiPermissionDenied() {
        _wifi.value = WifiSetupUi()
        step(0, io.github.soundtouchradio.discovery.StepState.OK, Txt.s(R.string.ui_indicador_wi_fi_ambar_fijo))
        step(1, io.github.soundtouchradio.discovery.StepState.FAILED,
            Txt.s(R.string.ui_sin_permiso_de_dispositivos_cercanos))
        _wifi.update { it.copy(stage = WifiStage.FAILED, permissionDenied = true) }
    }

    fun wifiReset() { wifiJob?.cancel(); c.apConnector.release(); setupApi = null; speakerNets = emptyList(); _wifi.value = WifiSetupUi() }

    /** ¿Responde el altavoz en modo configuración (192.0.2.1:8090) a través de esta red concreta? */
    private suspend fun probeAp(n: android.net.Network): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = c.lan.newBuilder().socketFactory(n.socketFactory)
            .callTimeout(2, java.util.concurrent.TimeUnit.SECONDS).build()
        runCatching {
            client.newCall(okhttp3.Request.Builder().url("http://${io.github.soundtouchradio.soundtouch.WifiSetupXml.AP_HOST}:8090/info").build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Pasos 1–3. Dos puertas, un solo flujo: si el móvil ya está en la red del altavoz (conectado desde Ajustes),
     * se usa esa; si no, se pide a Android con su diálogo. Después: comprobar que es un SoundTouch y pedirle las redes.
     */
    fun wifiConnect() {
        wifiJob?.cancel()
        _wifi.value = WifiSetupUi(stage = WifiStage.CONNECTING)
        step(0, io.github.soundtouchradio.discovery.StepState.OK, Txt.s(R.string.ui_indicador_wi_fi_ambar_fijo))
        step(1, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_buscando_la_red_del_altavoz))
        wifiJob = viewModelScope.launch {
            val joined = c.apConnector.findJoinedSpeakerNetwork { probeAp(it) }
            val network = joined ?: run {
                step(1, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_acepta_el_aviso_de_android))
                try { c.apConnector.connect() } catch (e: Exception) {
                    return@launch wifiFail(1, e.message ?: Txt.s(R.string.ui_no_se_pudo_conectar_con))
                }
            }
            continueOnSpeakerNetwork(network, viaSettings = joined != null)
        }
    }

    private fun atConnectStep(): Boolean {
        val w = _wifi.value
        return w.stage == WifiStage.INTRO || w.stage == WifiStage.WATCHING ||
            (w.stage == WifiStage.FAILED && w.steps.getOrNull(1)?.state == io.github.soundtouchradio.discovery.StepState.FAILED)
    }

    /**
     * Vía principal, como la app de Bose: el usuario abre el panel de Wi-Fi, elige la red «Bose…» del altavoz y responde «Sí»
     * a «¿Quieres mantener la conexión?». Mientras, la app comprueba cada 2 s durante 3 min y sigue sola al encontrarlo.
     */
    fun wifiWatch() {
        if (!atConnectStep()) return
        wifiJob?.cancel()
        c.apConnector.release()
        _wifi.value = WifiSetupUi(stage = WifiStage.WATCHING)
        step(0, io.github.soundtouchradio.discovery.StepState.OK, Txt.s(R.string.ui_indicador_wi_fi_ambar_fijo))
        step(1, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_esperando_a_que_el_movil_2))
        wifiJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val joined = io.github.soundtouchradio.discovery.pollUntil(WATCH_MS, 2_000) {
                _wifi.update { it.copy(watchedSeconds = ((System.currentTimeMillis() - started) / 1000).toInt()) }
                c.apConnector.findJoinedSpeakerNetwork { probeAp(it) }
            } ?: return@launch wifiFail(1,
                Txt.s(R.string.ui_el_movil_no_llego_a) +
                    Txt.s(R.string.ui_toca_el_aviso_y_elige))
            _wifi.update { it.copy(stage = WifiStage.CONNECTING) }
            continueOnSpeakerNetwork(joined, viaSettings = true)
        }
    }

    /**
     * Al volver a la app: si no hay nada en marcha y el asistente está en el paso 2, comprobar en silencio durante
     * 3 min (no una sola vez: Android puede seguir en «Obteniendo dirección IP…» al volver).
     */
    fun wifiAutoDetect() {
        if (!atConnectStep() || wifiJob?.isActive == true) return
        wifiJob = viewModelScope.launch {
            val joined = io.github.soundtouchradio.discovery.pollUntil(WATCH_MS, 2_000) {
                c.apConnector.findJoinedSpeakerNetwork { probeAp(it) }
            } ?: return@launch
            _wifi.value = WifiSetupUi(stage = WifiStage.CONNECTING)
            step(0, io.github.soundtouchradio.discovery.StepState.OK, Txt.s(R.string.ui_indicador_wi_fi_ambar_fijo))
            step(1, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_red_del_altavoz_encontrada))
            continueOnSpeakerNetwork(joined, viaSettings = true)
        }
    }

    private suspend fun continueOnSpeakerNetwork(network: android.net.Network, viaSettings: Boolean) {
        val client = c.lan.newBuilder().socketFactory(network.socketFactory).build()
        val api = io.github.soundtouchradio.soundtouch.SpeakerSetupApi(client, c.log).also { setupApi = it }
        // El DHCP del altavoz puede tardar unos segundos: tres intentos.
        var info: io.github.soundtouchradio.soundtouch.DeviceInfo? = null
        for (i in 1..3) { info = runCatching { api.info() }.getOrNull(); if (info != null) break; kotlinx.coroutines.delay(2_000) }
        if (info == null) return wifiFail(1, Txt.s(R.string.ui_conectado_a_la_red_pero))
        step(1, io.github.soundtouchradio.discovery.StepState.OK,
            listOf(info.type, info.name).distinct().joinToString(" · ") + if (viaSettings) Txt.s(R.string.ui_desde_el_panel_de_wi) else Txt.s(R.string.ui_conexion_automatica))
        step(2, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_el_altavoz_esta_buscando_redes))
        _wifi.update { it.copy(deviceInfo = info) }
        loadNetworks(api)
    }

    private var speakerNets: List<io.github.soundtouchradio.soundtouch.WifiNetwork> = emptyList()

    /** Paso 3: redes del altavoz (su escaneo) + las del móvil, en una sola lista. */
    private suspend fun loadNetworks(api: io.github.soundtouchradio.soundtouch.SpeakerSetupApi) {
        _wifi.update { it.copy(refreshing = true) }
        step(2, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_el_altavoz_esta_buscando_redes))
        val fromSpeaker = runCatching { api.survey() }.getOrNull()
        if (fromSpeaker != null) speakerNets = fromSpeaker
        mergeWithPhone()
        _wifi.update { it.copy(stage = WifiStage.CHOOSE, refreshing = false) }
        step(2, io.github.soundtouchradio.discovery.StepState.RUNNING, when {
            _wifi.value.surveyFailed -> Txt.s(R.string.ui_no_llego_ninguna_lista_escribe)
            fromSpeaker == null -> Txt.s(R.string.ui_redes_que_ve_el_movil)
            else -> Txt.s(R.string.ui_elige_tu_red_y_escribe)
        })
    }

    /** Añade las redes que ve el móvil. La UI la llama también al conceder «Dispositivos cercanos». */
    fun wifiMergePhoneNetworks() { if (_wifi.value.stage == WifiStage.CHOOSE) mergeWithPhone() }

    private fun mergeWithPhone() {
        val merged = io.github.soundtouchradio.soundtouch.WifiSetupXml.mergeNetworks(speakerNets, c.apConnector.phoneScan())
        val surveyOk = speakerNets.isNotEmpty()
        _wifi.update { it.copy(networks = merged, speakerSurveyOk = surveyOk, surveyFailed = merged.none { n -> !(n.only5GHz && surveyOk) }) }
    }

    /** «Actualizar»: vuelve a pedir las redes al altavoz y al móvil. */
    fun wifiRefreshNetworks() {
        val api = setupApi ?: return
        if (_wifi.value.stage != WifiStage.CHOOSE || _wifi.value.refreshing) return
        wifiJob?.cancel()
        wifiJob = viewModelScope.launch { loadNetworks(api) }
    }

    /** Tras un fallo al enviar o al unirse: volver a «Tu red» con la misma red elegida (la contraseña se vuelve a escribir). */
    fun wifiBackToChoose() {
        if (setupApi == null) return wifiReset()
        wifiJob?.cancel()
        _wifi.update { it.copy(stage = WifiStage.CHOOSE, stillInSetup = false, report = null) }
        for (i in 3..4) step(i, io.github.soundtouchradio.discovery.StepState.PENDING)
        step(2, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_revisa_la_red_y_escribe))
    }

    /** Pasos 4–5: enviar credenciales, soltar la red del altavoz, esperar y encontrarlo en casa por su identificador. */
    fun wifiSend(ssid: String, password: String) {
        val api = setupApi ?: return
        val apInfo = _wifi.value.deviceInfo ?: return
        val net = _wifi.value.networks.firstOrNull { it.ssid == ssid }
        val security = io.github.soundtouchradio.soundtouch.WifiSetupXml.chooseSecurity(
            net ?: if (password.isEmpty()) io.github.soundtouchradio.soundtouch.WifiNetwork(ssid, null, false, emptyList()) else null)
        wifiJob?.cancel()
        _wifi.update { it.copy(stage = WifiStage.SENDING, chosenSsid = ssid) }
        step(2, io.github.soundtouchradio.discovery.StepState.OK, "«$ssid»")
        step(3, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_enviando_a, apInfo.name))
        wifiJob = viewModelScope.launch {
            val outcome = try { api.sendProfile(ssid, password, security) } catch (e: Exception) {
                val rejected = e is io.github.soundtouchradio.soundtouch.SoundTouchException && e.cause == null
                return@launch wifiFail(3, if (rejected)
                    Txt.s(R.string.ui_el_altavoz_rechazo_los_datos)
                else Txt.s(R.string.ui_el_movil_ya_no_llega) +
                    Txt.s(R.string.ui_si_sigue_igual_empezar_de))
            }
            // Cerrar la configuración siempre, también sin respuesta: si no, el altavoz se queda en su propia red.
            api.leaveSetup()
            c.apConnector.release()   // el móvil vuelve a la Wi-Fi de casa
            _wifi.update { it.copy(stage = WifiStage.WAITING) }
            val joining = if (outcome == io.github.soundtouchradio.soundtouch.SendOutcome.NO_ANSWER)
                Txt.s(R.string.ui_probando_el_altavoz_corta_su, ssid) else Txt.s(R.string.ui_uniendose_a, ssid)
            for (s in 30 downTo 1) {
                _wifi.update { it.copy(secondsLeft = s) }
                if (s == 30) step(3, io.github.soundtouchradio.discovery.StepState.RUNNING, joining)
                kotlinx.coroutines.delay(1_000)
            }
            step(3, io.github.soundtouchradio.discovery.StepState.OK, Txt.s(R.string.ui_datos_enviados_a, apInfo.name))
            _wifi.update { it.copy(stage = WifiStage.FINDING, secondsLeft = 0) }
            step(4, io.github.soundtouchradio.discovery.StepState.RUNNING, Txt.s(R.string.ui_buscando_a_en_tu_red, apInfo.name))
            val found = findSpeaker(apInfo, timeoutMs = 120_000) ?: run {
                // ¿Sigue en modo configuración? Entonces no pudo unirse: contraseña o red.
                val ap = c.apConnector.findJoinedSpeakerNetwork { probeAp(it) }
                if (ap != null) {
                    setupApi = io.github.soundtouchradio.soundtouch.SpeakerSetupApi(c.lan.newBuilder().socketFactory(ap.socketFactory).build(), c.log)
                    _wifi.update { it.copy(stillInSetup = true) }
                    return@launch wifiFail(4, Txt.s(R.string.ui_sigue_en_su_propia_red, apInfo.name, ssid) +
                        Txt.s(R.string.ui_lo_habitual_contrasena_incorrecta_o))
                }
                return@launch wifiFail(4, Txt.s(R.string.ui_no_aparece_en_tu_red) +
                    Txt.s(R.string.ui_si_esta_blanco_prueba_vincular))
            }
            val report = c.linker.check(found) { r -> _wifi.update { it.copy(report = r) } }
            if (report.usable) {
                step(4, io.github.soundtouchradio.discovery.StepState.OK, Txt.s(R.string.ui_listo_para_usar, found.host))
                _wifi.update { it.copy(stage = WifiStage.DONE, report = report) }
            } else wifiFail(4, Txt.s(R.string.ui_esta_en_tu_red_pero, found.host))
        }
    }

    /** Descubre en la LAN y compara deviceID/MAC con lo que dio el altavoz en modo AP (la IP cambia). */
    private suspend fun findSpeaker(ap: io.github.soundtouchradio.soundtouch.DeviceInfo, timeoutMs: Long): Candidate? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val tried = mutableSetOf<String>()
        while (System.currentTimeMillis() < deadline) {
            val cands = mutableListOf<Candidate>()
            runCatching { c.discovery.discover(5_000).collect { cands += it } }
            for (cand in cands.distinctBy { it.host }) {
                if (cand.host in tried && cand.via != FoundVia.MDNS) continue
                tried += cand.host
                val info = runCatching { io.github.soundtouchradio.soundtouch.SoundTouchApi.forHost(cand.host, c.lan, c.log).info() }.getOrNull() ?: continue
                if (io.github.soundtouchradio.soundtouch.WifiSetupXml.sameSpeaker(ap, info)) return cand
            }
            kotlinx.coroutines.delay(3_000)
        }
        return null
    }

    /** Termina: vincula el altavoz y, si se cambió el nombre propuesto, se lo pone en el propio altavoz (API /name). */
    fun wifiUseSpeaker(name: String?) {
        val r = _wifi.value.report ?: return
        val rec = r.toRecord(manual = false, now = System.currentTimeMillis()) ?: return
        _link.value = r
        useLinked(manual = false)
        val wanted = name?.trim().orEmpty()
        if (wanted.isNotEmpty() && wanted != rec.name) renameSpeaker(rec.id, wanted) { err -> if (err != null) snack(Snack(err)) }
        wifiReset()
    }

    // --- Ajustes -------------------------------------------------------------------

    fun setDynamicColor(v: Boolean) = c.settings.update { it.copy(dynamicColor = v) }
    fun setMultiroom(v: Boolean) = c.settings.update { it.copy(multiroomExperimental = v) }

    // --- Multiroom (experimental) -------------------------------------------------------

    private val _zone = MutableStateFlow<Zone?>(null)
    val zone: StateFlow<Zone?> = _zone

    fun refreshZone() {
        val s = activeSpeaker.value ?: return
        viewModelScope.launch {
            _zone.value = runCatching { io.github.soundtouchradio.soundtouch.SoundTouchApi.forHost(s.host, c.lan, c.log).zone() }.getOrNull()
        }
    }

    fun createZone(memberIds: List<String>) {
        val master = activeSpeaker.value ?: return
        val masterMac = master.mac ?: return snack(Snack(Txt.s(R.string.ui_falta_la_mac_del_altavoz)))
        val members = speakers.value.filter { it.id in memberIds && it.mac != null }
        if (members.isEmpty()) return
        viewModelScope.launch {
            val api = io.github.soundtouchradio.soundtouch.SoundTouchApi.forHost(master.host, c.lan, c.log)
            runCatching {
                api.setZone(masterMac, master.host, listOf(ZoneMember(master.host, masterMac)) + members.map { ZoneMember(it.host, it.mac!!) })
            }.onSuccess { snack(Snack(Txt.s(R.string.ui_grupo_creado_comprueba_que_los))) }
                .onFailure { snack(Snack(Txt.s(R.string.ui_no_se_pudo_crear_el, it.message))) }
            refreshZone()
        }
    }

    fun dissolveZone() {
        val master = activeSpeaker.value ?: return
        val z = _zone.value ?: return
        val masterMac = z.masterMac ?: return
        viewModelScope.launch {
            val slaves = z.members.filter { it.macAddress != masterMac }
            runCatching { io.github.soundtouchradio.soundtouch.SoundTouchApi.forHost(master.host, c.lan, c.log).removeZoneSlaves(masterMac, slaves) }
                .onSuccess { snack(Snack(Txt.s(R.string.ui_grupo_deshecho))) }
                .onFailure { snack(Snack(Txt.s(R.string.ui_no_se_pudo_deshacer_el, it.message))) }
            refreshZone()
        }
    }

    // --- Banco de pruebas de formatos ---------------------------------------------------

    private val _bench = MutableStateFlow(BenchUi())
    val bench: StateFlow<BenchUi> = _bench
    private var benchJob: Job? = null

    fun runBench(text: String) {
        val ctrl = activeController.value ?: return snack(Snack(Txt.s(R.string.ui_primero_vincula_un_altavoz)))
        val urls = text.lines().map { it.trim() }.filter { UrlRules.validate(it) is UrlRules.Validation.Ok }
        if (urls.isEmpty()) return snack(Snack(Txt.s(R.string.ui_pega_una_url_http_s)))
        benchJob?.cancel()
        benchJob = viewModelScope.launch {
            _bench.value = BenchUi(running = true, total = urls.size)
            for ((i, url) in urls.withIndex()) {
                _bench.update { it.copy(current = i + 1) }
                val t0 = System.currentTimeMillis()
                ctrl.play(PlayRequest(null, "Prueba ${i + 1}", url)).join()
                val st = ctrl.state.value
                val ok = st.phase is Phase.Playing
                val result = if (ok) Txt.s(R.string.ui_suena) else (st.phase as? Phase.Failed)?.let { Copy.cause(it.cause).title } ?: Copy.phaseLine(st.phase)
                _bench.update { it.copy(rows = it.rows + BenchRow(url, result, st.resolved?.mime, (System.currentTimeMillis() - t0) / 1000.0, ok)) }
                if (ok) kotlinx.coroutines.delay(4_000) // escuchar unos segundos
            }
            ctrl.stopPlayback()
            _bench.update { it.copy(running = false) }
        }
    }

    fun cancelBench() { benchJob?.cancel(); _bench.update { it.copy(running = false) } }

    fun benchMarkdown(): String = buildString {
        val sp = activeSpeaker.value
        append(Txt.s(R.string.ui_url_resultado_mime_tiempo_s))
        for (r in _bench.value.rows) append("| ${r.url} | ${r.result} | ${r.mime ?: "?"} | ${"%.1f".format(r.seconds)} |\n")
        append("\nAltavoz: ${sp?.type ?: "?"} · firmware ${sp?.firmware ?: "?"}\n")
    }
}
