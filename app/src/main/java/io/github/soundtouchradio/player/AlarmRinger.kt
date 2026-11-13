package io.github.soundtouchradio.player

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.ui.Copy
import kotlinx.coroutines.delay

sealed interface AlarmOutcome {
    data object Playing : AlarmOutcome
    data class Failed(val reason: String) : AlarmOutcome
}

/**
 * Lo que hace el despertador a su hora: encender (lógica segura del ⏻, con reintentos),
 * volumen inicial, reproducir la emisora, confirmar que suena y subir el volumen poco a poco.
 * Nunca da por buena la alarma sin confirmación del altavoz.
 */
class AlarmRinger(
    private val retryMs: Long = AlarmPlan.RETRY_MS,
    private val fadeStepMs: Long = AlarmPlan.FADE_IN_MS / AlarmPlan.FADE_IN_STEPS,
) {
    suspend fun ring(
        controller: SpeakerController,
        api: SoundTouchApi,
        station: PlayRequest,
        volume: Int,
        fadeIn: Boolean,
        onProgress: (String) -> Unit = {},
    ): AlarmOutcome {
        // 1. Encender, con reintentos: el altavoz puede tardar en responder al salir del reposo.
        onProgress(Txt.s(R.string.ui_encendiendo_2, controller.speaker.name))
        var on = false
        for (attempt in 1..AlarmPlan.WAKE_ATTEMPTS) {
            when (controller.setPower(true)) {
                PowerResult.DONE, PowerResult.ALREADY -> { on = true }
                else -> if (attempt < AlarmPlan.WAKE_ATTEMPTS) delay(retryMs)
            }
            if (on) break
        }
        if (!on) return AlarmOutcome.Failed(Txt.s(R.string.ui_no_se_encendio_sin_wi, controller.speaker.name))

        // 2. Volumen de partida.
        val target = volume.coerceIn(1, 100)
        runCatching { api.setVolume(if (fadeIn) minOf(AlarmPlan.START_VOLUME, target) else target) }

        // 3. Emisora, confirmada contra /now_playing; un reintento si no arranca.
        onProgress(Txt.s(R.string.ui_poniendo, station.name))
        repeat(2) { attempt ->
            controller.play(station).join()
            if (controller.state.value.phase is Phase.Playing) {
                // 4. Subida gradual.
                if (fadeIn) for (v in AlarmPlan.fadeInSteps(target)) { runCatching { api.setVolume(v) }; delay(fadeStepMs) }
                return AlarmOutcome.Playing
            }
            if (attempt == 0) delay(retryMs / 2)
        }
        val cause = (controller.state.value.phase as? Phase.Failed)?.let { Copy.cause(it.cause).title } ?: Txt.s(R.string.ui_no_confirmo_que_suene)
        return AlarmOutcome.Failed("${station.name}: $cause.")
    }
}
