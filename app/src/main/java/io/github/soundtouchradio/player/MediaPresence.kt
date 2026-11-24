package io.github.soundtouchradio.player

import io.github.soundtouchradio.ui.Txt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import io.github.soundtouchradio.App
import io.github.soundtouchradio.R
import io.github.soundtouchradio.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Presencia de la radio en Android (1.9.1): pantalla de bloqueo, persiana de notificaciones y teclas de volumen.
 * Mientras suena o está en pausa:
 * - una sesión multimedia de reproducción «remota» con el volumen del SoundTouch (0–100): las teclas del móvil mueven el
 *   altavoz también con la pantalla apagada;
 * - una notificación de estilo multimedia (emisora, altavoz, pausa/continuar y detener) que Android muestra en la
 *   pantalla de bloqueo y en la persiana. Sin servicio en primer plano: la radio suena en el altavoz, no en el móvil.
 * Al detener, la notificación desaparece. Necesita el permiso de notificaciones (Android 13+).
 */
class MediaPresence(private val context: Context, private val step: () -> Int = { 1 }) {
    private val app = context.applicationContext
    private val session = MediaSession(app, "SoundTouchRadio")
    private val nm = app.getSystemService(NotificationManager::class.java)
    private var provider: VolumeProvider? = null
    private var target: SpeakerController? = null
    private var lastKey: String? = null

    init {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, Txt.s(R.string.ui_reproduccion), NotificationManager.IMPORTANCE_LOW).apply {
            description = Txt.s(R.string.ui_emisora_que_suena_en_el)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        session.setSessionActivity(openApp())
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { target?.resume() }
            override fun onPause() { pauseOrStop(target) }
            override fun onStop() { target?.stopPlayback() }
            override fun onCustomAction(action: String, extras: android.os.Bundle?) { if (action == CUSTOM_STOP) target?.stopPlayback() }
        })
    }

    /** Llamar con cada cambio de estado del altavoz activo. */
    fun update(controller: SpeakerController?, speakerName: String?, state: SpeakerState?) {
        target = controller
        val playing = state?.phase is Phase.Playing || state?.phase is Phase.Buffering
        val paused = state?.phase is Phase.Paused
        if (controller == null || state == null || !(playing || paused)) {
            if (session.isActive) session.isActive = false
            if (lastKey != null) { nm.cancel(NOTIFICATION_ID); lastKey = null }
            return
        }
        // Volumen remoto (fase 2 de las teclas físicas).
        state.volume?.let { v ->
            val p = provider
            if (p == null) {
                val created = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 100, v) {
                    override fun onSetVolumeTo(volume: Int) { currentVolume = volume.coerceIn(0, 100); target?.setVolume(currentVolume) }
                    override fun onAdjustVolume(direction: Int) {
                        if (direction == 0) return
                        currentVolume = (currentVolume + direction * step().coerceAtLeast(1)).coerceIn(0, 100)
                        target?.setVolume(currentVolume)
                    }
                }
                provider = created
                session.setPlaybackToRemote(created)
            } else if (p.currentVolume != v) p.currentVolume = v
        }
        val station = state.stationName ?: Txt.s(R.string.ui_radio_2)
        val speaker = speakerName ?: "SoundTouch"
        val canPause = playing && state.pauseAvailable
        // El volumen entra en la clave a pasos de 5: la carátula del frontal refleja el nivel sin repintar a cada punto.
        val key = "$station|$speaker|$paused|$canPause|${state.volume?.div(5)}"
        val art = PanelArt.render(app, onAir = !paused, volume = state.volume)
        if (key == lastKey && session.isActive) return
        lastKey = key

        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, station)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, speaker)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, Txt.s(R.string.ui_soundtouch_radio))
            .putBitmap(MediaMetadata.METADATA_KEY_ART, art)
            .build())
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
            .addCustomAction(PlaybackState.CustomAction.Builder(CUSTOM_STOP, Txt.s(R.string.ui_detener), R.drawable.ic_stop).build())
            .setState(if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())
        if (!session.isActive) session.isActive = true

        val toggle = if (paused) Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(app, R.drawable.ic_play), Txt.s(R.string.ui_continuar), action(ACTION_PLAY)).build()
            else Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(app, R.drawable.ic_pause), if (canPause) Txt.s(R.string.ui_pausa) else Txt.s(R.string.ui_detener), action(ACTION_PAUSE)).build()
        val stop = Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(app, R.drawable.ic_stop), Txt.s(R.string.ui_detener), action(ACTION_STOP)).build()
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle(station)
            .setContentText(if (paused) Txt.s(R.string.ui_en_pausa_2, speaker) else Txt.s(R.string.ui_suena_en_2, speaker))
            .setLargeIcon(art)
            .setContentIntent(openApp())
            .setDeleteIntent(null)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(!paused)
            .addAction(toggle).addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }   // sin permiso de notificaciones: no se muestra, sin error
    }

    private fun openApp() = PendingIntent.getActivity(app, 40, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
    private fun action(a: String) = PendingIntent.getBroadcast(app, a.hashCode(), Intent(app, MediaActionReceiver::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val CHANNEL = "playback"
        const val NOTIFICATION_ID = 50
        const val ACTION_PLAY = "io.github.soundtouchradio.MEDIA_PLAY"
        const val ACTION_PAUSE = "io.github.soundtouchradio.MEDIA_PAUSE"
        const val ACTION_STOP = "io.github.soundtouchradio.MEDIA_STOP"
        private const val CUSTOM_STOP = "stop"

        /** Pausa si el altavoz la admite; si no (algunas emisoras en directo), detener. */
        fun pauseOrStop(c: SpeakerController?) {
            c ?: return
            if (c.state.value.pauseAvailable) c.pause() else c.stopPlayback()
        }
    }
}

/** Botones de la notificación. Funciona también si Android cerró la app: el contenedor se recrea. No exportado. */
class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as App).container
        val ctrl = c.activeSpeakerRecord()?.let { c.controllerFor(it) } ?: return
        when (intent.action) {
            MediaPresence.ACTION_PLAY -> ctrl.resume()
            MediaPresence.ACTION_PAUSE -> MediaPresence.pauseOrStop(ctrl)
            MediaPresence.ACTION_STOP -> ctrl.stopPlayback()
        }
        // Tras la orden, leer el estado real del altavoz: la notificación se corrige o desaparece si ya no suena (1.9.2).
        c.scope.launch { kotlinx.coroutines.delay(1_500); ctrl.refresh() }
    }
}
