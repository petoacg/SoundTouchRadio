package io.github.soundtouchradio

import io.github.soundtouchradio.player.EqPlan
import io.github.soundtouchradio.player.EqSetting
import org.junit.Assert.*
import org.junit.Test

/** 1.10.2: ecualizador de «Este móvil». Bandas típicas de un Pixel: 60, 230, 910, 3600, 14000 Hz, ±15 dB. */
class EqPlanTest {
    private val centers = listOf(60, 230, 910, 3600, 14000)

    @Test fun neutralIsFlat() = assertEquals(listOf(0, 0, 0, 0, 0), EqPlan.levels(EqSetting(), centers, -1500, 1500))

    @Test fun presetsHaveTheIntendedShape() {
        val voz = EqPlan.levels(EqSetting(EqPlan.VOZ), centers, -1500, 1500)
        assertTrue(voz[0] < 0 && voz[2] > 0 && voz[3] > 0)            // menos retumbe, voces adelante
        val graves = EqPlan.levels(EqSetting(EqPlan.GRAVES), centers, -1500, 1500)
        assertTrue(graves[0] > graves[2] && graves[2] == 0)
        val noche = EqPlan.levels(EqSetting(EqPlan.NOCHE), centers, -1500, 1500)
        assertTrue(noche[0] < 0 && noche[4] < 0 && noche[2] == 0)
    }

    @Test fun personalIsClampedToTheDeviceRange() {
        val p = EqSetting(EqPlan.PERSONAL, listOf(3000, -3000, 0, 500, 0))
        assertEquals(listOf(1500, -1500, 0, 500, 0), EqPlan.levels(p, centers, -1500, 1500))
        // Si el móvil tiene otro número de bandas, «personal» vuelve a plano en vez de aplicar valores desfasados.
        assertEquals(listOf(0, 0, 0), EqPlan.levels(p, listOf(100, 1000, 10000), -1500, 1500))
    }

    @Test fun bandLabels() {
        assertEquals("60 Hz", EqPlan.bandLabel(60))
        assertEquals("3,6 kHz", EqPlan.bandLabel(3600))
        assertEquals("14 kHz", EqPlan.bandLabel(14000))
    }
}

/** 1.10.3: «Mío» se conserva y los nombres de salida se leen bien. */
class EqPlanMineTest {
    @Test fun mineSurvivesTryingAPreset() {
        val mine = EqSetting(EqPlan.PERSONAL, listOf(400, 200, 0, 300, -200))
        val tryVoz = mine.copy(preset = EqPlan.VOZ)
        assertEquals(mine.levels, tryVoz.levels)
        assertEquals(listOf(400, 200, 0, 300, -200), EqPlan.levels(tryVoz.copy(preset = EqPlan.PERSONAL), listOf(60, 230, 910, 3600, 14000), -1500, 1500))
    }

    @Test fun prettyOutputNames() {
        assertEquals("Edifier R1855DB", EqPlan.prettyName("EDIFIER R1855DB"))
        assertEquals("Pixel Buds Pro", EqPlan.prettyName("Pixel Buds Pro"))
        assertEquals("JBL Flip 5", EqPlan.prettyName("JBL FLIP 5"))
    }
}
