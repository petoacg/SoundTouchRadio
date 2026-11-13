package io.github.soundtouchradio.player

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.soundtouch.NowPlaying
import io.github.soundtouchradio.soundtouch.PlayStatus
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.upnp.AvTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Temporizador activo. Vive en el móvil: la API local de Bose no tiene temporizador. */
data class SleepState(
    val endsAtMillis: Long,
    val minutes: Int,
    val fade: Boolean,
    val speakerId: String,
    val speakerName: String,
    /** true durante el último minuto (bajada de volumen y apagado). */
    val finishing: Boolean = false,
)

/** Reglas puras del temporizador (probadas en la JVM). */
object SleepPlan {
    val CHOICES = listOf(15, 30, 45, 60, 90)
    const val FADE_MS = 60_000L
    const val FADE_STEPS = 12
    const val CHECK_MS = 30_000L

    /** Volúmenes de la bajada gradual: de [from] (sin incluir) hasta 0 (incluido). */
    fun fadeSteps(from: Int, steps: Int = FADE_STEPS): List<Int> =
        (1..steps).map { i -> (from * (steps - i) / steps.toFloat()).toInt() }.let { s ->
            if (from <= 0) listOf(0) else s
        }

    /** POWER alterna: solo se pulsa si el altavoz no está ya en reposo. */
    fun shouldPressPower(np: NowPlaying?): Boolean = np != null && !np.isStandby

    /** Si ya no suena la radio, el temporizador no apaga nada: se cancela y lo dice. */
    fun cancelReason(np: NowPlaying): String? = when {
        np.isStandby -> Txt.s(R.string.ui_el_altavoz_paso_a_reposo)
        !np.isUpnp -> Txt.s(R.string.ui_el_altavoz_cambio_a_otra)
        np.playStatus == PlayStatus.STOP -> Txt.s(R.string.ui_la_radio_se_detuvo)
        else -> null
    }

    fun remainingMinutes(endsAt: Long, now: Long): Int = (((endsAt - now).coerceAtLeast(0) + 59_999) / 60_000).toInt()
}

/**
 * Ejecuta el temporizador: espera, baja el volumen el último minuto, detiene, restaura el volumen
 * y pone el altavoz en reposo. Comprueba cada 30 s que la radio sigue sonando.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val clients: (SpeakerRecord) -> Pair<SoundTouchApi, AvTransport>,
    private val fadeStepMs: Long = SleepPlan.FADE_MS / SleepPlan.FADE_STEPS,
) {
    private val _state = MutableStateFlow<SleepState?>(null)
    val state: StateFlow<SleepState?> = _state
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events
    private var job: Job? = null

    fun start(speaker: SpeakerRecord, minutes: Int, fade: Boolean): Job {
        job?.cancel()
        val s = SleepState(clock() + minutes * 60_000L, minutes, fade, speaker.id, speaker.name)
        _state.value = s
        return scope.launch { run(speaker, s) }.also { job = it }
    }

    fun cancel(message: String? = Txt.s(R.string.ui_temporizador_cancelado)) {
        job?.cancel(); job = null
        if (_state.value != null) { _state.value = null; message?.let { _events.tryEmit(it) } }
    }

    private suspend fun run(speaker: SpeakerRecord, s: SleepState) {
        val (api, avt) = clients(speaker)
        val fadeAt = s.endsAtMillis - (if (s.fade) SleepPlan.FADE_MS else 0)
        try {
            while (clock() < fadeAt) {
                delay(minOf(SleepPlan.CHECK_MS, fadeAt - clock()))
                val np = runCatching { api.nowPlaying() }.getOrNull() ?: continue // sin red: se reintenta
                SleepPlan.cancelReason(np)?.let { reason -> finish(Txt.s(R.string.ui_temporizador_cancelado_2, reason)); return }
            }
            _state.value = s.copy(finishing = true)
            val message = shutdown(api, avt, s.fade)
            finish(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            finish(Txt.s(R.string.ui_no_se_pudo_apagar_sin))
        }
    }

    /** Bajada + Stop + restaurar volumen + reposo. Devuelve el texto para el usuario. */
    suspend fun shutdown(api: SoundTouchApi, avt: AvTransport, fade: Boolean): String {
        val original = api.volume().actual
        if (fade) for (v in SleepPlan.fadeSteps(original)) { api.setVolume(v); delay(fadeStepMs) }
        runCatching { avt.stop() }
        api.setVolume(original)
        val np = runCatching { api.nowPlaying() }.getOrNull()
        if (SleepPlan.shouldPressPower(np)) api.pressKey("POWER")
        return Txt.s(R.string.ui_apagado_por_el_temporizador_volumen, original)
    }

    private fun finish(message: String) {
        job = null
        _state.value = null
        _events.tryEmit(message)
    }
}
