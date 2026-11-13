package io.github.soundtouchradio.ui

import io.github.soundtouchradio.R

import io.github.soundtouchradio.player.FailureCause
import io.github.soundtouchradio.player.Phase

enum class FixAction { TRY_HTTP, POWER, RETRY, DIAGNOSTICS, LINK, ENABLE_BRIDGE }

data class CauseCopy(val title: String, val detail: String, val action: FixAction?, val actionLabel: String?)

/** Textos de la app. Concretos: qué pasa y qué hacer. */
object Copy {
    fun powerLine(target: Boolean): String = if (target) Txt.s(R.string.ui_encendiendo) else Txt.s(R.string.ui_pasando_a_reposo)

    fun phaseLine(p: Phase): String = when (p) {
        Phase.Idle -> Txt.s(R.string.ui_listo)
        is Phase.Resolving -> Txt.s(R.string.ui_comprobando_el_stream)
        is Phase.Sending -> Txt.s(R.string.ui_enviando_al_altavoz)
        is Phase.AwaitingConfirmation -> Txt.s(R.string.ui_esperando_a_que_suene)
        is Phase.Buffering -> Txt.s(R.string.ui_cargando_2)
        is Phase.Playing -> Txt.s(R.string.ui_sonando)
        is Phase.Paused -> Txt.s(R.string.ui_en_pausa)
        Phase.Stopped -> Txt.s(R.string.ui_detenido)
        Phase.Standby -> Txt.s(R.string.ui_altavoz_en_reposo)
        is Phase.OtherSource -> Txt.s(R.string.ui_otra_fuente, sourceName(p.source))
        is Phase.Failed -> cause(p.cause).title
        Phase.Unreachable -> Txt.s(R.string.ui_sin_conexion_con_el_altavoz)
    }

    fun sourceName(s: String): String = when (s) {
        "BLUETOOTH" -> Txt.s(R.string.ui_bluetooth)
        "AUX" -> "AUX"
        "TUNEIN" -> "TuneIn"
        "INTERNET_RADIO", "LOCAL_INTERNET_RADIO" -> Txt.s(R.string.ui_radio_de_bose)
        "SPOTIFY" -> Txt.s(R.string.ui_spotify)
        "INVALID_SOURCE" -> Txt.s(R.string.ui_sin_fuente)
        else -> s.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun cause(c: FailureCause): CauseCopy = when (c) {
        FailureCause.HTTPS_UNSUPPORTED -> CauseCopy(
            Txt.s(R.string.ui_no_suena_direccion_https),
            Txt.s(R.string.ui_el_soundtouch_no_reproduce_direcciones) +
                Txt.s(R.string.ui_esta_emisora_solo_emite_por),
            FixAction.ENABLE_BRIDGE, Txt.s(R.string.ui_activar_puente_y_reintentar))
        FailureCause.HLS_UNSUPPORTED -> CauseCopy(
            Txt.s(R.string.ui_no_suena_es_un_stream),
            Txt.s(R.string.ui_las_urls_m3u8_hls_no),
            null, null)
        FailureCause.RENDERER_NAME -> CauseCopy(
            Txt.s(R.string.ui_no_suena_nombre_del_altavoz),
            Txt.s(R.string.ui_el_nombre_del_altavoz_contiene),
            FixAction.DIAGNOSTICS, Txt.s(R.string.ui_ver_diagnostico))
        FailureCause.NATIVE_SOURCE_ACTIVE -> CauseCopy(
            Txt.s(R.string.ui_no_suena_otra_radio_activa),
            Txt.s(R.string.ui_el_altavoz_estaba_en_su),
            FixAction.RETRY, Txt.s(R.string.ui_reintentar_2))
        FailureCause.STANDBY_NOT_WOKEN -> CauseCopy(
            Txt.s(R.string.ui_el_altavoz_sigue_en_reposo),
            Txt.s(R.string.ui_no_se_desperto_con_la),
            FixAction.POWER, Txt.s(R.string.ui_encender_2))
        FailureCause.NOT_CONFIRMED -> CauseCopy(
            Txt.s(R.string.ui_el_altavoz_no_confirma_que),
            Txt.s(R.string.ui_acepto_la_orden_pero_no),
            FixAction.RETRY, Txt.s(R.string.ui_reintentar_2))
        FailureCause.UPNP_ERROR -> CauseCopy(
            Txt.s(R.string.ui_el_reproductor_rechazo_la_orden),
            Txt.s(R.string.ui_el_reproductor_upnp_respondio_con),
            FixAction.DIAGNOSTICS, Txt.s(R.string.ui_ver_diagnostico))
        FailureCause.SPEAKER_UNREACHABLE -> CauseCopy(
            Txt.s(R.string.ui_no_se_encuentra_el_altavoz),
            Txt.s(R.string.ui_no_responde_en_la_red),
            FixAction.LINK, Txt.s(R.string.ui_buscar_altavoz_2))
        FailureCause.PHONE_PLAYBACK -> CauseCopy(
            Txt.s(R.string.ui_movil_no_suena),
            Txt.s(R.string.ui_movil_no_suena_detalle),
            FixAction.RETRY, Txt.s(R.string.ui_reintentar))
        FailureCause.RESOLVE_FAILED -> CauseCopy(
            Txt.s(R.string.ui_no_se_pudo_leer_la),
            Txt.s(R.string.ui_la_lista_pls_m3u_no),
            null, null)
    }
}
