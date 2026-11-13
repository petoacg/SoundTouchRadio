package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import io.github.soundtouchradio.player.SleepPlan
import io.github.soundtouchradio.player.SleepState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.player.Phase
import io.github.soundtouchradio.player.SpeakerState
import io.github.soundtouchradio.playlist.StreamWarning
import io.github.soundtouchradio.ui.Copy
import io.github.soundtouchradio.ui.FixAction
import io.github.soundtouchradio.ui.components.GrilleMode
import io.github.soundtouchradio.ui.components.SpeakerGrille
import io.github.soundtouchradio.ui.theme.Tabular

@Composable
fun NowPlayingPanel(
    speaker: SpeakerRecord?,
    state: SpeakerState,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onVolume: (Int) -> Unit,
    onMute: () -> Unit,
    onFix: (FixAction) -> Unit,
    onLink: () -> Unit,
    /** Un punto arriba (+1) o abajo (−1): acciones de TalkBack de la barra. */
    onVolumeStep: (Int) -> Unit = {},
    /** Hoja «Sonido» (ecualizador). Solo con «Este móvil» (1.10.2). */
    onEq: () -> Unit = {},
    sleep: SleepState? = null,
    onSleep: () -> Unit = {},
    /** true: anclado abajo (teléfono en vertical). false: tarjeta dentro de la vista de dos paneles. */
    docked: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep?.endsAtMillis) { while (sleep != null) { now = System.currentTimeMillis(); delay(15_000) } }
    val sleepLeft = sleep?.let { SleepPlan.remainingMinutes(it.endsAtMillis, now) }
    Surface(
        color = cs.surfaceContainerHigh,
        shape = if (docked) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column((if (docked) Modifier.navigationBarsPadding() else Modifier).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            if (speaker == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeakerGrille(null, GrilleMode.IDLE, Txt.s(R.string.ui_sin_altavoz_2), size = 48.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_sin_altavoz), style = MaterialTheme.typography.titleMedium)
                        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vincula_tu_soundtouch_para_empezar), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Button(onClick = onLink) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vincular)) }
                }
                return@Column
            }

            val phase = state.phase
            val mode = when {
                state.powerTarget != null -> GrilleMode.LOADING
                phase is Phase.Playing -> GrilleMode.PLAYING
                phase.isBusy || phase is Phase.Buffering -> GrilleMode.LOADING
                phase is Phase.Failed || phase is Phase.Unreachable -> GrilleMode.ERROR
                else -> GrilleMode.IDLE
            }
            val title = when (phase) {
                is Phase.OtherSource -> phase.title ?: Copy.sourceName(phase.source)
                Phase.Standby, Phase.Unreachable -> speaker.name
                else -> state.stationName ?: speaker.name
            }
            val statusColor = when {
                phase is Phase.Playing -> cs.primary
                phase is Phase.Failed || phase is Phase.Unreachable -> cs.error
                else -> cs.onSurfaceVariant
            }
            val tech = state.resolved?.let { r ->
                listOfNotNull(
                    io.github.soundtouchradio.playlist.UrlRules.codecLabel(r.mime),
                    r.probe?.icyBitrate?.let { "${it}k" },
                ).joinToString(" ").ifEmpty { null }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakerGrille(
                    state.volume, mode,
                    "Volumen ${state.volume ?: "desconocido"} %, ${Copy.phaseLine(phase).lowercase()}",
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Estado en dos líneas (1.9.2): arriba el estado (y el temporizador), abajo el formato. Nada se corta.
                    Text(
                        (state.powerTarget?.let(Copy::powerLine) ?: Copy.phaseLine(phase)) + when {
                            sleep?.finishing == true -> Txt.s(R.string.ui_apagando)
                            sleepLeft != null -> Txt.s(R.string.ui_se_apaga_en_min, sleepLeft)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = statusColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { stateDescription = Copy.phaseLine(phase) },
                    )
                    if (speaker.type == "phone" && state.phoneOnCellular && (phase is Phase.Playing || phase is Phase.Buffering))
                        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_usando_datos_moviles),
                            style = MaterialTheme.typography.labelMedium, color = cs.tertiary, maxLines = 1)
                    if (phase is Phase.Playing || phase is Phase.Paused) tech?.let {
                        Text(it.replace(" ", " · ").replace(Regex("(\\d+)k$"), "$1\u00A0kbps"),
                            style = MaterialTheme.typography.labelMedium.merge(Tabular), color = cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val active = phase is Phase.Playing || phase is Phase.Buffering || phase.isBusy
                // Fila de controles (1.9.0): temporizador, pausa y parada, todos de 48 dp y con 4 dp entre ellos.
                val canSleep = sleep != null || phase is Phase.Playing || phase is Phase.Buffering || phase is Phase.Paused
                val sleepDesc = sleepLeft?.let { Txt.s(R.string.ui_temporizador_se_apaga_en_minutos, it) } ?: Txt.s(R.string.ui_temporizador_de_apagado)
                if (sleep != null) FilledTonalIconButton(onClick = onSleep, modifier = Modifier.size(48.dp).semantics { contentDescription = sleepDesc }) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null)
                } else IconButton(onClick = onSleep, enabled = canSleep, modifier = Modifier.size(48.dp).semantics { contentDescription = sleepDesc }) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = null)
                }
                if (speaker.type == "phone") IconButton(onClick = onEq, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Equalizer, contentDescription = androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ecualizador))
                }
                Spacer(Modifier.width(4.dp))
                if (phase is Phase.Playing && state.pauseAvailable) {
                    FilledTonalIconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Pause, contentDescription = Txt.s(R.string.ui_pausa))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (phase is Phase.Paused) {
                    FilledIconButton(onClick = onResume, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = Txt.s(R.string.ui_continuar))
                    }
                } else {
                    FilledIconButton(
                        onClick = onStop, enabled = active, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                    ) { Icon(Icons.Filled.Stop, contentDescription = Txt.s(R.string.ui_detener)) }
                }
            }

            // Avisos del stream que suena (ámbar).
            val warn = state.resolved?.warnings.orEmpty()
            AnimatedVisibility(phase is Phase.Playing && (StreamWarning.HTTPS in warn)) {
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_suena_con_https_si_se), style = MaterialTheme.typography.bodySmall,
                    color = cs.tertiary, modifier = Modifier.padding(top = 8.dp))
            }

            // Fallo: causa y una acción.
            if (phase is Phase.Failed) {
                val copy = Copy.cause(phase.cause)
                Surface(color = cs.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        // Plegable: con fuente grande el texto no se come la pantalla; se despliega al tocar.
                        var expanded by remember(phase) { mutableStateOf(false) }
                        var overflows by remember(phase) { mutableStateOf(false) }
                        Text(
                            copy.detail + if (phase.cause == io.github.soundtouchradio.player.FailureCause.PHONE_PLAYBACK && phase.detail != null) "\n" + phase.detail else "",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onErrorContainer,
                            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                            modifier = Modifier.clickable(enabled = overflows || expanded, onClickLabel = if (expanded) Txt.s(R.string.ui_ver_menos) else Txt.s(R.string.ui_ver_mas)) { expanded = !expanded },
                        )
                        if (copy.action != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            // Sin verde: el verde significa «confirmado por el altavoz». Botón en los colores del propio aviso.
                            OutlinedButton(onClick = { onFix(copy.action) },
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = cs.onErrorContainer),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cs.onErrorContainer.copy(alpha = 0.6f)),
                            ) { Text(copy.actionLabel!!) }
                        }
                    }
                }
            } else if (phase is Phase.Standby && state.powerTarget == null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onFix(FixAction.POWER) }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_encender)) }
                }
            } else if (phase is Phase.Unreachable) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onFix(FixAction.LINK) }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_buscar_altavoz)) }
                }
            }

            // Fila del volumen (1.9.0): solo silencio, barra larga y cifra. Sin teclas − / +: la barra mide casi el
            // doble y cada punto del tramo nocturno queda del ancho de una yema. Curva y arrastre solo desde el tirador.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                IconToggleButton(checked = state.muted, onCheckedChange = { onMute() }) {
                    Icon(
                        if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (state.muted) Txt.s(R.string.ui_quitar_silencio) else Txt.s(R.string.ui_silenciar),
                        tint = if (state.muted) cs.error else cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                val volEnabled = state.volume != null && state.reachable
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                VolumeBar(
                    volume = state.volume ?: 0, pending = state.volumePending, enabled = volEnabled,
                    onVolume = { v -> if (v != state.volume) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onVolume(v) } },
                    onStep = onVolumeStep,
                    modifier = Modifier.weight(1f),
                )
                // Cifra en dos estados (1.8.1): tenue = pedido, aún sin confirmar; normal = el altavoz devuelve ese valor.
                Text(
                    state.volume?.let { "$it" } ?: "—",
                    style = MaterialTheme.typography.labelLarge.merge(Tabular),
                    color = if (state.volumePending) cs.onSurfaceVariant.copy(alpha = 0.45f) else cs.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.widthIn(min = 36.dp).padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Barra de volumen segura de noche (1.8.2). Se mueve solo arrastrando el tirador, y en relativo: el volumen avanza lo
 * que avanza el dedo desde donde estaba. Un toque en la pista vacía no hace nada, así que un mal toque al ir a pulsar «+»
 * no puede subir el volumen de golpe. Dibujo como el Slider de Material 3 (pista activa, tirador vertical, punto final).
 * TalkBack: acciones «subir/bajar un punto»; no hay acción para fijar un valor arbitrario.
 */
@Composable
private fun VolumeBar(volume: Int, pending: Boolean, enabled: Boolean, onVolume: (Int) -> Unit, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slopPx = with(density) { 24.dp.toPx() }
    val latest by androidx.compose.runtime.rememberUpdatedState(volume)
    val on by androidx.compose.runtime.rememberUpdatedState(enabled)
    val send by androidx.compose.runtime.rememberUpdatedState(onVolume)
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Posición continua del dedo durante el arrastre (1.9.1): el tirador ya no salta de punto en punto; solo la cifra
    // va por puntos. Fuera del arrastre, el tirador se desliza suavemente al volumen confirmado.
    var dragPos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // Un único valor animado: durante el arrastre sigue al dedo al instante; al soltar (o al confirmar el altavoz)
    // se desliza desde donde está hasta su punto, sin salto (1.9.2).
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(io.github.soundtouchradio.player.VolumeCurve.toPosition(volume)) }
    androidx.compose.runtime.LaunchedEffect(volume, dragging) {
        if (!dragging) anim.animateTo(io.github.soundtouchradio.player.VolumeCurve.toPosition(volume),
            androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    androidx.compose.runtime.LaunchedEffect(dragging) {
        if (dragging) androidx.compose.runtime.snapshotFlow { dragPos }.collect { anim.snapTo(it) }
    }
    val position = anim.value
    val active = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f)
    val inactive = if (enabled) cs.surfaceContainerHighest else cs.onSurface.copy(alpha = 0.12f)
    androidx.compose.foundation.Canvas(
        modifier.height(48.dp)
            .semantics {
                contentDescription = Txt.s(R.string.ui_volumen)
                stateDescription = Txt.s(R.string.ui_de_100, volume) + if (pending) Txt.s(R.string.ui_pendiente_de_confirmar) else ""
                if (!enabled) disabled()
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction(Txt.s(R.string.ui_subir_un_punto)) { if (on) onStep(1); on },
                    androidx.compose.ui.semantics.CustomAccessibilityAction(Txt.s(R.string.ui_bajar_un_punto)) { if (on) onStep(-1); on },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val start = io.github.soundtouchradio.player.VolumeCurve.toPosition(latest)
                    // Fuera del tirador: se ignora el toque entero (no salta, no arrastra).
                    if (!on || !io.github.soundtouchradio.player.VolumeCurve.grabsThumb(down.position.x, start * w, slopPx)) return@awaitEachGesture
                    down.consume()
                    dragPos = start
                    dragging = true
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            val p = io.github.soundtouchradio.player.VolumeCurve.dragTo(start, c.position.x - down.position.x, w)
                            dragPos = p
                            send(io.github.soundtouchradio.player.VolumeCurve.fromPosition(p))
                        }
                    } finally { dragging = false }
                }
            },
    ) {
        val cy = size.height / 2
        val trackH = 16.dp.toPx(); val gap = 6.dp.toPx(); val thumbW = 4.dp.toPx()
        val thumbH = if (dragging) 44.dp.toPx() else 40.dp.toPx()
        val x = position * size.width
        val r = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
        if (x - gap > 0f) drawRoundRect(active, androidx.compose.ui.geometry.Offset(0f, cy - trackH / 2), androidx.compose.ui.geometry.Size((x - gap).coerceAtLeast(trackH / 2), trackH), r)
        if (x + gap < size.width) drawRoundRect(inactive, androidx.compose.ui.geometry.Offset(x + gap, cy - trackH / 2), androidx.compose.ui.geometry.Size(size.width - x - gap, trackH), r)
        drawCircle(active, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width - trackH / 2, cy))
        drawRoundRect(active, androidx.compose.ui.geometry.Offset(x - thumbW / 2, cy - thumbH / 2), androidx.compose.ui.geometry.Size(thumbW, thumbH), androidx.compose.ui.geometry.CornerRadius(thumbW / 2))
    }
}
