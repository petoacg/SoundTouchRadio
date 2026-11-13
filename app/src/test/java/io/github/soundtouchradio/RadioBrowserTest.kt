package io.github.soundtouchradio

import io.github.soundtouchradio.directory.Genre
import io.github.soundtouchradio.directory.RadioBrowser
import io.github.soundtouchradio.directory.RadioBrowserJson
import io.github.soundtouchradio.directory.RbQuery
import io.github.soundtouchradio.directory.RbStation
import io.github.soundtouchradio.soundtouch.Http
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

/** 1.7.0: buscador de emisoras sobre radio-browser.info. */
class RadioBrowserTest {
    // Forma real de la respuesta (campos recortados), consultada el 9 de noviembre de 2026.
    private val sample = """[
      {"stationuuid":"a1","name":"Cadena 100","url":"http://x/cadena100.mp3","url_resolved":"http://cadena100-streamers-mp3.flumotion.com/cope/cadena100.mp3","codec":"MP3","bitrate":128,"hls":0,"lastcheckok":1,"countrycode":"ES","tags":"pop,música","clickcount":900,"favicon":"https://x/logo.png","votes":3},
      {"stationuuid":"a2","name":"Radio HLS","url_resolved":"https://x/live.m3u8","codec":"AAC","bitrate":96,"hls":1,"lastcheckok":1,"countrycode":"ES","clickcount":800},
      {"stationuuid":"a3","name":"Caída","url_resolved":"http://x/down.mp3","codec":"MP3","bitrate":64,"hls":0,"lastcheckok":0,"countrycode":"ES","clickcount":700},
      {"stationuuid":"a4","name":"Ogg","url_resolved":"http://x/s.ogg","codec":"OGG","bitrate":128,"hls":0,"lastcheckok":1,"countrycode":"ES","clickcount":600},
      {"stationuuid":"a5","name":"Noticias AAC","url_resolved":"http://x/n.aac","codec":"AAC+","bitrate":null,"hls":0,"lastcheckok":1,"countrycode":"ES","tags":"news","clickcount":950}
    ]"""

    @Test fun parsesAndFiltersCompatible() {
        val all = RadioBrowserJson.parse(sample)
        assertEquals(5, all.size)
        assertEquals(0, all.last().bitrate)   // null → 0
        val ok = RadioBrowserJson.merge(listOf(all), compatibleOnly = true)
        assertEquals(listOf("Noticias AAC", "Cadena 100"), ok.map { it.name })   // sin HLS, caídas ni OGG; más escuchadas primero
        assertEquals("http://cadena100-streamers-mp3.flumotion.com/cope/cadena100.mp3", ok.last().streamUrl)
        assertEquals(5, RadioBrowserJson.merge(listOf(all), compatibleOnly = false).size)
    }

    @Test fun mergeRemovesDuplicatesAcrossTags() {
        val a = RbStation("u1", "SER", url_resolved = "http://x/ser.mp3", codec = "MP3", clickcount = 5)
        val b = RbStation("u2", "SER bis", url_resolved = "HTTP://x/ser.mp3", codec = "MP3", clickcount = 4)
        assertEquals(listOf("u1"), RadioBrowserJson.merge(listOf(listOf(a), listOf(a, b)), true).map { it.stationuuid })
    }

    /** Consulta: parámetros correctos, User-Agent propio, una petición por etiqueta del género, y paso al siguiente servidor si uno falla. */
    @Test fun searchQueriesBothGenreTagsAndFailsOver() {
        val server = MockWebServer()
        val seen = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request }
                return if (request.requestUrl?.queryParameter("tag") == "news")
                    MockResponse().setBody("""[{"stationuuid":"n1","name":"RNE","url_resolved":"http://x/rne.mp3","codec":"MP3","bitrate":128,"clickcount":10}]""")
                else MockResponse().setBody("""[{"stationuuid":"n2","name":"esRadio","url_resolved":"http://x/es.mp3","codec":"MP3","bitrate":64,"clickcount":5}]""")
            }
        }
        server.start()
        val base = server.url("/")
        // Un cliente que reescribe https://<host>/ al servidor de prueba; el primer host «falla» (puerto cerrado).
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val u = chain.request().url
            if (u.host == "caido.example") throw java.io.IOException("sin conexión")
            chain.proceed(chain.request().newBuilder().url(u.newBuilder().scheme("http").host(base.host).port(base.port).build()).build())
        }.build()
        val rb = RadioBrowser(client, hosts = listOf("caido.example", "bueno.example"))
        val r = runBlocking { rb.search(RbQuery(text = "radio", country = "ES", genre = Genre.NEWS)) }
        assertEquals(listOf("RNE", "esRadio"), r.map { it.name })
        assertEquals(2, seen.size)
        val q = seen.first().requestUrl!!
        assertEquals("radio", q.queryParameter("name"))
        assertEquals("ES", q.queryParameter("countrycode"))
        assertEquals("true", q.queryParameter("hidebroken"))
        assertEquals(setOf("news", "noticias"), seen.map { it.requestUrl!!.queryParameter("tag") }.toSet())
        assertEquals(Http.USER_AGENT, seen.first().getHeader("User-Agent"))
        server.shutdown()
    }
}

/** 1.7.1: http primero; las que solo dan https, detrás y marcadas. */
class RadioBrowserHttpsTest {
    @Test fun httpFirstAndHttpsMarked() {
        val a = RbStation("1", "Solo https", url = "https://x/a.mp3", url_resolved = "https://x/a.mp3", codec = "MP3", clickcount = 999)
        val b = RbStation("2", "Http", url_resolved = "http://y/b.mp3", codec = "MP3", clickcount = 10)
        val c = RbStation("3", "Http en url", url = "http://z/c.mp3", url_resolved = "https://z/c.mp3", codec = "MP3", clickcount = 500)
        val m = RadioBrowserJson.merge(listOf(listOf(a, b, c)), true)
        assertEquals(listOf("3", "2", "1"), m.map { it.stationuuid })
        assertEquals("http://z/c.mp3", c.streamUrl)       // se prefiere la http que da el directorio
        assertTrue(a.httpsOnly); assertFalse(c.httpsOnly)
    }
}
