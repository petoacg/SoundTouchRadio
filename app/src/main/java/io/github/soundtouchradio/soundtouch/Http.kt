package io.github.soundtouchradio.soundtouch

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    /** Cliente para la LAN: timeouts cortos, sin caché, sin cookies. */
    fun lan(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Cliente para sondear streams de emisoras: algo más paciente y siguiendo redirecciones. */
    fun internet(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val USER_AGENT = "SoundTouchRadio/1.0 (Android)"
}

class SoundTouchException(message: String, val error: SoundTouchError? = null, cause: Throwable? = null) :
    Exception(message, cause)
