package io.github.soundtouchradio

import io.github.soundtouchradio.ui.Txt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.soundtouchradio.player.AlarmOutcome
import io.github.soundtouchradio.player.PlayRequest
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Ejecuta el despertador (o la prueba «Probar ahora»): servicio en primer plano + wakelock acotado.
 * Si el altavoz no responde o la emisora no suena, suena una alarma en el propio móvil:
 * un despertador que falla en silencio es peor que no tenerlo.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val c = (application as App).container
        val test = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        val kind = intent?.getStringExtra(EXTRA_KIND)?.let { runCatching { io.github.soundtouchradio.player.AlarmPlan.Kind.valueOf(it) }.getOrNull() }
            ?: io.github.soundtouchradio.player.AlarmPlan.Kind.WEEKLY
        val once = kind == io.github.soundtouchradio.player.AlarmPlan.Kind.ONCE
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing(Txt.s(R.string.ui_despertador_3), Txt.s(R.string.ui_preparando)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundTouchRadio:alarm").apply { setReferenceCounted(false); acquire(5 * 60_000L) }
        }
        if (job?.isActive == true) return START_NOT_STICKY
        job = scope.launch {
            val all = c.settings.settings.value
            val alarm = all.alarm
            // «Una vez» usa su emisora y su volumen; el altavoz es el del despertador semanal o el activo.
            val stationId = if (once) all.once.stationId ?: alarm.stationId else alarm.stationId
            val volume = if (once) all.once.volume else alarm.volume
            val fadeIn = if (once) all.once.fadeIn else alarm.fadeIn
            val speaker = c.speakers.speakers.value.firstOrNull { it.id == alarm.speakerId } ?: c.activeSpeakerRecord()
            val station = c.stations.stations.value.firstOrNull { it.id == stationId }
            val outcome = when {
                speaker == null -> AlarmOutcome.Failed(Txt.s(R.string.ui_no_hay_ningun_altavoz_vinculado))
                station == null -> AlarmOutcome.Failed(Txt.s(R.string.ui_la_emisora_del_despertador_ya))
                else -> c.ringer.ring(
                    controller = c.controllerFor(speaker),
                    api = SoundTouchApi.forHost(speaker.host, c.lan, c.log),
                    station = PlayRequest(station.id, station.name, station.url),
                    volume = volume, fadeIn = fadeIn,
                    onProgress = { msg -> notifyOngoing(Txt.s(R.string.ui_despertador_3), msg) },
                )
            }
            when (outcome) {
                AlarmOutcome.Playing -> postResult(
                    Txt.s(R.string.ui_suena_en, station?.name, speaker?.name), if (test) Txt.s(R.string.ui_prueba_del_despertador_correcta) else Txt.s(R.string.ui_buenos_dias), fallback = false)
                is AlarmOutcome.Failed -> postResult(Txt.s(R.string.ui_el_despertador_no_pudo_poner), outcome.reason, fallback = true)
            }
            // Siguiente aviso: las alarmas de una sola vez se desactivan; las repetidas se reprograman.
            if (!test) {
                if (once) c.settings.update { it.copy(once = it.once.copy(enabled = false)) }
                else if (alarm.days.isEmpty()) c.settings.update { it.copy(alarm = it.alarm.copy(enabled = false)) }
                AlarmScheduler.schedule(this@AlarmService, c.settings.settings.value)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun ongoing(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH_ONGOING).setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title).setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()

    private fun notifyOngoing(title: String, text: String) =
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing(title, text))

    private fun postResult(title: String, text: String, fallback: Boolean) {
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, if (fallback) CH_FALLBACK else CH_RESULT)
            .setSmallIcon(R.drawable.ic_alarm).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        if (fallback) b.setPriority(NotificationCompat.PRIORITY_MAX)
        else b.addAction(0, Txt.s(R.string.ui_parar), PendingIntent.getBroadcast(this, 21,
            Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        getSystemService(NotificationManager::class.java).notify(RESULT_ID, b.build())
    }

    companion object {
        private const val CH_ONGOING = "alarm_ongoing"
        private const val CH_RESULT = "alarm_result"
        /** Canal con sonido de alarma: es el respaldo si la radio no suena. */
        private const val CH_FALLBACK = "alarm_fallback"
        private const val ONGOING_ID = 30
        private const val RESULT_ID = 31
        private const val EXTRA_TEST = "test"
        private const val EXTRA_KIND = "kind"

        fun start(context: Context, test: Boolean, kind: io.github.soundtouchradio.player.AlarmPlan.Kind = io.github.soundtouchradio.player.AlarmPlan.Kind.WEEKLY) =
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).putExtra(EXTRA_TEST, test).putExtra(EXTRA_KIND, kind.name))

        fun dismissResult(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(RESULT_ID)

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_ONGOING, Txt.s(R.string.ui_despertador_en_curso), NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_RESULT, Txt.s(R.string.ui_despertador_3), NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_FALLBACK, Txt.s(R.string.ui_alarma_de_respaldo_si_la), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                setBypassDnd(true)
            })
        }
    }
}
