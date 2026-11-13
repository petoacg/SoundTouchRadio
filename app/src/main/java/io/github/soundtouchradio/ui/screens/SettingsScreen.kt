package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.BuildConfigShim
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ajustes_2)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            if (Build.VERSION.SDK_INT >= 31) ListItem(
                modifier = Modifier.toggleable(s.dynamicColor, role = Role.Switch, onValueChange = vm::setDynamicColor),
                colors = ListItemDefaults.colors(containerColor = cs.surface),
                headlineContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_colores_del_fondo_de_pantalla)) },
                supportingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_sustituye_el_gris_y_verde)) },
                trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = null) },
            )
            ListItem(
                modifier = Modifier.toggleable(s.multiroomExperimental, role = Role.Switch, onValueChange = vm::setMultiroom),
                colors = ListItemDefaults.colors(containerColor = cs.surface),
                headlineContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_multiroom_experimental)) },
                supportingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_agrupar_altavoces_con_la_api)) },
                trailingContent = { Switch(checked = s.multiroomExperimental, onCheckedChange = null) },
            )
            ListItem(
                modifier = Modifier.toggleable(s.httpsBridge != false, role = Role.Switch) { on -> vm.setHttpsBridge(if (on) null else false) },
                colors = ListItemDefaults.colors(containerColor = cs.surface),
                headlineContent = { Text(Txt.s(R.string.ui_ajuste_puente)) },
                supportingContent = { Text(Txt.s(R.string.ui_ajuste_puente_detalle)) },
                trailingContent = { Switch(checked = s.httpsBridge != false, onCheckedChange = null) },
            )
            if (Build.VERSION.SDK_INT >= 33) LanguagePicker()
            SectionTitle(Txt.s(R.string.ui_volumen))
            Body(Txt.s(R.string.ui_cuanto_cambia_cada_pulsacion_de))
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val steps = listOf(1, 2, 3, 5)
                steps.forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = s.volumeStep == n, onClick = { vm.setVolumeStep(n) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, steps.size),
                        label = { Text(if (n == 1) Txt.s(R.string.ui_1_punto) else "$n", style = MaterialTheme.typography.labelLarge.merge(io.github.soundtouchradio.ui.theme.Tabular)) },
                    )
                }
            }

            TextButton(onClick = vm::restoreSeedStations, modifier = Modifier.padding(horizontal = 8.dp)) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_restaurar_emisoras_precargadas_2)) }

            SectionTitle(Txt.s(R.string.ui_privacidad))
            Body(Txt.s(R.string.ui_sin_cuentas_sin_analitica_sin))

            SectionTitle(Txt.s(R.string.ui_como_funciona))
            Body(Txt.s(R.string.ui_el_altavoz_descarga_el_stream))

            SectionTitle(Txt.s(R.string.ui_acerca_de))
            Body(Txt.s(R.string.ui_soundtouch_radio_software_libre_con, BuildConfigShim.VERSION))
            Body(Txt.s(R.string.ui_mit_license_se_concede_permiso))
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

/** Idioma de la app (Android 13+): «Como el móvil», español o inglés. Android recrea la pantalla al cambiarlo. */
@androidx.annotation.RequiresApi(33)
@Composable
private fun LanguagePicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lm = remember { context.getSystemService(android.app.LocaleManager::class.java) }
    val current = remember { lm?.applicationLocales?.toLanguageTags().orEmpty().substringBefore('-') }
    SectionTitle(Txt.s(R.string.ui_idioma))
    val options = listOf("" to Txt.s(R.string.ui_idioma_sistema), "es" to "Español", "en" to "English")
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        options.forEachIndexed { i, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = {
                    if (current != tag) lm?.applicationLocales =
                        if (tag.isEmpty()) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
                },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}
