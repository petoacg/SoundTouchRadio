package io.github.soundtouchradio

import io.github.soundtouchradio.soundtouch.PlayStatus
import io.github.soundtouchradio.soundtouch.SoundTouchXml
import io.github.soundtouchradio.soundtouch.ZoneMember
import org.junit.Assert.*
import org.junit.Test

class SoundTouchXmlTest {
    @Test fun parsesInfo() {
        val i = SoundTouchXml.parseInfo(Fixtures.info())
        assertEquals("Salón", i.name)
        assertEquals("SoundTouch 10", i.type)
        assertEquals("192.168.1.20", i.ipAddress)
        assertEquals("A0F6FD123456", i.macAddress)
        assertTrue(i.softwareVersion!!.startsWith("27.0.6"))
        assertFalse(i.nameBreaksRenderer)
    }

    @Test fun flagsNamesThatBreakTheRenderer() {
        assertTrue(SoundTouchXml.parseInfo(Fixtures.info("Salón & cocina")).nameBreaksRenderer)
    }

    @Test fun parsesNowPlayingUpnp() {
        val np = SoundTouchXml.parseNowPlaying(Fixtures.nowPlaying("UPNP", "PLAY_STATE", "Radio 3"))
        assertTrue(np.isUpnp)
        assertEquals(PlayStatus.PLAY, np.playStatus)
        assertEquals("Radio 3", np.itemName)
    }

    @Test fun parsesStandbyWithoutPlayStatus() {
        val np = SoundTouchXml.parseNowPlaying(Fixtures.nowPlaying("STANDBY"))
        assertTrue(np.isStandby)
        assertEquals(PlayStatus.UNKNOWN, np.playStatus)
    }

    @Test fun parsesVolume() {
        val v = SoundTouchXml.parseVolume(Fixtures.volume(35, muted = true))
        assertEquals(35, v.actual); assertTrue(v.muted)
    }

    @Test fun parsesZone() {
        val z = SoundTouchXml.parseZone(
            """<zone master="AAA"><member ipaddress="192.168.1.20">AAA</member><member ipaddress="192.168.1.21">BBB</member></zone>"""
        )
        assertEquals("AAA", z.masterMac)
        assertEquals(listOf("AAA", "BBB"), z.members.map { it.macAddress })
    }

    @Test fun parsesErrors() {
        val e = SoundTouchXml.parseError(
            """<errors deviceID="D05FB8A9591D"><error value="1019" name="CLIENT_XML_ERROR" severity="Unknown">1019</error></errors>"""
        )
        assertEquals("CLIENT_XML_ERROR", e!!.name)
        assertNull(SoundTouchXml.parseError("<status>/volume</status>"))
    }

    @Test fun buildsBodies() {
        assertEquals("<volume>100</volume>", SoundTouchXml.volumeBody(140))
        assertEquals("<key state=\"press\" sender=\"Gabbo\">MUTE</key>", SoundTouchXml.keyBody("MUTE", "press"))
        assertEquals(
            "<zone master=\"AAA\" senderIPAddress=\"192.168.1.20\"><member ipaddress=\"192.168.1.21\">BBB</member></zone>",
            SoundTouchXml.zoneBody("AAA", "192.168.1.20", listOf(ZoneMember("192.168.1.21", "BBB")))
        )
    }
}

/** /info con la estructura real de un SoundTouch 10 (dos networkInfo). MAC, números de serie e IP son ficticios. */
class RealDeviceInfoTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<info deviceID="0011223344AA"><name>SoundTouch 10</name><type>SoundTouch 10</type><margeAccountUUID></margeAccountUUID>
<components><component><componentCategory>SCM</componentCategory><softwareVersion>27.0.6.46330.5043500 epdbuild.trunk.hepdswbld04.2022-08-04T11:20:29</softwareVersion><serialNumber>P0000000000000000000000</serialNumber></component>
<component><componentCategory>PackagedProduct</componentCategory><softwareVersion>27.0.6.46330.5043500 epdbuild.trunk.hepdswbld04.2022-08-04T11:20:29</softwareVersion><serialNumber>000000P00000000AA</serialNumber></component></components>
<margeURL>https://streaming.bose.com</margeURL>
<networkInfo type="SCM"><macAddress>0011223344AA</macAddress><ipAddress>192.168.1.20</ipAddress></networkInfo>
<networkInfo type="SMSC"><macAddress>0011223344BB</macAddress><ipAddress>192.168.1.20</ipAddress></networkInfo>
<moduleType>sm2</moduleType><variant>rhino</variant><variantMode>normal</variantMode><countryCode>GB</countryCode><regionCode>GB</regionCode></info>"""

    @org.junit.Test fun parsesTwoInterfaces() {
        val i = io.github.soundtouchradio.soundtouch.SoundTouchXml.parseInfo(xml)
        org.junit.Assert.assertEquals("SoundTouch 10", i.name)
        org.junit.Assert.assertEquals("192.168.1.20", i.ipAddress)
        org.junit.Assert.assertEquals("0011223344AA", i.macAddress)
        org.junit.Assert.assertFalse(i.nameBreaksRenderer)
    }

    @org.junit.Test fun explainsNetworkErrors() {
        val L = io.github.soundtouchradio.discovery.Linker
        org.junit.Assert.assertTrue(L.explain(java.net.SocketException("connect failed: EPERM (Operation not permitted)")).startsWith("Android impide"))
        org.junit.Assert.assertTrue(L.explain(io.github.soundtouchradio.soundtouch.SoundTouchException("x", cause = java.net.SocketTimeoutException("timeout"))).startsWith("No responde a tiempo"))
        org.junit.Assert.assertTrue(L.explain(java.net.ConnectException("refused")).contains("[ConnectException: refused]"))
    }
}
