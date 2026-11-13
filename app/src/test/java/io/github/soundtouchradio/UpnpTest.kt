package io.github.soundtouchradio

import io.github.soundtouchradio.soundtouch.Xml
import io.github.soundtouchradio.upnp.DeviceDescription
import io.github.soundtouchradio.upnp.Didl
import io.github.soundtouchradio.upnp.Soap
import org.junit.Assert.*
import org.junit.Test

class UpnpTest {
    @Test fun envelopeEscapesArgumentsAndDidlIsDoubleEscaped() {
        val didl = Didl.broadcast("http://r.example/live?a=1&b=2", "Rock & Pop", "audio/mpeg")
        val env = Soap.envelope("SetAVTransportURI", Soap.AVT, listOf("InstanceID" to "0", "CurrentURI" to "http://r.example/live?a=1&b=2", "CurrentURIMetaData" to didl))
        assertTrue(env.contains("<CurrentURI>http://r.example/live?a=1&amp;b=2</CurrentURI>"))
        assertTrue(env.contains("&lt;dc:title&gt;Rock &amp;amp; Pop&lt;/dc:title&gt;"))
        // El sobre debe ser XML válido y el DIDL, una vez desescapado, también.
        val doc = Xml.parse(env, namespaceAware = true)
        val meta = doc.getElementsByTagName("CurrentURIMetaData").item(0).textContent
        val didlDoc = Xml.parse(meta, namespaceAware = true)
        assertEquals("Rock & Pop", didlDoc.getElementsByTagNameNS("*", "title").item(0).textContent)
        assertEquals("http-get:*:audio/mpeg:*",
            (didlDoc.getElementsByTagNameNS("*", "res").item(0) as org.w3c.dom.Element).getAttribute("protocolInfo"))
    }

    @Test fun soapActionHeaderIsQuoted() {
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Play\"", Soap.soapActionHeader(Soap.AVT, "Play"))
    }

    @Test fun parsesFault() {
        val f = Soap.parseFault(Fixtures.SOAP_FAULT)!!
        assertEquals("714", f.code); assertEquals("Illegal MIME-type", f.description)
        assertNull(Soap.parseFault(Fixtures.SOAP_OK))
    }

    @Test fun parsesActionResponse() {
        assertEquals("Play,Stop", Soap.parseResponse(Fixtures.SOAP_ACTIONS)["Actions"])
    }

    @Test fun readsControlUrlFromDescription() {
        val xml = """<?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
              <friendlyName>Salón</friendlyName><manufacturer>Bose Corporation</manufacturer><modelName>SoundTouch 10</modelName>
              <serviceList>
                <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/RenderingControl/Control</controlURL></service>
                <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>AVTransport/Control</controlURL></service>
              </serviceList></device></root>"""
        val d = DeviceDescription.parse(xml, "http://192.168.1.20:8091/XD/BO5EBO5E-F00D-F00D-FEED-A0F6FD123456.xml")
        assertEquals("http://192.168.1.20:8091/XD/AVTransport/Control", d.avTransportControlUrl)
        assertEquals("http://192.168.1.20:8091/RenderingControl/Control", d.renderingControlUrl)
        assertEquals("SoundTouch 10", d.modelName)
    }

    @Test fun defaultControlUrl() {
        assertEquals("http://192.168.1.20:8091/AVTransport/Control", DeviceDescription.defaultControlUrl("192.168.1.20"))
    }
}
