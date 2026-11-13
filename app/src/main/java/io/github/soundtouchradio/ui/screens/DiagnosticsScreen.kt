package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.data.Seed
import io.github.soundtouchradio.player.SpeakerController
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.Copy
import io.github.soundtouchradio.ui.components.Pilot
import io.github.soundtouchradio.ui.components.PilotLight
import io.github.soundtouchradio.ui.components.SectionTitle
import io.github.soundtouchradio.ui.theme.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val speaker by vm.activeSpeaker.collectAsStateWithLifecycle()
    val state by vm.speakerState.collectAsStateWithLifecycle()
    val log by vm.log.entries.collectAsStateWithLifecycle()
    val bench by vm.bench.collectAsStateWithLifecycle()
    var urls by rememberSaveable { mutableStateOf(Seed.stations.joinToString("\n") { it.url }) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.benchMarkdown().toByteArray()) } }.isSuccess }
            vm.notify(if (ok) Txt.s(R.string.ui_resultados_guardados) else Txt.s(R.string.ui_no_se_pudo_guardar))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_diagnostico)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp)) {
            // «Este móvil» (1.10.1): con el móvil como destino, su ficha va primero; la del SoundTouch, plegada.
            val onPhone by vm.onPhone.collectAsStateWithLifecycle()
            var showSpeaker by rememberSaveable { mutableStateOf(false) }
            if (onPhone) {
                SectionTitle(Txt.s(R.string.ui_este_movil))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Field(Txt.s(R.string.ui_diag_salida), state.phoneOutput ?: "—")
                    Field(Txt.s(R.string.ui_estado), Copy.phaseLine(state.phase))
                    Field(Txt.s(R.string.ui_diag_abre), state.phoneUrl ?: "—")
                    state.resolved?.let { r -> Field(Txt.s(R.string.ui_diag_formato), r.mime + (r.probe?.icyBitrate?.let { Txt.s(R.string.ui_kbps, it) } ?: "")) }
                    (state.phase as? io.github.soundtouchradio.player.Phase.Failed)?.detail?.let { Field(Txt.s(R.string.ui_diag_error), it) }
                    if (state.phoneOnCellular) Field(Txt.s(R.string.ui_diag_red), Txt.s(R.string.ui_usando_datos_moviles))
                }
                TextButton(onClick = { showSpeaker = !showSpeaker }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showSpeaker) Txt.s(R.string.ui_diag_ocultar_altavoz) else Txt.s(R.string.ui_diag_ver_altavoz))
                }
            }
            if (!onPhone || showSpeaker) SectionTitle(Txt.s(R.string.ui_altavoz))
            val s = speaker
            if (onPhone && !showSpeaker) Unit
            else if (s == null) Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ningun_altavoz_vinculado), modifier = Modifier.padding(horizontal = 16.dp))
            else Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Field(Txt.s(R.string.ui_nombre_3), s.name + if (state.info?.nameBreaksRenderer == true) Txt.s(R.string.ui_contiene_puede_romper_upnp) else "")
                Field(Txt.s(R.string.ui_modelo), state.info?.type ?: s.type)
                Field("IP", s.host)
                Field("MAC", state.info?.macAddress ?: s.mac ?: "—")
                Field(Txt.s(R.string.ui_firmware), state.info?.softwareVersion ?: s.firmware ?: "—")
                Field(Txt.s(R.string.ui_control_upnp), SpeakerController.controlUrlFor(s) + if (s.controlUrl == null) Txt.s(R.string.ui_por_defecto) else "")
                Field(Txt.s(R.string.ui_websocket_8080), if (state.socketConnected) Txt.s(R.string.ui_conectado) else Txt.s(R.string.ui_sin_conexion_se_consulta_cada))
                Field(Txt.s(R.string.ui_estado), Copy.phaseLine(state.phase))
                // Lo que el altavoz dice estar reproduciendo, completo (1.9.5).
                state.nowPlaying?.location?.takeIf { it.isNotBlank() }?.let { Field(Txt.s(R.string.ui_el_altavoz_reproduce), it) }
                state.resolved?.let { r -> Field(Txt.s(R.string.ui_ultimo_stream), "${r.streamUrl} · ${r.mime}${if (r.viaPlaylist) Txt.s(R.string.ui_via_lista) else ""}") }
            }

            // Informe para pedir ayuda: MAC acortada e IP sin su última cifra (1.9.4).
            if (s != null) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                OutlinedButton(onClick = {
                    val T = io.github.soundtouchradio.ui.Txt
                    val lines = listOf(
                        io.github.soundtouchradio.ui.DiagnosticsReport.Line(T.s(io.github.soundtouchradio.R.string.ui_modelo), state.info?.type ?: s.type ?: "—"),
                        io.github.soundtouchradio.ui.DiagnosticsReport.Line(T.s(io.github.soundtouchradio.R.string.ui_firmware), state.info?.softwareVersion ?: s.firmware ?: "—"),
                        io.github.soundtouchradio.ui.DiagnosticsReport.Line("IP", s.host),
                        io.github.soundtouchradio.ui.DiagnosticsReport.Line("MAC", io.github.soundtouchradio.ui.DiagnosticsReport.maskMac(state.info?.macAddress ?: s.mac)),
                        io.github.soundtouchradio.ui.DiagnosticsReport.Line(T.s(io.github.soundtouchradio.R.string.ui_control_upnp), SpeakerController.controlUrlFor(s)),
                        io.github.soundtouchradio.ui.DiagnosticsReport.Line(T.s(io.github.soundtouchradio.R.string.ui_estado), Copy.phaseLine(state.phase)),
                    ) + listOfNotNull(state.resolved?.let { r -> io.github.soundtouchradio.ui.DiagnosticsReport.Line(T.s(io.github.soundtouchradio.R.string.ui_ultimo_stream), "${r.streamUrl} · ${r.mime}") })
                    val logLines = log.take(30).map { e -> "${time.format(Date(e.timeMillis))} ${e.method} ${e.target} · ${e.outcome} · ${e.durationMs} ms" }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(io.github.soundtouchradio.ui.DiagnosticsReport.build(Txt.s(R.string.ui_soundtouch_radio_2, io.github.soundtouchradio.BuildConfigShim.VERSION), lines, logLines)))
                    vm.notify(T.s(io.github.soundtouchradio.R.string.ui_informe_copiado))
                }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_copiar_informe))
                }
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_copiar_informe_detalle),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }

            SectionTitle(Txt.s(R.string.ui_ultimo_now_playing))
            Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // Con sangría y saltos de línea: se lee sin desplazarlo de lado (1.8.0).
                Text(state.nowPlaying?.raw?.let(::prettyXml) ?: "—", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp))
            }

            // Emisoras que solo emiten por https (1.9.5, experimental).
            SectionTitle(Txt.s(R.string.ui_bridge_title))
            val settingsNow by vm.settings.collectAsStateWithLifecycle()
            val bridgeRunning by vm.bridgeTestRunning.collectAsStateWithLifecycle()
            var bridgeUrl by rememberSaveable { mutableStateOf("https://stream.radioalhara.net/ra") }
            Text(Txt.s(R.string.ui_bridge_body), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(value = bridgeUrl, onValueChange = { bridgeUrl = it.trim() }, singleLine = true,
                label = { Text(Txt.s(R.string.ui_bridge_url_label)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.testHttpsRedirect(bridgeUrl) }, enabled = !bridgeRunning && speaker != null) {
                    Text(Txt.s(R.string.ui_bridge_button))
                }
                if (bridgeRunning) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp), strokeWidth = 2.dp)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PilotLight(when (settingsNow.httpsBridge) { true -> Pilot.ON; false -> Pilot.OFF; null -> Pilot.ON }, Modifier.padding(end = 10.dp))
                Text(when (settingsNow.httpsBridge) {
                    true -> Txt.s(R.string.ui_bridge_state_on)
                    false -> Txt.s(R.string.ui_bridge_state_off)
                    null -> Txt.s(R.string.ui_bridge_state_none)
                }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (settingsNow.httpsBridge == false) TextButton(onClick = { vm.setHttpsBridge(null) }) { Text(Txt.s(R.string.ui_bridge_enable)) }
                else TextButton(onClick = { vm.setHttpsBridge(false) }) { Text(Txt.s(R.string.ui_bridge_disable)) }
            }

            SectionTitle(Txt.s(R.string.ui_banco_de_pruebas_de_formatos))
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_reproduce_cada_url_en_el),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = urls, onValueChange = { urls = it }, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_una_url_por_linea)) },
                textStyle = MaterialTheme.typography.bodySmall.merge(Tabular),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bench.running) OutlinedButton(onClick = vm::cancelBench) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_cancelar_3)) }
                else FilledTonalButton(onClick = { vm.runBench(urls) }, enabled = speaker != null) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_en_el_altavoz)) }
                if (bench.rows.isNotEmpty() && !bench.running) {
                    OutlinedButton(onClick = { saveMd.launch("pruebas-soundtouch.md") }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_exportar_md)) }
                    TextButton(onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(Txt.s(R.string.ui_pruebas), vm.benchMarkdown()))
                        vm.notify(Txt.s(R.string.ui_copiado_como_markdown))
                    }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_copiar)) }
                }
            }
            if (bench.running) {
                LinearProgressIndicator(progress = { bench.current / bench.total.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probando_de, (bench.current).toString(), (bench.total).toString()), style = MaterialTheme.typography.bodyMedium.merge(Tabular), modifier = Modifier.padding(horizontal = 16.dp))
            }
            bench.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PilotLight(if (r.ok) Pilot.ON else Pilot.ERROR, Modifier.padding(top = 6.dp, end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Text("${r.result} · ${r.mime ?: "?"} · ${"%.1f".format(r.seconds)} s", style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                            color = if (r.ok) cs.primary else cs.error)
                    }
                }
            }

            SectionTitle(Txt.s(R.string.ui_peticiones_recientes))
            if (log.isEmpty()) Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_aun_no_hay_peticiones), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            log.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                    PilotLight(if (e.ok) Pilot.ON else Pilot.ERROR, Modifier.padding(top = 5.dp, end = 10.dp), size = 8.dp)
                    Text(time.format(Date(e.timeMillis)), style = MaterialTheme.typography.bodySmall.merge(Tabular), color = cs.onSurfaceVariant, modifier = Modifier.width(64.dp))
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ms, (e.method).toString(), (e.target).toString(), (e.outcome).toString(), (e.durationMs).toString()), style = MaterialTheme.typography.bodySmall.merge(Tabular))
                }
            }
            TextButton(onClick = { vm.log.clear() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_vaciar_registro)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(136.dp).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(Tabular))
    }
}

/** Sangra un XML corto para leerlo. Si no se puede analizar, lo devuelve tal cual. */
internal fun prettyXml(xml: String): String = runCatching {
    val doc = io.github.soundtouchradio.soundtouch.Xml.parse(xml)
    val t = javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    }
    val out = java.io.StringWriter()
    t.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(out))
    // Un atributo por línea en las etiquetas con varios: nada queda fuera de la pantalla (1.9.5).
    out.toString().lines().filter { it.isNotBlank() }.flatMap { line ->
        val m = Regex("""^(\s*)<([\w:]+)((?:\s+[\w:]+="[^"]*"){2,})(\s*/?>.*)$""").find(line) ?: return@flatMap listOf(line)
        val (indent, tag, attrs, rest) = m.destructured
        val parts = Regex("""[\w:]+="[^"]*"""").findAll(attrs).map { it.value }.toList()
        listOf("$indent<$tag") + parts.map { "$indent    $it" } + listOf("$indent${rest.trimStart()}")
    }.joinToString("\n").trim()
}.getOrDefault(xml)
