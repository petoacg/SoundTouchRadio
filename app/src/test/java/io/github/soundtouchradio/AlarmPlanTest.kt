package io.github.soundtouchradio

import io.github.soundtouchradio.data.AlarmSettings
import io.github.soundtouchradio.player.AlarmPlan
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmPlanTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, madrid)
    private val weekdays = AlarmSettings(enabled = true, hour = 7, minute = 30, days = setOf(1, 2, 3, 4, 5))

    @Test fun disabledHasNoTrigger() = assertNull(AlarmPlan.nextTrigger(weekdays.copy(enabled = false), at(2026, 11, 9, 6, 0)))

    @Test fun laterTodayOnAWeekday() {
        // lunes 9-nov-2026 06:00 → hoy 07:30
        assertEquals(at(2026, 11, 9, 7, 30), AlarmPlan.nextTrigger(weekdays, at(2026, 11, 9, 6, 0)))
    }

    @Test fun skipsTheWeekend() {
        // viernes 13-nov 08:00 (ya pasó) → lunes 16-nov 07:30
        assertEquals(at(2026, 11, 16, 7, 30), AlarmPlan.nextTrigger(weekdays, at(2026, 11, 13, 8, 0)))
    }

    @Test fun oneShotIsTodayOrTomorrow() {
        val once = weekdays.copy(days = emptySet())
        assertEquals(at(2026, 11, 8, 7, 30), AlarmPlan.nextTrigger(once, at(2026, 11, 8, 7, 0)))
        assertEquals(at(2026, 11, 9, 7, 30), AlarmPlan.nextTrigger(once, at(2026, 11, 8, 7, 30)))
    }

    @Test fun springForwardGapIsResolved() {
        // 29-mar-2026: en Madrid de 02:00 se salta a 03:00. Una alarma a las 02:30 suena a las 03:30 (hora válida).
        val a = AlarmSettings(enabled = true, hour = 2, minute = 30, days = emptySet())
        val next = AlarmPlan.nextTrigger(a, at(2026, 3, 29, 1, 0))!!
        assertEquals(29, next.dayOfMonth); assertEquals(3, next.hour)
    }

    @Test fun fadeInRisesToTarget() {
        val s = AlarmPlan.fadeInSteps(20)
        assertEquals(12, s.size); assertEquals(20, s.last()); assertTrue(s.zipWithNext().all { (x, y) -> y >= x }); assertTrue(s.first() >= 5)
        assertEquals(3, AlarmPlan.fadeInSteps(3).last())
    }

    @Test fun labels() {
        assertEquals("L–V", AlarmPlan.daysLabel(setOf(1, 2, 3, 4, 5)))
        assertEquals("S y D", AlarmPlan.daysLabel(setOf(6, 7)))
        assertEquals("Una vez", AlarmPlan.daysLabel(emptySet()))
        assertEquals("L X V", AlarmPlan.daysLabel(setOf(5, 1, 3)))
        assertEquals("Sonará mañana a las 7:30 (en 9\u00A0h 30\u00A0min)", AlarmPlan.nextLabel(at(2026, 11, 9, 7, 30), at(2026, 11, 8, 22, 0)))
    }
}
