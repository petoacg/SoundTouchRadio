package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.data.Station
import io.github.soundtouchradio.directory.COUNTRIES
import io.github.soundtouchradio.directory.Genre
import io.github.soundtouchradio.directory.RbQuery
import io.github.soundtouchradio.directory.RbStation
import io.github.soundtouchradio.player.Phase
import io.github.soundtouchradio.player.SpeakerState
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.SearchUi
import io.github.soundtouchradio.ui.components.GrilleMode
import io.github.soundtouchradio.ui.components.NoSheetInsets
import io.github.soundtouchradio.ui.components.Pilot
import io.github.soundtouchradio.ui.components.PilotLight
import io.github.soundtouchradio.ui.components.SpeakerGrille
import io.github.soundtouchradio.ui.components.sheetBottomInset
import io.github.soundtouchradio.ui.theme.Tabular

@Composable
fun SearchScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val ui by vm.search.collectAsStateWithLifecycle()
    val stations by vm.stations.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val state by vm.speakerState.collectAsStateWithLifecycle()
    // Al abrir: las más escuchadas de España, para tener algo que tocar sin escribir.
    LaunchedEffect(Unit) { if (!ui.searched && !ui.loading) vm.searchStations(ui.query) }
    SearchContent(
        ui = ui, stations = stations, presets = presets, speaker = state, snackbar = snackbar,
        trialId = vm::directoryTrialId, onSearch = vm::searchStations, onTry = vm::tryDirectoryStation,
        onSave = vm::saveDirectoryStation, onBack = onBack,
    )
}

/** Contenido sin estado de la app (renderizable en pruebas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchContent(
    ui: SearchUi,
    stations: List<Station>,
    presets: List<Station?>,
    speaker: SpeakerState,
    snackbar: SnackbarHostState,
    trialId: (RbStation) -> String,
    onSearch: (RbQuery) -> Unit,
    onTry: (RbStation) -> Unit,
    onSave: (RbStation, Int?, String?) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var text by rememberSaveable { mutableStateOf(ui.query.text) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<RbStation?>(null) }
    /** Resultado de la última prueba de cada emisora: true = sonó (y con qué URL), false = no sonó. */
    fun trialOf(r: RbStation): Pair<Boolean, String?>? = if (speaker.stationId != trialId(r)) null else when (speaker.phase) {
        is Phase.Playing -> true to speaker.resolved?.originalUrl
        is Phase.Failed -> false to null
        else -> null
    }
    fun run(q: RbQuery) { keyboard?.hide(); onSearch(q) }
    val savedUrls = remember(stations) { stations.map { it.url.lowercase() }.toSet() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_buscar_emisoras_2)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_nombre_de_la_emisora)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = ""; run(ui.query.copy(text = "")) }) { Icon(Icons.Filled.Clear, Txt.s(R.string.ui_borrar_2)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(ui.query.copy(text = text)) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            // Filtros: país (menú), géneros y «Compatibles». Cada cambio vuelve a buscar.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var countryMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(selected = ui.query.country != null, onClick = { countryMenu = true },
                        label = { Text(COUNTRIES.firstOrNull { it.first == ui.query.country }?.second?.let { androidx.compose.ui.res.stringResource(it) } ?: Txt.s(R.string.ui_todo_el_mundo)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) })
                    DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                        COUNTRIES.forEach { (code, name) ->
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(name)) }, onClick = { countryMenu = false; run(ui.query.copy(text = text, country = code)) },
                                trailingIcon = { if (code == ui.query.country) Icon(Icons.Filled.Check, null) })
                        }
                    }
                }
                FilterChip(selected = ui.query.compatibleOnly, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_solo_compatibles)) },
                    leadingIcon = if (ui.query.compatibleOnly) ({ Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    onClick = { run(ui.query.copy(text = text, compatibleOnly = !ui.query.compatibleOnly)) })
                Genre.entries.forEach { g ->
                    FilterChip(selected = ui.query.genre == g, label = { Text(androidx.compose.ui.res.stringResource(g.labelRes)) },
                        onClick = { run(ui.query.copy(text = text, genre = if (ui.query.genre == g) null else g)) })
                }
            }
            HorizontalDivider()

            when {
                ui.loading && ui.results.isEmpty() -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpeakerGrille(null, GrilleMode.LOADING, Txt.s(R.string.ui_buscando), size = 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_buscando_en_el_directorio), style = MaterialTheme.typography.bodyLarge)
                }
                ui.error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ui.error, style = MaterialTheme.typography.bodyLarge)
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_comprueba_que_el_movil_tiene),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    OutlinedButton(onClick = { run(ui.query.copy(text = text)) }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_reintentar)) }
                }
                ui.searched && ui.results.isEmpty() -> Column(Modifier.padding(16.dp)) {
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_sin_resultados), style = MaterialTheme.typography.titleMedium)
                    Text(if (ui.query.compatibleOnly) Txt.s(R.string.ui_prueba_con_otro_nombre_o) else Txt.s(R.string.ui_prueba_con_otro_nombre_o_2),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Text(
                            ui.results.count { it.httpsOnly }.let { https ->
                                Txt.s(R.string.ui_http_solo_https_se_prueban, ui.results.size - https, https)
                            },
                            style = MaterialTheme.typography.labelMedium.merge(Tabular), color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    items(ui.results, key = { it.stationuuid }) { r ->
                        val open = expanded == r.stationuuid
                        val saved = r.streamUrl.lowercase() in savedUrls
                        val mine = speaker.stationId == trialId(r)
                        val pilot = when {
                            !mine -> null
                            speaker.phase is Phase.Playing -> Pilot.ON
                            speaker.phase is Phase.Failed -> Pilot.ERROR
                            speaker.phase is Phase.Resolving || speaker.phase is Phase.Sending ||
                                speaker.phase is Phase.AwaitingConfirmation || speaker.phase is Phase.Buffering -> Pilot.BUSY
                            else -> null
                        }
                        Column {
                            ListItem(
                                modifier = Modifier.clickable(onClickLabel = if (open) Txt.s(R.string.ui_ocultar_acciones) else Txt.s(R.string.ui_ver_acciones)) {
                                    expanded = if (open) null else r.stationuuid
                                },
                                colors = ListItemDefaults.colors(containerColor = cs.surface),
                                leadingContent = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { if (pilot != null) PilotLight(pilot) } },
                                headlineContent = { Text(r.name.trim(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(listOfNotNull(r.countrycode.ifBlank { null }, r.codec.ifBlank { null }?.uppercase(),
                                        if (r.httpsOnly) "https" else null,
                                        r.bitrate.takeIf { it > 0 }?.let { "$it kbps" }, r.tagList.take(3).joinToString(", ").ifBlank { null })
                                        .joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                                },
                                trailingContent = if (saved) ({ Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_guardada), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }) else null,
                            )
                            if (open) Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (mine) Text(when (pilot) {
                                    Pilot.ON -> if (r.httpsOnly) Txt.s(R.string.ui_suena_en_el_altavoz_por) else Txt.s(R.string.ui_suena_en_el_altavoz)
                                    Pilot.ERROR -> if (r.httpsOnly) Txt.s(R.string.ui_no_suena_solo_emite_por)
                                        else Txt.s(R.string.ui_el_altavoz_no_la_reproduce)
                                    else -> Txt.s(R.string.ui_probando_en_el_altavoz)
                                }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                Text(r.streamUrl, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onTry(r) }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_en_el_altavoz_2)) }
                                    Button(onClick = { saving = r }) { Text(if (saved) Txt.s(R.string.ui_poner_en_tecla) else Txt.s(R.string.ui_guardar_4)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    saving?.let { r ->
        val trial = trialOf(r)
        SaveSheet(r, presets, failed = trial?.first == false, onDismiss = { saving = null },
            onSave = { slot -> onSave(r, slot, trial?.second?.takeIf { trial.first }); saving = null })
    }
}

/** Guardar: sin tecla (por defecto) o en una tecla de preset; las libres primero. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(r: RbStation, presets: List<Station?>, failed: Boolean, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var slot by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { NoSheetInsets }) {
        Column(Modifier.padding(bottom = sheetBottomInset())) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_guardar, (r.name.trim()).toString()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (failed) Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_no_sono_en_tu_altavoz),
                style = MaterialTheme.typography.bodyMedium, color = cs.error, modifier = Modifier.padding(horizontal = 24.dp))
            val order = (null as Int?).let { listOf<Int?>(null) } + presets.indices.sortedBy { presets[it] != null }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(order) { i ->
                    ListItem(
                        modifier = Modifier.selectable(selected = slot == i, role = Role.RadioButton) { slot = i },
                        leadingContent = { RadioButton(selected = slot == i, onClick = null) },
                        headlineContent = { Text(if (i == null) Txt.s(R.string.ui_solo_en_la_lista_de) else "Tecla ${i + 1}", style = MaterialTheme.typography.bodyLarge.merge(Tabular)) },
                        supportingContent = if (i == null) null else ({ Text(presets[i]?.let { Txt.s(R.string.ui_sustituye_a, it.name) } ?: Txt.s(R.string.ui_libre), color = cs.onSurfaceVariant) }),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_cancelar_4)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(slot) }) { Text(if (failed) Txt.s(R.string.ui_guardar_igualmente) else Txt.s(R.string.ui_guardar_4)) }
            }
        }
    }
}
