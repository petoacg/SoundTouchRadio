package io.github.soundtouchradio

import io.github.soundtouchradio.player.VolumeCurve
import io.github.soundtouchradio.ui.screens.prettyXml
import org.junit.Assert.*
import org.junit.Test

/** 1.8.0: volumen fino. */
class VolumeTest {
    @Test fun curveRoundTripsEveryLevel() {
        for (v in 0..100) assertEquals(v, VolumeCurve.fromPosition(VolumeCurve.toPosition(v)))
    }

    /** El tramo de la noche (1–3) ocupa unas 3 veces más recorrido que con la barra lineal (0,03). */
    @Test fun lowLevelsGetMoreTravel() {
        val travel = VolumeCurve.toPosition(3) - VolumeCurve.toPosition(0)
        assertTrue("recorrido 0–3 = $travel", travel > 0.15f)
        assertEquals(6, VolumeCurve.fromPosition(0.25f))
        assertEquals(25, VolumeCurve.fromPosition(0.5f))
        assertEquals(100, VolumeCurve.fromPosition(1f))
        assertEquals(0, VolumeCurve.fromPosition(-1f))
    }

    @Test fun diagnosticsXmlIsIndented() {
        val out = prettyXml("<nowPlaying source=\"UPNP\"><art>x</art><playStatus>PLAY_STATE</playStatus></nowPlaying>")
        assertTrue(out.lines().size >= 3)
        assertTrue(out.lines().any { it.startsWith("  <playStatus>") })
        assertEquals("no es xml", prettyXml("no es xml"))
    }

    /** 1.8.2: un toque en la pista vacía no mueve nada; solo el tirador, y en relativo. */
    @Test fun barOnlyMovesFromTheThumb() {
        val width = 600f; val slop = 60f
        val thumbAt8 = VolumeCurve.toPosition(8) * width            // ≈ 170 px
        assertFalse(VolumeCurve.grabsThumb(590f, thumbAt8, slop))   // toque a la derecha, cerca de «+»: ignorado
        assertFalse(VolumeCurve.grabsThumb(0f, thumbAt8, slop))
        assertTrue(VolumeCurve.grabsThumb(thumbAt8 + 30f, thumbAt8, slop))
        // Agarrar el tirador 30 px a su derecha no lo hace saltar: sin desplazar el dedo, sigue en 8.
        assertEquals(8, VolumeCurve.fromPosition(VolumeCurve.dragTo(VolumeCurve.toPosition(8), 0f, width)))
        assertTrue(VolumeCurve.fromPosition(VolumeCurve.dragTo(VolumeCurve.toPosition(8), 20f, width)) in 9..12)
        assertEquals(100, VolumeCurve.fromPosition(VolumeCurve.dragTo(0.9f, 5000f, width)))
    }
}

/** 1.9.4: el informe de Diagnóstico no publica la MAC ni la IP completas. */
class DiagnosticsReportTest {
    private val R = io.github.soundtouchradio.ui.DiagnosticsReport
    @Test fun masksPersonalData() {
        assertEquals("A0F6…3456", R.maskMac("A0:F6:FD:12:34:56"))
        assertEquals("—", R.maskMac(null))
        val out = R.build("SoundTouch Radio", listOf(io.github.soundtouchradio.ui.DiagnosticsReport.Line("IP", "192.168.1.20"), io.github.soundtouchradio.ui.DiagnosticsReport.Line("Control", "http://192.168.1.20:8091/AVTransport/Control")),
            listOf("09:02 GET http://192.168.1.20:8090/volume · HTTP 200"))
        assertFalse(out.contains("192.168.1.20"))
        assertTrue(out.contains("192.168.1.x:8091"))
        assertTrue(out.contains("http://192.168.1.x:8090/volume"))
    }
}

/** 1.9.5: puente de redirección y XML legible. */
class RedirectBridgeTest {
    @Test fun answersOnlyKnownRedirects() {
        val b = io.github.soundtouchradio.player.RedirectBridge({ java.net.InetAddress.getLoopbackAddress() }, port = 0)
        val url = b.urlFor("https://stream.example/ra")!!
        val path = url.substringAfter(":").substringAfter(":").substringAfter("/").let { "/$it" }
        val ok = b.respond("GET $path HTTP/1.1")
        assertTrue(ok.startsWith("HTTP/1.1 302"))
        assertTrue(ok.contains("Location: https://stream.example/ra\r\n"))
        assertTrue(b.respond("GET /r/ffff HTTP/1.1").startsWith("HTTP/1.1 404"))
        assertTrue(b.respond("GET /../etc HTTP/1.1").startsWith("HTTP/1.1 404"))
        // Y de verdad por la red: una conexión real recibe la redirección.
        java.net.Socket(java.net.InetAddress.getLoopbackAddress(), url.substringAfterLast(":").substringBefore("/").toInt()).use { s ->
            s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            assertTrue(s.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 302"))
        }
        b.stop()
    }

    /** 1.9.6: cliente que envía varias cabeceras y lee todo; recibe la respuesta completa y queda registrado. */
    @Test fun fullResponseWithHeadersAndIsLogged() {
        val hits = mutableListOf<String>()
        val b = io.github.soundtouchradio.player.RedirectBridge({ java.net.InetAddress.getLoopbackAddress() }, port = 0,
            onRequest = { path, status, _ -> synchronized(hits) { hits += "$path $status" } })
        val url = b.urlFor("https://stream.example/ra")!!
        val path = "/" + url.substringAfter("://").substringAfter("/")
        val port = url.substringAfterLast(":").substringBefore("/").toInt()
        repeat(20) {
            java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port).use { s ->
                s.getOutputStream().write(("GET $path HTTP/1.1\r\nHost: x\r\nUser-Agent: SoundTouch\r\nRange: bytes=0-\r\n" +
                    "Icy-MetaData: 1\r\nConnection: close\r\n\r\n").toByteArray())
                val all = s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
                assertTrue(all.startsWith("HTTP/1.1 302"))
                assertTrue(all.contains("Location: https://stream.example/ra"))
                assertTrue(all.endsWith("\r\n\r\n"))
            }
        }
        repeat(50) { if (synchronized(hits) { hits.size } < 20) Thread.sleep(20) }
        assertEquals(20, synchronized(hits) { hits.size })
        assertTrue(hits.all { it.endsWith(" 302") })
        b.stop()
    }

    @Test fun xmlAttributesGoOnTheirOwnLines() {
        val out = io.github.soundtouchradio.ui.screens.prettyXml("<a><ContentItem source=\"UPNP\" location=\"http://x/y\" isPresetable=\"true\"><itemName>z</itemName></ContentItem></a>")
        assertTrue(out.lines().any { it.trim() == "location=\"http://x/y\"" })
    }
}
