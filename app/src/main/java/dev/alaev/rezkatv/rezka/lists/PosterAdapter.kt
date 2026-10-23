package dev.alaev.rezkatv.rezka.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.Images
import java.util.Locale

/** Постер списка: тайтл, место в рейтинге, подпись снизу. */
data class Poster(
    val item: RezkaTitle,
    val caption: String?,
    val rank: Int? = null,
    val winner: Boolean = false,
)

/**
 * Сетка «Рейтинга» и «Оскара» — как [dev.alaev.rezkatv.rezka.CardAdapter], плюс место, отметка «просмотрено»
 * и победитель. Отметки обновляются без перезагрузки постеров ([refreshMarks]).
 */
class PosterAdapter(
    private val marks: TitleMarks,
    private val onFocus: (Int) -> Unit,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<PosterAdapter.Holder>() {

    var items: List<Poster> = emptyList()
        private set

    fun submit(list: List<Poster>) {
        items = list
        notifyDataSetChanged()
    }

    fun refreshMarks() = notifyItemRangeChanged(0, items.size, MARKS)

    fun refreshMark(position: Int) = notifyItemChanged(position, MARKS)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.poster)
        val placeholder: TextView = view.findViewById(R.id.placeholder)
        val rank: TextView = view.findViewById(R.id.rank)
        val mark: ImageView = view.findViewById(R.id.mark)
        val badge: TextView = view.findViewById(R.id.badge)
        val progress: ProgressBar = view.findViewById(R.id.progress)
        /** Оформление победителя или обычное — меняем, только если оно другое (без лишних объектов при прокрутке) */
        var style: Int = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false)
        view.clipToOutline = true
        val holder = Holder(view)
        // Переносы по-русски, даже если язык системы другой: иначе длинное слово рвётся посередине
        holder.placeholder.textLocale = RU
        view.setOnFocusChangeListener { v, focused ->
            val scale = if (focused) FOCUS_SCALE else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            val position = holder.bindingAdapterPosition
            if (focused && position != RecyclerView.NO_POSITION) onFocus(position)
        }
        view.setOnClickListener { holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(onClick) }
        view.setOnLongClickListener {
            holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(onLongClick)
            true
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it === MARKS }) bindMark(holder, items[position]) else super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val p = items[position]
        val onRezka = p.item.id != null
        if (onRezka) {
            Images.poster(holder.poster, p.item.poster.orEmpty())
            holder.placeholder.visibility = View.GONE
        } else {
            Glide.with(holder.poster).clear(holder.poster)
            holder.poster.setImageDrawable(null)
            holder.placeholder.text = p.item.title
            holder.placeholder.visibility = View.VISIBLE
        }
        holder.rank.text = p.rank?.let { "#$it" }
        holder.rank.visibility = if (p.rank != null) View.VISIBLE else View.GONE
        holder.badge.text = p.caption
        holder.badge.visibility = if (p.caption.isNullOrEmpty()) View.GONE else View.VISIBLE
        val style = if (p.winner) WINNER else if (onRezka) NORMAL else MISSING
        if (holder.style != style) {
            holder.style = style
            val context = holder.itemView.context
            holder.badge.setBackgroundColor(context.getColor(if (p.winner) R.color.accent else R.color.badge_bg))
            holder.badge.setTextColor(context.getColor(if (p.winner) R.color.text_on_focus else if (onRezka) R.color.text else R.color.text_dim))
            holder.itemView.foreground = context.getDrawable(if (p.winner) R.drawable.card_winner else R.drawable.card_focus)
        }
        bindMark(holder, p)
    }

    private fun bindMark(holder: Holder, p: Poster) {
        val id = p.item.id
        val progress = marks.progress(id)
        val mark = marks.of(id, progress)
        when (mark) {
            Mark.WATCHED -> holder.mark.setImageResource(R.drawable.mark_watched)
            Mark.STARTED -> holder.mark.setImageResource(R.drawable.mark_started)
            Mark.NONE -> Unit
        }
        holder.mark.visibility = if (mark == Mark.NONE) View.GONE else View.VISIBLE
        holder.progress.visibility = if (progress == null) View.GONE else View.VISIBLE
        holder.progress.progress = progress ?: 0
    }

    override fun getItemCount() = items.size

    private companion object {
        const val FOCUS_SCALE = 1.08f
        const val NORMAL = 0
        const val WINNER = 1
        const val MISSING = 2
        val MARKS = Any()
        val RU: Locale = Locale.forLanguageTag("ru")
    }
}
