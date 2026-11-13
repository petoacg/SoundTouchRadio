package io.github.soundtouchradio.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import io.github.soundtouchradio.App
import io.github.soundtouchradio.playlist.ResolveException
import io.github.soundtouchradio.playlist.StreamProbe
import io.github.soundtouchradio.ui.Txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Este móvil» (1.10.0): la radio suena en el teléfono y Android la saca por la salida activa (altavoz del móvil,
 * auriculares o un altavoz Bluetooth). Publica el mismo [SpeakerState] que el SoundTouch, así el panel, los presets y
 * la luz piloto funcionan igual: «Sonando» solo cuando el reproductor confirma que está sonando.
 * Media3 (ExoPlayer) reproduce MP3, AAC, https y HLS; pausa al desconectarse la salida y respeta llamadas y avisos.
 */
@OptIn(UnstableApi::class)
class PhonePlayer(
    private val context: Context,
    private val probe: StreamProbe,
    private val scope: CoroutineScope,
    /** Ajuste de sonido guardado para una salida (1.10.2). */
    private val eqFor: (output: String) -> EqSetting = { EqSetting() },
) {
    /** Bandas que ofrece el ecualizador del móvil: centros en Hz y rango en mB. null si el móvil no lo ofrece. */
    data class EqBands(val centersHz: List<Int>, val minMb: Int, val maxMb: Int)
    private val _eqBands = MutableStateFlow<EqBands?>(null)
    val eqBands: StateFlow<EqBands?> = _eqBands
    private var eq: android.media.audiofx.Equalizer? = null
    private var eqSession = C.AUDIO_SESSION_ID_UNSET
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(SpeakerState(pauseAvailable = true))
    val state: StateFlow<SpeakerState> = _state
    private val _sleep = MutableStateFlow<SleepState?>(null)
    val sleep: StateFlow<SleepState?> = _sleep
    private var playJob: Job? = null
    private var sleepJob: Job? = null
    private var controller: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    val player: ExoPlayer by lazy {
        // Sigue redirecciones de http a https (por defecto Media3 no lo hace): emisoras como Radio alHara (1.10.1).
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(io.github.soundtouchradio.soundtouch.Http.USER_AGENT)
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(app, http)))
            .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)          // se desconectan los Edifier o los auriculares → pausa
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { sync(); attachEq() }
                override fun onPlayerError(error: PlaybackException) {
                    _state.update { it.copy(phase = Phase.Failed(FailureCause.PHONE_PLAYBACK, it.stationName, reason(error))) }
                }
            })
        }
    }

    init {
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshOutput()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshOutput()
        }, null)
        refreshOutput()
        // Volumen multimedia del móvil (0–100 para el panel) y datos móviles, cada segundo mientras haya algo cargado.
        scope.launch { while (true) { if (_state.value.stationId != null) refreshVolume(); delay(1_000) } }
    }

    fun play(req: PlayRequest) {
        playJob?.cancel()
        playJob = scope.launch {
            _state.update { it.copy(stationId = req.stationId, stationName = req.name, phase = Phase.Resolving(req.name), resolved = null) }
            // Listas .pls/.m3u y redirecciones se resuelven como para el altavoz; el móvil sí abre https y HLS.
            val resolved = try { probe.resolve(req.url) } catch (e: ResolveException) {
                _state.update { it.copy(phase = Phase.Failed(FailureCause.RESOLVE_FAILED, req.name, e.message)) }; return@launch
            }
            _state.update { it.copy(resolved = resolved, phase = Phase.Buffering(req.name)) }
            ensureService()
            // El móvil abre https sin problema: se usa directamente la dirección final que encontró la comprobación.
            val url = resolved.probe?.takeIf { it.reachable }?.finalUrl ?: resolved.streamUrl
            _state.update { it.copy(phoneUrl = url) }
            player.setMediaItem(MediaItem.Builder().setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(req.name).setArtist(Txt.s(io.github.soundtouchradio.R.string.ui_este_movil)).build()).build())
            player.prepare()
            player.play()
            refreshVolume()
        }
    }

    fun pause() { player.pause() }
    fun resume() { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() }
    fun stop() {
        playJob?.cancel(); player.stop(); player.clearMediaItems()
        _state.update { it.copy(phase = Phase.Stopped) }
        cancelSleep(null)
    }

    /** Volumen multimedia del móvil, 0–100 (el móvil tiene sus propios pasos: normalmente 15 o 25). */
    fun setVolume(v: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v.coerceIn(0, 100) * max / 100f), 0)
        refreshVolume()
    }
    fun stepVolume(delta: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        refreshVolume()
    }
    fun toggleMute() { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0); refreshVolume() }

    // --- Temporizador en el móvil ------------------------------------------------------------

    fun startSleep(minutes: Int, fade: Boolean) {
        sleepJob?.cancel()
        val s = SleepState(System.currentTimeMillis() + minutes * 60_000L, minutes, fade, "phone", Txt.s(io.github.soundtouchradio.R.string.ui_este_movil))
        _sleep.value = s
        sleepJob = scope.launch {
            delay((s.endsAtMillis - System.currentTimeMillis() - 60_000).coerceAtLeast(0))
            _sleep.update { it?.copy(finishing = true) }
            if (fade) for (i in 30 downTo 0) { player.volume = i / 30f; delay(2_000) } else delay(60_000)
            player.pause(); player.volume = 1f
            _sleep.value = null
        }
    }
    fun cancelSleep(@Suppress("UNUSED_PARAMETER") message: String?) { sleepJob?.cancel(); player.volume = 1f; _sleep.value = null }

    /** Motivo del fallo en palabras, con el código técnico entre paréntesis para «Copiar informe» (1.10.1). */
    private fun reason(e: PlaybackException): String {
        val id = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> io.github.soundtouchradio.R.string.ui_motivo_sin_internet
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> io.github.soundtouchradio.R.string.ui_motivo_servidor_rechaza
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> io.github.soundtouchradio.R.string.ui_motivo_redireccion
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> io.github.soundtouchradio.R.string.ui_motivo_formato
            else -> io.github.soundtouchradio.R.string.ui_motivo_otro
        }
        val http = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
        return Txt.s(id) + " (" + e.errorCodeName + (http?.let { ", HTTP $it" } ?: "") + ")"
    }

    // --- Estado ----------------------------------------------------------------------------

    private fun sync() {
        val name = _state.value.stationName ?: return
        val p = player
        _state.update {
            it.copy(phase = when {
                it.phase is Phase.Failed && p.playerError != null -> it.phase
                p.isPlaying -> Phase.Playing(name)
                p.playbackState == Player.STATE_BUFFERING -> Phase.Buffering(name)
                p.playbackState == Player.STATE_READY && !p.playWhenReady -> Phase.Paused(name)
                p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED -> if (it.phase is Phase.Resolving) it.phase else Phase.Stopped
                else -> it.phase
            }, reachable = true, pauseAvailable = true)
        }
    }

    private fun refreshVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val cell = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        _state.update { it.copy(volume = Math.round(cur * 100f / max), muted = audio.isStreamMute(AudioManager.STREAM_MUSIC), volumePending = false, phoneOnCellular = cell) }
    }

    /** Nombre de la salida activa: «Edifier R1855DB», «Auriculares» o «Altavoz del teléfono». */
    private fun refreshOutput() {
        val outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == 26 /* BLE_HEADSET */ || it.type == 27 /* BLE_SPEAKER */ }
        val wired = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        val name = when {
            bt != null -> bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: Txt.s(io.github.soundtouchradio.R.string.ui_salida_bluetooth)
            wired != null -> Txt.s(io.github.soundtouchradio.R.string.ui_salida_auriculares)
            else -> Txt.s(io.github.soundtouchradio.R.string.ui_salida_altavoz_movil)
        }
        _state.update { it.copy(phoneOutput = EqPlan.prettyName(name), phoneOutputKey = name) }
        applyEq()
    }

    // --- Ecualizador (1.10.2) ----------------------------------------------------------------

    /** Engancha el ecualizador de Android a la sesión de audio del reproductor (cuando ya existe). */
    private fun attachEq() {
        val session = player.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET || session == eqSession) return
        runCatching { eq?.release() }
        eq = runCatching { android.media.audiofx.Equalizer(0, session).apply { enabled = true } }.getOrNull()
        eqSession = session
        eq?.let { e ->
            val (min, max) = e.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
            _eqBands.value = EqBands((0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 }, min, max)
        }
        applyEq()
    }

    /** Aplica el ajuste guardado para la salida actual. */
    fun applyEq() {
        val e = eq ?: return
        val bands = _eqBands.value ?: return
        val setting = eqFor(_state.value.phoneOutputKey ?: return)
        EqPlan.levels(setting, bands.centersHz, bands.minMb, bands.maxMb).forEachIndexed { i, mb ->
            runCatching { e.setBandLevel(i.toShort(), mb.toShort()) }
        }
    }

    /** Conecta el servicio multimedia: notificación, pantalla de bloqueo y reproducción en segundo plano. */
    private fun ensureService() {
        if (controller != null) return
        controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PhonePlaybackService::class.java))).buildAsync()
    }
}

/** Servicio multimedia de «Este móvil»: Media3 gestiona la notificación y el primer plano mientras suena. */
@OptIn(UnstableApi::class)
class PhonePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val player = (application as App).container.phone.player
        session = MediaSession.Builder(this, player)
            .setSessionActivity(android.app.PendingIntent.getActivity(this, 60,
                android.content.Intent(this, io.github.soundtouchradio.ui.MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE))
            .build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Cerrar la app desde «recientes» con la radio en pausa: se libera todo. Si suena, sigue.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() { session?.run { release() }; session = null; super.onDestroy() }
}
