package dev.alaev.rezkatv.rezka

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.alaev.rezkatv.R

/** Постеры в ряду или сетке. Фокус увеличивает карточку и сообщает наружу. */
class CardAdapter(
    private val onFocus: (Card) -> Unit = {},
    private val onLongClick: ((Card) -> Unit)? = null,
    /** Доля просмотра 0..100 для полоски под постером, null — без полоски */
    private val progressOf: (Card) -> Int? = { null },
    private val onClick: (Card) -> Unit,
) : RecyclerView.Adapter<CardAdapter.Holder>() {

    private val cards = mutableListOf<Card>()

    /** Вызывается, когда до конца списка осталось несколько карточек — пора грузить следующую страницу. */
    var onNearEnd: (() -> Unit)? = null

    val items: List<Card> get() = cards

    fun append(more: List<Card>) {
        val start = cards.size
        cards += more
        notifyItemRangeInserted(start, more.size)
    }

    /** Новый список; если ничего не поменялось — ничего не перерисовываем. */
    fun replace(all: List<Card>): Boolean {
        if (all == cards) return false
        cards.clear()
        cards += all
        notifyDataSetChanged()
        return true
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.poster)
        val badge: TextView = view.findViewById(R.id.badge)
        val progress: ProgressBar = view.findViewById(R.id.progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        view.clipToOutline = true
        view.setOnFocusChangeListener { v, focused ->
            val scale = if (focused) FOCUS_SCALE else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            if (focused) (v.tag as? Card)?.let(onFocus)
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = cards[position]
        holder.itemView.tag = card
        Images.poster(holder.poster, card.poster)
        // «2 сезон 7 серия (Озвучка)» → «2 сезон 7 серия»
        val badge = card.badge?.substringBefore(" (")
        holder.badge.text = badge
        holder.badge.visibility = if (badge.isNullOrEmpty()) View.GONE else View.VISIBLE
        val progress = progressOf(card)
        holder.progress.visibility = if (progress == null) View.GONE else View.VISIBLE
        holder.progress.progress = progress ?: 0
        holder.itemView.setOnClickListener { onClick(card) }
        holder.itemView.setOnLongClickListener(onLongClick?.let { handler -> View.OnLongClickListener { handler(card); true } })
        if (position >= cards.size - PRELOAD_AHEAD) onNearEnd?.invoke()
    }

    override fun getItemCount() = cards.size

    private companion object {
        const val FOCUS_SCALE = 1.08f
        const val PRELOAD_AHEAD = 8
    }
}
