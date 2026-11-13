package io.github.soundtouchradio.player

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.playlist.ResolveException
import io.github.soundtouchradio.playlist.ResolvedStream
import io.github.soundtouchradio.playlist.StreamProbe
import io.github.soundtouchradio.playlist.UrlRules
import io.github.soundtouchradio.soundtouch.DeviceInfo
import io.github.soundtouchradio.soundtouch.NowPlaying
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.soundtouch.SoundTouchException
import io.github.soundtouchradio.upnp.AvTransport
import io.github.soundtouchradio.upnp.DeviceDescription
import io.github.soundtouchradio.upnp.UpnpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SpeakerState(
    val phase: Phase = Phase.Idle,
    val volume: Int? = null,
    /** El volumen mostrado es el pedido y el altavoz aún no lo ha devuelto (1.8.1). */
    val volumePending: Boolean = false,
    /** «Este móvil»: salida activa («Edifier R1855DB», «Auriculares»…) y si se usan datos móviles (1.10.0). */
    val phoneOutput: String? = null,
    /** Nombre original de la salida, con el que se guarda su ajuste de sonido (1.10.3). */
    val phoneOutputKey: String? = null,
    val phoneOnCellular: Boolean = false,
    /** «Este móvil»: dirección que está abriendo el reproductor del teléfono (1.10.1). */
    val phoneUrl: String? = null,
    val muted: Boolean = false,
    val info: DeviceInfo? = null,
    val nowPlaying: NowPlaying? = null,
    val pauseAvailable: Boolean = false,
    val stationId: String? = null,
    val stationName: String? = null,
    val resolved: ResolvedStream? = null,
    val reachable: Boolean = true,
    val socketConnected: Boolean = false,
    /** Encendido/apagado en curso hacia este estado (true = encender). */
    val powerTarget: Boolean? = null,
) {
    /** true encendido, false en reposo, null desconocido (sin datos o sin conexión). */
    val speakerOn: Boolean? get() = if (!reachable) null else nowPlaying?.let { !it.isStandby }
}

enum class PowerResult { DONE, ALREADY, NO_CONFIRMATION, UNREACHABLE }

data class PlayRequest(val stationId: String?, val name: String, val url: String)

data class Timing(
    val confirmTimeoutMs: Long = 15_000,
    val bufferingGraceMs: Long = 10_000,
    val confirmPollMs: Long = 1_000,
    val pollMs: Long = 3_000,
    val pollWithSocketMs: Long = 10_000,
    val volumeDebounceMs: Long = 150,
    val powerPollMs: Long = 1_000,
    val powerTimeoutMs: Long = 10_000,
)

/**
 * Controla un altavoz. Regla central: «sonando» solo cuando /now_playing lo confirma;
 * la respuesta SOAP correcta no se considera prueba (fallos documentados por la comunidad).
 */
class SpeakerController(
    val speaker: SpeakerRecord,
    private val api: SoundTouchApi,
    private val avt: AvTransport,
    private val probe: StreamProbe,
    private val scope: CoroutineScope,
    private val timing: Timing = Timing(),
    private val onTechInfo: (stationId: String, codec: String?, bitrate: Int?) -> Unit = { _, _, _ -> },
    /** Una emisora https sonó por su versión http: la app guarda esa versión (1.7.1). */
    private val onHttpUpgrade: (stationId: String, httpUrl: String) -> Unit = { _, _ -> },
    /** Puente de redirección (1.9.5): dirección http del móvil que redirige a la https, si está activado; si no, null. */
    private val bridge: (httpsUrl: String) -> String? = { null },
) {
    private val _state = MutableStateFlow(SpeakerState())
    val state: StateFlow<SpeakerState> = _state

    val controlUrl: String get() = avt.controlUrl

    private var playJob: Job? = null
    private var pollJob: Job? = null
    private var volumeJob: Job? = null
    private var socketUp = false
    private var lastPauseKey: String? = null

    // --- Ciclo de vida ---------------------------------------------------------------

    /** Arranca el seguimiento del estado (solo con la app en primer plano). */
    fun startMonitoring() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            if (_state.value.info == null) runCatching { api.info() }.onSuccess { i -> _state.update { it.copy(info = i) } }
            while (isActive) {
                if (playJob?.isActive != true) refresh()
                delay(if (socketUp) timing.pollWithSocketMs else timing.pollMs)
            }
        }
    }

    fun stopMonitoring() { pollJob?.cancel(); pollJob = null }

    /** Relee /info (p. ej. tras renombrar el altavoz). */
    suspend fun reloadInfo(): DeviceInfo? = runCatching { api.info() }.getOrNull()?.also { i -> _state.update { it.copy(info = i) } }

    fun onSocket(connected: Boolean) {
        socketUp = connected
        _state.update { it.copy(socketConnected = connected) }
    }

    /** Evento del WebSocket: releer ya. */
    fun nudge() { if (playJob?.isActive != true) scope.launch { refresh() } }

    suspend fun refresh() {
        try {
            val np = api.nowPlaying()
            val vol = runCatching { api.volume() }.getOrNull()
            _state.update { s ->
                s.copy(
                    nowPlaying = np,
                    phase = if (s.phase.isBusy) s.phase else Diagnosis.phaseFrom(np, s.phase, s.stationName),
                    volume = if (volumeJob?.isActive == true) s.volume else vol?.actual ?: s.volume,
                    muted = vol?.muted ?: s.muted,
                    reachable = true,
                )
            }
            // «¿Admite pausa?» solo cambia con la emisora o el estado: no se pregunta en cada ciclo (1.8.0).
            val key = "${np.source}|${np.playStatus}|${np.itemName}|${np.stationName}"
            if (np.isUpnp && key != lastPauseKey) { lastPauseKey = key; refreshPauseAvailability() }
        } catch (e: SoundTouchException) {
            _state.update { s -> s.copy(reachable = false, phase = if (s.phase.isBusy) s.phase else Phase.Unreachable) }
        }
    }

    private suspend fun refreshPauseAvailability() {
        val actions = runCatching { avt.currentActions() }.getOrNull() ?: return
        _state.update { it.copy(pauseAvailable = "Pause" in actions) }
    }

    // --- Reproducción ----------------------------------------------------------------

    fun play(req: PlayRequest): Job {
        playJob?.cancel()
        return scope.launch { runPlay(req) }.also { playJob = it }
    }

    private suspend fun runPlay(req: PlayRequest) {
        val name = req.name
        _state.update { it.copy(stationId = req.stationId, stationName = name, phase = Phase.Resolving(name), resolved = null) }

        var resolved = try {
            probe.resolve(req.url)
        } catch (e: ResolveException) {
            fail(FailureCause.RESOLVE_FAILED, name, e.message); return
        }
        // El SoundTouch no reproduce https (UPnP 402 «No URI supplied», verificado en un ST10 con firmware 27.0.6).
        // Antes de enviarla, se prueba la versión http: primero la de la URL original, luego la del stream final.
        var upgradedTo: String? = null
        if (UrlRules.isHttps(resolved.streamUrl)) {
            for (alt in httpCandidates(req.url, resolved.streamUrl)) {
                val r = runCatching { probe.resolve(alt) }.getOrNull() ?: continue
                if (!UrlRules.isHttps(r.streamUrl) && r.probe?.reachable == true) { resolved = r; upgradedTo = alt; break }
            }
            // Sin versión http: si el puente está activado, el altavoz recibe una dirección http del móvil que
            // redirige a la https. El audio va de la emisora al altavoz; el móvil solo responde la redirección.
            if (upgradedTo == null) bridge(resolved.streamUrl)?.let { b ->
                resolved = resolved.copy(streamUrl = b, warnings = resolved.warnings + io.github.soundtouchradio.playlist.StreamWarning.HTTPS)
            }
        }
        _state.update { it.copy(resolved = resolved) }
        if (req.stationId != null) {
            resolved.probe?.let { p ->
                if (p.reachable) onTechInfo(req.stationId, UrlRules.codecLabel(resolved.mime), p.icyBitrate)
            }
        }

        // Estado previo, para diagnosticar si no llega a sonar.
        val before = runCatching { api.nowPlaying() }.getOrNull()
        val info = _state.value.info ?: runCatching { api.info() }.getOrNull()?.also { i -> _state.update { it.copy(info = i) } }
        if (before == null && info == null) { fail(FailureCause.SPEAKER_UNREACHABLE, name, null); return }

        _state.update { it.copy(phase = Phase.Sending(name)) }
        try {
            avt.setUri(resolved.streamUrl, io.github.soundtouchradio.upnp.Didl.broadcast(resolved.streamUrl, name, resolved.mime))
            avt.play()
        } catch (e: UpnpException) {
            val cause = when {
                info?.nameBreaksRenderer == true -> FailureCause.RENDERER_NAME
                UrlRules.isHttps(resolved.streamUrl) -> FailureCause.HTTPS_UNSUPPORTED
                else -> FailureCause.UPNP_ERROR
            }
            fail(cause, name, e.message); return
        }

        _state.update { it.copy(phase = Phase.AwaitingConfirmation(name)) }
        var waited = 0L
        var bufferingSince: Long? = null
        var last: NowPlaying? = null
        while (true) {
            delay(timing.confirmPollMs)
            waited += timing.confirmPollMs
            last = runCatching { api.nowPlaying() }.getOrNull() ?: last
            last?.let { np -> _state.update { it.copy(nowPlaying = np) } }
            when (last?.let(Diagnosis::confirmationStep) ?: ConfirmStep.WAITING) {
                ConfirmStep.CONFIRMED -> {
                    _state.update { it.copy(phase = Phase.Playing(name), reachable = true) }
                    if (upgradedTo != null && req.stationId != null) onHttpUpgrade(req.stationId, upgradedTo)
                    refreshPauseAvailability()
                    return
                }
                ConfirmStep.BUFFERING -> {
                    if (bufferingSince == null) bufferingSince = waited
                    _state.update { it.copy(phase = Phase.Buffering(name)) }
                }
                ConfirmStep.WAITING -> Unit
            }
            // Si el altavoz llegó a «cargando», se le concede un margen extra.
            val limit = timing.confirmTimeoutMs + if (bufferingSince != null) timing.bufferingGraceMs else 0
            if (waited >= limit) break
        }

        val cause = Diagnosis.diagnose(
            DiagnosisContext(
                streamIsHttps = UrlRules.isHttps(resolved.streamUrl),
                streamIsHls = io.github.soundtouchradio.playlist.StreamWarning.HLS in resolved.warnings,
                speakerNameBreaksRenderer = info?.nameBreaksRenderer == true,
                nativeSourceWasActive = before?.isNativeStreaming == true,
                wasStandby = before?.isStandby == true,
                last = last,
            )
        )
        fail(cause, name, last?.let { Txt.s(R.string.ui_ultimo_estado, it.source, it.playStatus) })
    }

    /** Versiones http a probar, sin repetir: la de la URL que se pidió y la del stream final. */
    private fun httpCandidates(requested: String, stream: String): List<String> =
        listOfNotNull(UrlRules.httpVariant(requested), UrlRules.httpVariant(stream)).distinct()

    private fun fail(cause: FailureCause, station: String?, detail: String?) {
        _state.update { it.copy(phase = Phase.Failed(cause, station, detail)) }
    }

    fun stopPlayback() = scope.launch {
        playJob?.cancel()
        try {
            avt.stop()
        } catch (e: UpnpException) {
            // Si el renderer no responde, la tecla PLAY_PAUSE no es equivalente: solo informamos.
            fail(FailureCause.UPNP_ERROR, _state.value.stationName, e.message); return@launch
        } catch (e: CancellationException) { throw e }
        _state.update { it.copy(phase = Phase.Stopped) }
        delay(500); refresh()
    }

    fun pause() = scope.launch {
        runCatching { avt.pause() }
            .onSuccess { _state.update { s -> s.copy(phase = Phase.Paused(s.stationName ?: Txt.s(R.string.ui_emisora_2))) } }
            .onFailure { e -> if (e is CancellationException) throw e; fail(FailureCause.UPNP_ERROR, _state.value.stationName, e.message) }
    }

    fun resume() = scope.launch {
        runCatching { avt.play() }
        delay(800); refresh()
    }

    /**
     * Volumen con anti-rebote: el slider y las teclas no inundan al altavoz. Tras enviarlo se relee /volume:
     * la cifra queda «pendiente» hasta que el altavoz devuelve un valor, y entonces muestra ese (sea o no el pedido).
     */
    fun setVolume(level: Int) {
        val v = level.coerceIn(0, 100)
        _state.update { it.copy(volume = v, volumePending = true) }
        volumeJob?.cancel()
        volumeJob = scope.launch {
            delay(timing.volumeDebounceMs)
            runCatching { api.setVolume(v) }
            val actual = runCatching { api.volume() }.getOrNull()
            _state.update { s -> s.copy(volume = actual?.actual ?: s.volume, muted = actual?.muted ?: s.muted, volumePending = false) }
        }
    }

    /** Un punto arriba o abajo (teclas − / + y botones físicos). Parte del último valor pedido, no del leído. */
    fun stepVolume(delta: Int) { _state.value.volume?.let { setVolume(it + delta) } }

    /** ¿Se puede dar otro paso en esa dirección? (para que la repetición se pare en 0 o 100). */
    fun canStep(delta: Int): Boolean = _state.value.volume?.let { (delta < 0 && it > 0) || (delta > 0 && it < 100) } ?: false

    fun toggleMute() = scope.launch {
        runCatching { api.pressKey("MUTE") }
        delay(300)
        runCatching { api.volume() }.onSuccess { v -> _state.update { it.copy(muted = v.muted, volume = v.actual) } }
    }

    /**
     * Encender o apagar de forma segura. POWER *alterna*, así que: se lee el estado real justo antes,
     * se pulsa solo si difiere del pedido y se confirma leyendo /now_playing (máx. 10 s).
     * Nunca se da por hecho sin confirmación del altavoz.
     */
    suspend fun setPower(on: Boolean): PowerResult {
        val before = runCatching { api.nowPlaying() }.getOrNull()
            ?: return PowerResult.UNREACHABLE.also { _state.update { s -> s.copy(reachable = false) } }
        if (!before.isStandby == on) { refresh(); return PowerResult.ALREADY }
        _state.update { it.copy(powerTarget = on) }
        try {
            api.pressKey("POWER")
            var waited = 0L
            while (waited < timing.powerTimeoutMs) {
                delay(timing.powerPollMs); waited += timing.powerPollMs
                val np = runCatching { api.nowPlaying() }.getOrNull() ?: continue
                if (!np.isStandby == on) {
                    _state.update { s ->
                        s.copy(nowPlaying = np, reachable = true,
                            phase = if (on) Diagnosis.phaseFrom(np, Phase.Idle, s.stationName) else Phase.Standby)
                    }
                    return PowerResult.DONE
                }
            }
            return PowerResult.NO_CONFIRMATION
        } catch (e: SoundTouchException) {
            return PowerResult.UNREACHABLE
        } finally {
            _state.update { it.copy(powerTarget = null) }
            refresh()
        }
    }

    fun clearFailure() { _state.update { s -> if (s.phase is Phase.Failed) s.copy(phase = Phase.Idle) else s } }

    companion object {
        fun controlUrlFor(speaker: SpeakerRecord): String =
            speaker.controlUrl ?: DeviceDescription.defaultControlUrl(speaker.host)
    }
}
