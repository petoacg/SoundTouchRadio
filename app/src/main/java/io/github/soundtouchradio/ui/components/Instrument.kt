package io.github.soundtouchradio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.ui.theme.Readout

/*
 * Piezas de «instrumento» (1.13.0): el lenguaje de los equipos de sonido y radiofonía clásicos, dibujado en plano y
 * digital. Nada imita el objeto (sin relieve, sin brillo, sin texturas): pilotos rotulados, corona de segmentos,
 * fader con su carril y escala. Animación mínima y siempre con «Quitar animaciones» respetado.
 */

/**
 * Ámbar tenue de espera: el piloto de *standby* de los equipos de los 70 a los 90. Listo, detenido, en pausa o en
 * reposo. No late y es más apagado que el ámbar pleno de los avisos (https, datos móviles), que siempre llevan texto.
 */
@Composable
fun standbyColor(): Color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.62f)

/**
 * Piloto indicador rotulado, como los «ON AIR», «REC» o «STEREO» de un frontal: un cuadradito y su rótulo fijo. Nunca
 * aparece ni desaparece: se enciende en su color o queda apagado, así el panel no cambia.
 */
@Composable
fun Annunciator(label: String, lit: Boolean, color: Color, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(if (lit) color else cs.outlineVariant.copy(alpha = 0.45f)))
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.merge(Readout).copy(
                fontSize = TextUnit(9f, TextUnitType.Sp), letterSpacing = TextUnit(0.02f, TextUnitType.Em),
            ),
            color = if (lit) color else cs.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
        )
    }
}

/**
 * Línea de lectura de una sola línea: si no cabe, se desplaza despacio, como el rótulo de un display de matriz de
 * puntos. Con «Quitar animaciones», se corta con puntos suspensivos. Mantiene siempre la misma altura.
 */
@Composable
fun ReadoutLine(text: androidx.compose.ui.text.AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    Text(
        text, style = style, color = color, maxLines = 1, softWrap = false,
        overflow = if (reduce) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = if (reduce) modifier else modifier.basicMarquee(
            iterations = Int.MAX_VALUE, initialDelayMillis = 2_000, repeatDelayMillis = 4_000, velocity = 22.dp,
        ),
    )
}

/** Une datos de lectura con « · » en la letra normal: en mono, el punto con espacios ocuparía tres caracteres anchos. */
fun readoutJoin(parts: List<String>): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    parts.filter { it.isNotBlank() }.forEachIndexed { i, p ->
        if (i > 0) withStyle(androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Default)) { append(" · ") }
        append(p)
    }
}

/**
 * Corona de segmentos alrededor de un mando redondo, como el anillo de LED de un potenciómetro de mesa de los 80.
 * 11 segmentos en 270°, abiertos abajo. [level] 0–100 enciende los segmentos en proporción; los dos últimos, ámbar.
 */
@Composable
fun SegmentRing(level: Int, modifier: Modifier = Modifier, stroke: Dp = 3.dp) {
    val cs = MaterialTheme.colorScheme
    val lit = Math.round(level.coerceIn(0, 100) / 100f * RING_SEGMENTS)
    Canvas(modifier) {
        val sw = stroke.toPx()
        val inset = sw / 2
        val each = 270f / RING_SEGMENTS
        val gap = 4f
        for (i in 0 until RING_SEGMENTS) {
            val on = i < lit
            val color = when {
                !on -> cs.outlineVariant.copy(alpha = 0.35f)
                i >= RING_SEGMENTS - 2 -> cs.tertiary
                else -> cs.primary
            }
            drawArc(
                color, startAngle = 135f + i * each + gap / 2, sweepAngle = each - gap, useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - sw, size.height - sw),
                style = Stroke(width = sw, cap = StrokeCap.Butt),
            )
        }
    }
}

const val RING_SEGMENTS = 11

/**
 * Capuchón de fader (1.14.0): esquemático. Un rectángulo plano de 8 × 22 dp, sin estrías, borde ni relieve; si mide
 * algo confirmado, una muesca índice en [index] cruza su centro. Es el signo del fader, no el dibujo de uno.
 */
fun DrawScope.drawFaderCap(x: Float, cy: Float, body: Color, index: Color?, pressed: Boolean = false) {
    val w = 8.dp.toPx()
    val h = (if (pressed) 24.dp else 22.dp).toPx()
    drawRoundRect(body, Offset(x - w / 2, cy - h / 2), Size(w, h), CornerRadius(1.5.dp.toPx()))
    if (index != null) drawLine(index, Offset(x - w / 2, cy), Offset(x + w / 2, cy), strokeWidth = 2.dp.toPx())
}

/**
 * Carril del fader (1.14.0): una línea de 2 dp, el tramo recorrido en [fill], y la regla debajo.
 * [ticks] son posiciones 0–1 de las marcas; [majors] las que van más largas.
 */
fun DrawScope.drawFaderRail(
    fraction: Float, cy: Float, rail: Color, fill: Color?, tick: Color, ticks: List<Float>, majors: Set<Int> = emptySet(),
) {
    val h = 2.dp.toPx()
    drawRect(rail, Offset(0f, cy - h / 2), Size(size.width, h))
    if (fill != null && fraction > 0f) drawRect(fill, Offset(0f, cy - h / 2), Size(size.width * fraction, h))
    val top = cy + 14.dp.toPx()
    ticks.forEachIndexed { i, t ->
        val tx = (t * size.width).coerceIn(0.5.dp.toPx(), size.width - 0.5.dp.toPx())
        val major = i in majors
        drawLine(tick.copy(alpha = if (major) 0.9f else 0.55f), Offset(tx, top), Offset(tx, top + (if (major) 6.dp else 3.dp).toPx()), strokeWidth = 1.dp.toPx())
    }
}

/**
 * Escala de pilotos (1.14.0): fila de [count] cuadraditos sobre el carril que repite, de reojo, el nivel que marca el
 * fader. No reacciona al sonido: se enciende hasta donde se ha puesto el fader a mano. Verde y, desde [warnFrom]
 * (posición 0–1), ámbar.
 */
fun DrawScope.drawPilotScale(fraction: Float, top: Float, lit: Color, warn: Color, off: Color, warnFrom: Float, count: Int = 20) {
    val cell = size.width / count
    val sq = minOf(cell - 2.dp.toPx(), 6.dp.toPx())
    for (i in 0 until count) {
        val center = (i + 0.5f) / count
        val on = center <= fraction
        val color = when { !on -> off; center >= warnFrom -> warn; else -> lit }
        drawRect(color, Offset(cell * i + (cell - sq) / 2, top), Size(sq, sq))
    }
}

/**
 * Tecla rotulada de frontal (1.14.0): MUTE, SLEEP, EQ. Sin pictogramas: un rótulo en mono dentro de un rectángulo de
 * 1 dp. Activa: un cuadradito encendido en [activeColor] delante del rótulo. Área táctil de 48 dp.
 */
@Composable
fun PanelKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false,
             activeColor: Color = MaterialTheme.colorScheme.onSurface, enabled: Boolean = true, description: String? = null) {
    val cs = MaterialTheme.colorScheme
    val ink = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f)
    Box(
        modifier
            .height(48.dp)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (description != null) contentDescription = description
                if (active) stateDescription = io.github.soundtouchradio.ui.Txt.s(io.github.soundtouchradio.R.string.ui_tecla_activa)
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .border(1.dp, if (enabled) cs.outline else cs.outlineVariant, RoundedCornerShape(3.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(if (active) activeColor else cs.outlineVariant.copy(alpha = 0.45f)))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.merge(Readout).copy(letterSpacing = TextUnit(0.08f, TextUnitType.Em)),
                color = if (active) activeColor else ink, maxLines = 1)
        }
    }
}

/**
 * Fader de mesa sobre el Slider de Material (despertador, ecualizador): mismo comportamiento y accesibilidad que el
 * control del sistema, con el capuchón y el carril del lenguaje de instrumento. [accent] rellena el carril en verde.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaderSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
    tickCount: Int = 10,
    centerMajor: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val fill = if (accent) cs.primary else null
    val ticks = (0..tickCount).map { it / tickCount.toFloat() }
    val majors = buildSet { add(0); add(tickCount); if (centerMajor || tickCount % 2 == 0) add(tickCount / 2) }
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = modifier,
        thumb = {
            Canvas(Modifier.size(width = 12.dp, height = 56.dp)) {
                drawFaderCap(size.width / 2, size.height / 2, cs.onSurface, if (accent) cs.primary else null)
            }
        },
        track = { state: SliderState ->
            val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                drawFaderRail(fraction, size.height / 2, cs.surfaceContainerHighest, fill, cs.outline, ticks, majors)
            }
        },
    )
}
