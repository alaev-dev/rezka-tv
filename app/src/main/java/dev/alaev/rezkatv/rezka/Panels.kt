package dev.alaev.rezkatv.rezka

import android.view.View
import android.widget.TextView
import androidx.leanback.widget.VerticalGridView
import dev.alaev.rezkatv.R

/** Боковая панель со списком: озвучка, качество, субтитры. Back закрывает её, фокус возвращается назад. */
class ChoicePanel(private val root: View) {

    private val title: TextView = root.findViewById(R.id.choice_title)
    private val list: VerticalGridView = root.findViewById(R.id.choice_list)
    private var restoreFocus: View? = null

    val isShowing get() = root.visibility == View.VISIBLE

    init {
        list.itemAnimator = null
    }

    fun show(titleText: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        restoreFocus = root.rootView.findFocus()
        title.text = titleText
        list.adapter = ChipAdapter(items, selected) { i ->
            hide()
            onPick(i)
        }
        list.selectedPosition = selected.coerceAtLeast(0)
        root.visibility = View.VISIBLE
        list.requestFocus()
    }

    fun hide() {
        if (!isShowing) return
        root.visibility = View.GONE
        restoreFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        restoreFocus = null
    }
}

/** Сезоны и серии: сезон выбирается фокусом, серия — по OK. Текущая серия отмечена. */
class EpisodesPanel(private val root: View) {

    private val title: TextView = root.findViewById(R.id.episodes_title)
    private val seasonList: VerticalGridView = root.findViewById(R.id.season_list)
    private val episodeList: VerticalGridView = root.findViewById(R.id.episode_list)
    private var restoreFocus: View? = null

    val isShowing get() = root.visibility == View.VISIBLE

    init {
        seasonList.itemAnimator = null
        episodeList.itemAnimator = null
    }

    /** [label] — подпись серии в списке («Серия 7 · Сейчас», «Серия 6 · Просмотрено»). */
    fun show(
        titleText: String,
        episodes: List<Episode>,
        current: Episode?,
        label: (Episode) -> String,
        onPick: (Episode) -> Unit,
    ) {
        restoreFocus = root.rootView.findFocus()
        title.text = titleText
        val seasons = episodes.map { it.season }.distinct()
        val context = root.context
        var shownSeason = -1

        fun showSeason(season: Int) {
            if (season == shownSeason) return
            shownSeason = season
            val inSeason = episodes.filter { it.season == season }
            val currentIndex = inSeason.indexOfFirst { it == current }
            episodeList.adapter = ChipAdapter(inSeason.map(label), currentIndex) { i ->
                hide()
                onPick(inSeason[i])
            }
            episodeList.selectedPosition = currentIndex.coerceAtLeast(0)
        }

        val startSeason = current?.season?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: return
        val seasonIndex = seasons.indexOf(startSeason)
        seasonList.adapter = ChipAdapter(
            seasons.map { context.getString(R.string.season_n, it) },
            seasonIndex,
            onFocus = { i -> showSeason(seasons[i]) },
            onClick = { episodeList.requestFocus() },
        )
        seasonList.selectedPosition = seasonIndex
        showSeason(startSeason)
        seasonList.visibility = if (seasons.size > 1) View.VISIBLE else View.GONE
        root.visibility = View.VISIBLE
        episodeList.requestFocus()
    }

    fun hide() {
        if (!isShowing) return
        root.visibility = View.GONE
        restoreFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        restoreFocus = null
    }
}
