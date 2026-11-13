package io.github.soundtouchradio.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import io.github.soundtouchradio.ui.components.LocalNavBarBottom
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.soundtouchradio.ui.components.rememberReduceMotion
import io.github.soundtouchradio.ui.screens.DiagnosticsScreen
import io.github.soundtouchradio.ui.screens.LinkScreen
import io.github.soundtouchradio.ui.screens.RadioScreen
import io.github.soundtouchradio.ui.screens.SettingsScreen
import io.github.soundtouchradio.ui.screens.StationEditorScreen
import io.github.soundtouchradio.ui.theme.SoundTouchRadioTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Botones de volumen del móvil → altavoz, un punto por pulsación (con la app abierta). */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val delta = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> 1
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> 0
        }
        if (delta != 0 && vm.hardwareVolume(delta)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Txt.refresh(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            SoundTouchRadioTheme(dynamicColor = settings.dynamicColor) { AppNav(vm) }
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val speakers by vm.speakers.collectAsStateWithLifecycle()
    // Mientras no haya altavoz vinculado, la primera pantalla es «Vincular altavoz».
    val start = remember { if (speakers.isEmpty()) "link" else "radio" }
    val reduce = rememberReduceMotion()
    val duration = if (reduce) 0 else 200

    LaunchedEffect(Unit) {
        vm.snacks.collect { s ->
            val r = snackbar.showSnackbar(s.message, s.actionLabel, withDismissAction = s.actionLabel == null,
                duration = if (s.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) s.action?.invoke()
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalNavBarBottom provides navBottom) {
    NavHost(
        navController = nav, startDestination = start,
        // Fade-through de Material; instantáneo con «Quitar animaciones».
        enterTransition = { fadeIn(tween(duration)) }, exitTransition = { fadeOut(tween(duration)) },
        popEnterTransition = { fadeIn(tween(duration)) }, popExitTransition = { fadeOut(tween(duration)) },
    ) {
        composable("radio") {
            RadioScreen(
                vm, snackbar,
                onEditStation = { id -> nav.navigate("station/${id ?: "new"}") },
                onLink = { nav.navigate("link") },
                onDiagnostics = { nav.navigate("diagnostics") },
                onSettings = { nav.navigate("settings") },
                onAlarm = { nav.navigate("alarm") },
                onSearch = { nav.navigate("search") },
            )
        }
        composable("search") { io.github.soundtouchradio.ui.screens.SearchScreen(vm, snackbar, onBack = { nav.popBackStack() }) }
        composable("station/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            val existing = vm.stations.value.firstOrNull { it.id == id }
            StationEditorScreen(vm, existing, snackbar, onDone = { nav.popBackStack() })
        }
        composable("link") {
            val canGoBack = nav.previousBackStackEntry != null
            LinkScreen(vm, snackbar, canGoBack,
                onBack = { nav.popBackStack() },
                onLinked = {
                    if (!nav.popBackStack("radio", inclusive = false)) nav.navigate("radio") { popUpTo("link") { inclusive = true } }
                },
                onDiagnostics = { nav.navigate("diagnostics") },
                onWifiSetup = { nav.navigate("wifisetup") })
        }
        composable("diagnostics") { DiagnosticsScreen(vm, snackbar, onBack = { nav.popBackStack() }) }
        composable("wifisetup") {
            io.github.soundtouchradio.ui.screens.WifiSetupScreen(vm, snackbar, onBack = { nav.popBackStack() },
                onDone = { if (!nav.popBackStack("radio", inclusive = false)) nav.navigate("radio") { popUpTo("link") { inclusive = true } } })
        }
        composable("alarm") { io.github.soundtouchradio.ui.screens.AlarmScreen(vm, snackbar, onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(vm, snackbar, onBack = { nav.popBackStack() }) }
    }
    }
}
