package io.github.soundtouchradio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
 * «Radio Braun»: gris frío de carcasa, texto casi negro y un único color de mando (verde piloto).
 * Ámbar (tertiary) solo para avisos; rojo (error) solo para errores. Contraste AA verificado.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF2A6F47), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7EFC8), onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF55595C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9DDDF), onSecondaryContainer = Color(0xFF181C1E),
    tertiary = Color(0xFF7A4600), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8), onTertiaryContainer = Color(0xFF2A1700),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFECEDEB), onBackground = Color(0xFF1A1C1D),
    surface = Color(0xFFECEDEB), onSurface = Color(0xFF1A1C1D),
    surfaceVariant = Color(0xFFDCDDDB), onSurfaceVariant = Color(0xFF45474A),
    surfaceTint = Color(0xFF55595C),
    outline = Color(0xFF75777A), outlineVariant = Color(0xFFC5C6C4),
    surfaceBright = Color(0xFFF4F5F3), surfaceDim = Color(0xFFD6D7D5),
    surfaceContainerLowest = Color(0xFFF8F9F7), surfaceContainerLow = Color(0xFFF2F3F1),
    surfaceContainer = Color(0xFFE6E7E5), surfaceContainerHigh = Color(0xFFDFE0DE),
    surfaceContainerHighest = Color(0xFFD9DAD8),
    inverseSurface = Color(0xFF2E3032), inverseOnSurface = Color(0xFFEFF0EE), inversePrimary = Color(0xFF8BD5A2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD5A2), onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF12522F), onPrimaryContainer = Color(0xFFA6F2BD),
    secondary = Color(0xFFC1C5C8), onSecondary = Color(0xFF2B3133),
    secondaryContainer = Color(0xFF3F4447), onSecondaryContainer = Color(0xFFDDE1E3),
    tertiary = Color(0xFFFFB870), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3B00), onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1C1E), onBackground = Color(0xFFE3E3E0),
    surface = Color(0xFF1B1C1E), onSurface = Color(0xFFE3E3E0),
    surfaceVariant = Color(0xFF45474A), onSurfaceVariant = Color(0xFFC5C6C9),
    surfaceTint = Color(0xFFC1C5C8),
    outline = Color(0xFF8E9093), outlineVariant = Color(0xFF45474A),
    surfaceBright = Color(0xFF3A3B3D), surfaceDim = Color(0xFF1B1C1E),
    surfaceContainerLowest = Color(0xFF141517), surfaceContainerLow = Color(0xFF1F2022),
    surfaceContainer = Color(0xFF232426), surfaceContainerHigh = Color(0xFF2D2E30),
    surfaceContainerHighest = Color(0xFF38393B),
    inverseSurface = Color(0xFFE3E3E0), inverseOnSurface = Color(0xFF2E3032), inversePrimary = Color(0xFF2A6F47),
)

private val BraunShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)


/**
 * Tipografía de lectura (1.12.0): IBM Plex Mono, incluida en la app (SIL OFL 1.1, docs/LICENCIA-IBM-Plex-Mono.txt).
 * Solo para lo que se mide: números de canal y preset, códec y kbps, volumen, horas, IP. Los nombres siguen en Roboto.
 */
val PlexMono = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(io.github.soundtouchradio.R.font.ibm_plex_mono_regular, androidx.compose.ui.text.font.FontWeight.Normal),
    androidx.compose.ui.text.font.Font(io.github.soundtouchradio.R.font.ibm_plex_mono_medium, androidx.compose.ui.text.font.FontWeight.Medium),
    androidx.compose.ui.text.font.Font(io.github.soundtouchradio.R.font.ibm_plex_mono_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
)
val Readout = TextStyle(fontFamily = PlexMono, fontFeatureSettings = "tnum")

/** Cifras de lectura en toda la app (1.13.0): IP, kbps, porcentajes, tiempos y números de canal, en Plex Mono. */
val Tabular = Readout

/** Frases con cifras (avisos, horas dentro de una frase): Roboto con cifras tabulares, sin pasar a mono. */
val ProseNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SoundTouchRadioTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = BraunShapes, content = content)
}
