package io.github.soundtouchradio

import io.github.soundtouchradio.data.AlarmSettings
import io.github.soundtouchradio.data.OnceAlarm
import io.github.soundtouchradio.player.AlarmPlan
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** 1.7.0: alarma «Una vez» que convive con la semanal sin tocarla. */
class OnceAlarmTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, madrid)
    private val weekly = AlarmSettings(enabled = true, hour = 7, minute = 30, days = setOf(1, 2, 3, 4, 5))
    // Lunes 9 de noviembre de 2026, 1:19
    private val now = at(2026, 11, 9, 1, 19)

    @Test fun onceRingsOnItsDate() =
        assertEquals(at(2026, 11, 14, 9, 0), AlarmPlan.nextOnce(OnceAlarm(true, "2026-11-14", 9, 0), now))

    @Test fun pastOrDisabledOnceDoesNotRing() {
        assertNull(AlarmPlan.nextOnce(OnceAlarm(true, "2026-11-09", 1, 0), now))
        assertNull(AlarmPlan.nextOnce(OnceAlarm(false, "2026-11-14", 9, 0), now))
        assertNull(AlarmPlan.nextOnce(OnceAlarm(true, null, 9, 0), now))
    }

    /** Un sábado suelto: suena antes la de una vez y la rutina semanal no cambia. */
    @Test fun earlierOnceWinsAndWeeklyStaysAsIs() {
        val up = AlarmPlan.nextAlarm(weekly, OnceAlarm(true, "2026-11-09", 6, 15), now)!!
        assertEquals(AlarmPlan.Kind.ONCE, up.kind)
        assertEquals(at(2026, 11, 9, 6, 15), up.at)
        // Tras sonar (6:16), la siguiente es la semanal de ese mismo lunes.
        val after = AlarmPlan.nextAlarm(weekly, OnceAlarm(false, "2026-11-09", 6, 15), at(2026, 11, 9, 6, 16))!!
        assertEquals(AlarmPlan.Kind.WEEKLY, after.kind)
        assertEquals(at(2026, 11, 9, 7, 30), after.at)
    }

    @Test fun laterOnceWaitsForWeekly() {
        val up = AlarmPlan.nextAlarm(weekly, OnceAlarm(true, "2026-11-14", 9, 0), now)!!
        assertEquals(AlarmPlan.Kind.WEEKLY, up.kind)
    }

    /** Misma hora: suena una sola vez, como «Una vez». */
    @Test fun sameMinuteRingsOnce() {
        val up = AlarmPlan.nextAlarm(weekly, OnceAlarm(true, "2026-11-09", 7, 30), now)!!
        assertEquals(AlarmPlan.Kind.ONCE, up.kind)
        assertEquals(AlarmPlan.Kind.WEEKLY, AlarmPlan.nextAlarm(weekly, OnceAlarm(false, "2026-11-09", 7, 30), at(2026, 11, 9, 7, 31))!!.kind)
        assertEquals(at(2026, 11, 10, 7, 30), AlarmPlan.nextAlarm(weekly, OnceAlarm(false), at(2026, 11, 9, 7, 31))!!.at)
    }

    @Test fun labels() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("mañana, martes 10 de noviembre", AlarmPlan.dateLabel(LocalDate.of(2026, 11, 10), today))
        assertEquals("viernes 1 de enero de 2027", AlarmPlan.dateLabel(LocalDate.of(2027, 1, 1), today))
        assertEquals("2026-11-10", AlarmPlan.defaultOnceDate(now))
        assertTrue(AlarmPlan.nextLabel(at(2026, 11, 24, 9, 0), now).startsWith("Sonará el martes 24 de noviembre a las 9:00 (en 15\u00A0días)"))
    }
}
