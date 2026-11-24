package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import io.github.soundtouchradio.ui.components.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.soundtouchradio.ui.components.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.soundtouchradio.ui.components.NoSheetInsets
import io.github.soundtouchradio.ui.components.sheetBottomInset
import io.github.soundtouchradio.ui.components.OutlinedButton
import io.github.soundtouchradio.ui.components.RadioButton
import androidx.compose.material3.Text
import io.github.soundtouchradio.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.data.SpeakerRecord
import io.github.soundtouchradio.data.Station
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.theme.Tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerSheet(vm: AppViewModel, speakers: List<SpeakerRecord>, active: SpeakerRecord?, onDismiss: () -> Unit, onLink: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone by vm.zone.collectAsStateWithLifecycle()
    val onPhone by vm.onPhone.collectAsStateWithLifecycle()
    val phoneState by vm.speakerState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { NoSheetInsets }) {
        Column(Modifier.padding(bottom = sheetBottomInset())) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_destino), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            var renaming by remember { mutableStateOf<SpeakerRecord?>(null) }
            speakers.forEach { s ->
                ListItem(
                    modifier = Modifier.selectable(selected = !onPhone && s.id == active?.id, role = Role.RadioButton) { vm.selectSpeaker(s.id); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                    leadingContent = { RadioButton(selected = !onPhone && s.id == active?.id, onClick = null) },
                    headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${s.type} · ${s.host}", style = MaterialTheme.typography.bodyMedium.merge(Tabular)) },
                    trailingContent = {
                        // Tecla rotulada en lugar del lápiz (1.15.0).
                        io.github.soundtouchradio.ui.components.PanelKey(Txt.s(R.string.ui_tecla_nombre), onClick = { renaming = s }, description = "Renombrar ${s.name}")
                    },
                )
            }
            // «Este móvil» (1.10.0): suena en el teléfono, por su altavoz, auriculares o un altavoz Bluetooth.
            ListItem(
                modifier = Modifier.selectable(selected = onPhone, role = Role.RadioButton) { vm.selectPhone(); onDismiss() },
                colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                leadingContent = { RadioButton(selected = onPhone, onClick = null) },
                headlineContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_este_movil)) },
                supportingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_este_movil_detalle,
                    if (onPhone) phoneState.phoneOutput ?: "—" else androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_salida_altavoz_movil))) },
            )
            renaming?.let { sp -> RenameDialog(sp, onDismiss = { renaming = null }) { name, done -> vm.renameSpeaker(sp.id, name, done) } }
            TextButton(onClick = onLink, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vincular_otro_altavoz)).uppercase())
            }

            if (settings.multiroomExperimental && active != null && speakers.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LaunchedEffect(active.id) { vm.refreshZone() }
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_multiroom_experimental_2), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_agrupa_con_como_principal_no, (active.name).toString()),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                val chosen = remember { mutableStateListOf<String>() }
                speakers.filter { it.id != active.id }.forEach { s ->
                    val inZone = zone?.members?.any { it.macAddress == s.mac } == true
                    ListItem(
                        modifier = Modifier.toggleable(value = s.id in chosen || inZone, role = Role.Checkbox, enabled = !inZone) {
                            if (it) chosen += s.id else chosen -= s.id
                        },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                        leadingContent = { Checkbox(checked = s.id in chosen || inZone, onCheckedChange = null, enabled = !inZone) },
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(if (inZone) Txt.s(R.string.ui_en_el_grupo) else s.host, style = MaterialTheme.typography.bodyMedium.merge(Tabular)) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if ((zone?.members?.size ?: 0) > 1) OutlinedButton(onClick = vm::dissolveZone) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_deshacer_grupo)).uppercase()) }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { vm.createZone(chosen.toList()); chosen.clear() }, enabled = chosen.isNotEmpty()) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_agrupar)).uppercase()) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSheet(slot: Int, current: Station?, stations: List<Station>, onDismiss: () -> Unit, onAssign: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { NoSheetInsets }) {
        Column(Modifier.padding(bottom = sheetBottomInset())) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_preset, (slot + 1).toString()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(current?.let { Txt.s(R.string.ui_ahora_elige_otra_emisora_o, it.name) } ?: Txt.s(R.string.ui_elige_la_emisora_para_esta),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(stations, key = { it.id }) { st ->
                    ListItem(
                        modifier = Modifier.selectable(selected = st.id == current?.id, role = Role.RadioButton) { onAssign(st.id) },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                        leadingContent = { RadioButton(selected = st.id == current?.id, onClick = null) },
                        headlineContent = { Text(st.name) },
                    )
                }
                if (current != null) item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { onAssign(null) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vaciar_preset, (slot + 1).toString())).uppercase(), color = cs.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignToPresetSheet(station: Station, presets: List<Station?>, onDismiss: () -> Unit, onAssign: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { NoSheetInsets }) {
        Column(Modifier.padding(bottom = sheetBottomInset())) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_guardar_en, (station.name).toString()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn { items(presets.size) { i -> val p = presets[i]
                ListItem(
                    modifier = Modifier.clickable { onAssign(i) },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                    leadingContent = { Text("${i + 1}", style = MaterialTheme.typography.titleMedium.merge(Tabular)) },
                    headlineContent = { Text(p?.name ?: Txt.s(R.string.ui_vacio), color = if (p == null) cs.onSurfaceVariant else cs.onSurface) },
                    supportingContent = if (p != null && p.id != station.id) ({ Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_se_sustituira)) }) else null,
                )
            } }
        }
    }
}


/** Renombrar el altavoz (se guarda en el propio altavoz). */
@Composable
private fun RenameDialog(sp: SpeakerRecord, onDismiss: () -> Unit, onSave: (String, (String?) -> Unit) -> Unit) {
    var name by remember { mutableStateOf(sp.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_nombre_del_altavoz)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it; error = io.github.soundtouchradio.soundtouch.SoundTouchXml.validateName(it) },
                    singleLine = true, isError = error != null, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_nombre)) },
                    supportingText = { Text(error ?: Txt.s(R.string.ui_se_guarda_en_el_altavoz)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && io.github.soundtouchradio.soundtouch.SoundTouchXml.validateName(name) == null && name.trim() != sp.name, onClick = {
                busy = true
                onSave(name) { e -> busy = false; if (e == null) onDismiss() else error = e }
            }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_guardar_2)).uppercase()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_cancelar_5)).uppercase()) } },
    )
}
