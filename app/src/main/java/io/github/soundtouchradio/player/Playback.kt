package io.github.soundtouchradio.player

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import io.github.soundtouchradio.soundtouch.NowPlaying
import io.github.soundtouchradio.soundtouch.PlayStatus

/** Por qué no está sonando. El orden de [Diagnosis.diagnose] es el de probabilidad documentada. */
enum class FailureCause {
    HTTPS_UNSUPPORTED,
    HLS_UNSUPPORTED,
    RENDERER_NAME,
    NATIVE_SOURCE_ACTIVE,
    STANDBY_NOT_WOKEN,
    NOT_CONFIRMED,
    UPNP_ERROR,
    SPEAKER_UNREACHABLE,
    RESOLVE_FAILED,
    /** «Este móvil»: el reproductor del teléfono no pudo cargar la emisora (sin internet, servidor caído…). */
    PHONE_PLAYBACK,
}

sealed interface Phase {
    data object Idle : Phase
    data class Resolving(val station: String) : Phase
    data class Sending(val station: String) : Phase
    data class AwaitingConfirmation(val station: String) : Phase
    data class Buffering(val station: String) : Phase
    data class Playing(val station: String) : Phase
    data class Paused(val station: String) : Phase
    data object Stopped : Phase
    data object Standby : Phase
    data class OtherSource(val source: String, val title: String?) : Phase
    data class Failed(val cause: FailureCause, val station: String?, val detail: String?) : Phase
    data object Unreachable : Phase

    val isBusy: Boolean get() = this is Resolving || this is Sending || this is AwaitingConfirmation
    /** La luz piloto solo se enciende si el altavoz confirma que suena. */
    val isConfirmedPlaying: Boolean get() = this is Playing
}

enum class ConfirmStep { CONFIRMED, BUFFERING, WAITING }

data class DiagnosisContext(
    val streamIsHttps: Boolean,
    val streamIsHls: Boolean,
    val speakerNameBreaksRenderer: Boolean,
    val nativeSourceWasActive: Boolean,
    val wasStandby: Boolean,
    val last: NowPlaying?,
)

object Diagnosis {
    fun confirmationStep(np: NowPlaying): ConfirmStep = when {
        np.isUpnp && np.playStatus == PlayStatus.PLAY -> ConfirmStep.CONFIRMED
        np.isUpnp && np.playStatus == PlayStatus.BUFFERING -> ConfirmStep.BUFFERING
        else -> ConfirmStep.WAITING
    }

    fun diagnose(c: DiagnosisContext): FailureCause = when {
        c.streamIsHttps -> FailureCause.HTTPS_UNSUPPORTED
        c.streamIsHls -> FailureCause.HLS_UNSUPPORTED
        c.speakerNameBreaksRenderer -> FailureCause.RENDERER_NAME
        c.wasStandby && c.last?.isStandby != false -> FailureCause.STANDBY_NOT_WOKEN
        c.nativeSourceWasActive && c.last?.isUpnp != true -> FailureCause.NATIVE_SOURCE_ACTIVE
        else -> FailureCause.NOT_CONFIRMED
    }

    /**
     * Estado mostrado a partir del estado real del altavoz, fuera del arranque.
     * Un fallo se mantiene hasta que el altavoz demuestre otra cosa (suena o el usuario actúa).
     */
    fun phaseFrom(np: NowPlaying, current: Phase, stationName: String?): Phase {
        val name = stationName ?: np.itemName ?: np.stationName ?: Txt.s(R.string.ui_emisora_2)
        if (np.isStandby) return if (current is Phase.Failed) current else Phase.Standby
        if (np.isUpnp) {
            return when (np.playStatus) {
                PlayStatus.PLAY -> Phase.Playing(name)
                PlayStatus.BUFFERING -> if (current is Phase.Failed) current else Phase.Buffering(name)
                PlayStatus.PAUSE -> Phase.Paused(name)
                PlayStatus.STOP, PlayStatus.INVALID, PlayStatus.UNKNOWN ->
                    if (current is Phase.Failed) current else Phase.Stopped
            }
        }
        if (current is Phase.Failed) return current
        // Sin fuente (tras Stop o un fallo): es «Detenido», no otra fuente.
        if (np.source == "INVALID_SOURCE") return Phase.Stopped
        return Phase.OtherSource(np.source, np.itemName ?: np.stationName)
    }
}
