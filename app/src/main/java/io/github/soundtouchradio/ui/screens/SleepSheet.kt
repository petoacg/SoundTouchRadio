package io.github.soundtouchradio.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import io.github.soundtouchradio.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.soundtouchradio.ui.components.NoSheetInsets
import io.github.soundtouchradio.ui.components.sheetBottomInset
import io.github.soundtouchradio.ui.components.OutlinedButton
import io.github.soundtouchradio.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.soundtouchradio.player.SleepPlan
import io.github.soundtouchradio.player.SleepState
import io.github.soundtouchradio.ui.theme.Tabular
import java.text.DateFormat
import java.util.Date

/** «Apagar en…»: chips de minutos, bajada gradual opcional, una única acción verde. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepSheet(active: SleepState?, onStart: (Int, Boolean) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var fade by rememberSaveable { mutableStateOf(true) }
    // Android 13+: la notificación del temporizador necesita permiso. Si se niega, el temporizador funciona igual.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onStart(minutes, fade); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { NoSheetInsets }) {
        Column(Modifier.padding(bottom = sheetBottomInset())) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_apagar_en), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (active != null) {
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(active.endsAtMillis))
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_se_apaga_a_las, (active.speakerName).toString(), (time).toString()), style = MaterialTheme.typography.bodyLarge.merge(io.github.soundtouchradio.ui.theme.ProseNumbers),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                OutlinedButton(onClick = { onCancel(); onDismiss() }, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_cancelar_temporizador)).uppercase())
                }
                return@Column
            }
            // Contenido desplazable y «Iniciar» fijo debajo: siempre visible, también con fuente grande (1.9.2).
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            // Una sola fila: selector segmentado de Material, minutos en cifras tabulares.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SleepPlan.CHOICES.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = minutes == m, onClick = { minutes = m },
                        shape = SegmentedButtonDefaults.itemShape(i, SleepPlan.CHOICES.size, baseShape = io.github.soundtouchradio.ui.components.KeyShape),
                        icon = {},
                        modifier = Modifier.semantics { contentDescription = "$m minutos" },
                    ) { Text("$m", style = MaterialTheme.typography.labelLarge.merge(Tabular)) }
                }
            }
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_minutos), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 4.dp))
            ListItem(
                modifier = Modifier.toggleable(fade, role = Role.Switch) { fade = it },
                colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                headlineContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_bajar_el_volumen_poco_a)) },
                supportingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_durante_el_ultimo_minuto_despues)) },
                trailingContent = { Switch(checked = fade, onCheckedChange = null) },
            )
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_el_temporizador_funciona_en_este),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
            Button(
                onClick = {
                    val needs = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needs) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else { onStart(minutes, fade); onDismiss() }
                },
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
            ) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_iniciar_apagar_en_min, (minutes).toString())).uppercase()) }
        }
    }
}

