package io.github.soundtouchradio.player

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Curva de la barra de volumen (1.8.0): el tramo bajo ocupa más recorrido. Posición p ∈ [0, 1] → volumen = 100·p².
 * El primer cuarto de la barra cubre 0–6 y la mitad llega a 25: cada punto bajo mide unas 3 veces más que antes.
 * Ida y vuelta exacta para todos los valores enteros 0–100 (probado en VolumeCurveTest).
 */
object VolumeCurve {
    fun toPosition(volume: Int): Float = sqrt(volume.coerceIn(0, 100) / 100f)
    fun fromPosition(p: Float): Int = (100f * p.coerceIn(0f, 1f) * p.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 100)

    /**
     * Seguridad nocturna (1.8.2): la barra solo se mueve arrastrando su tirador. Un toque en la pista vacía no hace nada.
     * ¿Empieza la pulsación sobre el tirador? (margen [slopPx] a cada lado).
     */
    fun grabsThumb(downX: Float, thumbX: Float, slopPx: Float): Boolean = kotlin.math.abs(downX - thumbX) <= slopPx

    /** Arrastre relativo: la posición avanza lo que avanza el dedo, desde donde estaba el tirador (sin saltos). */
    fun dragTo(startPosition: Float, dxPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) startPosition else (startPosition + dxPx / widthPx).coerceIn(0f, 1f)
}
