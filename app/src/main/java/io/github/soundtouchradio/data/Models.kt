package io.github.soundtouchradio.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Station(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    /** Última información técnica observada (no editable): «MP3», 128, etc. */
    val codec: String? = null,
    val bitrateKbps: Int? = null,
)

@Serializable
data class SpeakerRecord(
    val host: String,
    val name: String,
    val type: String = "SoundTouch",
    val mac: String? = null,
    val firmware: String? = null,
    /** URL de control AVTransport leída de la descripción UPnP; null = ruta por defecto. */
    val controlUrl: String? = null,
    val manual: Boolean = false,
    val lastSeenMillis: Long = 0,
) {
    val id: String get() = mac ?: host
}

@Serializable
data class Settings(
    val activeSpeakerId: String? = null,
    val dynamicColor: Boolean = false,
    val multiroomExperimental: Boolean = false,
    /** 10 presets de la app: id de emisora o null (vacío). No son los botones físicos del altavoz. */
    val presets: List<String?> = Seed.presets,
    val alarm: AlarmSettings = AlarmSettings(),
    /** Alarma de una sola vez, independiente de la semanal (1.7.0). */
    val once: OnceAlarm = OnceAlarm(),
    /** Puntos por pulsación de − / + y de los botones del móvil (1.8.1). 1 por defecto; 2, 3 o 5 si el altavoz va por escalones. */
    val volumeStep: Int = 1,
    /** Ya se pidió el permiso de notificaciones para los controles en la pantalla de bloqueo (1.9.1). */
    val mediaNotifAsked: Boolean = false,
    /**
     * Puente de redirección para emisoras solo https. Activado por defecto desde la 1.9.6 (verificado con un
     * SoundTouch 10): null = activado, sin probar en este altavoz; true = activado y verificado con la prueba de
     * Diagnóstico; false = desactivado por el usuario.
     */
    val httpsBridge: Boolean? = null,
    /** Destino de la radio (1.10.0): false = el altavoz activo; true = «Este móvil». */
    val playOnPhone: Boolean = false,
    /** Ecualizador de «Este móvil», un ajuste por salida (clave: nombre de la salida) (1.10.2). */
    val eq: Map<String, io.github.soundtouchradio.player.EqSetting> = emptyMap(),
)

/** Despertador (uno). days: 1 = lunes … 7 = domingo (ISO); vacío = una sola vez. */
@Serializable
data class AlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val stationId: String? = null,
    val speakerId: String? = null,
    val volume: Int = 20,
    val fadeIn: Boolean = true,
)

/**
 * Alarma de una sola vez en una fecha concreta. Convive con la semanal: no hay que desmarcar la rutina.
 * [date] en ISO (aaaa-mm-dd). Al sonar se desactiva sola y conserva sus ajustes.
 */
@Serializable
data class OnceAlarm(
    val enabled: Boolean = false,
    val date: String? = null,
    val hour: Int = 7,
    val minute: Int = 0,
    val stationId: String? = null,
    val volume: Int = 20,
    val fadeIn: Boolean = true,
)
