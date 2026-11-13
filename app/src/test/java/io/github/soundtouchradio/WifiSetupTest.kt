package io.github.soundtouchradio

import io.github.soundtouchradio.player.RequestLog
import io.github.soundtouchradio.soundtouch.DeviceInfo
import io.github.soundtouchradio.soundtouch.Http
import io.github.soundtouchradio.soundtouch.SoundTouchException
import io.github.soundtouchradio.soundtouch.SpeakerSetupApi
import io.github.soundtouchradio.soundtouch.WifiNetwork
import io.github.soundtouchradio.soundtouch.WifiSetupXml
import io.github.soundtouchradio.soundtouch.Xml
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class WifiSetupTest {
    // Formato documentado (gesellix, UNIMPLEMENTED-ENDPOINTS: performWirelessSiteSurvey).
    private val survey = """<PerformWirelessSiteSurveyResponse error="none"><items>
        <item ssid="Casa" signalStrength="-58" secure="true"><securityTypes><type>wpa_or_wpa2</type></securityTypes></item>
        <item ssid="Vecino" signalStrength="-80" secure="true"><securityTypes><type>wpa2aes</type></securityTypes></item>
        <item ssid="Casa" signalStrength="-70" secure="true"><securityTypes><type>wpa_or_wpa2</type></securityTypes></item>
        <item ssid="Bar abierto" signalStrength="-62" secure="false"><securityTypes><type>none</type></securityTypes></item>
        <item ssid="" signalStrength="-40" secure="true"/>
    </items></PerformWirelessSiteSurveyResponse>"""

    @Test fun parsesDedupesAndSortsBySignal() {
        val n = WifiSetupXml.parseSurvey(survey)
        assertEquals(listOf("Casa", "Bar abierto", "Vecino"), n.map { it.ssid })   // sin vacías ni duplicados
        assertEquals(-58, n[0].signalDbm)                                            // se queda la más fuerte
        assertEquals(3, n[0].bars); assertEquals(1, n[2].bars)   // -58 dBm = 3 barras; -80 dBm = 1
        assertFalse(n[1].secure)
    }

    @Test fun choosesSecurity() {
        val n = WifiSetupXml.parseSurvey(survey).associateBy { it.ssid }
        assertEquals("wpa_or_wpa2", WifiSetupXml.chooseSecurity(n["Casa"]))
        assertEquals("wpa2aes", WifiSetupXml.chooseSecurity(n["Vecino"]))
        assertEquals("none", WifiSetupXml.chooseSecurity(n["Bar abierto"]))
        assertEquals("wpa_or_wpa2", WifiSetupXml.chooseSecurity(null))   // red escrita a mano
    }

    @Test fun credentialsAreEscapedAndRoundTrip() {
        val ssid = "Casa \"5\" & <Pérez>"; val pass = "a&b\"c<d>'e"
        val body = WifiSetupXml.profileBody(ssid, pass, "wpa_or_wpa2")
        val profile = Xml.parse(body).getElementsByTagName("profile").item(0) as org.w3c.dom.Element
        assertEquals(ssid, profile.getAttribute("ssid")); assertEquals(pass, profile.getAttribute("password"))
        assertEquals("wpa_or_wpa2", profile.getAttribute("securityType"))
    }

    @Test fun recognisesTheSameSpeakerAfterIpChange() {
        val ap = DeviceInfo("0011223344AA", "SoundTouch 10", "SoundTouch 10", "192.0.2.1", "0011223344AA", null)
        val home = DeviceInfo("0011223344AA", "SoundTouch 10", "SoundTouch 10", "192.168.1.20", "00:11:22:33:44:BB", null)
        val other = DeviceInfo("FFEEDDCCBBAA", "Cocina", "SoundTouch 20", "192.168.1.21", "FFEEDDCCBBAA", null)
        assertTrue(WifiSetupXml.sameSpeaker(ap, home)); assertFalse(WifiSetupXml.sameSpeaker(ap, other))
    }

    /** Altavoz en modo AP simulado: el primer envío de credenciales se cuelga (documentado), el segundo funciona. */
    @Test fun setupFlowRetriesFirstHangingPost() = runTest {
        val server = MockWebServer()
        val posts = AtomicInteger(0)
        val paths = CopyOnWriteArrayList<String>()
        val bodies = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path.orEmpty(); bodies += request.body.readUtf8()
                return when (request.path) {
                    "/info" -> MockResponse().setBody(Fixtures.info("SoundTouch 10"))
                    "/performWirelessSiteSurvey" -> MockResponse().setBody(survey)
                    "/addWirelessProfile" -> if (posts.incrementAndGet() == 1) MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                        else MockResponse().setBody("<status>/addWirelessProfile</status>")
                    "/setup" -> MockResponse().setBody("<status>/setup</status>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = SpeakerSetupApi(Http.lan(), RequestLog(), baseUrl = server.url("/").toString().trimEnd('/'),
            perAttemptTimeoutMs = 500, retryDelayMs = 50)
        assertEquals("SoundTouch 10", api.info().name)
        assertEquals("Casa", api.survey().first().ssid)
        // Sin respuesta (el altavoz cortó su red al probar la tuya): no es un fallo, y no se reenvía.
        assertEquals(io.github.soundtouchradio.soundtouch.SendOutcome.NO_ANSWER, api.sendProfile("Casa", "secreto123", "wpa_or_wpa2"))
        api.leaveSetup()
        assertEquals(1, posts.get())
        assertEquals(listOf("/info", "/performWirelessSiteSurvey", "/addWirelessProfile", "/setup"), paths)
        assertTrue(bodies.last().contains("SETUP_WIFI_LEAVE"))
        server.shutdown()
    }

    @Test fun httpErrorIsARejectionAndPasswordIsNeverLogged() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500) }
        server.start()
        val log = RequestLog()
        val api = SpeakerSetupApi(Http.lan(), log, baseUrl = server.url("/").toString().trimEnd('/'), perAttemptTimeoutMs = 500, retryDelayMs = 10)
        assertThrows(SoundTouchException::class.java) { kotlinx.coroutines.runBlocking { api.sendProfile("Casa", "secreto123", "wpa_or_wpa2") } }
        assertEquals(1, server.requestCount)
        assertTrue(log.entries.value.none { "secreto123" in it.target || "secreto123" in it.outcome })
        server.shutdown()
    }

    /** El altavoz tarda más que el corte de lectura del cliente LAN (4 s): el escaneo debe esperar. */
    @Test fun slowSurveyIsNotCutByTheLanReadTimeout() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<r><items><item ssid=\"Casa\" signalStrength=\"-50\" secure=\"true\"/></items></r>")
            .setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS))
        server.start()
        val api = SpeakerSetupApi(Http.lan(), RequestLog(), baseUrl = server.url("/").toString().trimEnd('/'))
        assertEquals("Casa", kotlinx.coroutines.runBlocking { api.survey() }.single().ssid)
        server.shutdown()
    }

    @Test fun answeredProfileIsAccepted() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<status>/addWirelessProfile</status>").setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS))
        server.start()
        val api = SpeakerSetupApi(Http.lan(), RequestLog(), baseUrl = server.url("/").toString().trimEnd('/'))
        assertEquals(io.github.soundtouchradio.soundtouch.SendOutcome.ACCEPTED,
            kotlinx.coroutines.runBlocking { api.sendProfile("Casa", "secreto123", "wpa_or_wpa2") })
        server.shutdown()
    }

    @Test fun mergesSpeakerAndPhoneScans() {
        val speaker = listOf(WifiNetwork("Casa_2G", -60, true, listOf("wpa_or_wpa2")))
        val phone = listOf(
            io.github.soundtouchradio.soundtouch.PhoneScan("Casa_2G", 2437, -40, "[WPA2-PSK-CCMP]"),
            io.github.soundtouchradio.soundtouch.PhoneScan("Vecino", 2412, -70, "[WPA2-PSK-CCMP]"),
            io.github.soundtouchradio.soundtouch.PhoneScan("Vecino", 5180, -55, "[WPA2-PSK-CCMP]"),
            io.github.soundtouchradio.soundtouch.PhoneScan("Casa_5G", 5500, -45, "[WPA2-PSK-CCMP]"),
            io.github.soundtouchradio.soundtouch.PhoneScan("Cafe", 2462, -80, "[ESS]"),
            io.github.soundtouchradio.soundtouch.PhoneScan("Bose ST 10 (1A2B3C)", 2412, -30, "[ESS]"),
            io.github.soundtouchradio.soundtouch.PhoneScan("", 2412, -30, "[ESS]"),
        )
        val m = io.github.soundtouchradio.soundtouch.WifiSetupXml.mergeNetworks(speaker, phone)
        assertEquals(listOf("Vecino", "Casa_2G", "Cafe", "Casa_5G"), m.map { it.ssid })
        assertEquals(-60, m.first { it.ssid == "Casa_2G" }.signalDbm)   // manda el escaneo del altavoz
        assertFalse(m.first { it.ssid == "Vecino" }.only5GHz)           // también está en 2,4 GHz
        assertTrue(m.last().only5GHz)                                   // solo 5 GHz: al final, deshabilitada
        assertEquals("none", io.github.soundtouchradio.soundtouch.WifiSetupXml.chooseSecurity(m.first { it.ssid == "Cafe" }))
    }

    @Suppress("unused") private val keep = WifiNetwork("x", null, false, emptyList())
}
