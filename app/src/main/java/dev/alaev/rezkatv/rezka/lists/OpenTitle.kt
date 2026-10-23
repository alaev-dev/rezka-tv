package dev.alaev.rezkatv.rezka.lists

import android.app.Activity
import dev.alaev.rezkatv.rezka.Card
import dev.alaev.rezkatv.rezka.RezkaClient
import dev.alaev.rezkatv.rezka.openDetails
import dev.alaev.rezkatv.rezka.openSearch

/** Страница тайтла на текущем зеркале; если тайтла нет на rezka — поиск по названию. */
fun Activity.openTitle(client: RezkaClient, item: RezkaTitle) {
    val path = item.path ?: return openSearch(item.title)
    openDetails(Card(item.id.orEmpty(), client.resolve(path), item.title, item.poster.orEmpty(), "", null))
}
