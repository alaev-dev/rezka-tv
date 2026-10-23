package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StreamParserTest {

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/rezka/$name")) { "missing fixture $name" }.readText()

    @Test
    fun qualitiesWithHlsFirstAndMirrors() {
        val streams = StreamParser.streams(StreamParser.response(fixture("stream_series.json")))

        assertEquals(listOf("360p", "720p", "1080p Ultra"), streams.qualities.map { it.label })
        assertEquals(
            listOf("https://cdn-a.test/aa:hls:manifest.m3u8", "https://cdn-b.test/aa:hls:manifest.m3u8", "https://cdn-a.test/aa.mp4"),
            streams.qualities[0].urls,
        )
        // MP4 стоял первым, но HLS предпочтительнее
        assertEquals(listOf("https://cdn-a.test/cc:hls:manifest.m3u8", "https://cdn-a.test/cc.mp4"), streams.qualities[1].urls)
        assertEquals(1080, streams.qualities[2].height)
        assertTrue(streams.qualities[2].ultra)
        assertEquals("/ajax/get_cdn_tiles/1/42/?t=1", streams.thumbnails)
    }

    @Test
    fun subtitlesWithLanguages() {
        val streams = StreamParser.streams(StreamParser.response(fixture("stream_series.json")))

        assertEquals(
            listOf(
                Subtitle("Русский", "https://static.test/s/ru.vtt", "ru"),
                Subtitle("English", "https://static.test/s/en.vtt", "en"),
            ),
            streams.subtitles,
        )
        assertEquals("ru", streams.defaultSubtitle)
    }

    @Test
    fun falseFieldsMeanNothing() {
        val streams = StreamParser.streams(StreamParser.response(fixture("stream_plain.json")))

        assertEquals(listOf("480p"), streams.qualities.map { it.label })
        assertTrue(streams.subtitles.isEmpty())
        assertNull(streams.defaultSubtitle)
        assertNull(streams.thumbnails)
    }

    @Test
    fun unknownOrEncryptedStringGivesNoQualities() {
        assertTrue(StreamParser.qualities("#h2Vbq3Zm9fYWxs//_//ZXhhbXBsZQ==").isEmpty())
        assertTrue(StreamParser.qualities("").isEmpty())
        assertTrue(StreamParser.qualities("[720p]not-a-url").isEmpty())
    }

    @Test(expected = IOException::class)
    fun failedResponseThrows() {
        StreamParser.response("""{"success":false,"message":"Время сессии истекло"}""")
    }

    @Test
    fun thumbnailsFromVtt() {
        val tiles = StreamParser.thumbnails(fixture("thumbnails.vtt"), "https://rezka.test/ajax/get_cdn_tiles/1/42/?t=1")

        assertEquals(3, tiles.size) // блок без #xywh пропущен
        assertEquals(Tile(0, 30_000, "https://static.test/t/1.jpg", 0, 0, 160, 90), tiles[0])
        assertEquals(Tile(30_000, 60_000, "https://static.test/t/1.jpg", 160, 0, 160, 90), tiles[1])
        // Часы и относительная ссылка
        assertEquals(Tile(3_600_000, 3_630_000, "https://rezka.test/t/2.jpg", 0, 90, 160, 90), tiles[2])
    }
}
