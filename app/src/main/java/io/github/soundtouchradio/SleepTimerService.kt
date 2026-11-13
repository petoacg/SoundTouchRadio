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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.soundtouchradio.player.SleepState
import io.github.soundtouchradio.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Servicio en primer plano (tipo connectedDevice) que mantiene vivo el temporizador de apagado
 * con la pantalla apagada. La lógica está en [io.github.soundtouchradio.player.SleepTimer];
 * este servicio solo muestra la notificación, sostiene un wakelock acotado y se para solo.
 */
class SleepTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as App).container
        if (intent?.action == ACTION_CANCEL) {
            container.sleep.cancel()
            stopSelf(); return START_NOT_STICKY
        }
        val initial = container.sleep.state.value ?: run { stopSelf(); return START_NOT_STICKY }
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(initial),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        // Wakelock parcial con caducidad: la CPU no se duerme durante la cuenta atrás.
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundTouchRadio:sleep").apply {
                setReferenceCounted(false)
                acquire((initial.endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0) + 3 * 60_000L)
            }
        }
        scope.launch {
            container.sleep.state.collectLatest { s ->
                if (s == null) { stopSelf(); return@collectLatest }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(s))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, Txt.s(R.string.ui_temporizador_de_apagado), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(s: SleepState): Notification {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.endsAtMillis))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, SleepTimerService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sleep)
            .setContentTitle(if (s.finishing) Txt.s(R.string.ui_apagando_2, s.speakerName) else Txt.s(R.string.ui_se_apaga_a_las_2, s.speakerName, time))
            .setContentText(if (s.fade) Txt.s(R.string.ui_bajara_el_volumen_poco_a) else Txt.s(R.string.ui_se_apagara_sin_bajar_el))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, Txt.s(R.string.ui_cancelar_6), cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "sleep_timer"
        private const val NOTIFICATION_ID = 7
        const val ACTION_CANCEL = "io.github.soundtouchradio.CANCEL_SLEEP"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, SleepTimerService::class.java))
    }
}
