package io.github.soundtouchradio

import io.github.soundtouchradio.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun firstRunSeedsSixStationsAndPresets() {
        val dir = tmp.newFolder()
        val stations = StationRepository(dir).stations.value
        assertEquals(listOf("Onda Cero", "RNE", "RNE Radio 5", "Cadena SER", "COPE", "esRadio"), stations.map { it.name })
        assertTrue("todas http directas", stations.all { it.url.startsWith("http://") && !it.url.contains(".m3u8") })
        val presets = SettingsRepository(dir).settings.value.presets
        assertEquals(10, presets.size)
        assertEquals(stations.map { it.id }, presets.take(6))
        assertTrue(presets.drop(6).all { it == null })
    }

    @Test fun deletingDoesNotReseedAndPersists() {
        val dir = tmp.newFolder()
        val repo = StationRepository(dir)
        repo.stations.value.forEach { repo.delete(it.id) }
        assertTrue(run { JsonFile.drain(); StationRepository(dir).stations.value.isEmpty() })
    }

    @Test fun presetsAssignAndClear() {
        val dir = tmp.newFolder()
        val s = SettingsRepository(dir)
        s.assignPreset(9, "x")
        s.clearPresetsFor("seed-ser")
        JsonFile.drain()
        val p = SettingsRepository(dir).settings.value.presets
        assertEquals("x", p[9]); assertNull(p[3])
    }

    @Test fun importSkipsDuplicatesAndUndoRestoresPosition() {
        val dir = tmp.newFolder()
        val repo = StationRepository(dir)
        assertEquals(1, repo.importAll(listOf(Station(name = "SER", url = Seed.stations[3].url), Station(name = "Nueva", url = "http://n.example/a"))))
        val (idx, removed) = repo.delete("seed-rne")!!
        repo.restore(idx, removed)
        assertEquals("seed-rne", repo.stations.value[1].id)
        repo.move("seed-rne", -1)
        assertEquals("seed-rne", repo.stations.value[0].id)
    }

    @Test fun speakersAreKeyedByMacAndUpdateIp() {
        val dir = tmp.newFolder()
        val r = SpeakerRepository(dir)
        r.remember(SpeakerRecord(host = "192.168.1.20", name = "Salón", mac = "AAA", controlUrl = "http://192.168.1.20:8091/X"))
        r.remember(SpeakerRecord(host = "192.168.1.33", name = "Salón", mac = "AAA"))
        JsonFile.drain()
        val list = SpeakerRepository(dir).speakers.value
        assertEquals(1, list.size)
        assertEquals("192.168.1.33", list[0].host)
        assertEquals("http://192.168.1.33:8091/X", list[0].controlUrl)
    }
}
