package io.github.soundtouchradio

import io.github.soundtouchradio.playlist.PlaylistContent
import io.github.soundtouchradio.playlist.PlaylistKind
import io.github.soundtouchradio.playlist.PlaylistParser
import io.github.soundtouchradio.playlist.UrlRules
import org.junit.Assert.*
import org.junit.Test

class PlaylistAndUrlTest {
    @Test fun validatesUrls() {
        assertTrue(UrlRules.validate(" http://r.example/live ") is UrlRules.Validation.Ok)
        assertTrue(UrlRules.validate("ftp://x") is UrlRules.Validation.Invalid)
        assertTrue(UrlRules.validate("") is UrlRules.Validation.Invalid)
    }

    @Test fun httpsVariant() {
        assertEquals("http://r.example/live.mp3", UrlRules.httpVariant("https://r.example/live.mp3"))
        assertEquals("http://r.example:8443/a", UrlRules.httpVariant("https://r.example:8443/a"))
        assertNull(UrlRules.httpVariant("http://r.example/a"))
    }

    @Test fun distinguishesHlsFromM3u() {
        assertTrue(UrlRules.looksLikeHls("http://r.example/live/index.m3u8?token=1"))
        assertNull(UrlRules.playlistKind("http://r.example/live/index.m3u8"))
        assertEquals(PlaylistKind.M3U, UrlRules.playlistKind("http://r.example/radio.m3u"))
        assertEquals(PlaylistKind.PLS, UrlRules.playlistKind("http://r.example/listen", "audio/x-scpls"))
    }

    @Test fun guessesMime() {
        assertEquals("audio/aac", UrlRules.guessMime("http://x/stream", "audio/aacp"))
        assertEquals("audio/mpeg", UrlRules.guessMime("http://x/stream", "text/html"))
        assertEquals("audio/aac", UrlRules.guessMime("http://x/live.aac"))
        assertEquals("MP3", UrlRules.codecLabel("audio/mpeg"))
    }

    @Test fun parsesPlsInOrderAndRelative() {
        val pls = "[playlist]\nNumberOfEntries=2\nFile2=http://b.example/2\nTitle1=Uno\nFile1=/stream1\nVersion=2\n"
        assertEquals(listOf("http://base.example/stream1", "http://b.example/2"), PlaylistParser.parsePls(pls, "http://base.example/list.pls"))
    }

    @Test fun parsesM3uAndDetectsHls() {
        val m3u = "#EXTM3U\n#EXTINF:-1,Uno\nhttp://a.example/1\n\n# comentario\nhttp://a.example/2\n"
        assertEquals(PlaylistContent.Entries(listOf("http://a.example/1", "http://a.example/2")), PlaylistParser.parseM3u(m3u))
        assertEquals(PlaylistContent.Hls, PlaylistParser.parseM3u("#EXTM3U\n#EXT-X-VERSION:3\nchunk.ts\n"))
    }

    @Test fun decodesLatin1AndBom() {
        val latin = "Title1=Canción\n".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Title1=Canción\n", PlaylistParser.decode(latin))
        assertEquals("x", PlaylistParser.decode("﻿x".toByteArray()))
    }
}
