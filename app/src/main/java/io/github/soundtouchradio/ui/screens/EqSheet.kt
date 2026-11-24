package io.github.soundtouchradio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import io.github.soundtouchradio.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.R
import io.github.soundtouchradio.player.EqPlan
import io.github.soundtouchradio.player.EqSetting
import io.github.soundtouchradio.player.PhonePlayer
import io.github.soundtouchradio.ui.components.NoSheetInsets
import io.github.soundtouchradio.ui.components.sheetBottomInset
import io.github.soundtouchradio.ui.theme.Tabular
import kotlin.math.roundToInt

/**
 * Hoja «Sonido» de «Este móvil» (1.10.2): cuatro ajustes de un toque y, plegado, un ajuste fino con deslizadores
 * verticales, como los de un equipo de música. Sin verde en los deslizadores: el verde es «confirmado».
 * Un ajuste por salida (Edifier, auriculares, altavoz del teléfono).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqSheet(output: String, setting: EqSetting, bands: PhonePlayer.EqBands?, onChange: (EqSetting) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { NoSheetInsets }) {
        EqContent(output, setting, bands, onChange, Modifier.padding(bottom = sheetBottomInset()))
    }
}

/** Contenido de la hoja, sin la hoja (renderizable en pruebas). */
@Composable
internal fun EqContent(output: String, setting: EqSetting, bands: PhonePlayer.EqBands?, onChange: (EqSetting) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var fine by rememberSaveable { mutableStateOf(setting.preset == EqPlan.PERSONAL) }
    run {
        Column(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ui_sonido), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_eq_para, EqPlan.prettyName(output)), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (bands == null) {
                Text(stringResource(R.string.ui_eq_no_disponible), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            // Nombres de una palabra (caben siempre) y ✓ en el activo. «Mío» aparece cuando hay ajuste fino propio (1.10.3).
            val labels = mapOf(EqPlan.NEUTRO to R.string.ui_eq_neutro, EqPlan.VOZ to R.string.ui_eq_voz, EqPlan.GRAVES to R.string.ui_eq_graves,
                EqPlan.NOCHE to R.string.ui_eq_noche, EqPlan.PERSONAL to R.string.ui_eq_mio)
            val options = EqPlan.PRESETS + if (setting.levels.isNotEmpty()) listOf(EqPlan.PERSONAL) else emptyList()
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, p ->
                    SegmentedButton(selected = setting.preset == p, onClick = { onChange(setting.copy(preset = p)) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = io.github.soundtouchradio.ui.components.KeyShape),
                        icon = { if (setting.preset == p) io.github.soundtouchradio.ui.components.SelectedPilot() }) {
                        Text(stringResource(labels.getValue(p)), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            io.github.soundtouchradio.ui.components.Disclosure(stringResource(R.string.ui_eq_fino), fine, { fine = !fine })
            if (fine) {
                // Los deslizadores muestran el ajuste activo; al moverlos, se parte de él y pasa a «Mío».
                val current = EqPlan.levels(setting, bands.centersHz, bands.minMb, bands.maxMb)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    bands.centersHz.forEachIndexed { i, hz ->
                        val db = current[i] / 100f
                        val dbText = if (Math.round(db) == 0) "0 dB" else "%+.0f dB".format(db)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dbText, style = MaterialTheme.typography.labelMedium.merge(Tabular), color = cs.onSurfaceVariant)
                            // Fader vertical de mesa (1.13.0): capuchón con su línea índice, carril y escala con el 0 dB marcado.
                            io.github.soundtouchradio.ui.components.FaderSlider(
                                value = current[i].toFloat(), valueRange = bands.minMb.toFloat()..bands.maxMb.toFloat(),
                                onValueChange = { v ->
                                    val next = current.toMutableList().also { it[i] = (v / 100f).roundToInt() * 100 }
                                    onChange(EqSetting(EqPlan.PERSONAL, next))   // el nuevo «Mío» sustituye al anterior
                                },
                                accent = false, tickCount = 6, centerMajor = true,
                                modifier = Modifier.vertical().width(160.dp).height(56.dp)
                                    .semantics { contentDescription = EqPlan.bandLabel(hz); stateDescription = dbText },
                            )
                            Text(EqPlan.bandLabel(hz), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(stringResource(R.string.ui_eq_nota), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Gira un control horizontal 90° (de abajo arriba) y le da su tamaño en vertical. */
private fun Modifier.vertical() = graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin(0f, 0f) }
    .layout { measurable, c ->
        val p = measurable.measure(Constraints(minWidth = c.minHeight, maxWidth = c.maxHeight, minHeight = c.minWidth, maxHeight = c.maxWidth))
        layout(p.height, p.width) { p.place(-p.width, 0) }
    }
