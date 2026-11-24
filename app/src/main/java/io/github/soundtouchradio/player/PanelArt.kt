package io.github.soundtouchradio.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.soundtouchradio.R

/**
 * Carátula del reproductor de la persiana y la pantalla de bloqueo (1.16.0): el mismo frontal que el panel de la app,
 * dibujado como imagen, porque Android pinta el reproductor del sistema con su propia letra y sus colores y solo deja a
 * la app la imagen de fondo.
 *
 * Composición apaisada (Android 13+ la usa de fondo a todo lo ancho y escribe el título arriba a la izquierda): a la
 * derecha, la rejilla de perforaciones con los puntos encendidos según el volumen; arriba, los pilotos rotulados AIRE y
 * ESPERA; abajo, la escala de 20 pilotos del volumen. La zona izquierda queda lisa para el texto del sistema.
 * En versiones que la recortan en cuadrado, queda la rejilla en el centro.
 */
object PanelArt {
    private const val W = 960
    private const val H = 480

    private val CARCASA = 0xFF2D2E30.toInt()
    private val APAGADO = 0xFF45474A.toInt()
    private val TINTA = 0xFFC5C6C9.toInt()
    private val VERDE = 0xFF8BD5A2.toInt()
    private val AMBAR = 0xFFFFB870.toInt()
    private val ESPERA = 0x9EFFB870.toInt()   // ámbar al 62 %: el «standby»

    private var cacheKey: String? = null
    private var cache: Bitmap? = null

    /** [onAir] = suena confirmado; si no, en espera (pausa). [volume] 0–100 o null si no se conoce. */
    fun render(context: Context, onAir: Boolean, volume: Int?): Bitmap {
        val aire = io.github.soundtouchradio.ui.Txt.s(R.string.ui_piloto_aire)
        val espera = io.github.soundtouchradio.ui.Txt.s(R.string.ui_piloto_espera)
        val key = "$onAir|$volume|$aire"
        cache?.takeIf { cacheKey == key }?.let { return it }
        val mono = runCatching { ResourcesCompat.getFont(context, R.font.ibm_plex_mono_medium) }.getOrNull() ?: Typeface.MONOSPACE
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(CARCASA)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val lit = if (onAir) VERDE else ESPERA

        // Rejilla de perforaciones, 6 × 6, a la derecha: se llena desde abajo en proporción al volumen.
        val n = 6
        val cell = 44f
        val gx = W - 72f - cell * n
        val gy = 92f
        val on = (((volume ?: 0).coerceIn(0, 100) / 100f) * n * n).toInt()
        for (row in 0 until n) for (col in 0 until n) {
            val index = (n - 1 - row) * n + col
            p.color = if (index < on) lit else APAGADO
            c.drawCircle(gx + cell * (col + 0.5f), gy + cell * (row + 0.5f), cell * 0.28f, p)
        }

        // Ventana de pilotos rotulados, encima de la rejilla.
        p.textSize = 22f
        p.typeface = mono
        p.letterSpacing = 0.06f
        val labels = listOf(aire to (if (onAir) VERDE else null), espera to (if (onAir) null else ESPERA))
        var x = gx
        val ly = 52f
        for ((label, color) in labels) {
            p.color = color ?: APAGADO
            c.drawRect(x, ly - 14f, x + 14f, ly, p)
            p.color = color ?: (TINTA and 0x99FFFFFF.toInt())
            c.drawText(label, x + 22f, ly, p)
            x += 22f + p.measureText(label) + 28f
        }

        // Escala de 20 pilotos del volumen a lo ancho, abajo (verde; ámbar desde el 80).
        val left = 72f
        val right = W - 72f
        val top = H - 92f
        val count = 20
        val step = (right - left) / count
        val sq = 18f
        val litCount = Math.round(((volume ?: 0).coerceIn(0, 100) / 100f) * count)
        for (i in 0 until count) {
            p.color = when {
                i >= litCount -> APAGADO
                i >= (count * 0.8f).toInt() -> AMBAR
                else -> lit
            }
            val sx = left + step * i + (step - sq) / 2
            c.drawRect(RectF(sx, top, sx + sq, top + sq), p)
        }
        // Carril fino bajo la escala, como el del fader.
        p.color = APAGADO
        c.drawRect(left, top + sq + 16f, right, top + sq + 18f, p)
        cacheKey = key; cache = b
        return b
    }
}
