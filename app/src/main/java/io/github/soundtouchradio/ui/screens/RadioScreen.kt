package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Event
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.data.Station
import io.github.soundtouchradio.player.Phase
import io.github.soundtouchradio.playlist.UrlRules
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.FixAction
import io.github.soundtouchradio.ui.components.Pilot
import io.github.soundtouchradio.ui.components.PilotLight
import io.github.soundtouchradio.ui.components.PresetKey
import io.github.soundtouchradio.ui.components.PowerButton
import io.github.soundtouchradio.ui.components.SectionTitle
import io.github.soundtouchradio.ui.theme.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onEditStation: (String?) -> Unit,
    onLink: () -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    onAlarm: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val speaker by vm.activeSpeaker.collectAsStateWithLifecycle()
    val speakers by vm.speakers.collectAsStateWithLifecycle()
    val state by vm.speakerState.collectAsStateWithLifecycle()
    val onPhone by vm.onPhone.collectAsStateWithLifecycle()
    // «Este móvil»: el panel lo trata como un altavoz más, con el nombre de la salida (1.10.0).
    val phoneName = androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_este_movil)
    val target = if (onPhone) io.github.soundtouchradio.data.SpeakerRecord(host = "", name = state.phoneOutput?.let { "$phoneName · $it" } ?: phoneName, type = "phone") else speaker
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSpeakers by remember { mutableStateOf(false) }
    var presetSlot by remember { mutableStateOf<Int?>(null) }
    var assignStation by remember { mutableStateOf<Station?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    // La primera vez que algo suena: permiso de notificaciones para mostrar la radio en la pantalla de bloqueo y en
    // la persiana (Android 13+). Se pide una sola vez; si se deniega, todo lo demás funciona igual.
    val notifSettings by vm.settings.collectAsStateWithLifecycle()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.markMediaNotifAsked() }
    val playingNow = state.phase is Phase.Playing
    androidx.compose.runtime.LaunchedEffect(playingNow) {
        if (playingNow && android.os.Build.VERSION.SDK_INT >= 33 && !notifSettings.mediaNotifAsked &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // SAF: importar/exportar sin permisos de almacenamiento.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (text == null) vm.notify(Txt.s(R.string.ui_no_se_pudo_leer_el))
            else vm.importText(io.github.soundtouchradio.playlist.PlaylistParser.decode(text))
        }
    }
    fun exporter(content: () -> String) = { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content().toByteArray()) } }.isSuccess
            }
            vm.notify(if (ok) Txt.s(R.string.ui_emisoras_exportadas) else Txt.s(R.string.ui_no_se_pudo_guardar_el))
        }
        Unit
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), exporter(vm::exportJson))
    val exportM3u = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl"), exporter(vm::exportM3u))

    val playingId = (state.phase as? Phase.Playing)?.let { state.stationId }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var showSleep by remember { mutableStateOf(false) }
    val sleep by vm.sleep.collectAsStateWithLifecycle()

    val panel: @Composable (Boolean, Modifier) -> Unit = { docked, m ->
            NowPlayingPanel(
                speaker = target, state = state, onEq = { showEq = true },
                onStop = vm::stop, onPause = vm::pause, onResume = vm::resume,
                onVolume = vm::setVolume, onMute = vm::toggleMute, onLink = onLink, onVolumeStep = vm::stepVolume,
                onFix = { a ->
                    when (a) {
                        FixAction.TRY_HTTP -> vm.tryHttpVariant(state.stationId)
                        FixAction.POWER -> vm.setPower(true)
                        FixAction.RETRY -> stations.firstOrNull { it.id == state.stationId }?.let(vm::play) ?: vm.clearFailure()
                        FixAction.DIAGNOSTICS -> onDiagnostics()
                        FixAction.ENABLE_BRIDGE -> vm.enableBridgeAndRetry(state.stationId)
                        FixAction.LINK -> onLink()
                    }
                },
                sleep = sleep, onSleep = { showSleep = true },
                docked = docked, modifier = m,
            )
    }
    val settingsNow by vm.settings.collectAsStateWithLifecycle()
    val presetGrid: @Composable () -> Unit = {
        val al = settingsNow.alarm
        val once = settingsNow.once
        // La que suena antes de las dos; si es «Una vez», se dice su día y, debajo, que la semanal sigue.
        // Despertadores (1.9.1): una línea por cada alarma activada, la semanal y la de «una vez», a la vez.
        // La que suena antes va en color normal; la otra, tenue. «Una vez» lleva icono de calendario.
        // Se recalcula cada minuto: «hoy/mañana» y cuál es la próxima siguen siendo ciertos con la app abierta (1.9.2).
        var minuteTick by remember { mutableStateOf(System.currentTimeMillis() / 60_000) }
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000); minuteTick = System.currentTimeMillis() / 60_000 } }
        val nowZ = remember(al, once, minuteTick) { java.time.ZonedDateTime.now() }
        val weeklyNext = io.github.soundtouchradio.player.AlarmPlan.nextTrigger(al, nowZ)
        val onceNext = io.github.soundtouchradio.player.AlarmPlan.nextOnce(once, nowZ)
        val first = io.github.soundtouchradio.player.AlarmPlan.nextAlarm(al, once, nowZ)?.kind
        if (weeklyNext != null || onceNext != null) Column(Modifier.padding(vertical = 4.dp)) {
            fun stationName(id: String?) = stations.firstOrNull { it.id == id }?.name ?: Txt.s(R.string.ui_sin_emisora)
            val rows = listOfNotNull(
                weeklyNext?.let { Triple(io.github.soundtouchradio.player.AlarmPlan.Kind.WEEKLY, it,
                    Txt.s(R.string.ui_d_02d_s_s).format(it.hour, it.minute, io.github.soundtouchradio.player.AlarmPlan.daysLabel(al.days), stationName(al.stationId))) },
                onceNext?.let {
                    val d = java.time.temporal.ChronoUnit.DAYS.between(nowZ.toLocalDate(), it.toLocalDate())
                    val day = when (d) { 0L -> Txt.s(R.string.ui_next_today); 1L -> Txt.s(R.string.ui_manana); else -> "%d/%d".format(it.dayOfMonth, it.monthValue) }
                    Triple(io.github.soundtouchradio.player.AlarmPlan.Kind.ONCE, it,
                        Txt.s(R.string.ui_d_02d_s_s_una).format(it.hour, it.minute, day, stationName(once.stationId ?: al.stationId)))
                },
            ).sortedBy { it.second.toEpochSecond() }
            rows.forEach { (kind, _, line0) ->
                val next = kind == first
                val stationId = if (kind == io.github.soundtouchradio.player.AlarmPlan.Kind.ONCE) once.stationId ?: al.stationId else al.stationId
                val missing = stations.none { it.id == stationId }
                // Sin emisora (1.10.2): el despertador no pondría radio; se dice y se ofrece arreglarlo con un toque.
                val line = if (missing) line0.substringBefore(" · " + Txt.s(R.string.ui_sin_emisora)) + " · " + Txt.s(R.string.ui_sin_emisora_elige)
                    else if (onPhone && speaker != null) line0 + " · " + Txt.s(R.string.ui_en_altavoz, speaker!!.name) else line0
                val tint = if (missing) MaterialTheme.colorScheme.tertiary else if (next) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        .clickable(onClickLabel = Txt.s(R.string.ui_editar_despertador), onClick = onAlarm)
                        .padding(horizontal = 16.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = (if (kind == io.github.soundtouchradio.player.AlarmPlan.Kind.ONCE) Txt.s(R.string.ui_alarma_de_una_vez) else Txt.s(R.string.ui_despertador_semanal)) +
                                line + if (next && rows.size > 1) Txt.s(R.string.ui_es_la_proxima) else ""
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (kind == io.github.soundtouchradio.player.AlarmPlan.Kind.ONCE) Icons.Filled.Event else Icons.Filled.Alarm,
                        contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
                SectionTitle(Txt.s(R.string.ui_presets))
                // Cada tecla ocupa su columna (las etiquetas ya no se tocan); ancho máximo para que
                // en horizontal o pantallas anchas el teclado no se estire de borde a borde.
                Column(
                    Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 560.dp).padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Con fuente grande (≥150 %), 3 columnas: cada nombre tiene sitio sin partir palabras.
                    val cols = if (LocalDensity.current.fontScale >= 1.5f) 3 else 5
                    for (row in 0 until (presets.size + cols - 1) / cols) Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until cols) {
                            val slot = row * cols + col
                            if (slot >= presets.size) { Spacer(Modifier.weight(1f)); continue }
                            val st = presets.getOrNull(slot)
                            PresetKey(
                                number = slot + 1, label = st?.name,
                                playing = st != null && st.id == playingId,
                                onClick = { if (st != null) vm.playPreset(slot) else presetSlot = slot },
                                onLongClick = { presetSlot = slot },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            
    }
    val stationItems: LazyListScope.() -> Unit = {
            // «Añadir» vive en la cabecera de su sección: sin botón flotante que tape filas encima del panel «Ahora suena».
            item(key = "title") { StationsHeader { showAdd = true } }
            if (stations.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_aun_no_hay_emisoras), style = MaterialTheme.typography.titleMedium)
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_anade_la_url_de_un),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_importar)) }
                        FilledTonalButton(onClick = { showAdd = true }) { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_anadir_emisora)) }
                    }
                    androidx.compose.material3.TextButton(onClick = vm::restoreSeedStations, modifier = Modifier.padding(top = 8.dp)) {
                        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_restaurar_emisoras_precargadas))
                    }
                }
            }
            items(stations, key = { it.id }) { st ->
                StationRow(
                    station = st,
                    pilot = when {
                        st.id == playingId -> Pilot.ON
                        st.id == state.stationId && (state.phase.isBusy || state.phase is Phase.Buffering) -> Pilot.BUSY
                        st.id == state.stationId && state.phase is Phase.Failed -> Pilot.ERROR
                        else -> null
                    },
                    presetNumbers = presets.withIndex().filter { it.value?.id == st.id }.map { it.index + 1 },
                    onPlay = { vm.play(st) },
                    onEdit = { onEditStation(st.id) },
                    onAssign = { assignStation = st },
                    onUp = { vm.moveStation(st.id, -1) },
                    onDown = { vm.moveStation(st.id, +1) },
                    onDelete = { vm.deleteStation(st.id) },
                )
            }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_radio)) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                actions = {
                    if (speaker != null && !onPhone) PowerButton(
                        on = state.speakerOn, pending = state.powerTarget != null,
                        speakerName = speaker?.name ?: "altavoz", onToggle = vm::setPower,
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { showSpeakers = true },
                        label = { Text(if (onPhone) phoneName else speaker?.name ?: Txt.s(R.string.ui_elegir_altavoz), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                        leadingIcon = {
                            if (onPhone) Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.height(AssistChipDefaults.IconSize)) else
                            PilotLight(when {
                                speaker == null -> Pilot.OFF
                                state.phase is Phase.Playing -> Pilot.ON
                                state.phase is Phase.Failed || state.phase is Phase.Unreachable -> Pilot.ERROR
                                else -> Pilot.OFF
                            })
                        },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = Txt.s(R.string.ui_cambiar_de_altavoz), Modifier.height(AssistChipDefaults.IconSize)) },
                    )
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = Txt.s(R.string.ui_mas_opciones)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_buscar_emisoras)) }, onClick = { menu = false; onSearch() })
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_importar_emisoras)) }, onClick = { menu = false; importer.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_exportar_a_json)) }, onClick = { menu = false; exportJson.launch("emisoras.json") })
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_exportar_a_m3u)) }, onClick = { menu = false; exportM3u.launch("emisoras.m3u") })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_despertador_2)) }, onClick = { menu = false; onAlarm() })
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_diagnostico_2)) }, onClick = { menu = false; onDiagnostics() })
                            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_ajustes)) }, onClick = { menu = false; onSettings() })
                        }
                    }
                },
            )
        },
        bottomBar = { if (!wide) panel(true, Modifier) },
    ) { padding ->
        if (wide) {
            // Horizontal / pantalla ancha: teclado y «Ahora suena» a la izquierda, emisoras a la derecha.
            Row(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                Column(
                    Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState()).navigationBarsPadding(),
                ) {
                    // Lo que suena, primero; el teclado debajo.
                    panel(false, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
                    presetGrid()
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.weight(0.55f).fillMaxHeight(),
                ) { stationItems() }
            }
        } else LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "presets") { presetGrid() }
            stationItems()
        }
    }

    if (showEq && onPhone) {
        val output = state.phoneOutputKey ?: phoneName
        val eqBands by vm.eqBands.collectAsStateWithLifecycle()
        val all by vm.settings.collectAsStateWithLifecycle()
        EqSheet(output, all.eq[output] ?: io.github.soundtouchradio.player.EqSetting(), eqBands,
            onChange = { vm.setEq(output, it) }, onDismiss = { showEq = false })
    }
    if (showAdd) AddStationSheet(
        onDismiss = { showAdd = false },
        onSearch = { showAdd = false; onSearch() },
        onUrl = { showAdd = false; onEditStation(null) },
        onImport = { showAdd = false; importer.launch(arrayOf("*/*")) },
    )
    if (showSleep) SleepSheet(sleep, onStart = vm::startSleep, onCancel = vm::cancelSleep, onDismiss = { showSleep = false })
    if (showSpeakers) SpeakerSheet(vm, speakers, speaker, onDismiss = { showSpeakers = false }, onLink = { showSpeakers = false; onLink() })
    presetSlot?.let { slot ->
        PresetSheet(slot, presets.getOrNull(slot), stations, onDismiss = { presetSlot = null },
            onAssign = { id -> vm.assignPreset(slot, id); presetSlot = null })
    }
    assignStation?.let { st ->
        AssignToPresetSheet(st, presets, onDismiss = { assignStation = null },
            onAssign = { slot -> vm.assignPreset(slot, st.id); assignStation = null; vm.notify(Txt.s(R.string.ui_en_el_preset, st.name, slot + 1)) })
    }
}

@Composable
internal fun StationRow(
    station: Station,
    pilot: Pilot?,
    presetNumbers: List<Int>,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onAssign: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val https = UrlRules.isHttps(station.url)
    val hls = UrlRules.looksLikeHls(station.url)
    val tech = listOfNotNull(station.codec, station.bitrateKbps?.let { "$it kbps" }).joinToString(" · ")
    val stateText = when (pilot) {
        Pilot.ON -> Txt.s(R.string.ui_sonando); Pilot.BUSY -> Txt.s(R.string.ui_cargando); Pilot.ERROR -> Txt.s(R.string.ui_no_suena); Pilot.WARN -> Txt.s(R.string.ui_aviso); else -> null
    }
    ListItem(
        modifier = Modifier
            .clickable(onClickLabel = Txt.s(R.string.ui_reproducir), onClick = onPlay)
            .semantics { if (stateText != null) stateDescription = stateText },
        colors = ListItemDefaults.colors(containerColor = cs.surface),
        leadingContent = {
            Box(Modifier.widthIn(min = 24.dp), contentAlignment = Alignment.Center) {
                if (pilot != null) PilotLight(pilot) else if (presetNumbers.isNotEmpty())
                    Text("${presetNumbers.first()}", style = MaterialTheme.typography.labelLarge.merge(Tabular), color = cs.onSurfaceVariant)
            }
        },
        headlineContent = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // Una sola línea secundaria (1.9.0): «AAC · 128 kbps · dominio». Los avisos (https, HLS) en ámbar al principio.
        supportingContent = {
            val host = station.url.substringAfter("://").substringBefore('/')
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    val warn = when { hls -> "HLS"; https -> "https"; else -> null }
                    if (warn != null) { withStyle(androidx.compose.ui.text.SpanStyle(color = cs.tertiary)) { append(warn) }; append(" · ") }
                    if (tech.isNotEmpty()) append("$tech · ")
                    append(host)
                },
                style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = Txt.s(R.string.ui_opciones_de, station.name)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_editar)) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_guardar_en_preset)) }, onClick = { menu = false; onAssign() })
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_subir)) }, onClick = { menu = false; onUp() })
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_bajar)) }, onClick = { menu = false; onDown() })
                        // Acción destructiva aislada y separada del resto.
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_borrar), color = cs.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
    )
}


/** Cabecera «Emisoras» con su acción «Añadir»: sin botón flotante que tape filas encima del panel «Ahora suena». */
@Composable
internal fun StationsHeader(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
        SectionTitle(Txt.s(R.string.ui_emisoras), Modifier.weight(1f).alignByBaseline())
        TextButton(onClick = onAdd, modifier = Modifier.alignByBaseline()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_anadir))
        }
    }
}

/** «Añadir»: las tres formas de traer una emisora, en una hoja. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AddStationSheet(onDismiss: () -> Unit, onSearch: () -> Unit, onUrl: () -> Unit, onImport: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { io.github.soundtouchradio.ui.components.NoSheetInsets }) {
        Column(Modifier.padding(bottom = io.github.soundtouchradio.ui.components.sheetBottomInset())) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_anadir_emisora_2), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            AddRow(Icons.Filled.Search, Txt.s(R.string.ui_buscar_en_el_directorio), Txt.s(R.string.ui_emisoras_de_espana_y_del), onSearch)
            AddRow(Icons.Filled.Link, Txt.s(R.string.ui_escribir_una_url), Txt.s(R.string.ui_si_ya_tienes_la_direccion), onUrl)
            AddRow(Icons.Filled.FileOpen, Txt.s(R.string.ui_importar_archivo), Txt.s(R.string.ui_json_m3u_o_pls), onImport)
        }
    }
}

@Composable
private fun AddRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
    )
}
