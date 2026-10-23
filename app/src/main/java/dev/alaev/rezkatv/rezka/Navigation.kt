package dev.alaev.rezkatv.rezka

import android.app.Activity
import android.content.Intent
import dev.alaev.rezkatv.RezkaActivity
import dev.alaev.rezkatv.rezka.login.LoginActivity
import dev.alaev.rezkatv.rezka.player.PlayerActivity

const val EXTRA_URL = "url"
const val EXTRA_TITLE = "title"
const val EXTRA_POSTER = "poster"

fun Activity.detailsIntent(card: Card): Intent =
    Intent(this, DetailsActivity::class.java)
        .putExtra(EXTRA_URL, card.url)
        .putExtra(EXTRA_TITLE, card.title)
        .putExtra(EXTRA_POSTER, card.poster)

fun Activity.openDetails(card: Card) = startActivity(detailsIntent(card))

/** Поиск, сразу с запросом (например, фильм из списка, которого мы не нашли на rezka). */
fun Activity.openSearch(query: String? = null) =
    startActivity(Intent(this, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_QUERY, query))

/** «Продолжить просмотр»: сразу плеер, а под ним — страница тайтла, куда ведёт Back. */
fun Activity.playFromCard(card: Card) =
    startActivities(arrayOf(detailsIntent(card), PlayerActivity.intent(this, card.url, card.title, card.poster)))

/** Плеер сайта в WebView — запасной вариант, если родной плеер не справился. */
fun Activity.playOnSite(url: String) = startActivity(RezkaActivity.playerIntent(this, url))

/** Вход: QR-код для телефона, запасной путь — сайт на телевизоре. */
fun Activity.openLogin() = startActivity(Intent(this, LoginActivity::class.java))

/** Обычный сайт в WebView: вход в аккаунт, всё, чего нет в приложении. */
fun Activity.openSite(url: String? = null) = startActivity(RezkaActivity.siteIntent(this, url))
