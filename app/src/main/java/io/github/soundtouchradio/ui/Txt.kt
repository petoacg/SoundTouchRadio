package io.github.soundtouchradio.ui

import android.content.Context

/**
 * Textos de la app fuera de las funciones de pantalla (accesibilidad, avisos, ViewModel, notificaciones).
 * Lee de res/values/strings.xml, así que se traducen con un values-xx/strings.xml (1.9.4).
 * Se inicializa en App.onCreate; en las pruebas de capturas, en el arnés.
 */
object Txt {
    @Volatile private var ctx: Context? = null
    fun init(context: Context) { ctx = context.applicationContext ?: context }

    /** Desde la actividad: toma su configuración (idioma elegido en Ajustes) sin retener la actividad (1.11.0). */
    fun refresh(activity: Context) {
        val app = activity.applicationContext ?: activity
        ctx = app.createConfigurationContext(activity.resources.configuration)
    }
    fun s(id: Int, vararg args: Any?): String {
        val strArgs = args.map { it?.toString() ?: "" }.toTypedArray()
        val c = ctx ?: return fallback(id, strArgs)
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *strArgs)
    }

    /**
     * Solo en pruebas JVM (sin Android): lee el mismo res/values/strings.xml del módulo. En la app nunca se usa,
     * porque App.onCreate inicializa el contexto antes de nada.
     */
    private val table: Map<Int, String> by lazy {
        val file = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml").map { java.io.File(it) }.firstOrNull { it.exists() }
            ?: return@lazy emptyMap()
        val names = io.github.soundtouchradio.R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val byName = (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as org.w3c.dom.Element
            var v = e.textContent
            if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
            e.getAttribute("name") to v.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        }
        names.mapNotNull { (id, n) -> byName[n]?.let { id to it } }.toMap()
    }

    private fun fallback(id: Int, args: Array<String>): String {
        val raw = table[id] ?: error("Txt: texto $id no encontrado")
        return if (args.isEmpty()) raw else String.format(raw, *args)
    }
}
