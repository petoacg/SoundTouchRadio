package io.github.soundtouchradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe la alarma del sistema (y la orden «Parar» de la notificación). No exportado. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RING -> AlarmService.start(context, test = false,
                kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { io.github.soundtouchradio.player.AlarmPlan.Kind.valueOf(it) }.getOrNull() }
                    ?: io.github.soundtouchradio.player.AlarmPlan.Kind.WEEKLY)
            ACTION_STOP -> {
                val c = (context.applicationContext as App).container
                c.activeSpeakerRecord()?.let { c.controllerFor(it).stopPlayback() }
                AlarmService.dismissResult(context)
            }
        }
    }

    companion object {
        const val ACTION_RING = "io.github.soundtouchradio.ALARM_RING"
        const val ACTION_STOP = "io.github.soundtouchradio.ALARM_STOP"
        const val EXTRA_KIND = "kind"
    }
}

/** Tras reiniciar, actualizar la app o cambiar la hora/zona: volver a programar. Solo acciones protegidas del sistema. */
class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as App).container
        AlarmScheduler.schedule(context, c.settings.settings.value)
    }
}
