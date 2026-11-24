package io.github.soundtouchradio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.ui.theme.Readout

/*
 * Controles del frontal (1.15.0). Toda la app es un mismo aparato: estas piezas sustituyen a los controles de Material
 * con el mismo nombre y la misma firma, de modo que cada pantalla las usa sin cambiar su lógica ni su accesibilidad.
 *
 * Gramática del frontal:
 *  - TECLA: rectángulo de radio 4 dp (nunca píldora). Acción principal rellena en verde; secundaria con contorno de
 *    1 dp; terciaria solo rótulo. Sin sombras.
 *  - PILOTO: cuadradito. Encendido en su color (verde = activo o confirmado, ámbar tenue = espera, ámbar = aviso,
 *    rojo = fallo), apagado en gris. Es el único indicador de estado: sustituye a los ✓, a los círculos de selección y
 *    a las píldoras de los interruptores.
 *  - RÓTULO: mayúsculas en Plex Mono con algo de espaciado, como la serigrafía de un equipo. Sin pictogramas: solo los
 *    signos que llevan los equipos (transporte ❚❚ ▶ ■, encendido ⏻, ▾ de desplegable, flecha Atrás del sistema, ⋮).
 */

val KeyShape = RoundedCornerShape(4.dp)

/** Rótulo serigrafiado: mayúsculas en mono con espaciado de 0,08 em. */
@Composable
fun engravedStyle(size: TextUnit = TextUnit(11f, TextUnitType.Sp)) =
    MaterialTheme.typography.labelMedium.merge(Readout).copy(fontSize = size, letterSpacing = TextUnit(0.08f, TextUnitType.Em))

/** Piloto cuadrado. [lit] en [color]; apagado en gris. */
@Composable
fun PilotSquare(lit: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 7.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(1.dp)).background(if (lit) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

/** Etiqueta serigrafiada en un recuadro de 1 dp (WPA, ALARMA, 1 VEZ…). */
@Composable
fun EngravedTag(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(java.util.Locale.ROOT), style = engravedStyle(TextUnit(9f, TextUnitType.Sp)), color = color, maxLines = 1,
        modifier = modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Escala de pilotos de señal (Wi-Fi): [bars] cuadraditos en escalera; se encienden [level] (0–[bars]).
 * Pocos pilotos encendidos = ámbar; el resto en tinta. Sin dibujo de antena.
 */
@Composable
fun SignalScale(level: Int, modifier: Modifier = Modifier, bars: Int = 4) {
    val cs = MaterialTheme.colorScheme
    val on = if (level <= 1) cs.tertiary else cs.onSurface
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until bars) Box(
            Modifier.size(width = 4.dp, height = (5 + i * 3).dp).background(if (i < level) on else cs.outlineVariant.copy(alpha = 0.5f)),
        )
    }
}

// ---------- Teclas (sustituyen a los botones de Material con la misma firma) ----------

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Principal (1.19.0): rellena en tinta clara con letra oscura. El verde queda para lo que está en marcha.
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = KeyShape, colors = colors,
    elevation = null, border = border, contentPadding = contentPadding, content = { androidx.compose.material3.ProvideTextStyle(engravedStyle(TextUnit(12f, TextUnitType.Sp))) { content() } },
)

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.FilledTonalButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = KeyShape, colors = colors,
    elevation = null, border = border, contentPadding = contentPadding, content = { androidx.compose.material3.ProvideTextStyle(engravedStyle(TextUnit(12f, TextUnitType.Sp))) { content() } },
)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = KeyShape, colors = colors,
    elevation = null,
    border = border ?: BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
    contentPadding = contentPadding, content = { androidx.compose.material3.ProvideTextStyle(engravedStyle(TextUnit(12f, TextUnitType.Sp))) { content() } },
)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(
    // Sin texto suelto (1.17.0): toda acción secundaria es una tecla con contorno de 1 dp, para que nunca se lea
    // como un título o un enlace.
    onClick = onClick, modifier = modifier, enabled = enabled, shape = KeyShape, colors = colors,
    elevation = null,
    border = border ?: BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
    contentPadding = contentPadding, content = { androidx.compose.material3.ProvideTextStyle(engravedStyle(TextUnit(12f, TextUnitType.Sp))) { content() } },
)

// ---------- Interruptor: tecla con enclavamiento y su piloto ----------

/**
 * Interruptor del frontal: tecla de 1 dp con piloto y rótulo ON / OFF (nunca la píldora de Material). Encendido = piloto
 * verde. Misma firma que el Switch de Material: con [onCheckedChange] nulo, la fila que lo contiene gestiona el toque.
 */
@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val ink = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f)
    val touch = if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier
    Box(modifier.then(touch).height(48.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.border(1.dp, if (enabled) cs.outline else cs.outlineVariant, RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PilotSquare(checked && enabled, color = cs.primary)
            Spacer(Modifier.width(6.dp))
            Text(if (checked) "ON" else "OFF", style = engravedStyle(), color = if (checked && enabled) cs.primary else ink,
                modifier = Modifier.width(24.dp), maxLines = 1)
        }
    }
}

// ---------- Selectores: piloto en lugar del círculo y de la marca ----------

/**
 * Selector de una opción (sustituye al RadioButton): un recuadro de 1 dp con el piloto dentro, encendido en verde si
 * es la opción elegida. Como las teclas de selección de canal con LED.
 */
@Composable
fun RadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val touch = if (onClick != null) Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick) else Modifier
    // Solo el piloto (1.17.0): apagado en gris, encendido en verde. Sin recuadro, para no confundirse con la casilla de
    // selección múltiple.
    Box(modifier.then(touch).size(24.dp), contentAlignment = Alignment.Center) {
        PilotSquare(selected, size = 11.dp, color = if (enabled) cs.primary else cs.outline)
    }
}

/**
 * Desplegable de frontal (1.17.0): fila a todo el ancho con contorno de 1 dp, piloto encendido si está abierta, el
 * rótulo en verde y el signo ▾ / ▴ a la derecha. No se confunde con un título: tiene forma de tecla y dice su estado.
 */
@Composable
fun Disclosure(label: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(KeyShape)
            .border(1.dp, cs.outline, KeyShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) io.github.soundtouchradio.ui.Txt.s(io.github.soundtouchradio.R.string.ui_desplegado) else io.github.soundtouchradio.ui.Txt.s(io.github.soundtouchradio.R.string.ui_plegado) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PilotSquare(expanded)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
    }
}

/**
 * Tecla «+» (1.18.0; tinta desde la 1.19.0): cuadradito de 30 dp con contorno de 1 dp y el signo + en tinta, dentro de un área táctil
 * de 48 dp. Para acciones secundarias que no deben competir con el contenido (añadir emisora).
 */
@Composable
fun AddKey(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    // Tinta (1.19.0): es una acción, no un estado; el ámbar queda para la espera.
    val amber = MaterialTheme.colorScheme.onSurface
    Box(
        modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).border(1.dp, MaterialTheme.colorScheme.outline, KeyShape), contentAlignment = Alignment.Center) {
            Text("+", style = MaterialTheme.typography.titleMedium.merge(Readout), color = amber)
        }
    }
}

/** Selección múltiple (sustituye al Checkbox): mismo recuadro; el piloto lleno marca lo elegido. */
@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val touch = if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange) else Modifier
    Box(modifier.then(touch).size(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).border(1.dp, if (enabled) cs.outline else cs.outlineVariant, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
            if (checked) Box(Modifier.size(10.dp).background(if (enabled) cs.primary else cs.outline))
        }
    }
}

/** Marca de opción activa en chips y teclas segmentadas: el piloto en lugar de ✓. */
@Composable
fun SelectedPilot() = PilotSquare(true, Modifier.padding(end = 2.dp), size = 7.dp)
