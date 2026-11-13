package io.github.soundtouchradio.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/** Persistencia de un valor en un fichero JSON con escritura atómica (tmp + fsync + rename). */
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    fun read(): T = try {
        if (file.exists()) JSON.decodeFromString(serializer, file.readText()) else default()
    } catch (_: Exception) {
        // Fichero corrupto: se conserva una copia para no perder datos y se arranca vacío.
        runCatching { file.copyTo(File(file.path + ".corrupt"), overwrite = true) }
        default()
    }

    /** Escritura asíncrona y ordenada (un solo hilo): la UI no espera al disco. */
    fun write(value: T) { EXECUTOR.execute { writeNow(value) } }

    @Synchronized
    fun writeNow(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(JSON.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "No se pudo guardar ${file.name}" }
        }
    }

    companion object {
        private val EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "json-store").apply { isDaemon = true } }

        /** Espera a que terminen las escrituras pendientes (tests). */
        fun drain() { EXECUTOR.submit {}.get() }

        val JSON = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    }
}
