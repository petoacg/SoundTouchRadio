package io.github.soundtouchradio

import io.github.soundtouchradio.player.*
import io.github.soundtouchradio.soundtouch.SoundTouchXml
import org.junit.Assert.*
import org.junit.Test

class DiagnosisTest {
    private fun np(source: String, status: String? = null) = SoundTouchXml.parseNowPlaying(Fixtures.nowPlaying(source, status))
    private fun ctx(https: Boolean = false, hls: Boolean = false, name: Boolean = false, native: Boolean = false, standby: Boolean = false, last: String = "UPNP") =
        DiagnosisContext(https, hls, name, native, standby, np(last, "STOP_STATE"))

    @Test fun confirmationNeedsUpnpAndPlay() {
        assertEquals(ConfirmStep.CONFIRMED, Diagnosis.confirmationStep(np("UPNP", "PLAY_STATE")))
        assertEquals(ConfirmStep.BUFFERING, Diagnosis.confirmationStep(np("UPNP", "BUFFERING_STATE")))
        assertEquals(ConfirmStep.WAITING, Diagnosis.confirmationStep(np("TUNEIN", "PLAY_STATE")))
        assertEquals(ConfirmStep.WAITING, Diagnosis.confirmationStep(np("UPNP", "STOP_STATE")))
    }

    @Test fun diagnosisOrder() {
        assertEquals(FailureCause.HTTPS_UNSUPPORTED, Diagnosis.diagnose(ctx(https = true, hls = true, name = true)))
        assertEquals(FailureCause.HLS_UNSUPPORTED, Diagnosis.diagnose(ctx(hls = true, name = true)))
        assertEquals(FailureCause.RENDERER_NAME, Diagnosis.diagnose(ctx(name = true, native = true)))
        assertEquals(FailureCause.STANDBY_NOT_WOKEN, Diagnosis.diagnose(ctx(standby = true, last = "STANDBY")))
        assertEquals(FailureCause.NATIVE_SOURCE_ACTIVE, Diagnosis.diagnose(ctx(native = true, last = "TUNEIN")))
        assertEquals(FailureCause.NOT_CONFIRMED, Diagnosis.diagnose(ctx()))
    }

    @Test fun phaseFollowsSpeakerAndKeepsFailure() {
        assertEquals(Phase.Playing("X"), Diagnosis.phaseFrom(np("UPNP", "PLAY_STATE"), Phase.Idle, "X"))
        assertEquals(Phase.Standby, Diagnosis.phaseFrom(np("STANDBY"), Phase.Playing("X"), "X"))
        val failed = Phase.Failed(FailureCause.NOT_CONFIRMED, "X", null)
        assertEquals(failed, Diagnosis.phaseFrom(np("UPNP", "STOP_STATE"), failed, "X"))
        assertEquals(Phase.Playing("X"), Diagnosis.phaseFrom(np("UPNP", "PLAY_STATE"), failed, "X"))
        assertTrue(Diagnosis.phaseFrom(np("BLUETOOTH", "PLAY_STATE"), Phase.Idle, null) is Phase.OtherSource)
    }
}
