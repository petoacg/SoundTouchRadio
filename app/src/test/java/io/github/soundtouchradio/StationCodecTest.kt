package io.github.soundtouchradio

import io.github.soundtouchradio.data.Station
import io.github.soundtouchradio.data.StationCodec
import org.junit.Assert.*
import org.junit.Test

class StationCodecTest {
    private val stations = listOf(Station(name = "Radio Uno", url = "http://a.example/1"), Station(name = "Dos, con coma", url = "https://b.example/2.aac"))

    @Test fun jsonRoundTrip() {
        val r = StationCodec.import(StationCodec.toJson(stations)) as StationCodec.ImportResult.Ok
        assertEquals(stations.map { it.name to it.url }, r.stations.map { it.name to it.url })
    }

    @Test fun m3uRoundTrip() {
        val r = StationCodec.import(StationCodec.toM3u(stations)) as StationCodec.ImportResult.Ok
        assertEquals(stations.map { it.name to it.url }, r.stations.map { it.name to it.url })
    }

    @Test fun importsPlsWithTitles() {
        val r = StationCodec.import("[playlist]\nFile1=http://a.example/x\nTitle1=Equis\n") as StationCodec.ImportResult.Ok
        assertEquals("Equis", r.stations.single().name)
    }

    @Test fun skipsInvalidAndNamesUntitled() {
        val r = StationCodec.import("ftp://nope\nhttp://radio.example:8000/live\n") as StationCodec.ImportResult.Ok
        assertEquals(1, r.skipped)
        assertEquals("radio.example", r.stations.single().name)
    }

    @Test fun rejectsForeignJsonAndHls() {
        assertTrue(StationCodec.import("""{"format":"otra-cosa","stations":[]}""") is StationCodec.ImportResult.Error)
        assertTrue(StationCodec.import("#EXTM3U\n#EXT-X-TARGETDURATION:6\na.ts") is StationCodec.ImportResult.Error)
        assertTrue(StationCodec.import("   ") is StationCodec.ImportResult.Error)
    }
}
