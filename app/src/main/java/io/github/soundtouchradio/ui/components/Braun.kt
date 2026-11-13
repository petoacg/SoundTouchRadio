package io.github.soundtouchradio.ui.components

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.ui.theme.Tabular

enum class Pilot { OFF, ON, BUSY, WARN, ERROR }

/** Luz piloto: verde = confirmado por el altavoz. Gris = apagado. Ámbar = aviso. Rojo = fallo. */
@Composable
fun PilotLight(state: Pilot, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val cs = MaterialTheme.colorScheme
    val color = when (state) {
        Pilot.ON -> cs.primary
        Pilot.BUSY -> cs.primary.copy(alpha = 0.45f)
        Pilot.WARN -> cs.tertiary
        Pilot.ERROR -> cs.error
        Pilot.OFF -> cs.outlineVariant
    }
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

enum class GrilleMode { IDLE, PLAYING, LOADING, ERROR }

/**
 * Gesto propio: la rejilla de perforaciones del altavoz como indicador real.
 * Puntos verdes ∝ volumen; laten solo con reproducción confirmada; barren filas al cargar;
 * grises detenido; contorno rojo en error. Estático si «Quitar animaciones».
 */
@Composable
fun SpeakerGrille(volume: Int?, mode: GrilleMode, description: String, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val cs = MaterialTheme.colorScheme
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "grille")
    // Los State de la animación se leen dentro del Canvas (fase de dibujo): solo se redibuja, no se recompone.
    // Solo existen mientras suena o carga: en reposo no hay animación ni redibujado.
    val pulseState = if (!reduce && mode == GrilleMode.PLAYING)
        transition.animateFloat(0.72f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse") else null
    val sweepState = if (!reduce && mode == GrilleMode.LOADING)
        transition.animateFloat(0f, N.toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "sweep") else null

    val lit = ((volume ?: 0).coerceIn(0, 100) / 100f * N * N).toInt()
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val pulse = pulseState?.value ?: 1f
        val sweep = sweepState?.value ?: -1f
        val step = this.size.minDimension / N
        val r = step * 0.3f
        for (row in 0 until N) for (col in 0 until N) {
            // Se llena desde abajo, como un nivel.
            val index = (N - 1 - row) * N + col
            val center = Offset(step * (col + 0.5f), step * (row + 0.5f))
            val on = index < lit
            val sweepRow = sweep >= 0 && (N - 1 - row) == sweep.toInt() % N
            val color = when (mode) {
                GrilleMode.PLAYING -> if (on) cs.primary.copy(alpha = pulse) else cs.outlineVariant
                GrilleMode.LOADING -> if (sweepRow) cs.primary.copy(alpha = 0.7f) else cs.outlineVariant
                GrilleMode.ERROR -> cs.outlineVariant
                GrilleMode.IDLE -> if (on) cs.onSurfaceVariant.copy(alpha = 0.55f) else cs.outlineVariant
            }
            drawCircle(color, r, center)
            if (mode == GrilleMode.ERROR) drawCircle(cs.error, r, center, style = Stroke(width = 1.5f.dp.toPx()))
        }
    }
}

private const val N = 6

/** Tecla de preset estilo aparato: circular, número tabular, nombre corto debajo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetKey(
    number: Int,
    label: String?,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val filled = label != null
    val desc = if (filled) Txt.s(R.string.ui_preset_lleno, number, label, if (playing) Txt.s(R.string.ui_coma_sonando) else "")
    else Txt.s(R.string.ui_preset_vacio_manten_pulsado_para, number)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(if (filled) Modifier.background(cs.secondaryContainer) else Modifier.border(1.dp, cs.outline, CircleShape))
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = Txt.s(R.string.ui_cambiar_preset)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                color = if (filled) cs.onSecondaryContainer else cs.onSurfaceVariant,
            )
            if (playing) PilotLight(Pilot.ON, Modifier.align(Alignment.TopCenter).padding(top = 7.dp), size = 7.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label ?: "—",
            color = if (filled) cs.onSurface else cs.onSurfaceVariant,
            // Siempre dos líneas reservadas: las teclas de la fila siguiente quedan alineadas (1.9.0).
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.15f,
            // Corte equilibrado entre palabras («RNE / Radio 5») y nunca con guion dentro de una palabra.
            style = MaterialTheme.typography.labelSmall.copy(lineBreak = LineBreak.Heading, hyphens = Hyphens.None),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() }.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}



/**
 * Encendido del altavoz. Verde relleno = el altavoz confirma que está encendido (misma semántica que
 * la luz piloto); contorno gris = en reposo; anillo de progreso mientras cambia; desactivado si no se sabe.
 */
@Composable
fun PowerButton(on: Boolean?, pending: Boolean, speakerName: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val desc = if (on == true) "Apagar $speakerName" else "Encender $speakerName"
    val state = when { pending -> Txt.s(R.string.ui_cambiando); on == true -> Txt.s(R.string.ui_encendido); on == false -> Txt.s(R.string.ui_en_reposo); else -> Txt.s(R.string.ui_estado_desconocido) }
    val a11y = Modifier.semantics { contentDescription = desc; stateDescription = state }
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (on == true) androidx.compose.material3.FilledIconButton(
            onClick = { onToggle(false) }, enabled = !pending, modifier = a11y,
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.PowerSettingsNew, contentDescription = null) }
        else androidx.compose.material3.OutlinedIconButton(
            onClick = { onToggle(true) }, enabled = on != null && !pending, modifier = a11y,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (on != null && !pending) cs.outline else cs.outlineVariant),
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.PowerSettingsNew, contentDescription = null, tint = cs.onSurfaceVariant) }
        if (pending) androidx.compose.material3.CircularProgressIndicator(Modifier.size(46.dp), strokeWidth = 2.dp, color = cs.primary)
    }
}
