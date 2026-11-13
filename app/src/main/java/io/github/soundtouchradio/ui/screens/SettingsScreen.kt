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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
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
    val language = if (Build.VERSION.SDK_INT >= 33) rememberLanguageState() else null
    SettingsContent(
        s, language, snackbar, onBack,
        onDynamicColor = vm::setDynamicColor, onMultiroom = vm::setMultiroom,
        onHttpsBridge = { on -> vm.setHttpsBridge(if (on) null else false) },
        onVolumeStep = vm::setVolumeStep, onRestoreSeeds = vm::restoreSeedStations,
    )
}

/**
 * Idioma de la app (1.11.1). [selected] es "" (como el móvil), "es" o "en"; [systemUses] es el idioma que la app
 * usa al seguir al móvil (español si el móvil está en uno que la app no tiene).
 */
data class LanguageState(val selected: String, val systemUses: String, val onSelect: (String) -> Unit)

@androidx.annotation.RequiresApi(33)
@Composable
private fun rememberLanguageState(): LanguageState? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lm = remember { context.getSystemService(android.app.LocaleManager::class.java) } ?: return null
    return remember {
        val selected = lm.applicationLocales.toLanguageTags().substringBefore(',').substringBefore('-')
        val system = lm.systemLocales.get(0)?.language.orEmpty()
        LanguageState(selected, if (system in SUPPORTED) system else "es") { tag ->
            lm.applicationLocales = if (tag.isEmpty()) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
        }
    }
}

private val SUPPORTED = listOf("es", "en")

/** Nombre de cada idioma en su propio idioma, como en los ajustes de Android. */
private fun nativeName(tag: String) = when (tag) { "en" -> "English"; else -> "Español" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    s: io.github.soundtouchradio.data.Settings,
    language: LanguageState?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onMultiroom: (Boolean) -> Unit,
    onHttpsBridge: (Boolean) -> Unit,
    onVolumeStep: (Int) -> Unit,
    onRestoreSeeds: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(Txt.s(R.string.ui_ajustes_2)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            // Aspecto: colores (Android 12+) e idioma (Android 13+). Sin ninguno de los dos, la sección no aparece.
            if (Build.VERSION.SDK_INT >= 31 || language != null) SectionTitle(Txt.s(R.string.ui_aspecto))
            if (Build.VERSION.SDK_INT >= 31) SwitchRow(
                Txt.s(R.string.ui_colores_del_fondo_de_pantalla), Txt.s(R.string.ui_sustituye_el_gris_y_verde), s.dynamicColor, onDynamicColor)
            if (language != null) LanguagePicker(language)

            SectionTitle(Txt.s(R.string.ui_reproduccion))
            SwitchRow(Txt.s(R.string.ui_ajuste_puente), Txt.s(R.string.ui_ajuste_puente_detalle), s.httpsBridge != false, onHttpsBridge)
            SwitchRow(Txt.s(R.string.ui_multiroom_experimental), Txt.s(R.string.ui_agrupar_altavoces_con_la_api), s.multiroomExperimental, onMultiroom)

            SectionTitle(Txt.s(R.string.ui_volumen))
            Body(Txt.s(R.string.ui_cuanto_cambia_cada_pulsacion_de))
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val steps = listOf(1, 2, 3, 5)
                steps.forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = s.volumeStep == n, onClick = { onVolumeStep(n) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, steps.size),
                        label = { Text(if (n == 1) Txt.s(R.string.ui_1_punto) else "$n", style = MaterialTheme.typography.labelLarge.merge(io.github.soundtouchradio.ui.theme.Tabular)) },
                    )
                }
            }

            SectionTitle(Txt.s(R.string.ui_emisoras))
            Body(Txt.s(R.string.ui_restaurar_detalle))
            TextButton(onClick = onRestoreSeeds, modifier = Modifier.padding(horizontal = 8.dp)) { Text(Txt.s(R.string.ui_restaurar_emisoras_precargadas_2)) }

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
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/**
 * Lista de opciones con círculo, una por línea (1.11.1): la fila de botones cortaba «Como el móvil». Es el patrón del
 * propio selector de idioma de Android y aguanta letra grande y más idiomas. Android recrea la pantalla al elegir.
 */
@Composable
private fun LanguagePicker(state: LanguageState) {
    Text(Txt.s(R.string.ui_idioma), style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
    val options = listOf("" to Txt.s(R.string.ui_idioma_sistema)) + SUPPORTED.map { it to nativeName(it) }
    Column(Modifier.selectableGroup()) {
        options.forEach { (tag, label) ->
            val selected = state.selected == tag
            ListItem(
                modifier = Modifier.selectable(selected, role = Role.RadioButton) { if (!selected) state.onSelect(tag) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                leadingContent = { RadioButton(selected = selected, onClick = null) },
                headlineContent = { Text(label) },
                supportingContent = if (tag.isEmpty()) { { Text(Txt.s(R.string.ui_idioma_ahora, nativeName(state.systemUses))) } } else null,
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}
