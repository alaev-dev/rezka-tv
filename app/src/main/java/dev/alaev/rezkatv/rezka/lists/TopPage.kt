package dev.alaev.rezkatv.rezka.lists

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.VerticalGridView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.ChoicePanel
import dev.alaev.rezkatv.rezka.RezkaClient
import dev.alaev.rezkatv.rezka.pinFocusToStart

class TopPage(
    private val activity: Activity,
    private val client: RezkaClient,
    private val scope: CoroutineScope,
    private val marks: TitleMarks,
    private val choice: ChoicePanel,
) {
    private val page: View = activity.findViewById(R.id.top_page)
    private val bucketChips: LinearLayout = activity.findViewById(R.id.top_buckets)
    private val decadeChip: TextView = activity.findViewById(R.id.top_decade)
    private val genreChip: TextView = activity.findViewById(R.id.top_genre)
    private val hideChip: TextView = activity.findViewById(R.id.top_hide_watched)
    private val count: TextView = activity.findViewById(R.id.top_count)
    private val info: TextView = activity.findViewById(R.id.top_info)
    private val status: TextView = activity.findViewById(R.id.top_status)
    private val grid: VerticalGridView = activity.findViewById(R.id.top_grid)
    private val adapter = PosterAdapter(marks, onFocus = ::showInfo, onClick = ::open, onLongClick = ::toggleWatched)

    private var lists: TopLists? = null
    private var loaded = false
    private var loadJob: Job? = null
    private var focusOnLoad = false
    private var bucket = TopBucket.FILMS
    private var filter = TopFilter()
    private var shown: List<TopItem> = emptyList()

    val isVisible get() = page.visibility == View.VISIBLE

    init {
        grid.setNumColumns(COLUMNS)
        grid.pinFocusToStart(grid.paddingTop)
        val spacing = activity.resources.getDimensionPixelSize(R.dimen.card_spacing)
        grid.horizontalSpacing = spacing + spacing / 2
        grid.verticalSpacing = spacing + spacing / 2
        grid.adapter = adapter
        val inflater = LayoutInflater.from(activity)
        TopBucket.entries.forEach { b ->
            val chip = inflater.inflate(R.layout.item_chip, bucketChips, false) as TextView
            chip.text = b.title
            chip.id = View.generateViewId()
            chip.setOnClickListener { selectBucket(b) }
            bucketChips.addView(chip)
        }
        decadeChip.setOnClickListener { chooseDecade() }
        genreChip.setOnClickListener { chooseGenre() }
        hideChip.setOnClickListener {
            filter = filter.copy(hideWatched = !filter.hideWatched)
            render(resetPosition = true)
        }
        updateChips()
    }

    fun show() {
        page.visibility = View.VISIBLE
        if (loaded) return refresh()
        if (loadJob?.isActive == true) return
        marks.refresh()
        info.setText(R.string.loading)
        loadJob = scope.launch {
            lists = ListsAssets.top(activity).await()
            loaded = true
            render(resetPosition = true)
            if (focusOnLoad && page.hasFocus()) focus()
            focusOnLoad = false
        }
    }

    fun hide() {
        page.visibility = View.GONE
    }

    fun focus() {
        if (!loaded) focusOnLoad = true
        if (adapter.itemCount > 0) grid.requestFocus() else bucketChips.getChildAt(bucket.ordinal)?.requestFocus()
    }

    fun onBack(): Boolean {
        if (!grid.hasFocus() || grid.selectedPosition < COLUMNS) return false
        grid.setSelectedPositionSmooth(0)
        return true
    }

    fun refresh() {
        if (!loaded) return
        marks.refresh()
        if (filter.hideWatched && shown.any { marks.isWatched(it.id) }) render(resetPosition = false) else adapter.refreshMarks()
        updateCount()
        if (grid.hasFocus()) showInfo(grid.selectedPosition)
    }

    private fun selectBucket(b: TopBucket) {
        if (b == bucket) return
        bucket = b
        filter = TopFilter(hideWatched = filter.hideWatched)
        render(resetPosition = true)
    }

    private fun render(resetPosition: Boolean) {
        val all = lists?.buckets?.get(bucket).orEmpty()
        updateChips()
        if (lists == null) {
            adapter.submit(emptyList())
            info.text = ""
            count.text = ""
            status.setText(R.string.top_missing)
            status.visibility = View.VISIBLE
            return
        }
        val position = grid.selectedPosition
        shown = filter.apply(all, marks::isWatched)
        adapter.submit(shown.map { Poster(it, ListText.imdb(it.rating, it.votes), rank = it.rank) })
        grid.selectedPosition = if (resetPosition) 0 else position.coerceIn(0, (shown.size - 1).coerceAtLeast(0))
        status.setText(R.string.nothing_here)
        status.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        if (!grid.hasFocus()) info.setText(R.string.lists_hint)
        updateCount()
    }

    private fun updateChips() {
        for (i in 0 until bucketChips.childCount) bucketChips.getChildAt(i).isActivated = i == bucket.ordinal
        val up = bucketChips.getChildAt(bucket.ordinal).id
        listOf(decadeChip, genreChip, hideChip).forEach { it.nextFocusUpId = up }
        decadeChip.text = activity.getString(R.string.filter_decade, filter.decade?.let(ListText::decade) ?: activity.getString(R.string.filter_all))
        decadeChip.isActivated = filter.decade != null
        genreChip.text = activity.getString(R.string.filter_genre, filter.genre ?: activity.getString(R.string.filter_all))
        genreChip.isActivated = filter.genre != null
        hideChip.isActivated = filter.hideWatched
    }

    private fun updateCount() {
        val all = lists?.buckets?.get(bucket).orEmpty()
        val watched = all.count { marks.isWatched(it.id) }
        count.text = if (shown.size == all.size) {
            activity.getString(R.string.top_count, ListText.titles(all.size), watched)
        } else {
            activity.getString(R.string.top_count_filtered, shown.size, all.size, watched)
        }
    }

    private fun chooseDecade() {
        val all = lists?.buckets?.get(bucket) ?: return
        val decades = TopFilter.decades(all)
        val labels = listOf(activity.getString(R.string.all_years)) + decades.map(ListText::decade)
        choice.show(activity.getString(R.string.decades), labels, decades.indexOf(filter.decade) + 1) { i ->
            filter = filter.copy(decade = decades.getOrNull(i - 1))
            render(resetPosition = true)
        }
    }

    private fun chooseGenre() {
        val all = lists?.buckets?.get(bucket) ?: return
        val genres = TopFilter.genres(all)
        val labels = listOf(activity.getString(R.string.all_genres)) + genres
        choice.show(activity.getString(R.string.genres), labels, genres.indexOf(filter.genre) + 1) { i ->
            filter = filter.copy(genre = genres.getOrNull(i - 1))
            render(resetPosition = true)
        }
    }

    private fun showInfo(position: Int) {
        val item = shown.getOrNull(position) ?: return
        info.text = listOfNotNull(
            item.title,
            item.original.takeIf { it.isNotEmpty() && it != item.title },
            item.year.takeIf { it > 0 }?.toString(),
            item.genres.take(3).joinToString(", ").ifEmpty { null },
            markText(item.id),
        ).joinToString("  ·  ")
    }

    private fun markText(id: String) = when {
        marks.isWatched(id) -> activity.getString(R.string.watched)
        marks.of(id) != Mark.NONE -> activity.getString(R.string.started)
        else -> null
    }

    private fun open(position: Int) {
        val item = shown.getOrNull(position) ?: return
        activity.openTitle(client, item)
    }

    private fun toggleWatched(position: Int) {
        val item = shown.getOrNull(position) ?: return
        marks.toggle(item.id)
        adapter.refreshMark(position)
        showInfo(position)
        updateCount()
    }

    private companion object {
        const val COLUMNS = 6
    }
}
