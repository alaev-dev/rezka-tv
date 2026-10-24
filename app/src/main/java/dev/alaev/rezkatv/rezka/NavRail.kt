package dev.alaev.rezkatv.rezka

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.alaev.rezkatv.R

enum class Section(val label: Int, val icon: Int, val category: Category? = null) {
    SEARCH(R.string.nav_search, R.drawable.ic_search),
    HOME(R.string.nav_home, R.drawable.ic_home),
    SERIES(R.string.nav_series, R.drawable.ic_series, Category.SERIES),
    FILMS(R.string.nav_films, R.drawable.ic_films, Category.FILMS),
    CARTOONS(R.string.nav_cartoons, R.drawable.ic_cartoons, Category.CARTOONS),
    ANIME(R.string.nav_anime, R.drawable.ic_anime, Category.ANIME),
    FAVORITES(R.string.nav_favorites, R.drawable.ic_bookmark, Category.FAVORITES),
    TOP(R.string.nav_top, R.drawable.ic_top),
    OSCAR(R.string.nav_oscar, R.drawable.ic_award),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

class NavRail(
    private val container: LinearLayout,
    private val scrim: View,
    private val onSelect: (Section) -> Unit,
    private val onExpand: () -> Unit = {},
) {
    private val items: Map<Section, View>
    private var expanded = false

    var selected = Section.HOME
        set(value) {
            items[field]?.isActivated = false
            field = value
            items[value]?.isActivated = true
        }

    init {
        val inflater = LayoutInflater.from(container.context)
        items = Section.entries.associateWith { section ->
            val item = inflater.inflate(R.layout.item_rail, container, false)
            item.findViewById<ImageView>(R.id.icon).setImageResource(section.icon)
            item.findViewById<TextView>(R.id.label).setText(section.label)
            item.contentDescription = container.context.getString(section.label)
            item.setOnClickListener { onSelect(section) }
            item.setOnFocusChangeListener { _, _ -> container.post { setExpanded(container.hasFocus()) } }
            container.addView(item)
            item
        }
        selected = Section.HOME
    }

    fun contains(view: View?): Boolean {
        var v: View? = view
        while (v != null) {
            if (v === container) return true
            v = v.parent as? View
        }
        return false
    }

    fun selectedView(): View = items.getValue(selected)

    fun focus() = selectedView().requestFocus()

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        items.values.forEach { it.findViewById<View>(R.id.label).visibility = if (value) View.VISIBLE else View.GONE }
        scrim.visibility = if (value) View.VISIBLE else View.INVISIBLE
        if (value) onExpand()
    }
}
