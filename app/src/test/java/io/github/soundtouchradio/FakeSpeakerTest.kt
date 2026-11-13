package io.github.soundtouchradio

import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.player.*
import io.github.soundtouchradio.playlist.StreamProbe
import io.github.soundtouchradio.soundtouch.Http
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.upnp.AvTransport
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Altavoz simulado: sirve /info, /now_playing, /volume y AVTransport en un mismo servidor local,
 * además de un «stream» y una lista .pls. Permite reproducir los fallos documentados por la comunidad.
 */
class FakeSpeakerTest {
    private lateinit var server: MockWebServer
    @Volatile private var source = "STANDBY"
    @Volatile private var status: String? = null
    /** Qué hace el altavoz tras recibir Play: "plays", "buffers-then-plays", "ignores". */
    @Volatile private var behaviour = "plays"
    @Volatile private var nowPlayingCalls = 0
    private val soapActions = CopyOnWriteArrayList<String>()
    private val powerPresses = CopyOnWriteArrayList<String>()
    private val volumesSet = CopyOnWriteArrayList<Int>()
    /** Altavoz que no reacciona a la tecla POWER (para probar el tiempo de espera). */
    @Volatile private var ignorePower = false
    private val sentUris = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/info" -> xml(Fixtures.info())
                    path == "/volume" && request.method == "GET" -> xml(Fixtures.volume(volumesSet.lastOrNull() ?: 30))
                    path == "/volume" -> { volumesSet += request.body.readUtf8().substringAfter("<volume>").substringBefore("<").toInt(); xml("<status>/volume</status>") }
                    path == "/key" -> {
                        val body = request.body.readUtf8()
                        if ("POWER" in body && "release" in body) {
                            powerPresses += "POWER"
                            if (!ignorePower) { if (source == "STANDBY") { source = "UPNP"; status = "STOP_STATE" } else { source = "STANDBY"; status = null } }
                        }
                        xml("<status>/key</status>")
                    }
                    path == "/now_playing" -> {
                        nowPlayingCalls++
                        if (behaviour == "buffers-then-plays" && source == "UPNP" && status == "BUFFERING_STATE" && nowPlayingCalls > 3) status = "PLAY_STATE"
                        xml(Fixtures.nowPlaying(source, status, if (source == "UPNP") "Radio" else null))
                    }
                    path == "/AVTransport/Control" -> {
                        val action = request.getHeader("SOAPACTION").orEmpty().substringAfter('#').trimEnd('"')
                        soapActions += action
                        val body = request.body.readUtf8()
                        if (action == "SetAVTransportURI") sentUris += body.substringAfter("<CurrentURI>").substringBefore("</CurrentURI>")
                        // Como el SoundTouch 10 real (firmware 27.0.6): rechaza https con UPnP 402.
                        if (action == "SetAVTransportURI" && "<CurrentURI>https://" in body) return MockResponse().setResponseCode(500)
                            .setHeader("Content-Type", "text/xml").setBody(Fixtures.SOAP_FAULT.replace("714", "402").replace("Illegal MIME-type", "Can't play. No URI supplied."))
                        if (action == "Play") when (behaviour) {
                            "plays" -> { source = "UPNP"; status = "PLAY_STATE" }
                            "buffers-then-plays" -> { source = "UPNP"; status = "BUFFERING_STATE"; nowPlayingCalls = 0 }
                            "ignores" -> Unit // SOAP responde OK pero no suena (renderer bloqueado)
                        }
                        if (action == "Stop") { status = "STOP_STATE" }
                        if (action == "GetCurrentTransportActions") xml(Fixtures.SOAP_ACTIONS) else xml(Fixtures.SOAP_OK)
                    }
                    path == "/stream.mp3" -> MockResponse().setHeader("Content-Type", "audio/mpeg").setHeader("icy-br", "128").setBody("ID3")
                    path == "/redirect" -> MockResponse().setResponseCode(302).setHeader("Location", server.url("/stream.mp3").toString())
                    path == "/list.pls" -> MockResponse().setHeader("Content-Type", "audio/x-scpls")
                        .setBody("[playlist]\nFile1=${server.url("/dead.mp3")}\nFile2=${server.url("/stream.mp3")}\n")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun xml(body: String) = MockResponse().setHeader("Content-Type", "text/xml").setBody(body.trim())

    private fun controller(scope: kotlinx.coroutines.CoroutineScope): SpeakerController {
        val log = RequestLog()
        val lan = Http.lan()
        val host = server.hostName
        val speaker = SpeakerRecord(host = host, name = "Salón", controlUrl = server.url("/AVTransport/Control").toString())
        val api = SoundTouchApi.forHost(host, lan, log, port = server.port)
        val avt = AvTransport(speaker.controlUrl!!, lan, log)
        return SpeakerController(speaker, api, avt, StreamProbe(Http.internet()), scope,
            Timing(confirmTimeoutMs = 5_000, bufferingGraceMs = 5_000, confirmPollMs = 500))
    }

    /**
     * Cliente de «internet» para el sondeo: radio.example por https sirve audio; por http solo existe /both.mp3.
     * Así se simulan una emisora con versión http y otra que solo emite por https.
     */
    private fun internetWithHttpsHost() = Http.internet().newBuilder().addInterceptor { chain ->
        val u = chain.request().url
        if (u.host != "radio.example") return@addInterceptor chain.proceed(chain.request())
        if (!u.isHttps && u.encodedPath != "/both.mp3") throw java.io.IOException("sin versión http")
        // Responde aquí mismo, sin cambiar la URL: el sondeo debe ver https o http tal cual.
        okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", "audio/mpeg").header("icy-br", "128")
            .body(okhttp3.ResponseBody.Companion.create(null, "ID3")).build()
    }.build()

    private fun controllerWith(scope: kotlinx.coroutines.CoroutineScope, upgrades: MutableList<String>, bridge: (String) -> String? = { null }): SpeakerController {
        val log = RequestLog()
        val lan = Http.lan()
        val speaker = SpeakerRecord(host = server.hostName, name = "Salón", controlUrl = server.url("/AVTransport/Control").toString())
        return SpeakerController(speaker, SoundTouchApi.forHost(server.hostName, lan, log, port = server.port),
            AvTransport(speaker.controlUrl!!, lan, log), StreamProbe(internetWithHttpsHost()), scope,
            Timing(confirmTimeoutMs = 5_000, bufferingGraceMs = 5_000, confirmPollMs = 500),
            onHttpUpgrade = { id, url -> upgrades += "$id=$url" }, bridge = bridge)
    }

    /** 1.7.1: una emisora https con versión http suena por http, y la app guarda esa versión. */
    @Test fun httpsStationPlaysThroughItsHttpVersion() = runTest {
        val upgrades = mutableListOf<String>()
        val c = controllerWith(this, upgrades)
        c.play(PlayRequest("s1", "Radio", "https://radio.example/both.mp3")).join()
        assertEquals(Phase.Playing("Radio"), c.state.value.phase)
        assertTrue(sentUris.none { it.startsWith("https://") })
        assertEquals(listOf("s1=http://radio.example/both.mp3"), upgrades)
        assertEquals("http://radio.example/both.mp3", c.state.value.resolved?.originalUrl)
    }

    /** Solo https: el altavoz devuelve 402 y la causa es «https», no un error UPnP genérico. */
    @Test fun httpsOnlyStationExplainsTheCause() = runTest {
        val upgrades = mutableListOf<String>()
        val c = controllerWith(this, upgrades)
        c.play(PlayRequest("s2", "Solo https", "https://radio.example/only.mp3")).join()
        val p = c.state.value.phase
        assertTrue(p is Phase.Failed && p.cause == FailureCause.HTTPS_UNSUPPORTED)
        assertTrue(upgrades.isEmpty())
    }

    /** 1.9.5: solo https y puente activado: el altavoz recibe la dirección http del puente, nunca la https. */
    @Test fun httpsOnlyStationUsesTheRedirectBridgeWhenEnabled() = runTest {
        val bridgeUrl = server.url("/stream.mp3").toString()
        val c = controllerWith(this, mutableListOf()) { https -> if (https.startsWith("https://")) bridgeUrl else null }
        c.play(PlayRequest("s3", "Solo https", "https://radio.example/only.mp3")).join()
        assertEquals(Phase.Playing("Solo https"), c.state.value.phase)
        assertTrue(sentUris.none { it.startsWith("https://") })
        assertEquals(bridgeUrl, sentUris.last())
    }

    @Test fun invalidSourceIsStoppedNotAnotherSource() =
        assertEquals(Phase.Stopped, Diagnosis.phaseFrom(io.github.soundtouchradio.soundtouch.SoundTouchXml.parseNowPlaying(Fixtures.nowPlaying("INVALID_SOURCE", null)), Phase.Idle, null))

    /** Teclas − / +: un punto desde el último valor; el anti-rebote envía solo el final. */
    @Test fun volumeStepsSendOnlyTheFinalValue() = runTest {
        val c = controller(this)
        c.refresh()
        assertEquals(30, c.state.value.volume)
        repeat(3) { c.stepVolume(-1) }
        assertEquals(27, c.state.value.volume)
        testScheduler.advanceTimeBy(2_000); testScheduler.runCurrent()
        kotlinx.coroutines.delay(1)
        repeat(50) { if (volumesSet.isEmpty()) Thread.sleep(20) }
        assertEquals(listOf(27), volumesSet.toList())
        repeat(100) { if (c.state.value.volumePending) { testScheduler.runCurrent(); Thread.sleep(20) } }
        assertFalse(c.state.value.volumePending)          // el altavoz devolvió 27: cifra confirmada
        assertEquals(27, c.state.value.volume)
    }

    @Test fun canStepStopsAtTheLimits() = runTest {
        val c = controller(this)
        c.refresh()
        assertTrue(c.canStep(-1)); assertTrue(c.canStep(1))
        c.setVolume(0); assertFalse(c.canStep(-1)); assertTrue(c.canStep(1))
        c.setVolume(100); assertFalse(c.canStep(1))
    }

    /** «¿Admite pausa?» no se pregunta en cada ciclo de sondeo si nada ha cambiado. */
    @Test fun pauseAvailabilityIsNotPolledWhenNothingChanges() = runTest {
        source = "UPNP"; status = "PLAY_STATE"
        val c = controller(this)
        repeat(3) { c.refresh() }
        assertEquals(1, soapActions.count { it == "GetCurrentTransportActions" })
    }

    @Test fun playIsConfirmedAgainstNowPlaying() = runTest {
        val c = controller(this)
        c.play(PlayRequest("s1", "Radio", server.url("/stream.mp3").toString())).join()
        assertEquals(Phase.Playing("Radio"), c.state.value.phase)
        assertEquals(listOf("SetAVTransportURI", "Play", "GetCurrentTransportActions"), soapActions.take(3))
        assertFalse(c.state.value.pauseAvailable)
    }

    @Test fun bufferingThenPlaying() = runTest {
        behaviour = "buffers-then-plays"
        val c = controller(this)
        c.play(PlayRequest(null, "Radio", server.url("/stream.mp3").toString())).join()
        assertEquals(Phase.Playing("Radio"), c.state.value.phase)
    }

    @Test fun soapOkButNeverPlaysIsAFailureNotASuccess() = runTest {
        behaviour = "ignores"
        val c = controller(this)
        c.play(PlayRequest(null, "Radio", server.url("/stream.mp3").toString())).join()
        val phase = c.state.value.phase
        assertTrue("fase: $phase", phase is Phase.Failed)
        // Venía de reposo y sigue en reposo: la causa es no haber despertado.
        assertEquals(FailureCause.STANDBY_NOT_WOKEN, (phase as Phase.Failed).cause)
    }

    @Test fun renderStuckWhileAwakeIsNotConfirmed() = runTest {
        behaviour = "ignores"; source = "UPNP"; status = "STOP_STATE"
        val c = controller(this)
        c.play(PlayRequest(null, "Radio", server.url("/stream.mp3").toString())).join()
        assertEquals(FailureCause.NOT_CONFIRMED, (c.state.value.phase as Phase.Failed).cause)
    }

    @Test fun nativeRadioSwallowsPush() = runTest {
        behaviour = "ignores"; source = "TUNEIN"; status = "PLAY_STATE"
        val c = controller(this)
        c.play(PlayRequest(null, "Radio", server.url("/stream.mp3").toString())).join()
        assertEquals(FailureCause.NATIVE_SOURCE_ACTIVE, (c.state.value.phase as Phase.Failed).cause)
    }

    @Test fun plsIsResolvedToFirstWorkingEntry() = runTest {
        val c = controller(this)
        c.play(PlayRequest(null, "Radio", server.url("/list.pls").toString())).join()
        assertEquals(server.url("/stream.mp3").toString(), sentUris.single().replace("&amp;", "&"))
        assertTrue(c.state.value.resolved!!.viaPlaylist)
    }

    @Test fun redirectsAreFollowedOnThePhone() = runTest {
        val c = controller(this)
        c.play(PlayRequest(null, "Radio", server.url("/redirect").toString())).join()
        assertEquals(server.url("/stream.mp3").toString(), sentUris.single())
        assertEquals("audio/mpeg", c.state.value.resolved!!.mime)
    }

    @Test fun unreachableSpeaker() = runTest {
        val c = controller(this)
        server.shutdown()
        c.play(PlayRequest(null, "Radio", "http://127.0.0.1:9/stream.mp3")).join()
        assertEquals(FailureCause.SPEAKER_UNREACHABLE, (c.state.value.phase as Phase.Failed).cause)
    }

    // --- Encendido seguro (POWER alterna: leer, pulsar solo si hace falta, confirmar) ---

    @Test fun powerOnFromStandbyPressesOnceAndConfirms() = runTest {
        val c = controller(this)
        assertEquals(PowerResult.DONE, c.setPower(true))
        assertEquals(1, powerPresses.size)
        assertEquals(true, c.state.value.speakerOn)
        assertNull(c.state.value.powerTarget)
    }

    @Test fun powerOnWhenAlreadyOnDoesNotToggle() = runTest {
        source = "UPNP"; status = "PLAY_STATE"
        val c = controller(this)
        assertEquals(PowerResult.ALREADY, c.setPower(true))
        assertTrue(powerPresses.isEmpty())
    }

    @Test fun powerOffGoesToStandby() = runTest {
        source = "UPNP"; status = "PLAY_STATE"
        val c = controller(this)
        assertEquals(PowerResult.DONE, c.setPower(false))
        assertEquals(Phase.Standby, c.state.value.phase)
        assertEquals(false, c.state.value.speakerOn)
    }

    @Test fun noConfirmationIsReportedNotFaked() = runTest {
        ignorePower = true
        val c = controller(this)
        assertEquals(PowerResult.NO_CONFIRMATION, c.setPower(true))
        assertEquals(false, c.state.value.speakerOn) // sigue en reposo: no se finge «encendido»
        assertEquals(1, powerPresses.size)           // una sola pulsación, sin reintentos que lo vuelvan a apagar
    }

    @Test fun unreachableSpeakerPower() = runTest {
        val c = controller(this)
        server.shutdown()
        assertEquals(PowerResult.UNREACHABLE, c.setPower(true))
        assertTrue(powerPresses.isEmpty())
    }

    // --- Despertador: encender, volumen, emisora confirmada, subida gradual ---

    private val ringer = AlarmRinger(retryMs = 200, fadeStepMs = 1)
    private fun api() = SoundTouchApi.forHost(server.hostName, Http.lan(), RequestLog(), port = server.port)

    @Test fun alarmWakesPlaysAndFadesIn() = runTest {
        val c = controller(this)
        val r = ringer.ring(c, api(), PlayRequest("s", "Cadena SER", server.url("/stream.mp3").toString()), volume = 20, fadeIn = true)
        assertEquals(AlarmOutcome.Playing, r)
        assertEquals(1, powerPresses.size)                               // encendido una vez
        assertEquals(listOf(5) + AlarmPlan.fadeInSteps(20), volumesSet)  // empieza bajo y sube hasta 20
        assertEquals(Phase.Playing("Cadena SER"), c.state.value.phase)
    }

    @Test fun alarmFailsLoudlyIfSpeakerNeverWakes() = runTest {
        ignorePower = true
        val c = controller(this)
        val r = ringer.ring(c, api(), PlayRequest("s", "Cadena SER", server.url("/stream.mp3").toString()), volume = 20, fadeIn = true)
        assertTrue(r is AlarmOutcome.Failed)
        assertEquals(AlarmPlan.WAKE_ATTEMPTS, powerPresses.size)          // reintenta, pero sin quedarse en bucle
    }

    @Test fun alarmFailsIfStationNeverPlays() = runTest {
        source = "UPNP"; status = "STOP_STATE"; behaviour = "ignores"
        val c = controller(this)
        val r = ringer.ring(c, api(), PlayRequest("s", "RNE", server.url("/stream.mp3").toString()), volume = 15, fadeIn = false)
        assertTrue(r is AlarmOutcome.Failed)
        assertTrue((r as AlarmOutcome.Failed).reason.startsWith("RNE"))
    }
}
