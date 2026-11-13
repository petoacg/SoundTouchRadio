package io.github.soundtouchradio.data

/**
 * Emisoras precargadas en el primer arranque (presets 1–6). URLs elegidas por ser http directas
 * (MP3/AAC, sin HLS), que es lo que el renderer UPnP del SoundTouch reproduce según la comunidad.
 * Evidencia de cada una en README.md («Emisoras precargadas»). Se pueden editar o borrar.
 */
object Seed {
    const val PRESET_COUNT = 10

    val stations = listOf(
        Station(id = "seed-ondacero", name = "Onda Cero", url = "http://playerservices.streamtheworld.com/api/livestream-redirect/OCAAC.aac", codec = "AAC"),
        Station(id = "seed-rne", name = "RNE", url = "http://dispatcher.rndfnk.com/crtve/rne1/main/mp3/high", codec = "MP3", bitrateKbps = 128),
        Station(id = "seed-rne5", name = "RNE Radio 5", url = "http://dispatcher.rndfnk.com/crtve/rne5/main/mp3/high", codec = "MP3", bitrateKbps = 128),
        Station(id = "seed-ser", name = "Cadena SER", url = "http://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER.mp3", codec = "MP3", bitrateKbps = 128),
        Station(id = "seed-cope", name = "COPE", url = "http://flucast12-h-cloud.flumotion.com/cope/net1.mp3", codec = "MP3", bitrateKbps = 96),
        Station(id = "seed-esradio", name = "esRadio", url = "http://livestreaming.esradio.fm/stream64.mp3", codec = "MP3", bitrateKbps = 56),
    )

    val presets: List<String?> = stations.map { it.id } + List(PRESET_COUNT - stations.size) { null }
}
