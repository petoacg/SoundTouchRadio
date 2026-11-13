package io.github.soundtouchradio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.soundtouchradio.data.Settings
import io.github.soundtouchradio.player.AlarmPlan
import io.github.soundtouchradio.ui.MainActivity
import java.time.ZonedDateTime

/**
 * Programa el despertador con setAlarmClock: exacto, no se retrasa en reposo (Doze) y muestra el icono
 * de alarma del sistema. En Android 13+ se usa USE_EXACT_ALARM (propio de apps despertador, sin pedir permiso).
 */
object AlarmScheduler {
    private fun pending(context: Context, kind: AlarmPlan.Kind = AlarmPlan.Kind.WEEKLY) = PendingIntent.getBroadcast(
        context, 10, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RING).putExtra(AlarmReceiver.EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /**
     * Programa la que suene antes de las dos (semanal y «Una vez»): un solo aviso del sistema, que lleva cuál es.
     * Al sonar, el servicio vuelve a llamar aquí y se programa la siguiente. Devuelve la próxima, o null.
     */
    fun schedule(context: Context, settings: Settings, now: ZonedDateTime = ZonedDateTime.now()): AlarmPlan.Upcoming? {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context))
        val up = AlarmPlan.nextAlarm(settings.alarm, settings.once, now) ?: return null
        val pi = pending(context, up.kind)
        val next = up.at
        val at = next.toInstant().toEpochMilli()
        if (canScheduleExact(context)) {
            val show = PendingIntent.getActivity(context, 11, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            // Sin permiso de alarma exacta (Android 12 con el permiso retirado): aproximada, avisada en la UI.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        return up
    }
}
