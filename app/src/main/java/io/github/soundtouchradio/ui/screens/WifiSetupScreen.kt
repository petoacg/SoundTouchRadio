package io.github.soundtouchradio.ui.screens

import io.github.soundtouchradio.ui.Txt
import io.github.soundtouchradio.R

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import io.github.soundtouchradio.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import io.github.soundtouchradio.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import io.github.soundtouchradio.ui.components.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.github.soundtouchradio.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soundtouchradio.soundtouch.WifiNetwork
import io.github.soundtouchradio.ui.AppViewModel
import io.github.soundtouchradio.ui.WifiSetupUi
import io.github.soundtouchradio.ui.WifiStage
import io.github.soundtouchradio.ui.components.GrilleMode
import io.github.soundtouchradio.ui.components.SectionTitle
import io.github.soundtouchradio.ui.components.SpeakerGrille
import io.github.soundtouchradio.ui.components.StepList
import io.github.soundtouchradio.ui.theme.Tabular

@Composable
fun WifiSetupScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit, onDone: () -> Unit) {
    val ui by vm.wifi.collectAsStateWithLifecycle()
    // Permiso que exige Android para unirse a la red del altavoz (sin ubicación en Android 13+).
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.wifiConnect() else vm.wifiPermissionDenied()
    }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val inFlight = ui.stage == WifiStage.SENDING || ui.stage == WifiStage.WAITING || ui.stage == WifiStage.FINDING
    fun exit() { vm.wifiReset(); onBack() }
    fun leave() { if (inFlight) confirmLeave = true else exit() }
    BackHandler { leave() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.wifiAutoDetect() }
    if (confirmLeave) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_salir_del_asistente)) },
        text = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_el_altavoz_seguira_uniendose_a)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; exit() }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_salir)).uppercase()) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_seguir_esperando)).uppercase()) } },
    )
    val ctx = LocalContext.current
    // Paso 3: la lista incluye las redes que ve el móvil. Para leerlas, Android pide «Dispositivos cercanos» (una vez).
    val askScan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.wifiMergePhoneNetworks() }
    var askedScan by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(ui.stage == WifiStage.CHOOSE) {
        if (ui.stage != WifiStage.CHOOSE) return@LaunchedEffect
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.wifiMergePhoneNetworks() else if (!askedScan) { askedScan = true; askScan.launch(perm) }
    }
    WifiSetupContent(
        ui = ui, inAppConnect = vm.wifiInAppConnect, snackbar = snackbar,
        onOpenWifi = { vm.wifiWatch(); openWifiPanel(ctx) },
        onAuto = { if (Build.VERSION.SDK_INT >= 29) ask.launch(perm) else vm.wifiConnect() },
        onSend = vm::wifiSend, onRetry = vm::wifiReset,
        onRefresh = vm::wifiRefreshNetworks, onBackToChoose = vm::wifiBackToChoose,
        onUse = { name -> vm.wifiUseSpeaker(name); onDone() }, onBack = ::leave,
    )
}

/**
 * Panel de Wi-Fi de Android encima de la app (Android 10+); si no existe, los ajustes de Wi-Fi.
 * Es una conexión normal, así que Android muestra su aviso «no tiene acceso a Internet · ¿mantener la conexión?».
 */
private fun openWifiPanel(ctx: android.content.Context) {
    val panel = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
    runCatching { ctx.startActivity(panel) }.onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
}

/** Asistente de 5 pasos con luces piloto. Sin estado propio de la app (renderizable en pruebas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WifiSetupContent(
    ui: WifiSetupUi,
    inAppConnect: Boolean,
    snackbar: SnackbarHostState,
    onOpenWifi: () -> Unit,
    onAuto: () -> Unit,
    onSend: (String, String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBackToChoose: () -> Unit,
    onUse: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_configurar_la_wi_fi)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Txt.s(R.string.ui_volver)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp)) {
            Surface(color = cs.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                StepList(ui.steps, Modifier.padding(vertical = 8.dp))
            }

            when (ui.stage) {
                WifiStage.INTRO -> Intro(inAppConnect, onOpenWifi, onAuto)
                WifiStage.WATCHING -> Watching(ui, inAppConnect, onOpenWifi, onAuto)
                WifiStage.CONNECTING -> Busy(Txt.s(R.string.ui_conectando_con_el_altavoz))
                WifiStage.CHOOSE -> ChooseNetwork(ui, onSend, onRefresh)
                WifiStage.SENDING -> Busy(Txt.s(R.string.ui_enviando_los_datos_al_altavoz))
                WifiStage.WAITING -> Waiting(ui)
                WifiStage.FINDING -> Busy(Txt.s(R.string.ui_buscando_el_altavoz_en_tu))
                WifiStage.DONE -> Done(ui, onUse)
                WifiStage.FAILED -> {
                    val ctx = LocalContext.current
                    val atConnect = ui.steps.getOrNull(1)?.state == io.github.soundtouchradio.discovery.StepState.FAILED
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (atConnect) {
                            // Misma jerarquía que la entrada: el panel de Wi-Fi primero, la conexión automática detrás.
                            JoinSteps()
                            Button(onClick = onOpenWifi, modifier = Modifier.fillMaxWidth()) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_abrir_wi_fi)).uppercase()) }
                            if (inAppConnect) TextButton(onClick = onAuto) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_conexion_automatica)).uppercase()) }
                            if (ui.permissionDenied) TextButton(onClick = {
                                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
                            }) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_conceder_el_permiso_en_los)).uppercase()) }
                        } else {
                            // Fallo al enviar, o el altavoz sigue en su red: corregir sin repetir la conexión.
                            val canResend = ui.steps.getOrNull(3)?.state == io.github.soundtouchradio.discovery.StepState.FAILED || ui.stillInSetup
                            if (canResend) Button(onClick = onBackToChoose, modifier = Modifier.fillMaxWidth()) {
                                Text((if (ui.stillInSetup) Txt.s(R.string.ui_corregir_y_volver_a_enviar) else Txt.s(R.string.ui_volver_a_enviar)).uppercase())
                            }
                            OutlinedButton(onClick = onRetry, modifier = if (canResend) Modifier.fillMaxWidth() else Modifier) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_empezar_de_nuevo)).uppercase()) }
                        }
                    }
                }
            }

            // Último recurso, plegado: el formulario web del propio altavoz (fuera de la app). Solo mientras se busca la conexión.
            val connecting = ui.stage == WifiStage.INTRO || ui.stage == WifiStage.WATCHING ||
                (ui.stage == WifiStage.FAILED && ui.steps.getOrNull(1)?.state == io.github.soundtouchradio.discovery.StepState.FAILED)
            if (connecting) LastResort()
        }
    }
}

@Composable
private fun LastResort() {
    val cs = MaterialTheme.colorScheme
    Column {
            var lastResort by rememberSaveable { mutableStateOf(false) }
            io.github.soundtouchradio.ui.components.Disclosure(Txt.s(R.string.ui_si_nada_de_esto_funciona), lastResort, { lastResort = !lastResort },
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            if (lastResort) Text(
                Txt.s(R.string.ui_con_el_movil_conectado_a) +
                    Txt.s(R.string.ui_despues_vuelve_aqui_y_usa),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
    }
}

@Composable
private fun Intro(inAppConnect: Boolean, onOpenWifi: () -> Unit, onAuto: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_pon_el_altavoz_en_modo), style = MaterialTheme.typography.titleMedium)
        // Genérico para cualquier SoundTouch (1.9.3): el modelo no se conoce hasta conectar con el altavoz.
        Text(Txt.s(R.string.ui_si_su_indicador_wi_fi) +
            Txt.s(R.string.ui_los_de_esta_app_no),
            style = MaterialTheme.typography.bodyMedium)
        ResetByModel()
        Spacer(Modifier.size(8.dp))
        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_conecta_el_movil_a_la), style = MaterialTheme.typography.titleMedium)
        JoinSteps()
        Button(onClick = onOpenWifi, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_abrir_wi_fi_2)).uppercase()) }
        if (inAppConnect) TextButton(onClick = onAuto) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_conexion_automatica_2)).uppercase()) }
    }
}

/**
 * Los tres pasos que sigue también la app de Bose. El segundo es el que sostiene la conexión: sin ese «Sí»,
 * Android vuelve a la Wi-Fi de casa. Iconos: el de Wi-Fi y el de «sin internet» que muestra el propio Android.
 */
@Composable
private fun JoinSteps() {
    val cs = MaterialTheme.colorScheme
    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = cs.onSurface)
    val steps: List<Pair<ImageVector?, AnnotatedString>> = listOf(
        null to buildAnnotatedString {
            append(Txt.s(R.string.ui_toca_abrir_wi_fi_y)); withStyle(strong) { append(Txt.s(R.string.ui_bose)) }
            append(Txt.s(R.string.ui_por_ejemplo_bose_st_10))
        },
        null to buildAnnotatedString {
            append(Txt.s(R.string.ui_android_avisara_de_que_la))
            withStyle(strong) { append(Txt.s(R.string.ui_si)) }; append(Txt.s(R.string.ui_para_mantener_la_conexion))
        },
        null to AnnotatedString(Txt.s(R.string.ui_cierra_el_panel_o_vuelve)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        steps.forEachIndexed { i, (icon, text) ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium.merge(Tabular), color = cs.onSurfaceVariant,
                    modifier = Modifier.width(24.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (icon != null) {
                    Spacer(Modifier.width(12.dp))
                    Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                } else Spacer(Modifier.width(36.dp))
            }
        }
    }
}

/** Esperando a que el móvil entre en la red del altavoz (el usuario está o estuvo en el panel de Wi-Fi). */
@Composable
private fun Watching(ui: WifiSetupUi, inAppConnect: Boolean, onOpenWifi: () -> Unit, onAuto: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = Txt.s(R.string.ui_esperando_a_que_el_movil)
        }) {
            SpeakerGrille(null, GrilleMode.LOADING, Txt.s(R.string.ui_esperando_a_la_red_del_2), size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_esperando_a_la_red_del), style = MaterialTheme.typography.bodyLarge)
        }
        JoinSteps()
        OutlinedButton(onClick = onOpenWifi, modifier = Modifier.fillMaxWidth()) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_abrir_wi_fi_otra_vez)).uppercase()) }
        if (inAppConnect) TextButton(onClick = onAuto) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_probar_conexion_automatica_3)).uppercase()) }
    }
}

/** Final: como en la app de Bose, se propone un nombre («SoundTouch 10» tras un reset) antes de usar el altavoz. */
@Composable
private fun Done(ui: WifiSetupUi, onUse: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val info = ui.report?.info
    var name by rememberSaveable(info?.deviceId) {
        mutableStateOf(info?.let { io.github.soundtouchradio.soundtouch.WifiSetupXml.suggestedName(it) }.orEmpty())
    }
    val error = if (name.isBlank()) null else io.github.soundtouchradio.soundtouch.SoundTouchXml.validateName(name)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(info?.type ?: "SoundTouch", style = MaterialTheme.typography.headlineSmall)
        Text(listOfNotNull(info?.name, ui.report?.host).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge.merge(Tabular), color = cs.onSurfaceVariant)
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_nombre_del_altavoz_2)) },
            supportingText = { Text(error ?: Txt.s(R.string.ui_por_ejemplo_salon_se_guarda)) },
            isError = error != null, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Button(onClick = { onUse(name.takeIf { it.isNotBlank() }) }, enabled = error == null, modifier = Modifier.fillMaxWidth()) {
            Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_usar_este_altavoz_2)).uppercase())
        }
    }
}

/**
 * Cómo se restablece cada modelo. Solo el SoundTouch 10 está verificado con esta app; el resto sigue las instrucciones
 * publicadas por Bose y se indica así. Plegado: no estorba a quien ya sabe hacerlo.
 */
@Composable
private fun ResetByModel() {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    io.github.soundtouchradio.ui.components.Disclosure(Txt.s(R.string.ui_como_se_restablece_mi_modelo), open, { open = !open },
        Modifier.padding(vertical = 4.dp))
    if (open) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Txt.s(R.string.ui_soundtouch_10) to Txt.s(R.string.ui_manten_pulsados_preset_1_y),
            Txt.s(R.string.ui_soundtouch_20_y_30) to Txt.s(R.string.ui_manten_pulsados_preset_1_y_2),
            Txt.s(R.string.ui_soundtouch_portable) to Txt.s(R.string.ui_manten_pulsados_preset_1_y_2),
            Txt.s(R.string.ui_otros_300_wave_sa_5) to Txt.s(R.string.ui_sigue_el_manual_de_tu),
        ).forEach { (model, how) ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(model, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.width(132.dp))
                Text(how, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeakerGrille(null, GrilleMode.LOADING, text, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Waiting(ui: WifiSetupUi) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeakerGrille(null, GrilleMode.LOADING, Txt.s(R.string.ui_esperando_a_que_el_altavoz), size = 64.dp)
        Spacer(Modifier.width(16.dp))
        val announced = ((ui.secondsLeft + 9) / 10) * 10
        Column(Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = Txt.s(R.string.ui_quedan_unos_segundos_el_altavoz, announced, ui.chosenSsid)
        }) {
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_s, (ui.secondsLeft).toString()), style = MaterialTheme.typography.displaySmall.merge(Tabular))
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_el_altavoz_se_esta_uniendo, (ui.chosenSsid).toString()),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChooseNetwork(ui: WifiSetupUi, onSend: (String, String) -> Unit, onRefresh: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Una red solo de 5 GHz se descarta únicamente si el altavoz devolvió su lista y no la ve (vale para cualquier
    // modelo: hay SoundTouch que usan 5 GHz). Sin lista del altavoz, se permite con aviso.
    fun blocked(n: WifiNetwork) = n.only5GHz && ui.speakerSurveyOk
    val usable = ui.networks.filterNot { blocked(it) }
    val initial = ui.chosenSsid?.takeIf { c -> usable.any { it.ssid == c } } ?: usable.firstOrNull()?.ssid
    var selected by rememberSaveable { mutableStateOf<String?>(initial) }
    // La lista puede llegar después (red del móvil, «Actualizar»): si no había nada elegido, se elige la primera.
    androidx.compose.runtime.LaunchedEffect(usable.firstOrNull()?.ssid) { if (selected == null || usable.none { it.ssid == selected }) selected = initial }
    var manual by rememberSaveable { mutableStateOf(ui.surveyFailed && ui.chosenSsid == null) }
    androidx.compose.runtime.LaunchedEffect(ui.surveyFailed) { if (!ui.surveyFailed && selected != null) manual = false }
    var manualSsid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    val ssid = if (manual) manualSsid.trim() else selected.orEmpty()
    val net = ui.networks.firstOrNull { it.ssid == ssid }
    val needsPassword = if (manual) !manualOpen else net?.secure != false

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
            Column(Modifier.weight(1f)) { SectionTitle(Txt.s(R.string.ui_redes_wi_fi)) }
            if (ui.refreshing) SpeakerGrille(null, GrilleMode.LOADING, Txt.s(R.string.ui_buscando_redes), size = 24.dp)
            else TextButton(onClick = onRefresh) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_actualizar)).uppercase()) }
        }
        Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_si_android_aun_pregunta_si),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        run {
            ui.networks.forEach { n ->
                val enabled = !blocked(n)
                ListItem(
                    modifier = Modifier.selectable(selected = enabled && !manual && n.ssid == selected, enabled = enabled, role = Role.RadioButton) { manual = false; selected = n.ssid },
                    colors = ListItemDefaults.colors(containerColor = cs.surface),
                    leadingContent = { RadioButton(selected = enabled && !manual && n.ssid == selected, onClick = null, enabled = enabled) },
                    headlineContent = { Text(n.ssid, color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f)) },
                    supportingContent = when {
                        !enabled -> ({ Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_solo_5_ghz_y_tu)) })
                        n.only5GHz -> ({ Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_solo_5_ghz_comprueba_que)) })
                        else -> null
                    },
                    trailingContent = { NetworkSignal(n) },
                )
            }
            ListItem(
                modifier = Modifier.selectable(selected = manual, role = Role.RadioButton) { manual = true },
                colors = ListItemDefaults.colors(containerColor = cs.surface),
                leadingContent = { RadioButton(selected = manual, onClick = null) },
                headlineContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_otra_red)) },
                supportingContent = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_red_oculta_o_que_no)) },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (manual) OutlinedTextField(
                value = manualSsid, onValueChange = { manualSsid = it }, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_nombre_de_la_red)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (manual) Row(
                Modifier.fillMaxWidth().toggleable(manualOpen, role = Role.Switch) { manualOpen = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_red_sin_contrasena), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                io.github.soundtouchradio.ui.components.Switch(checked = manualOpen, onCheckedChange = null)
            }
            if (needsPassword) OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_contrasena_de_la_wi_fi)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    // Tecla rotulada VER / OCULT. en lugar del ojo (1.15.0).
                    io.github.soundtouchradio.ui.components.PanelKey(if (show) Txt.s(R.string.ui_tecla_ocultar) else Txt.s(R.string.ui_tecla_ver),
                        onClick = { show = !show }, active = show, modifier = Modifier.padding(end = 6.dp),
                        description = if (show) Txt.s(R.string.ui_ocultar_contrasena) else Txt.s(R.string.ui_mostrar_contrasena))
                },
            )
            Text(androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_la_contrasena_viaja_unos_segundos),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Button(
                onClick = { onSend(ssid, if (needsPassword) password else "") },
                enabled = ssid.isNotEmpty() && (!needsPassword || password.length >= 8 || (net != null && "wep" in net.securityTypes)),
                modifier = Modifier.fillMaxWidth(),
            ) { Text((androidx.compose.ui.res.stringResource(io.github.soundtouchradio.R.string.ui_enviar_a_soundtouch)).uppercase()) }
        }
    }
}

@Composable
private fun NetworkSignal(n: WifiNetwork) {
    val cs = MaterialTheme.colorScheme
    val desc = Txt.s(R.string.ui_senal, listOf(Txt.s(R.string.ui_senal_muy_debil),Txt.s(R.string.ui_senal_debil),Txt.s(R.string.ui_senal_media),Txt.s(R.string.ui_senal_buena),Txt.s(R.string.ui_senal_excelente))[n.bars]) + if (n.secure) Txt.s(R.string.ui_protegida) else Txt.s(R.string.ui_abierta)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = desc }) {
        // Rótulo WPA y escala de pilotos de señal en lugar del candado y del abanico Wi-Fi (1.15.0).
        if (n.secure) io.github.soundtouchradio.ui.components.EngravedTag("WPA")
        Spacer(Modifier.width(8.dp))
        io.github.soundtouchradio.ui.components.SignalScale(n.bars)
    }
}
