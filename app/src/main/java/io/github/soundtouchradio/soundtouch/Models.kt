package io.github.soundtouchradio.soundtouch

data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val type: String,
    val ipAddress: String?,
    val macAddress: String?,
    val softwareVersion: String?,
) {
    /** Caracteres que, según informes de la comunidad, impiden arrancar el renderer UPnP. */
    val nameBreaksRenderer: Boolean get() = name.any { it == '&' || it == '<' || it == '>' }
}

enum class PlayStatus { PLAY, PAUSE, STOP, BUFFERING, INVALID, UNKNOWN;
    companion object {
        fun parse(s: String?): PlayStatus = when (s) {
            "PLAY_STATE" -> PLAY
            "PAUSE_STATE" -> PAUSE
            "STOP_STATE" -> STOP
            "BUFFERING_STATE" -> BUFFERING
            "INVALID_PLAY_STATUS" -> INVALID
            else -> UNKNOWN
        }
    }
}

data class NowPlaying(
    val source: String,
    val playStatus: PlayStatus,
    val itemName: String?,
    val stationName: String?,
    val track: String?,
    val artist: String?,
    val location: String?,
    val raw: String,
) {
    val isStandby: Boolean get() = source == "STANDBY"
    val isUpnp: Boolean get() = source == "UPNP"
    /** Fuente nativa de radio/streaming de Bose que puede tragarse órdenes UPnP. */
    val isNativeStreaming: Boolean get() = source in NATIVE_STREAMING

    companion object {
        val NATIVE_STREAMING = setOf(
            "TUNEIN", "INTERNET_RADIO", "LOCAL_INTERNET_RADIO", "SIRIUSXM", "PANDORA",
            "SPOTIFY", "DEEZER", "AMAZON", "IHEART", "RADIOPLAYER", "STORED_MUSIC",
        )
    }
}

data class Volume(val target: Int, val actual: Int, val muted: Boolean)

data class ZoneMember(val ipAddress: String?, val macAddress: String)
data class Zone(val masterMac: String?, val members: List<ZoneMember>)

data class SoundTouchError(val value: String?, val name: String?, val message: String?)
