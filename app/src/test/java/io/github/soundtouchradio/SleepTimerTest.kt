package io.github.soundtouchradio

import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.player.RequestLog
import io.github.soundtouchradio.player.SleepPlan
import io.github.soundtouchradio.player.SleepTimer
import io.github.soundtouchradio.soundtouch.Http
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.soundtouch.SoundTouchXml
import io.github.soundtouchradio.upnp.AvTransport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SleepPlanTest {
    private fun np(source: String, status: String? = null) = SoundTouchXml.parseNowPlaying(Fixtures.nowPlaying(source, status))

    @Test fun fadeGoesDownToZero() {
        val s = SleepPlan.fadeSteps(24)
        assertEquals(12, s.size); assertEquals(0, s.last()); assertTrue(s.zipWithNext().all { (a, b) -> b <= a }); assertTrue(s.first() < 24)
        assertEquals(listOf(0), SleepPlan.fadeSteps(0))
    }

    @Test fun powerOnlyWhenAwake() {
        assertTrue(SleepPlan.shouldPressPower(np("UPNP", "STOP_STATE")))
        assertFalse(SleepPlan.shouldPressPower(np("STANDBY")))
        assertFalse(SleepPlan.shouldPressPower(null))
    }

    @Test fun cancelsWhenRadioIsNoLongerPlaying() {
        assertNull(SleepPlan.cancelReason(np("UPNP", "PLAY_STATE")))
        assertNull(SleepPlan.cancelReason(np("UPNP", "PAUSE_STATE")))
        assertNotNull(SleepPlan.cancelReason(np("STANDBY")))
        assertNotNull(SleepPlan.cancelReason(np("BLUETOOTH", "PLAY_STATE")))
        assertNotNull(SleepPlan.cancelReason(np("UPNP", "STOP_STATE")))
    }

    @Test fun remainingMinutesRoundsUp() {
        assertEquals(1, SleepPlan.remainingMinutes(60_000, 1))
        assertEquals(0, SleepPlan.remainingMinutes(0, 5))
    }
}

/** Temporizador completo contra el altavoz simulado, con tiempo virtual. */
class SleepTimerTest {
    private lateinit var server: MockWebServer
    @Volatile private var source = "UPNP"
    @Volatile private var status = "PLAY_STATE"
    private val volumes = CopyOnWriteArrayList<Int>()
    private val keys = CopyOnWriteArrayList<String>()
    private val soap = CopyOnWriteArrayList<String>()

    @Before fun up() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    request.path == "/volume" && request.method == "GET" -> MockResponse().setBody(Fixtures.volume(24))
                    request.path == "/volume" -> { volumes += body.substringAfter("<volume>").substringBefore("<").toInt(); MockResponse().setBody("<status>/volume</status>") }
                    request.path == "/key" -> { if ("press" in body) keys += body.substringAfter(">").substringBefore("<"); if ("POWER" in body && "release" in body) { source = "STANDBY" }; MockResponse().setBody("<status>/key</status>") }
                    request.path == "/now_playing" -> MockResponse().setBody(Fixtures.nowPlaying(source, if (source == "STANDBY") null else status))
                    request.path == "/AVTransport/Control" -> { val a = request.getHeader("SOAPACTION").orEmpty().substringAfter('#').trimEnd('"'); soap += a; if (a == "Stop") status = "STOP_STATE"; MockResponse().setBody(Fixtures.SOAP_OK) }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }
    @After fun down() = server.shutdown()

    private fun TestScope.timer() = SleepTimer(this, clock = { currentTime }, clients = { _ ->
        SoundTouchApi.forHost(server.hostName, Http.lan(), RequestLog(), port = server.port) to
            AvTransport(server.url("/AVTransport/Control").toString(), Http.lan(), RequestLog())
    })
    private val speaker = SpeakerRecord(host = "x", name = "SoundTouch 10", mac = "A")

    @Test fun fadesStopsRestoresVolumeAndSleeps() = runTest {
        val t = timer()
        val events = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { t.events.collect { events += it } }
        t.start(speaker, 15, fade = true).join()
        assertEquals(SleepPlan.fadeSteps(24) + 24, volumes)        // bajada y restauración
        assertTrue("Stop" in soap)
        assertEquals(listOf("POWER"), keys)                         // reposo, una sola vez
        assertNull(t.state.value)
        assertTrue(events.single().contains("restaurado a 24"))
    }

    @Test fun cancelsIfSpeakerLeavesRadio() = runTest {
        val t = timer()
        val events = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { t.events.collect { events += it } }
        source = "BLUETOOTH"
        t.start(speaker, 30, fade = true).join()
        assertTrue(keys.isEmpty()); assertTrue(volumes.isEmpty())   // no toca nada
        assertTrue(events.single().contains("otra fuente"))
    }

    @Test fun doesNotPressPowerIfAlreadyInStandbyAtTheEnd() = runTest {
        val t = timer()
        // El altavoz entra en reposo por su cuenta antes de la hora: la comprobación lo detecta y cancela.
        source = "STANDBY"
        t.start(speaker, 15, fade = false).join()
        assertTrue(keys.isEmpty())
    }

    @Test fun userCancel() = runTest {
        val t = timer()
        t.start(speaker, 15, fade = true)
        t.cancel()
        advanceUntilIdle()
        assertNull(t.state.value); assertTrue(volumes.isEmpty())
    }
}
