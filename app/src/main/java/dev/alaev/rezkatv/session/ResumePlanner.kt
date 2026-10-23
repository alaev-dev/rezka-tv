package dev.alaev.rezkatv.session

/** С чего продолжить: озвучка, серия, позиция (-1 — из сохранённого места этой серии или с начала). */
data class ResumePlan(
    val translatorId: String,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    /** позицию или серию взяли с сайта: смотрели на другом устройстве */
    val fromOtherDevice: Boolean,
)

/**
 * Свести локальный просмотр с тем, что знает сайт. Сайт хранит только озвучку и серию (без времени),
 * поэтому время берём своё, если серия та же.
 */
object ResumePlanner {
    const val FROM_STORE = -1L

    /**
     * [local] — последний просмотр этого тайтла на телевизоре (null — не смотрели здесь),
     * [site] — позиция с сайта (initCDN…Events) или null.
     */
    fun plan(local: WatchSession?, site: SitePosition?): ResumePlan? {
        if (local == null) return site?.let { ResumePlan(it.translatorId, it.season, it.episode, FROM_STORE, false) }
        val mine = ResumePlan(local.translatorId, local.season, local.episode, local.positionMs, false)
        if (site == null) return mine
        // Сайт сдвинулся, только если показывает не то, что мы ему последним отправили.
        // Не дошла наша отправка (не было сети) — верим себе, а не устаревшему сайту.
        val reported = local.reported ?: return mine
        if (site == reported) return mine
        return if (site.season == local.season && site.episode == local.episode) {
            // Та же серия, другая озвучка: время своё, озвучка — выбранная на другом устройстве
            mine.copy(translatorId = site.translatorId, fromOtherDevice = site.translatorId != local.translatorId)
        } else {
            ResumePlan(site.translatorId, site.season, site.episode, FROM_STORE, true)
        }
    }
}
