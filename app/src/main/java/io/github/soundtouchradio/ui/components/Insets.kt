package io.github.soundtouchradio.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Alto de la barra de navegación de Android medido en la ventana principal (fiable).
 * Las hojas inferiores viven en su propia ventana, donde ese valor puede llegar a 0 con edge-to-edge:
 * por eso se usa el mayor de los dos.
 */
val LocalNavBarBottom = staticCompositionLocalOf { 0.dp }

@Composable
fun sheetBottomInset(): Dp =
    max(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), LocalNavBarBottom.current) + 16.dp

/** Las hojas gestionan sus márgenes ellas mismas (ver [sheetBottomInset]). */
val NoSheetInsets = WindowInsets(0, 0, 0, 0)
