package dev.alaev.rezkatv.rezka

import org.json.JSONObject
import java.io.IOException
import java.net.URI

/** Ответы плеера rezka: потоки по качествам, субтитры, превью перемотки. */
object StreamParser {

    // «[720p]https://a…m3u8 or https://b…mp4,[1080p]…»; в самих ссылках нет «[», а запятые бывают только между блоками
    private val BLOCK = Regex("""\[([^\]]+)]([^\[]*)""")
    private val VTT_TIME = Regex("""(\d+:)?(\d+):(\d+)[.,](\d{1,3})""")

    /** Ответ /ajax/get_cdn_series/ (get_stream, get_movie, get_episodes). */
    fun response(json: String): JSONObject {
        val obj = JSONObject(json)
        if (!obj.optBoolean("success")) throw IOException("rezka: ${obj.optString("message").ifEmpty { "нет потока" }}")
        return obj
    }

    fun streams(obj: JSONObject): Streams {
        val languages = obj.optJSONObject("subtitle_lns")?.let { lns ->
            lns.keys().asSequence().associateWith { lns.optString(it) }
        }.orEmpty()
        return Streams(
            qualities = qualities(obj.optString("url").takeUnless { obj.isNull("url") }.orEmpty()),
            subtitles = subtitles(obj.optString("subtitle").takeUnless { obj.isNull("subtitle") }.orEmpty(), languages),
            defaultSubtitle = obj.optString("subtitle_def").takeIf { it.isNotEmpty() && it != "false" },
            thumbnails = obj.optString("thumbnails").takeIf { it.isNotEmpty() && it != "false" },
        )
    }

    /**
     * Строка потоков → качества. Пусто, если формат незнакомый (например, зашифрованная строка):
     * тогда играем через плеер сайта.
     */
    fun qualities(raw: String): List<Quality> =
        BLOCK.findAll(raw).mapNotNull { m ->
            val urls = m.groupValues[2].trim().trimEnd(',').split(" or ")
                .map { it.trim() }
                .filter { it.startsWith("http") }
            if (urls.isEmpty()) return@mapNotNull null
            // HLS перематывается и стартует быстрее MP4: сначала все m3u8, потом остальное
            Quality(m.groupValues[1].trim(), urls.sortedBy { if (it.endsWith(".m3u8")) 0 else 1 })
        }.toList()

    /** «[Русский]https://…vtt,[English]https://…vtt» + {"Русский": "ru"} */
    fun subtitles(raw: String, languages: Map<String, String>): List<Subtitle> =
        BLOCK.findAll(raw).mapNotNull { m ->
            val label = m.groupValues[1].trim()
            val url = m.groupValues[2].trim().trimEnd(',')
            if (!url.startsWith("http")) return@mapNotNull null
            Subtitle(label, url, languages[label]?.ifEmpty { null })
        }.toList()

    /** WebVTT со ссылками на спрайты: «00:00:30.000 --> 00:01:00.000» и «https://…/t.jpg#xywh=160,0,160,90». */
    fun thumbnails(vtt: String, vttUrl: String): List<Tile> {
        val base = runCatching { URI(vttUrl) }.getOrNull()
        val lines = vtt.lineSequence().map { it.trim() }.toList()
        val tiles = ArrayList<Tile>()
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.contains("-->")) continue
            val start = time(line.substringBefore("-->")) ?: continue
            val end = time(line.substringAfter("-->")) ?: continue
            val ref = lines.getOrNull(i + 1)?.takeIf { it.isNotEmpty() } ?: continue
            val xywh = ref.substringAfter("#xywh=", "").split(',').mapNotNull { it.trim().toIntOrNull() }
            if (xywh.size != 4) continue
            val image = ref.substringBefore('#')
            val url = if (image.startsWith("http") || base == null) image else base.resolve(image).toString()
            tiles += Tile(start, end, url, xywh[0], xywh[1], xywh[2], xywh[3])
        }
        return tiles
    }

    private fun time(text: String): Long? {
        val m = VTT_TIME.find(text.trim()) ?: return null
        val hours = m.groupValues[1].removeSuffix(":").toLongOrNull() ?: 0
        val minutes = m.groupValues[2].toLong()
        val seconds = m.groupValues[3].toLong()
        val millis = m.groupValues[4].padEnd(3, '0').toLong()
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }
}
