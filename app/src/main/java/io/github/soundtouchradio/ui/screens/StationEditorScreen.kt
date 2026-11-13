package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.data.Station
import io.github.soundtouchradio.data.StationCodec
import io.github.soundtouchradio.playlist.ProbeResult
import io.github.soundtouchradio.playlist.UrlRules
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.theme.Tabular
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditorScreen(vm: AppViewModel, existing: Station?, snackbar: SnackbarHostState, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(existing?.url.orEmpty()) }
    var probing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    var httpTry by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = name != existing?.name.orEmpty() || url != existing?.url.orEmpty()
    // Atrás (gesto o flecha) con cambios sin guardar: preguntar antes de descartar.
    androidx.activity.compose.BackHandler(enabled = dirty) { confirmDiscard = true }
    fun leave() { if (dirty) confirmDiscard = true else onDone() }
    if (confirmDiscard) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_descartar_los_cambios)) },
        text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_la_emisora_no_se_ha)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_descartar)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_seguir_editando)) } },
    )

    val validation = UrlRules.validate(url)
    val urlError = (validation as? UrlRules.Validation.Invalid)?.reason?.takeIf { url.isNotBlank() }
    val canSave = validation is UrlRules.Validation.Ok
    val https = UrlRules.isHttps(url)
    val hls = UrlRules.looksLikeHls(url)
    val playlist = UrlRules.playlistKind(url) != null

    fun save() {
        val v = validation as? UrlRules.Validation.Ok ?: return
        val finalName = name.trim().ifEmpty { probe?.icyName ?: StationCodec.defaultName(v.url) }
        val tech = probe?.takeIf { it.reachable }
        vm.saveStation(
            (existing ?: Station(name = finalName, url = v.url)).copy(
                name = finalName, url = v.url,
                codec = tech?.let { UrlRules.codecLabel(UrlRules.guessMime(it.finalUrl, it.contentType)) } ?: existing?.codec.takeIf { existing?.url == v.url },
                bitrateKbps = tech?.icyBitrate ?: existing?.bitrateKbps.takeIf { existing?.url == v.url },
            )
        )
        onDone()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) Txt.s(R.string.ui_nueva_emisora) else Txt.s(R.string.ui_editar_emisora)) },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } },
                actions = { TextButton(onClick = ::save, enabled = canSave) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_guardar_3)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_nombre_2)) },
                placeholder = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_se_rellena_con_el_nombre)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it.trim(); probe = null; httpTry = null }, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_url_del_stream_o_de)) },
                placeholder = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_http_stream_mp3_aac_pls)) },
                isError = urlError != null, supportingText = { Text(urlError ?: Txt.s(R.string.ui_mejor_http_directo)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.merge(Tabular),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )

            if (canSave && https) Notice(
                cs.tertiaryContainer, cs.onTertiaryContainer,
                Txt.s(R.string.ui_es_https_segun_la_comunidad),
                action = Txt.s(R.string.ui_probar_version_http) to {
                    scope.launch {
                        val http = UrlRules.httpVariant(url) ?: return@launch
                        probing = true
                        val p = vm.probe(http)
                        probing = false
                        if (p.reachable) { url = http; probe = p; httpTry = null } else httpTry = Txt.s(R.string.ui_version_http_no_responde, p.error ?: Txt.s(R.string.ui_sin_audio))
                    }
                },
            )
            httpTry?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium) }
            if (canSave && hls) Notice(cs.tertiaryContainer, cs.onTertiaryContainer,
                Txt.s(R.string.ui_es_hls_m3u8_el_soundtouch))
            if (canSave && playlist) Notice(cs.surfaceContainerHigh, cs.onSurface,
                Txt.s(R.string.ui_es_una_lista_al_reproducir))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = canSave && !probing, onClick = {
                    scope.launch { probing = true; probe = vm.probe((validation as UrlRules.Validation.Ok).url); probing = false }
                }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_comprobar_stream)) }
                // Probar sin guardar (1.9.5): el aviso de https es orientativo; lo que manda es si suena en el altavoz.
                androidx.compose.material3.TextButton(enabled = canSave, onClick = {
                    vm.play(io.github.soundtouchradio.data.Station(id = "editor-trial", name = name.ifBlank { "Prueba" }, url = (validation as UrlRules.Validation.Ok).url))
                }, modifier = Modifier.padding(start = 8.dp)) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_en_el_altavoz_editor)) }
                if (probing) CircularProgressIndicator(Modifier.padding(start = 16.dp).padding(4.dp), strokeWidth = 2.dp)
            }
            probe?.let { p ->
                val ok = p.reachable
                Text(
                    if (ok) listOfNotNull(Txt.s(R.string.ui_responde), p.contentType?.substringBefore(';'), p.icyBitrate?.let { "$it kbps" }, p.icyName?.let { "«$it»" }).joinToString(" · ")
                    else Txt.s(R.string.ui_no_responde_causa, p.error ?: Txt.s(R.string.ui_sin_audio)),
                    color = if (ok) cs.primary else cs.error, style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                )
                if (ok && p.redirectedToHttps) Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_redirige_a_https_puede_que), color = cs.tertiary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_la_comprobacion_se_hace_desde),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(bg: Color, fg: Color, text: String, action: Pair<String, () -> Unit>? = null) {
    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
            action?.let { (label, onClick) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = onClick) { Text(label) }
                }
            }
        }
    }
}
