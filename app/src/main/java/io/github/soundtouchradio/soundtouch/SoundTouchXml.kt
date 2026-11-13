package io.github.soundtouchradio.soundtouch

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

/** Parsers y constructores XML de la API web local (puerto 8090), según el documento oficial v1.1.0. */
object SoundTouchXml {

    fun parseInfo(xml: String): DeviceInfo {
        val root = Xml.parse(xml).documentElement
        require(root.tagName == "info") { Txt.s(R.string.ui_respuesta_info_inesperada, root.tagName) }
        val net = root.getElementsByTagName("networkInfo")
        var ip: String? = null
        var mac: String? = null
        for (i in 0 until net.length) {
            val e = net.item(i) as org.w3c.dom.Element
            // Preferimos la interfaz SCM/Wi-Fi si hay varias.
            val thisIp = e.childText("ipAddress")
            val thisMac = e.childText("macAddress")
            if (ip == null || e.attr("type") == "SCM") { ip = thisIp ?: ip; mac = thisMac ?: mac }
        }
        val sw = root.getElementsByTagName("component").let { list ->
            (0 until list.length).map { list.item(it) as org.w3c.dom.Element }
                .firstOrNull { it.childText("componentCategory") == "SCM" }
                ?: (if (list.length > 0) list.item(0) as org.w3c.dom.Element else null)
        }?.childText("softwareVersion")
        return DeviceInfo(
            deviceId = root.attr("deviceID") ?: mac.orEmpty(),
            name = root.childText("name") ?: "SoundTouch",
            type = root.childText("type") ?: "SoundTouch",
            ipAddress = ip,
            macAddress = mac ?: root.attr("deviceID"),
            softwareVersion = sw,
        )
    }

    fun parseNowPlaying(xml: String): NowPlaying {
        val root = Xml.parse(xml).documentElement
        require(root.tagName == "nowPlaying") { Txt.s(R.string.ui_respuesta_now_playing_inesperada, root.tagName) }
        val content = root.child("ContentItem")
        return NowPlaying(
            source = root.attr("source") ?: "UNKNOWN",
            playStatus = PlayStatus.parse(root.childText("playStatus")),
            itemName = content?.childText("itemName"),
            stationName = root.childText("stationName"),
            track = root.childText("track"),
            artist = root.childText("artist"),
            location = content?.attr("location"),
            raw = xml.trim(),
        )
    }

    fun parseVolume(xml: String): Volume {
        val root = Xml.parse(xml).documentElement
        require(root.tagName == "volume") { Txt.s(R.string.ui_respuesta_volume_inesperada, root.tagName) }
        val actual = root.childText("actualvolume")?.toIntOrNull() ?: 0
        return Volume(
            target = root.childText("targetvolume")?.toIntOrNull() ?: actual,
            actual = actual,
            muted = root.childText("muteenabled") == "true",
        )
    }

    fun parseZone(xml: String): Zone {
        val root = Xml.parse(xml).documentElement
        val members = root.children("member").map {
            ZoneMember(it.attr("ipaddress"), it.textContent.trim().trim('"'))
        }
        return Zone(root.attr("master"), members)
    }

    /** Devuelve el error si la respuesta es un <errors>/<error>, o null si no lo es. */
    fun parseError(xml: String): SoundTouchError? {
        val root = runCatching { Xml.parse(xml).documentElement }.getOrNull() ?: return null
        return when (root.tagName) {
            "errors" -> root.child("error").let {
                SoundTouchError(it?.attr("value"), it?.attr("name"), it?.textContent?.trim())
            }
            "error" -> SoundTouchError(null, null, root.textContent.trim())
            else -> null
        }
    }

    /** /name (documento oficial 6.15). */
    fun nameBody(name: String): String = "<name>${Xml.escape(name.trim())}</name>"

    /** Error de validación del nombre del altavoz, o null si vale. & < > rompen el renderer UPnP (comunidad). */
    fun validateName(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> Txt.s(R.string.ui_escribe_un_nombre)
            n.length > 32 -> Txt.s(R.string.ui_maximo_32_caracteres)
            n.any { it == '&' || it == '<' || it == '>' } -> Txt.s(R.string.ui_sin_impiden_que_el_altavoz)
            else -> null
        }
    }

    fun volumeBody(level: Int): String = "<volume>${level.coerceIn(0, 100)}</volume>"

    fun keyBody(key: String, state: String): String =
        "<key state=\"$state\" sender=\"Gabbo\">${Xml.escape(key)}</key>"

    fun zoneBody(masterMac: String, senderIp: String?, members: List<ZoneMember>): String = buildString {
        append("<zone master=\"").append(Xml.escape(masterMac)).append('"')
        if (senderIp != null) append(" senderIPAddress=\"").append(Xml.escape(senderIp)).append('"')
        append('>')
        for (m in members) {
            append("<member")
            if (m.ipAddress != null) append(" ipaddress=\"").append(Xml.escape(m.ipAddress)).append('"')
            append('>').append(Xml.escape(m.macAddress)).append("</member>")
        }
        append("</zone>")
    }
}
