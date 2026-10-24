package dev.alaev.rezkatv.rezka

object PlayLink {

    fun build(url: String, translatorId: String, season: Int = 0, episode: Int = 0): String {
        val base = url.substringBefore('#')
        return if (season > 0 && episode > 0) "$base#t:$translatorId-s:$season-e:$episode" else "$base#t:$translatorId"
    }
}
