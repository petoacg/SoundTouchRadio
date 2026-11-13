package io.github.soundtouchradio

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.soundtouchradio.data.SettingsRepository
import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.data.SpeakerRepository
import io.github.soundtouchradio.data.StationRepository
import io.github.soundtouchradio.discovery.Discovery
import io.github.soundtouchradio.discovery.Linker
import io.github.soundtouchradio.player.RequestLog
import io.github.soundtouchradio.player.SpeakerController
import io.github.soundtouchradio.playlist.StreamProbe
import io.github.soundtouchradio.soundtouch.Http
import io.github.soundtouchradio.soundtouch.SoundTouchApi
import io.github.soundtouchradio.soundtouch.SoundTouchSocket
import io.github.soundtouchradio.upnp.AvTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        io.github.soundtouchradio.ui.Txt.init(this)
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = container.setForeground(false)
        })
    }
}

/** Dependencias de la app, sin framework de inyección. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val log = RequestLog()
    val lan = Http.lan()
    val internet = Http.internet()
    val probe = StreamProbe(internet)
    /** Puente de redirección a https (1.9.5, experimental): solo se usa si la prueba de Diagnóstico salió bien. */
    /** «Este móvil» (1.10.0): reproductor del teléfono. Se crea al usarlo por primera vez. */
    val phone by lazy {
        io.github.soundtouchradio.player.PhonePlayer(app, probe, scope) { output -> settings.settings.value.eq[output] ?: io.github.soundtouchradio.player.EqSetting() }
    }
    val redirect = io.github.soundtouchradio.player.RedirectBridge({ io.github.soundtouchradio.player.RedirectBridge.wifiIpv4(app) },
        onRequest = { path, status, started -> log.add("PUENTE", "altavoz → $path", if (status == 302) "302 → https" else "HTTP $status", status == 302, started) })
    /** Directorio de emisoras (1.7.0): solo se usa al buscar. */
    val directory = io.github.soundtouchradio.directory.RadioBrowser(internet)
    val stations = StationRepository(app.filesDir)
    val speakers = SpeakerRepository(app.filesDir)
    val settings = SettingsRepository(app.filesDir)
    val discovery = Discovery(app)
    val linker = Linker(lan, log, discovery)

    /** Temporizador de apagado: usa clientes propios para no interferir con el controlador. */
    val sleep = io.github.soundtouchradio.player.SleepTimer(scope, clients = { s ->
        SoundTouchApi.forHost(s.host, lan, log) to AvTransport(SpeakerController.controlUrlFor(s), lan, log)
    })

    val ringer = io.github.soundtouchradio.player.AlarmRinger()
    val apConnector = io.github.soundtouchradio.discovery.SpeakerApConnector(app)

    /** Altavoz activo según ajustes (para servicios en segundo plano). */
    fun activeSpeakerRecord(): SpeakerRecord? {
        val list = speakers.speakers.value
        return list.firstOrNull { it.id == settings.settings.value.activeSpeakerId } ?: list.firstOrNull()
    }

    private val controllers = mutableMapOf<String, Pair<SpeakerController, SoundTouchSocket>>()

    /** Botones físicos de volumen con la pantalla bloqueada (1.8.0): sigue al altavoz activo mientras suena. */
    val media = io.github.soundtouchradio.player.MediaPresence(app) { settings.settings.value.volumeStep }
    private var foreground = false
    private var reconnectJob: Job? = null

    fun controllerFor(s: SpeakerRecord): SpeakerController {
        val existing = controllers[s.id]
        val wantedControl = SpeakerController.controlUrlFor(s)
        if (existing != null && existing.first.speaker.host == s.host && existing.first.controlUrl == wantedControl) return existing.first
        existing?.let { (c, sock) -> c.stopMonitoring(); sock.close() }

        val api = SoundTouchApi.forHost(s.host, lan, log)
        val controller = SpeakerController(s, api, AvTransport(wantedControl, lan, log), probe, scope,
            onTechInfo = { id, codec, br -> stations.updateTech(id, codec, br) },
            onHttpUpgrade = { id, url -> stations.stations.value.firstOrNull { it.id == id }?.let { stations.upsert(it.copy(url = url)) } },
            bridge = { https -> if (settings.settings.value.httpsBridge != false) redirect.urlFor(https) else null },
        )
        lateinit var socket: SoundTouchSocket
        socket = SoundTouchSocket(s.host, lan,
            onEvent = { text -> if (SoundTouchSocket.isRelevant(text)) scope.launch { controller.nudge() } },
            onClosed = { scope.launch { controller.onSocket(false); scheduleReconnect() } },
            onOpen = { scope.launch { controller.onSocket(true) } },
        )
        controllers[s.id] = controller to socket
        if (foreground) start(controller, socket)
        return controller
    }

    private fun start(c: SpeakerController, sock: SoundTouchSocket) {
        c.startMonitoring()
        sock.open()
    }

    /** Solo se vigila el altavoz con la app visible: en segundo plano no queda nada abierto. */
    fun setForeground(value: Boolean) {
        foreground = value
        controllers.values.forEach { (c, sock) -> if (value) start(c, sock) else { c.stopMonitoring(); sock.close(); c.onSocket(false) } }
        if (!value) reconnectJob?.cancel()
    }

    private fun scheduleReconnect() {
        if (!foreground || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(10_000)
            if (foreground) controllers.values.forEach { (_, sock) -> sock.open() }
        }
    }

    // Al final de la clase: usa campos ya inicializados (controllers, foreground).
    init {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        scope.launch {
            combine(settings.settings, speakers.speakers) { _, _ -> activeSpeakerRecord() }
                .distinctUntilChanged()
                .flatMapLatest { rec ->
                    if (rec == null) flowOf<Pair<SpeakerController?, io.github.soundtouchradio.player.SpeakerState?>>(null to null)
                    else controllerFor(rec).let { c -> c.state.map<io.github.soundtouchradio.player.SpeakerState, Pair<SpeakerController?, io.github.soundtouchradio.player.SpeakerState?>> { c to it } }
                }
                .collect { (c, st) -> media.update(c, activeSpeakerRecord()?.name, st) }
        }
    }
}
