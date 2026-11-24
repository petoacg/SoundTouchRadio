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
import io.github.soundtouchradio.ui.components.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import io.github.soundtouchradio.ui.components.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.github.soundtouchradio.ui.components.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.player.Phase
import io.github.soundtouchradio.player.SpeakerState
import io.github.soundtouchradio.playlist.StreamWarning
import io.github.soundtouchradio.ui.Copy
import io.github.soundtouchradio.ui.FixAction
import io.github.soundtouchradio.ui.components.GrilleMode
import io.github.soundtouchradio.ui.components.SpeakerGrille
import io.github.soundtouchradio.ui.components.Annunciator
import io.github.soundtouchradio.ui.components.ReadoutLine
import io.github.soundtouchradio.ui.components.readoutJoin
import io.github.soundtouchradio.ui.components.standbyColor
import io.github.soundtouchradio.ui.components.drawFaderCap
import io.github.soundtouchradio.ui.components.drawFaderRail
import io.github.soundtouchradio.ui.components.drawPilotScale
import io.github.soundtouchradio.ui.components.PanelKey
import androidx.compose.material3.OutlinedIconButton
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
    val phase = state.phase
    Column(modifier.fillMaxWidth()) {
    // Avisos y fallos (1.13.0): van en una franja ENCIMA del panel, nunca dentro. El panel es un frontal de tamaño fijo:
    // no crece ni encoge al cambiar de estado o de emisora.
    if (speaker != null) Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
        // Avisos del stream que suena (ámbar).
        val warn = state.resolved?.warnings.orEmpty()
        AnimatedVisibility(phase is Phase.Playing && (StreamWarning.HTTPS in warn)) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_suena_con_https_si_se), style = MaterialTheme.typography.bodySmall,
                color = cs.tertiary, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(12.dp))
        }

        // Fallo: causa y una acción.
        if (phase is Phase.Failed) {
            val copy = Copy.cause(phase.cause)
            Surface(color = cs.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
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
                    ) { Text((copy.actionLabel!!).uppercase()) }
                }
                }
            }
        } else if (phase is Phase.Standby && state.powerTarget == null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onFix(FixAction.POWER) }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_encender)).uppercase()) }
            }
        } else if (phase is Phase.Unreachable) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onFix(FixAction.LINK) }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_buscar_altavoz)).uppercase()) }
            }
        }

    }
    Surface(
        color = cs.surfaceContainerHigh,
        // Esquinas de 16 dp y una junta de 1 dp en el borde (1.12.0): pieza de la misma carcasa, no una tarjeta flotante.
        shape = if (docked) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
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
                    Button(onClick = onLink) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vincular)).uppercase()) }
                }
                return@Column
            }

            val playing = phase is Phase.Playing
            val failed = phase is Phase.Failed || phase is Phase.Unreachable
            val busy = state.powerTarget != null || phase.isBusy || phase is Phase.Buffering
            // Espera: listo, detenido, en pausa o en reposo. Piloto ámbar tenue, rejilla quieta en ámbar tenue.
            val standingBy = !playing && !failed && !busy
            val standby = standbyColor()
            val mode = when {
                busy -> GrilleMode.LOADING
                playing -> GrilleMode.PLAYING
                failed -> GrilleMode.ERROR
                else -> GrilleMode.STANDBY
            }
            val title = when (phase) {
                is Phase.OtherSource -> phase.title ?: Copy.sourceName(phase.source)
                Phase.Standby, Phase.Unreachable -> speaker.name
                else -> state.stationName ?: speaker.name
            }
            val statusColor = when {
                playing -> cs.primary
                failed -> cs.error
                busy -> cs.onSurfaceVariant
                else -> standby
            }
            val tech = state.resolved?.let { r ->
                listOfNotNull(
                    io.github.soundtouchradio.playlist.UrlRules.codecLabel(r.mime),
                    r.probe?.icyBitrate?.let { "$it\u00A0kbps" },
                )
            }.orEmpty()
            val statusParts = buildList {
                add(state.powerTarget?.let(Copy::powerLine) ?: Copy.phaseLine(phase))
                if (phase is Phase.Playing || phase is Phase.Paused) addAll(tech)
                when {
                    sleep?.finishing == true -> add(Txt.s(R.string.ui_apagando).trimStart(' ', '·'))
                    sleepLeft != null -> add(Txt.s(R.string.ui_se_apaga_en_min, sleepLeft).trimStart(' ', '·'))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakerGrille(
                    state.volume, mode,
                    "Volumen ${state.volume ?: "desconocido"} %, ${Copy.phaseLine(phase).lowercase()}",
                    size = 56.dp,
                )
                Spacer(Modifier.width(10.dp))
                // Ventana de lectura (1.12.0; fija desde la 1.13.0): tres líneas que no cambian nunca de número ni de
                // tamaño. Nombre y estado en una línea cada uno, que se desplaza despacio si no cabe; debajo, los pilotos.
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surfaceContainerLowest)
                        .border(1.dp, cs.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    ReadoutLine(androidx.compose.ui.text.AnnotatedString(title), MaterialTheme.typography.titleMedium, cs.onSurface)
                    ReadoutLine(
                        readoutJoin(statusParts), MaterialTheme.typography.labelMedium.merge(io.github.soundtouchradio.ui.theme.Readout), statusColor,
                        Modifier.padding(top = 1.dp).semantics { stateDescription = Copy.phaseLine(phase) },
                    )
                    // Pilotos rotulados: siempre los mismos y en el mismo sitio; solo se encienden o se apagan. El temporizador
                    // no lleva piloto: ya lo dicen la luna rellena y los minutos de la línea de estado.
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Annunciator(Txt.s(R.string.ui_piloto_aire), playing, cs.primary)
                        Annunciator(Txt.s(R.string.ui_piloto_espera), standingBy, standby)
                        if (speaker.type == "phone") Annunciator(Txt.s(R.string.ui_piloto_datos), state.phoneOnCellular, cs.tertiary)
                    }
                }
                val active = phase is Phase.Playing || phase is Phase.Buffering || phase.isBusy
                // Transporte en huecos fijos (1.14.0): [pausa/continuar] y parada, con sus signos de siempre (❚❚ ▶ ■), que
                // son los únicos que llevan los equipos. Contorno de 1 dp; solo Stop se rellena en verde si hay algo que
                // parar. Un mando se desactiva, no desaparece.
                Spacer(Modifier.width(8.dp))
                val pauseSlot = speaker.type == "phone" || state.pauseAvailable
                val ring = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
                if (pauseSlot) {
                    if (phase is Phase.Paused) OutlinedIconButton(onClick = onResume, border = ring, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = Txt.s(R.string.ui_continuar))
                    } else OutlinedIconButton(onClick = onPause, enabled = phase is Phase.Playing, modifier = Modifier.size(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (phase is Phase.Playing) cs.outline else cs.outlineVariant)) {
                        Icon(Icons.Filled.Pause, contentDescription = Txt.s(R.string.ui_pausa))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (!pauseSlot && phase is Phase.Paused) {
                    OutlinedIconButton(onClick = onResume, border = ring, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = Txt.s(R.string.ui_continuar))
                    }
                } else {
                    val canStop = active || phase is Phase.Paused
                    if (canStop) FilledIconButton(
                        onClick = onStop, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                    ) { Icon(Icons.Filled.Stop, contentDescription = Txt.s(R.string.ui_detener)) }
                    else OutlinedIconButton(onClick = onStop, enabled = false, modifier = Modifier.size(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant)) {
                        Icon(Icons.Filled.Stop, contentDescription = Txt.s(R.string.ui_detener))
                    }
                }
            }

            // Fader (1.14.0): a todo el ancho, con la escala de pilotos encima, la regla debajo y la cifra a la derecha.
            // Sin teclas − / + y sin pictogramas. Curva y arrastre solo desde el capuchón (seguridad nocturna, 1.8.2).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                val volEnabled = state.volume != null && state.reachable
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                VolumeBar(
                    volume = state.volume ?: 0, pending = state.volumePending, enabled = volEnabled, muted = state.muted,
                    onVolume = { v -> if (v != state.volume) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onVolume(v) } },
                    onStep = onVolumeStep,
                    modifier = Modifier.weight(1f),
                )
                // Cifra en dos estados (1.8.1): tenue = pedido, aún sin confirmar; normal = el altavoz devuelve ese valor.
                Text(
                    state.volume?.let { "$it" } ?: "—",
                    style = MaterialTheme.typography.titleSmall.merge(io.github.soundtouchradio.ui.theme.Readout),
                    color = if (state.volumePending) cs.onSurfaceVariant.copy(alpha = 0.45f) else cs.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.widthIn(min = 36.dp).padding(start = 12.dp),
                )
            }

            // Tira de teclas rotuladas (1.14.0): MUTE · SLEEP · EQ. Sin dibujos, como en un frontal técnico: se aprende
            // qué hace cada una. Activa = cuadradito encendido. EQ solo con «Este móvil».
            val canSleep = sleep != null || phase is Phase.Playing || phase is Phase.Buffering || phase is Phase.Paused
            val sleepDesc = sleepLeft?.let { Txt.s(R.string.ui_temporizador_se_apaga_en_minutos, it) } ?: Txt.s(R.string.ui_temporizador_de_apagado)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelKey(Txt.s(R.string.ui_tecla_mute), onMute, active = state.muted, activeColor = cs.tertiary,
                    enabled = state.volume != null && state.reachable,
                    description = if (state.muted) Txt.s(R.string.ui_quitar_silencio) else Txt.s(R.string.ui_silenciar))
                PanelKey(Txt.s(R.string.ui_tecla_sleep), onSleep, active = sleep != null, enabled = canSleep, description = sleepDesc)
                if (speaker.type == "phone") PanelKey(Txt.s(R.string.ui_tecla_eq), onEq,
                    description = androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ecualizador))
            }
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
private fun VolumeBar(volume: Int, pending: Boolean, enabled: Boolean, onVolume: (Int) -> Unit, onStep: (Int) -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) {
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
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val scaleStyle = MaterialTheme.typography.labelSmall.merge(io.github.soundtouchradio.ui.theme.Readout).copy(color = cs.onSurfaceVariant, fontSize = 9.sp)
    val active = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f)
    val inactive = if (enabled) cs.surfaceContainerHighest else cs.onSurface.copy(alpha = 0.12f)
    androidx.compose.foundation.Canvas(
        modifier.height(56.dp)
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
        // Fader esquemático (1.14.0): arriba la escala de pilotos (20 cuadraditos, verde y ámbar desde 80), en medio el
        // carril de 2 dp con el tramo recorrido en verde y el capuchón plano, y debajo la regla con 0, 50 y 100.
        // Con MUTE, los pilotos se apagan: el nivel sigue marcado por el capuchón, pero no suena.
        val cy = 22.dp.toPx()
        val ticks = (0..100 step 10).map { io.github.soundtouchradio.player.VolumeCurve.toPosition(it) }
        drawPilotScale(if (muted) 0f else position, 2.dp.toPx(), active, cs.tertiary, cs.outlineVariant.copy(alpha = 0.4f),
            warnFrom = io.github.soundtouchradio.player.VolumeCurve.toPosition(80))
        drawFaderRail(position, cy, cs.outlineVariant, active, cs.outline, ticks, majors = setOf(0, 5, 10))
        val tickTop = cy + 14.dp.toPx()
        for (v in listOf(0, 50, 100)) {
            val tx = io.github.soundtouchradio.player.VolumeCurve.toPosition(v) * size.width
            val layout = measurer.measure("$v", scaleStyle)
            val lx = (tx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(lx, tickTop + 8.dp.toPx()))
        }
        val x = (position * size.width).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
        drawFaderCap(x, cy, if (enabled) cs.onSurface else cs.outline, if (enabled) cs.primary else null, pressed = dragging)
    }
}
