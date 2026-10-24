package dev.alaev.rezkatv.session

data class ResumePlan(
    val translatorId: String,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    val fromOtherDevice: Boolean,
)

object ResumePlanner {
    const val FROM_STORE = -1L

    fun plan(local: WatchSession?, site: SitePosition?): ResumePlan? {
        if (local == null) return site?.let { ResumePlan(it.translatorId, it.season, it.episode, FROM_STORE, false) }
        val mine = ResumePlan(local.translatorId, local.season, local.episode, local.positionMs, false)
        if (site == null) return mine
        val reported = local.reported ?: return mine
        if (site == reported) return mine
        return if (site.season == local.season && site.episode == local.episode) {
            mine.copy(translatorId = site.translatorId, fromOtherDevice = site.translatorId != local.translatorId)
        } else {
            ResumePlan(site.translatorId, site.season, site.episode, FROM_STORE, true)
        }
    }
}
