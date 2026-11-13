package io.github.soundtouchradio

import io.github.soundtouchradio.discovery.AUTO_FAILED
import io.github.soundtouchradio.discovery.explainSecurity
import io.github.soundtouchradio.discovery.pollUntil
import io.github.soundtouchradio.soundtouch.DeviceInfo
import io.github.soundtouchradio.soundtouch.WifiSetupXml
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 1.6.0: conexión con el altavoz como la app de Bose (panel de Wi-Fi + «Sí») y detección continua. */
class WifiJoinTest {
    private fun info(name: String, type: String = "SoundTouch 10") =
        DeviceInfo(deviceId = "A1", name = name, type = type, ipAddress = null, macAddress = null, softwareVersion = null)

    /** Android tarda en dar IP: la red aparece al cuarto intento (6 s) y se detecta, no se da por perdida. */
    @Test fun detectionKeepsTryingUntilTheNetworkAppears() = runTest {
        var tries = 0
        val found = pollUntil(180_000, 2_000) { tries++; if (tries >= 4) "red" else null }
        assertEquals("red", found)
        assertEquals(4, tries)
        assertEquals(6_000, currentTime)
    }

    @Test fun detectionGivesUpAfterTheTimeout() = runTest {
        var tries = 0
        assertNull(pollUntil(180_000, 2_000) { tries++; null })
        assertEquals(91, tries)            // 0 s, 2 s … 180 s
        assertEquals(180_000, currentTime)
    }

    @Test fun factoryNameBecomesModel() {
        assertEquals("SoundTouch 10", WifiSetupXml.suggestedName(info("Bose SoundTouch 1A2B3C")))
        assertEquals("Salón", WifiSetupXml.suggestedName(info("Salón")))
        assertEquals("SoundTouch 10", WifiSetupXml.suggestedName(info("  ")))
        // 1.9.3: nombres de fábrica de otros modelos y el modelo que informa el propio altavoz.
        assertEquals("SoundTouch 20", WifiSetupXml.suggestedName(info("Bose SoundTouch 20 A1B2C3", type = "SoundTouch 20")))
        assertEquals("SoundTouch 300", WifiSetupXml.suggestedName(info("Bose SoundTouch 9F8E7D", type = "SoundTouch 300")))
    }

    /** El error ya no dice «no se encontró la red» (sí se encontró) y manda a la vía que funciona. */
    @Test fun messagesPointToTheWifiPanel() {
        assertFalse(AUTO_FAILED.contains("No se encontró"))
        assertTrue(AUTO_FAILED.contains("Abrir Wi-Fi"))
        assertTrue(explainSecurity(SecurityException("requires android.permission.NEARBY_WIFI_DEVICES")).contains("Abrir Wi-Fi"))
    }
}
