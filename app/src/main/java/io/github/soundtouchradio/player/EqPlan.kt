package io.github.soundtouchradio.player

import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Ajuste de sonido de «Este móvil» para una salida (Edifier, auriculares…). levels: el ajuste fino propio («Mío»), en mB
 * por banda. Se conserva aunque se elija otro ajuste (1.10.3): se puede probar «Voz» y volver a «Mío» sin perderlo.
 */
@Serializable
data class EqSetting(val preset: String = EqPlan.NEUTRO, val levels: List<Int> = emptyList())

/**
 * Ecualizador sencillo (1.10.2): cuatro ajustes pensados para la radio y un ajuste fino. Los ajustes se definen como
 * curvas en dB según la frecuencia, y se traducen a las bandas que ofrezca el móvil (normalmente 5).
 */
object EqPlan {
    const val NEUTRO = "neutro"; const val VOZ = "voz"; const val GRAVES = "graves"; const val NOCHE = "noche"; const val PERSONAL = "personal"
    val PRESETS = listOf(NEUTRO, VOZ, GRAVES, NOCHE)

    /** Ganancia en dB del ajuste [preset] a la frecuencia [hz]. */
    fun gainDb(preset: String, hz: Int): Float {
        val f = hz.coerceAtLeast(20).toFloat()
        return when (preset) {
            VOZ -> when { f < 150 -> -4f; f < 400 -> -1f; f < 4_000 -> 3f; else -> 1f }     // voces claras: noticias y tertulias
            GRAVES -> when { f < 120 -> 5f; f < 300 -> 3f; f < 1_000 -> 0f; else -> 0f }
            NOCHE -> when { f < 150 -> -5f; f < 400 -> -2f; f < 4_000 -> 0f; else -> -2f } // menos retumbe y agudos suaves
            else -> 0f
        }
    }

    /** Niveles en milibelios para las bandas del móvil, recortados a su rango. */
    fun levels(setting: EqSetting, centersHz: List<Int>, minMb: Int, maxMb: Int): List<Int> =
        if (setting.preset == PERSONAL && setting.levels.size == centersHz.size) setting.levels.map { it.coerceIn(minMb, maxMb) }
        else centersHz.map { (gainDb(setting.preset, it) * 100).roundToInt().coerceIn(minMb, maxMb) }

    /** «60 Hz», «230 Hz», «910 Hz», «3,6 kHz», «14 kHz». */
    fun bandLabel(hz: Int): String = when {
        hz < 1_000 -> "$hz Hz"
        hz < 10_000 -> "%.1f kHz".format(java.util.Locale("es", "ES"), hz / 1000f)
        else -> "${(hz / 1000f).roundToInt()} kHz"
    }

    /**
     * Nombre legible de la salida: «EDIFIER R1855DB» → «Edifier R1855DB». Las palabras con cifras (modelos) o de hasta 3 letras (siglas: JBL, LG) se dejan
     * como vienen. Solo para mostrar; el ajuste se guarda con el nombre original.
     */
    fun prettyName(raw: String): String {
        val letters = raw.filter { it.isLetter() }
        if (letters.isEmpty() || letters != letters.uppercase()) return raw
        return raw.split(' ').joinToString(" ") { w ->
            if (w.any { it.isDigit() } || w.length <= 3) w else w.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    @Suppress("unused") private fun octaves(a: Int, b: Int) = log10(b.toFloat() / a) / log10(2f)
}
