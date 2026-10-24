package dev.alaev.rezkatv.rezka

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.leanback.widget.VerticalGridView
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.R

class SearchActivity : Activity() {

    private enum class Layout(val letters: String, val label: String) {
        RU("абвгдеёжзийклмнопрстуфхцчшщъыьэюя", "АБВ"),
        EN("abcdefghijklmnopqrstuvwxyz", "ABC"),
        DIGITS("1234567890", "123"),
    }

    private val scope = MainScope()
    private val query = StringBuilder()
    private var layout = Layout.RU
    private var searchJob: Job? = null

    private lateinit var client: RezkaClient
    private lateinit var loader: Loader
    private lateinit var queryView: TextView
    private lateinit var emptyView: TextView
    private lateinit var keyboard: VerticalGridView
    private lateinit var results: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        client = RezkaClient.get(this)
        queryView = findViewById(R.id.query)
        emptyView = findViewById(R.id.empty)
        loader = Loader(this, null)
        keyboard = findViewById(R.id.keyboard)
        keyboard.setNumColumns(KEY_COLUMNS)
        keyboard.itemAnimator = null

        results = CardAdapter(onClick = { openDetails(it) })
        findViewById<VerticalGridView>(R.id.results).apply {
            setNumColumns(RESULT_COLUMNS)
            val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
            horizontalSpacing = spacing
            verticalSpacing = spacing
            itemAnimator = null
            adapter = results
        }

        showKeyboard()
        keyboard.requestFocus()
        intent.getStringExtra(EXTRA_QUERY)?.let {
            query.append(it.lowercase())
            onQueryChanged()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showKeyboard() {
        val keys = layout.letters.map { it.uppercase() } + EDIT_KEYS + nextLayout().label
        keyboard.adapter = ChipAdapter(keys, layout = R.layout.item_key) { i -> onKey(keys[i]) }
    }

    private fun layoutSwitchPosition() = layout.letters.length + EDIT_KEYS.size

    private fun nextLayout() = Layout.entries[(layout.ordinal + 1) % Layout.entries.size]

    private fun onKey(key: String) {
        when (key) {
            SPACE -> if (query.isNotEmpty() && query.last() != ' ') query.append(' ')
            BACKSPACE -> if (query.isNotEmpty()) query.setLength(query.length - 1)
            CLEAR -> query.setLength(0)
            nextLayout().label -> {
                layout = nextLayout()
                showKeyboard()
                keyboard.selectedPosition = layoutSwitchPosition()
                return
            }
            else -> query.append(key.lowercase())
        }
        onQueryChanged()
    }

    private fun onQueryChanged() {
        queryView.text = query.toString()
        searchJob?.cancel()
        val text = query.toString().trim()
        if (text.length < MIN_QUERY) {
            results.replace(emptyList())
            showEmpty(R.string.search_hint)
            return
        }
        searchJob = scope.launch {
            delay(TYPING_PAUSE_MS)
            emptyView.visibility = View.GONE
            val page = loader.load { client.page(client.searchUrl(text)) }
            if (page == null) {
                showEmpty(R.string.load_failed)
                return@launch
            }
            results.replace(page.cards)
            if (page.cards.isEmpty()) showEmpty(R.string.nothing_found)
        }
    }

    private fun showEmpty(text: Int) {
        emptyView.setText(text)
        emptyView.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_QUERY = "query"
        private const val SPACE = "␣"
        private const val BACKSPACE = "⌫"
        private const val CLEAR = "✕"
        private val EDIT_KEYS = listOf(SPACE, BACKSPACE, CLEAR)
        private const val KEY_COLUMNS = 6
        private const val RESULT_COLUMNS = 4
        private const val MIN_QUERY = 2
        private const val TYPING_PAUSE_MS = 600L
    }
}
