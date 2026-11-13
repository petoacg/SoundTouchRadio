package io.github.soundtouchradio.player

import io.github.soundtouchradio.R
import io.github.soundtouchradio.ui.Txt

import io.github.soundtouchradio.data.AlarmSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Reglas puras del despertador (probadas en la JVM). */
object AlarmPlan {
    const val FADE_IN_MS = 60_000L
    const val FADE_IN_STEPS = 12
    const val START_VOLUME = 5
    const val WAKE_ATTEMPTS = 3
    const val RETRY_MS = 20_000L

    /** Próxima vez que debe sonar, o null si está desactivado. Respeta zona horaria y cambios de hora. */
    fun nextTrigger(a: AlarmSettings, now: ZonedDateTime): ZonedDateTime? {
        if (!a.enabled) return null
        val zone: ZoneId = now.zone
        val today = now.toLocalDate()
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (a.days.isNotEmpty() && date.dayOfWeek.value !in a.days) continue
            // atZone resuelve horas inexistentes (cambio de hora de marzo) avanzando lo necesario.
            val candidate = LocalDateTime.of(date, java.time.LocalTime.of(a.hour, a.minute)).atZone(zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /** Próxima (y única) vez de la alarma «Una vez», o null si está apagada, sin fecha o ya pasó. */
    fun nextOnce(o: io.github.soundtouchradio.data.OnceAlarm, now: ZonedDateTime): ZonedDateTime? {
        if (!o.enabled) return null
        val date = o.date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
        val at = LocalDateTime.of(date, java.time.LocalTime.of(o.hour, o.minute)).atZone(now.zone)
        return at.takeIf { it.isAfter(now) }
    }

    enum class Kind { WEEKLY, ONCE }
    data class Upcoming(val kind: Kind, val at: ZonedDateTime)

    /** La que suena antes. Si coinciden en el mismo minuto, gana «Una vez» y suena una sola vez. */
    fun nextAlarm(weekly: AlarmSettings, once: io.github.soundtouchradio.data.OnceAlarm, now: ZonedDateTime): Upcoming? {
        val w = nextTrigger(weekly, now)?.let { Upcoming(Kind.WEEKLY, it) }
        val o = nextOnce(once, now)?.let { Upcoming(Kind.ONCE, it) }
        return listOfNotNull(o, w).minByOrNull { it.at.toEpochSecond() }   // estable: «Una vez» primero en empate
    }

    /** Fecha por defecto de la alarma «Una vez»: mañana. */
    fun defaultOnceDate(now: ZonedDateTime): String = now.toLocalDate().plusDays(1).toString()

    /** «mañana, martes 10 de noviembre» / «tomorrow, Tuesday 10 November» (textos en strings.xml, 1.11.0). */
    fun dateLabel(date: java.time.LocalDate, today: java.time.LocalDate): String {
        val months = Txt.s(R.string.ui_months).split(',')
        val wd = weekday(date.dayOfWeek.value)
        val base = Txt.s(R.string.ui_date_base, wd, date.dayOfMonth, months[date.monthValue - 1]) +
            if (date.year != today.year) Txt.s(R.string.ui_date_year, date.year) else ""
        return when (ChronoUnit.DAYS.between(today, date)) {
            0L -> Txt.s(R.string.ui_date_today, base); 1L -> Txt.s(R.string.ui_date_tomorrow, base); else -> base
        }
    }

    /** Nombre del día ISO (1 = lunes). */
    fun weekday(iso: Int): String = Txt.s(R.string.ui_weekdays).split(',')[iso - 1]
    /** Inicial del día ISO (L M X J V S D / M T W T F S S). */
    fun dayKey(iso: Int): String = Txt.s(R.string.ui_day_keys).split(',')[iso - 1]

    /** Volúmenes de la subida gradual: desde START_VOLUME hasta [target], incluido. */
    fun fadeInSteps(target: Int, steps: Int = FADE_IN_STEPS): List<Int> {
        val start = minOf(START_VOLUME, target)
        return (1..steps).map { i -> start + ((target - start) * i / steps.toFloat()).toInt() }
    }

    /** «L–V», «S y D», «Todos los días», «Una vez»… */
    fun daysLabel(days: Set<Int>): String = when {
        days.isEmpty() -> Txt.s(R.string.ui_days_once)
        days.size == 7 -> Txt.s(R.string.ui_days_every)
        days == setOf(1, 2, 3, 4, 5) -> Txt.s(R.string.ui_days_weekdays)
        days == setOf(6, 7) -> Txt.s(R.string.ui_days_weekend)
        else -> days.sorted().joinToString(" ") { dayKey(it) }
    }

    /** «Sonará mañana a las 7:30 (en 9 h 30 min)». Espacios que no se rompen entre cifra y unidad (1.9.2). */
    fun nextLabel(next: ZonedDateTime, now: ZonedDateTime): String {
        val dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())
        val day = when {
            dayDiff == 0L -> Txt.s(R.string.ui_next_today)
            dayDiff == 1L -> Txt.s(R.string.ui_next_tomorrow)
            dayDiff < 7L -> Txt.s(R.string.ui_next_on, weekday(next.dayOfWeek.value))
            else -> Txt.s(R.string.ui_next_on, dateLabel(next.toLocalDate(), now.toLocalDate()))
        }
        val mins = ChronoUnit.MINUTES.between(now, next).coerceAtLeast(0)
        val h = mins / 60; val m = mins % 60
        val inText = when { dayDiff >= 2L -> Txt.s(R.string.ui_in_days, dayDiff); h > 0 -> Txt.s(R.string.ui_in_hm, h, m); else -> Txt.s(R.string.ui_in_m, m) }
        return Txt.s(R.string.ui_will_ring, day, "%d:%02d".format(next.hour, next.minute), inText)
    }

}
