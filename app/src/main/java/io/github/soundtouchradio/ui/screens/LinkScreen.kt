package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.discovery.FoundVia
import io.github.soundtouchradio.discovery.LinkReport
import io.github.soundtouchradio.discovery.Linker
import io.github.soundtouchradio.discovery.StepState
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.components.Pilot
import io.github.soundtouchradio.ui.components.PilotLight
import io.github.soundtouchradio.ui.components.SectionTitle
import io.github.soundtouchradio.ui.theme.Tabular

/** Vincular = encontrar en la red + verificar API 8090 + verificar UPnP 8091 + comprobar el nombre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkScreen(vm: AppViewModel, snackbar: SnackbarHostState, canGoBack: Boolean, onBack: () -> Unit, onLinked: () -> Unit, onDiagnostics: () -> Unit, onWifiSetup: () -> Unit = {}) {
    val disc by vm.discovery.collectAsStateWithLifecycle()
    val report by vm.link.collectAsStateWithLifecycle()
    val speakers by vm.speakers.collectAsStateWithLifecycle()
    var ip by rememberSaveable { mutableStateOf("") }
    var ipError by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { vm.resetLink(); vm.scan() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vincular_altavoz)) },
                navigationIcon = { if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } },
                actions = {
                    IconButton(onClick = vm::scan, enabled = !disc.scanning) { Icon(Icons.Filled.Refresh, Txt.s(R.string.ui_buscar_de_nuevo)) }
                    IconButton(onClick = onDiagnostics) { Icon(Icons.Outlined.Info, Txt.s(R.string.ui_diagnostico_3)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_tu_soundtouch_debe_estar_encendido),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

            // 1. Resultado de la comprobación (si hay una en curso o hecha).
            report?.let { r -> LinkSteps(r, onUse = { vm.useLinked(manual); onLinked() }, onRetry = { vm.checkManualIp(r.host) }) }

            // 2. Encontrados en la red.
            SectionTitle(Txt.s(R.string.ui_en_tu_red), Modifier.semantics { heading() })
            if (disc.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            val linkedHosts = speakers.map { it.host }.toSet()
            disc.candidates.forEach { c ->
                ListItem(
                    modifier = Modifier.clickable(onClickLabel = Txt.s(R.string.ui_comprobar_3)) { manual = false; vm.checkCandidate(c) },
                    colors = ListItemDefaults.colors(containerColor = cs.surface),
                    leadingContent = { PilotLight(if (c.host in linkedHosts) Pilot.ON else Pilot.OFF) },
                    headlineContent = { Text(c.label?.takeIf { c.via == FoundVia.MDNS } ?: c.host, style = MaterialTheme.typography.bodyLarge.merge(Tabular)) },
                    supportingContent = { Text("${c.host} · ${Linker.viaLabel(c.via)}${if (c.host in linkedHosts) " · " + Txt.s(R.string.ui_vinculado_etiqueta) else ""}", style = MaterialTheme.typography.bodyMedium.merge(Tabular)) },
                    trailingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_comprobar), color = cs.primary, style = MaterialTheme.typography.labelLarge) },
                )
            }
            if (!disc.scanning && disc.scannedOnce && disc.candidates.isEmpty()) {
                Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_no_aparece_ningun_altavoz), style = MaterialTheme.typography.titleSmall)
                        listOf(
                            Txt.s(R.string.ui_el_movil_esta_en_otra),
                            Txt.s(R.string.ui_el_router_aisla_a_los),
                            Txt.s(R.string.ui_el_altavoz_esta_en_reposo),
                            Txt.s(R.string.ui_la_red_bloquea_el_multicast),
                        ).forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                    }
                }
            } else if (disc.scannedOnce && disc.candidates.isNotEmpty()) {
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ssdp_tambien_muestra_otros_aparatos),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // 3. IP manual.
            SectionTitle(Txt.s(R.string.ui_por_ip), Modifier.semantics { heading() })
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it; ipError = null },
                    label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ip_del_altavoz)) }, placeholder = { Text("192.168.1.20") },
                    singleLine = true, isError = ipError != null,
                    supportingText = { Text(ipError ?: Txt.s(R.string.ui_la_encuentras_en_la_lista)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { manual = true; ipError = vm.checkManualIp(ip) }),
                    textStyle = MaterialTheme.typography.bodyLarge.merge(Tabular),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { manual = true; ipError = vm.checkManualIp(ip) }, modifier = Modifier.padding(top = 8.dp)) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_comprobar_2)) }
            }

            SectionTitle(Txt.s(R.string.ui_altavoz_recien_reseteado))
            Text(
                androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_si_el_altavoz_no_esta),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
            androidx.compose.material3.FilledTonalButton(onClick = onWifiSetup, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_configurar_la_wi_fi_del))
            }
        }
    }
}

@Composable
internal fun LinkSteps(r: LinkReport, onUse: () -> Unit, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            io.github.soundtouchradio.ui.components.StepList(r.steps)
            val info = r.info
            if (r.done && info != null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(info.type, style = MaterialTheme.typography.headlineSmall)
                    Text(info.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(r.host, info.macAddress).joinToString(" · "), style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (r.usable) Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_usar_este_altavoz)) }
                    else TextButton(onClick = onRetry) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_volver_a_comprobar)) }
                }
            } else if (r.done) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_volver_a_comprobar_2)) }
            }
        }
    }
}
