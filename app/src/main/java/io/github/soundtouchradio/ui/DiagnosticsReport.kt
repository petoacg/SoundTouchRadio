package io.github.soundtouchradio.ui

/**
 * «Copiar informe» de Diagnóstico (1.9.4): texto para pedir ayuda sin publicar datos personales completos.
 * La MAC y el número de serie se acortan (40BD…BFBE) y la última cifra de la IP se oculta (192.168.1.x).
 * Las URL de las emisoras son públicas y se dejan tal cual.
 */
object DiagnosticsReport {
    fun maskMac(mac: String?): String {
        val m = mac?.replace(":", "")?.replace("-", "")?.trim().orEmpty()
        return if (m.length < 8) "—" else m.take(4) + "…" + m.takeLast(4)
    }

    /** Oculta el último octeto de cualquier IPv4 del texto. */
    fun maskIps(text: String): String = Regex("""\b(\d{1,3}\.\d{1,3}\.\d{1,3})\.\d{1,3}\b""").replace(text) { "${it.groupValues[1]}.x" }

    data class Line(val label: String, val value: String)

    fun build(app: String, lines: List<Line>, log: List<String>): String = buildString {
        appendLine(app)
        lines.forEach { appendLine("${it.label}: ${maskIps(it.value)}") }
        if (log.isNotEmpty()) {
            appendLine()
            log.forEach { appendLine(maskIps(it)) }
        }
    }.trimEnd()
}
