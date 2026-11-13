package io.github.soundtouchradio

/** Respuestas con la forma del documento oficial de Bose (SoundTouch API WebServices v1.1.0). */
object Fixtures {
    fun info(name: String = "Salón") = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <info deviceID="A0F6FD123456">
          <name>${name.replace("&", "&amp;")}</name>
          <type>SoundTouch 10</type>
          <margeAccountUUID>1234</margeAccountUUID>
          <components>
            <component><componentCategory>SCM</componentCategory><softwareVersion>27.0.6.46330.5043500 epdbuild.trunk.hepdswbld04.2022-08-04T11:20:29</softwareVersion><serialNumber>X</serialNumber></component>
          </components>
          <margeURL>https://streaming.bose.com</margeURL>
          <networkInfo type="SCM"><macAddress>A0F6FD123456</macAddress><ipAddress>192.168.1.20</ipAddress></networkInfo>
        </info>
    """

    fun nowPlaying(source: String, status: String? = null, item: String? = null) = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <nowPlaying deviceID="A0F6FD123456" source="$source">
          ${if (item != null) "<ContentItem source=\"$source\" location=\"http://x\" isPresetable=\"false\"><itemName>$item</itemName></ContentItem>" else "<ContentItem source=\"$source\" />"}
          ${if (status != null) "<playStatus>$status</playStatus>" else ""}
        </nowPlaying>
    """

    fun volume(v: Int, muted: Boolean = false) = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <volume deviceID="A0F6FD123456"><targetvolume>$v</targetvolume><actualvolume>$v</actualvolume><muteenabled>$muted</muteenabled></volume>
    """

    const val SOAP_OK = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:PlayResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/></s:Body></s:Envelope>"""

    const val SOAP_ACTIONS = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetCurrentTransportActionsResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><Actions>Play,Stop</Actions></u:GetCurrentTransportActionsResponse></s:Body></s:Envelope>"""

    const val SOAP_FAULT = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>714</errorCode><errorDescription>Illegal MIME-type</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""
}
