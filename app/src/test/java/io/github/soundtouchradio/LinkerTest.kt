package io.github.soundtouchradio

import io.github.soundtouchradio.discovery.Candidate
import io.github.soundtouchradio.discovery.FoundVia
import io.github.soundtouchradio.discovery.Linker
import io.github.soundtouchradio.discovery.StepState
import io.github.soundtouchradio.player.RequestLog
import io.github.soundtouchradio.soundtouch.Http
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

/** Regresión 1.0.1: la API se construía sin «http://» a partir de una IP. */
class LinkerTest {
    @Test fun apiForHostBuildsHttpUrl() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Fixtures.info()))
        server.start()
        val info = SoundTouchApi.forHost(server.hostName, Http.lan(), RequestLog(), port = server.port).info()
        assertEquals("Salón", info.name)
        assertEquals("/info", server.takeRequest().path)
        server.shutdown()
    }

    @Test fun baseUrlWithoutSchemeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SoundTouchApi.forBaseUrl("192.168.1.20", Http.lan(), RequestLog()) }
    }

    @Test fun linkerGoesGreenAgainstFakeSpeaker() = runTest {
        // Mismo servidor para 8090 y 8091: el Linker usa la ruta por defecto http://IP:8091/AVTransport/Control,
        // así que aquí se comprueba el paso 8090 con la IP y el puerto reales del servidor simulado.
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.path) {
                "/info" -> MockResponse().setBody(Fixtures.info("SoundTouch 10"))
                else -> MockResponse().setBody(Fixtures.SOAP_OK)
            }
        }
        server.start()
        val linker = Linker(Http.lan(), RequestLog(), null)
        val api = SoundTouchApi.forHost(server.hostName, Http.lan(), RequestLog(), port = server.port)
        assertEquals("SoundTouch 10", api.info().name)
        // Paso 8090 del Linker con IP sin esquema (como introduce el usuario):
        val r = linker.check(Candidate(server.hostName, FoundVia.MANUAL)) {}
        // En el test el 8090 real no es el puerto del servidor simulado, así que solo exigimos que el error
        // ya NO sea el de «URL sin esquema».
        assertFalse(r.steps[1].detail.orEmpty(), r.steps[1].detail.orEmpty().contains("scheme"))
        server.shutdown()
    }
}
