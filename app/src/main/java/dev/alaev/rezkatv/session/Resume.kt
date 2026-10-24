package dev.alaev.rezkatv.session

import android.app.Activity
import android.content.Intent
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.BrowseActivity
import dev.alaev.rezkatv.rezka.DetailsActivity
import dev.alaev.rezkatv.rezka.EXTRA_POSTER
import dev.alaev.rezkatv.rezka.EXTRA_TITLE
import dev.alaev.rezkatv.rezka.EXTRA_URL
import dev.alaev.rezkatv.rezka.player.PlayerActivity

fun Activity.resume(session: WatchSession) =
    openTitle(session, PlayerActivity.intent(this, session.url, session.title, session.poster))

fun Activity.openTitle(session: WatchSession, vararg above: Intent) {
    startActivities(
        arrayOf(
            Intent(this, BrowseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            Intent(this, DetailsActivity::class.java)
                .putExtra(EXTRA_URL, session.url)
                .putExtra(EXTRA_TITLE, session.title)
                .putExtra(EXTRA_POSTER, session.poster),
            *above,
        ),
    )
}

fun Activity.resumeInfo(session: WatchSession): String {
    val position = getString(R.string.continue_position, TimeText.format(session.positionMs), TimeText.format(session.durationMs))
    val prefix = if (session.isSeries) session.episodeCode else session.translatorName
    return listOf(prefix, position).filter { it.isNotEmpty() }.joinToString("  ·  ")
}
