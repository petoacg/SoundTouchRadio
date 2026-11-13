package io.github.soundtouchradio

import io.github.soundtouchradio.discovery.explainSecurity
import io.github.soundtouchradio.soundtouch.SoundTouchXml
import io.github.soundtouchradio.soundtouch.Xml
import org.junit.Assert.*
import org.junit.Test

class ConnectionFixesTest {
    /** Mensaje literal que mostró el Pixel 10 (1.4.0): no debe atribuirse a «Dispositivos cercanos». */
    @Test fun missingNetworkStateIsNotBlamedOnNearbyDevices() {
        val m = explainSecurity(SecurityException("io.github.soundtouchradio was not granted either of these permissions: " +
            "android.permission.CHANGE_NETWORK_STATE,android.permission.WRITE_SETTINGS."))
        assertFalse(m.contains("Dispositivos cercanos"))
        assertTrue(m.startsWith("Android no dejó a la app pedir la conexión"))
        assertTrue(m.contains("«Bose…»"))   // ofrece la otra puerta, sin nombrar un modelo concreto
    }

    @Test fun nearbyDevicesDenialIsExplained() {
        assertTrue(explainSecurity(SecurityException("requires android.permission.NEARBY_WIFI_DEVICES")).contains("Dispositivos cercanos"))
    }

    @Test fun nameBodyIsEscapedAndTrimmed() {
        val body = SoundTouchXml.nameBody("  Salón \"grande\" ")
        assertEquals("Salón \"grande\"", Xml.parse(body).documentElement.textContent)
    }

    @Test fun nameValidation() {
        assertNull(SoundTouchXml.validateName("Salón"))
        assertNotNull(SoundTouchXml.validateName("  "))
        assertNotNull(SoundTouchXml.validateName("Salón & cocina"))   // rompería el renderer UPnP
        assertNotNull(SoundTouchXml.validateName("x".repeat(33)))
    }
}
