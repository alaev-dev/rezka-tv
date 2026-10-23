package dev.alaev.rezkatv.rezka.lists

import android.app.Activity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.RezkaClient
import dev.alaev.rezkatv.rezka.pinFocusToStart

/**
 * «Оскар»: церемонии от новых к старым с прогрессом «3 из 10», номинанты выбранной (победитель первым).
 * Прогресс считает только фильмы, которые есть на rezka: остальные нельзя посмотреть здесь,
 * они показаны серыми и посчитаны отдельно («ещё 12 нет на rezka»).
 */
class OscarPage(
    private val activity: Activity,
    private val client: RezkaClient,
    private val scope: CoroutineScope,
    private val marks: TitleMarks,
) {
    private val page: View = activity.findViewById(R.id.oscar_page)
    private val categoryChips: LinearLayout = activity.findViewById(R.id.oscar_categories)
    private val progressText: TextView = activity.findViewById(R.id.oscar_progress_text)
    private val progressBar: ProgressBar = activity.findViewById(R.id.oscar_progress)
    private val years: HorizontalGridView = activity.findViewById(R.id.oscar_years)
    private val info: TextView = activity.findViewById(R.id.oscar_info)
    private val status: TextView = activity.findViewById(R.id.oscar_status)
    private val grid: VerticalGridView = activity.findViewById(R.id.oscar_grid)
    private val adapter = PosterAdapter(marks, onFocus = ::showInfo, onClick = ::open, onLongClick = ::toggleWatched)
    private val yearAdapter = YearAdapter()

    private var oscars: Oscars? = null
    private var loaded = false
    private var loadJob: Job? = null
    private var focusOnLoad = false
    private var category = OscarCategory.PICTURE
    /** Церемонии, где есть выбранная категория */
    private var ceremonies: List<Ceremony> = emptyList()
    private var selected = 0
    private var nominees: List<Nominee> = emptyList()
    private var selectJob: Job? = null

    val isVisible get() = page.visibility == View.VISIBLE

    init {
        grid.setNumColumns(COLUMNS)
        grid.pinFocusToStart(grid.paddingTop)
        val spacing = activity.resources.getDimensionPixelSize(R.dimen.card_spacing)
        grid.horizontalSpacing = spacing + spacing / 2
        grid.verticalSpacing = spacing + spacing / 2
        grid.adapter = adapter
        // Вверх с первого ряда номинантов — всегда на выбранный год: поиск фокуса по геометрии
        // иногда перескакивает ряд лет и попадает на категории
        grid.setOnKeyInterceptListener { e ->
            val up = e.keyCode == KeyEvent.KEYCODE_DPAD_UP && e.action == KeyEvent.ACTION_DOWN
            up && grid.selectedPosition < COLUMNS && years.requestFocus()
        }
        years.itemAnimator = null
        years.adapter = yearAdapter
        buildCategories()
    }

    fun show() {
        page.visibility = View.VISIBLE
        if (loaded) return refresh()
        if (loadJob?.isActive == true) return
        marks.refresh()
        info.setText(R.string.loading)
        loadJob = scope.launch {
            val all = ListsAssets.oscars(activity).await()
            oscars = all
            loaded = true
            // Категорий без номинантов в данных нет — их чипы убираем
            for (i in 0 until categoryChips.childCount) categoryChips.getChildAt(i).let {
                it.visibility = if (all != null && (it.tag as OscarCategory) in all.categories) View.VISIBLE else View.GONE
            }
            if (all == null || all.categories.isEmpty()) {
                status.setText(R.string.oscars_missing)
                status.visibility = View.VISIBLE
                info.text = ""
            } else {
                selectCategory(if (category in all.categories) category else all.categories.first())
            }
            // Фокус ждал на категориях — переводим на номинантов; если пользователь ушёл в меню, не трогаем
            if (focusOnLoad && page.hasFocus()) focus()
            focusOnLoad = false
        }
    }

    fun hide() {
        page.visibility = View.GONE
        selectJob?.cancel()
    }

    fun focus() {
        // Список ещё разбирается: фокус перейдёт на номинантов, когда они появятся
        if (!loaded) focusOnLoad = true
        if (adapter.itemCount > 0) grid.requestFocus() else categoryChips.findViewWithTag<View>(category)?.requestFocus()
    }

    /** Back: из глубины сетки — наверх, иначе — в меню. */
    fun onBack(): Boolean {
        if (!grid.hasFocus() || grid.selectedPosition < COLUMNS) return false
        grid.setSelectedPositionSmooth(0)
        return true
    }

    /** Вернулись со страницы тайтла или из плеера: отметки и прогресс могли измениться. */
    fun refresh() {
        if (!loaded) return
        marks.refresh()
        adapter.refreshMarks()
        updateProgress()
        yearAdapter.notifyItemRangeChanged(0, ceremonies.size)
        if (grid.hasFocus()) showInfo(grid.selectedPosition) else showCeremonyInfo()
    }

    private fun buildCategories() {
        val inflater = LayoutInflater.from(activity)
        OscarCategory.entries.forEach { c ->
            val chip = inflater.inflate(R.layout.item_chip, categoryChips, false) as TextView
            chip.text = c.title
            chip.tag = c
            chip.id = View.generateViewId()
            chip.isActivated = c == category
            chip.setOnClickListener { if (c != category) selectCategory(c) }
            categoryChips.addView(chip)
        }
    }

    private fun selectCategory(c: OscarCategory) {
        val all = oscars ?: return
        // Отложенный выбор года относится к прежнему списку церемоний
        selectJob?.cancel()
        val current = ceremonies.getOrNull(selected)?.number
        category = c
        for (i in 0 until categoryChips.childCount) categoryChips.getChildAt(i).let { it.isActivated = it.tag == c }
        ceremonies = all.ceremonies.filter { c in it.nominees }
        // Та же церемония, если в ней есть эта категория, иначе — самая новая
        selected = ceremonies.indexOfFirst { it.number == current }.coerceAtLeast(0)
        yearAdapter.notifyDataSetChanged()
        years.selectedPosition = selected
        updateProgress()
        showCeremony()
    }

    private fun selectCeremony(index: Int) {
        if (index == selected || index !in ceremonies.indices) return
        val old = selected
        selected = index
        yearAdapter.notifyItemChanged(old)
        yearAdapter.notifyItemChanged(index)
        showCeremony()
    }

    private fun showCeremony() {
        val c = ceremonies.getOrNull(selected) ?: return
        nominees = c.nominees[category].orEmpty()
        adapter.submit(nominees.map { n ->
            Poster(n, caption = when {
                n.won -> activity.getString(R.string.winner)
                !n.onRezka -> activity.getString(R.string.not_on_rezka)
                else -> null
            }, winner = n.won)
        })
        grid.selectedPosition = 0
        showCeremonyInfo()
    }

    private fun showCeremonyInfo() {
        val c = ceremonies.getOrNull(selected) ?: return
        val p = OscarProgress.ceremony(c, category, marks::isWatched)
        info.text = listOfNotNull(
            ListText.ceremony(c),
            activity.getString(R.string.oscar_ceremony_progress, p.watched, p.total).takeIf { p.total > 0 },
            activity.getString(R.string.oscar_ceremony_missing, p.missing).takeIf { p.missing > 0 },
        ).joinToString("  ·  ")
    }

    private fun updateProgress() {
        val all = oscars ?: return
        val p = OscarProgress.overall(all, category, marks::isWatched)
        progressText.text = if (p.missing > 0) activity.getString(R.string.oscar_progress_missing, p.watched, p.total, p.missing)
        else activity.getString(R.string.oscar_progress, p.watched, p.total)
        progressBar.progress = if (p.total > 0) p.watched * 1000 / p.total else 0
    }

    private fun showInfo(position: Int) {
        val n = nominees.getOrNull(position) ?: return
        info.text = listOfNotNull(
            n.title,
            n.original.takeIf { it.isNotEmpty() && it != n.title },
            n.year.takeIf { it > 0 }?.toString(),
            activity.getString(R.string.winner).takeIf { n.won },
            when {
                !n.onRezka -> activity.getString(R.string.not_on_rezka_search)
                marks.isWatched(n.id) -> activity.getString(R.string.watched)
                else -> null
            },
        ).joinToString("  ·  ")
    }

    private fun open(position: Int) {
        val n = nominees.getOrNull(position) ?: return
        activity.openTitle(client, n)
    }

    private fun toggleWatched(position: Int) {
        val id = nominees.getOrNull(position)?.id ?: return
        marks.toggle(id)
        adapter.refreshMark(position)
        yearAdapter.notifyItemChanged(selected)
        updateProgress()
        showInfo(position)
    }

    private inner class YearAdapter : RecyclerView.Adapter<YearAdapter.Holder>() {

        inner class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val holder = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chip, parent, false) as TextView)
            holder.text.setOnFocusChangeListener { _, focused ->
                val position = holder.bindingAdapterPosition
                if (!focused || position == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                // Номинанты меняются, когда пользователь остановился на году: пролёт по ряду не грузит постеры зря
                selectJob?.cancel()
                selectJob = scope.launch {
                    delay(SELECT_DELAY_MS)
                    selectCeremony(position)
                }
                if (position == selected) showCeremonyInfo()
            }
            holder.text.setOnClickListener { if (adapter.itemCount > 0) grid.requestFocus() }
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val c = ceremonies[position]
            val p = OscarProgress.ceremony(c, category, marks::isWatched)
            holder.text.text = when {
                p.total == 0 -> c.year.toString()
                p.watched == p.total -> activity.getString(R.string.oscar_year_done, c.year, p.watched, p.total)
                else -> activity.getString(R.string.oscar_year, c.year, p.watched, p.total)
            }
            holder.text.isActivated = position == selected
            // Вверх с любого года — на выбранную категорию, а не на ближайшую по горизонтали
            holder.text.nextFocusUpId = categoryChips.findViewWithTag<View>(category)?.id ?: View.NO_ID
        }

        override fun getItemCount() = ceremonies.size
    }

    private companion object {
        const val COLUMNS = 6
        const val SELECT_DELAY_MS = 150L
    }
}
