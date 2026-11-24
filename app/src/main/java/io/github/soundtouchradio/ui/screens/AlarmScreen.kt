package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import io.github.soundtouchradio.ui.components.OutlinedButton
import io.github.soundtouchradio.ui.components.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import io.github.soundtouchradio.ui.components.Switch
import androidx.compose.material3.Text
import io.github.soundtouchradio.ui.components.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.data.AlarmSettings
import io.github.soundtouchradio.data.OnceAlarm
import java.time.LocalDate
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Tab
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.soundtouchradio.player.AlarmPlan
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.components.SectionTitle
import io.github.soundtouchradio.ui.theme.Tabular
import kotlinx.coroutines.delay
import java.time.ZonedDateTime



/**
 * Despertador «Radio Braun»: la hora en grande con cifras tabulares (como un despertador de mesa),
 * días como teclas redondas, emisora, volumen y una prueba inmediata. Se guarda al cambiar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stations by vm.stations.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val speaker by vm.activeSpeaker.collectAsStateWithLifecycle()
    AlarmContent(settings.alarm, settings.once, stations, presets, speaker, vm.canScheduleExactAlarm(), snackbar,
        onSave = vm::saveAlarm, onSaveOnce = vm::saveOnce, onTest = vm::testAlarm, onBack = onBack)
}

/**
 * Dos alarmas que conviven, en dos pestañas: «Semanal» (la rutina) y «Una vez» (una fecha concreta).
 * El interruptor de la barra es el de la pestaña visible. Contenido sin estado de la app (renderizable en pruebas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmContent(
    a: AlarmSettings,
    o: OnceAlarm,
    stations: List<io.github.soundtouchradio.data.Station>,
    presets: List<io.github.soundtouchradio.data.Station?>,
    speaker: io.github.soundtouchradio.data.SpeakerRecord?,
    canExact: Boolean,
    snackbar: SnackbarHostState,
    onSave: (AlarmSettings, Boolean) -> Unit,
    onSaveOnce: (OnceAlarm, Boolean) -> Unit,
    onTest: (Boolean) -> Unit,
    onBack: () -> Unit,
    initialTab: Int = if (o.enabled && !a.enabled) 1 else 0,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val isOnce = tab == 1
    var pickTime by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    fun save(n: AlarmSettings, announce: Boolean = false) = onSave(n, announce)
    fun saveO(n: OnceAlarm, announce: Boolean = false) = onSaveOnce(n, announce)

    val enabled = if (isOnce) o.enabled else a.enabled
    var pendingEnableOnce by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pendingEnableOnce) saveO(o.copy(enabled = true), announce = true) else save(a.copy(enabled = true), announce = true)
    }
    fun enable(on: Boolean) {
        val needs = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        pendingEnableOnce = isOnce
        when {
            needs -> notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            isOnce -> saveO(o.copy(enabled = on), announce = on)
            else -> save(a.copy(enabled = on), announce = on)
        }
    }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_despertador)) },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = cs.surfaceContainer),
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } },
                    actions = {
                        Switch(checked = enabled, onCheckedChange = ::enable,
                            modifier = Modifier.padding(end = 16.dp).semantics {
                                contentDescription = if (isOnce) Txt.s(R.string.ui_alarma_de_una_vez_activada) else Txt.s(R.string.ui_despertador_semanal_activado)
                            })
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab, containerColor = cs.surface) {
                    AlarmTab(Txt.s(R.string.ui_semanal), a.enabled, tab == 0) { tab = 0 }
                    AlarmTab(Txt.s(R.string.ui_una_vez), o.enabled, tab == 1) { tab = 1 }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            val nowZ = remember(now, a, o) { ZonedDateTime.now() }   // se recalcula cada 30 s o al cambiar
            val next = if (isOnce) AlarmPlan.nextOnce(o, nowZ) else AlarmPlan.nextTrigger(a, nowZ)
            val hour = if (isOnce) o.hour else a.hour
            val minute = if (isOnce) o.minute else a.minute
            // Hora en grande: el gesto de despertador de mesa.
            Column(
                Modifier.fillMaxWidth().clickable(onClickLabel = Txt.s(R.string.ui_cambiar_la_hora)) { pickTime = true }.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.displayLarge.merge(Tabular),
                    color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
                Text(
                    when {
                        enabled && next != null -> AlarmPlan.nextLabel(next, nowZ)
                        enabled && isOnce -> Txt.s(R.string.ui_esa_hora_ya_ha_pasado)
                        else -> Txt.s(R.string.ui_desactivada_toca_la_hora_para)
                    },
                    style = MaterialTheme.typography.bodyMedium.merge(io.github.soundtouchradio.ui.theme.ProseNumbers), color = cs.onSurfaceVariant,
                )
            }

            if (enabled && !canExact) Surface(color = cs.tertiaryContainer, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_android_no_permite_a_la),
                        color = cs.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                    if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${ctx.packageName}")))
                    }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_permitir_alarmas_exactas)).uppercase()) }
                }
            }

            if (!isOnce) {
                SectionTitle(Txt.s(R.string.ui_dias))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (d in 1..7) {
                        val on = d in a.days
                        // Marcado: relleno y borde de 2 dp en el color del texto; sin marcar: solo contorno fino.
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .then(if (on) Modifier.background(cs.secondaryContainer).border(2.dp, cs.onSecondaryContainer, CircleShape)
                                    else Modifier.border(1.dp, cs.outline, CircleShape))
                                .toggleable(on, role = Role.Checkbox) { save(a.copy(days = if (it) a.days + d else a.days - d)) }
                                .semantics { contentDescription = AlarmPlan.weekday(d); stateDescription = if (on) Txt.s(R.string.ui_suena) else Txt.s(R.string.ui_no_suena) },
                            contentAlignment = Alignment.Center,
                        ) { Text(AlarmPlan.dayKey(d), style = MaterialTheme.typography.titleSmall, color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant) }
                    }
                }
                Text(
                    if (a.days.isEmpty()) Txt.s(R.string.ui_sin_dias_marcados_sonara_una)
                    else Txt.s(R.string.ui_para_un_dia_suelto_sin),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            } else {
                SectionTitle(Txt.s(R.string.ui_fecha))
                val date = o.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.parse(AlarmPlan.defaultOnceDate(nowZ))
                ListItem(
                    modifier = Modifier.clickable(onClickLabel = Txt.s(R.string.ui_cambiar_la_fecha)) { pickDate = true },
                    colors = ListItemDefaults.colors(containerColor = cs.surface),
                    headlineContent = { Text(AlarmPlan.dateLabel(date, nowZ.toLocalDate()).replaceFirstChar { it.uppercase() }) },
                    supportingContent = { Text(if (a.enabled) Txt.s(R.string.ui_el_despertador_semanal_sigue_igual) else Txt.s(R.string.ui_se_apaga_sola_despues_de)) },
                )
            }

            SectionTitle(Txt.s(R.string.ui_emisora))
            val stationId = if (isOnce) o.stationId ?: a.stationId else a.stationId
            val ordered = presets.filterNotNull().distinctBy { it.id } + stations.filter { s -> presets.none { it?.id == s.id } }
            ordered.forEach { st ->
                val preset = presets.indexOfFirst { it?.id == st.id }.takeIf { it >= 0 }?.let { it + 1 }
                ListItem(
                    modifier = Modifier.selectable(selected = st.id == stationId, role = Role.RadioButton) {
                        if (isOnce) saveO(o.copy(stationId = st.id)) else save(a.copy(stationId = st.id))
                    },
                    colors = ListItemDefaults.colors(containerColor = cs.surface),
                    leadingContent = { RadioButton(selected = st.id == stationId, onClick = null) },
                    headlineContent = { Text(st.name) },
                    trailingContent = preset?.let { { Text("$it", style = MaterialTheme.typography.labelLarge.merge(Tabular), color = cs.onSurfaceVariant) } },
                )
            }

            SectionTitle(Txt.s(R.string.ui_volumen_al_despertar))
            val volume = if (isOnce) o.volume else a.volume
            val fadeIn = if (isOnce) o.fadeIn else a.fadeIn
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                io.github.soundtouchradio.ui.components.FaderSlider(value = volume.toFloat(), onValueChange = { v -> if (isOnce) saveO(o.copy(volume = v.toInt())) else save(a.copy(volume = v.toInt())) },
                    valueRange = 1f..60f, tickCount = 6, modifier = Modifier.weight(1f).semantics { contentDescription = Txt.s(R.string.ui_volumen_al_despertar) })
                Text("$volume", style = MaterialTheme.typography.labelLarge.merge(Tabular), color = cs.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 32.dp).padding(start = 8.dp))
            }
            ListItem(
                modifier = Modifier.toggleable(fadeIn, role = Role.Switch) { v -> if (isOnce) saveO(o.copy(fadeIn = v)) else save(a.copy(fadeIn = v)) },
                colors = ListItemDefaults.colors(containerColor = cs.surface),
                headlineContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_subir_el_volumen_poco_a)) },
                supportingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_empieza_bajo_y_llega_al)) },
                trailingContent = { Switch(checked = fadeIn, onCheckedChange = null) },
            )

            SectionTitle(Txt.s(R.string.ui_altavoz))
            Text(speaker?.let { sp -> androidx.compose.ui.text.buildAnnotatedString {
                // Lo que se nombra en Roboto; lo que se mide (la IP) en lectura (1.17.0).
                withStyle(androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Default)) { append(sp.name); append(" · ") }
                append(sp.host)
            } } ?: androidx.compose.ui.text.AnnotatedString(Txt.s(R.string.ui_ningun_altavoz_vinculado_2)),
                style = MaterialTheme.typography.bodyLarge.merge(Tabular), modifier = Modifier.padding(horizontal = 16.dp))

            Row(Modifier.padding(16.dp)) {
                OutlinedButton(onClick = { onTest(isOnce) }, enabled = speaker != null) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_ahora)).uppercase()) }
            }
            Text(
                Txt.s(R.string.ui_funciona_desde_este_movil_tiene) +
                    Txt.s(R.string.ui_si_el_altavoz_no_responde),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.width(1.dp))
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = if (isOnce) o.hour else a.hour, initialMinute = if (isOnce) o.minute else a.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = {
                if (isOnce) saveO(o.copy(hour = state.hour, minute = state.minute), announce = o.enabled)
                else save(a.copy(hour = state.hour, minute = state.minute))
                pickTime = false
            }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_aceptar)).uppercase()) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_cancelar)).uppercase()) } },
            text = { TimePicker(state = state) },
        )
    }

    if (pickDate) {
        val today = LocalDate.now()
        val current = o.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today.plusDays(1)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toEpochDay() * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / 86_400_000L >= today.toEpochDay()
                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { saveO(o.copy(date = LocalDate.ofEpochDay(it / 86_400_000L).toString()), announce = o.enabled) }
                pickDate = false
            }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_aceptar_2)).uppercase()) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_cancelar_2)).uppercase()) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

/** Pestaña con el icono de alarma cuando esa alarma está activada (el estado se ve sin entrar). */
@Composable
private fun AlarmTab(label: String, armed: Boolean, selected: Boolean, onClick: () -> Unit) {
    Tab(selected = selected, onClick = onClick,
        selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { stateDescription = if (armed) Txt.s(R.string.ui_activada) else Txt.s(R.string.ui_desactivada) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Piloto de armado en lugar del dibujo de reloj (1.15.0): encendido si esa alarma está activada.
                io.github.soundtouchradio.ui.components.PilotSquare(armed); Spacer(Modifier.width(6.dp))
                Text(label)
            }
        })
}
