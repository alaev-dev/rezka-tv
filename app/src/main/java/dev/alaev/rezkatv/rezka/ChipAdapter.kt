package dev.alaev.rezkatv.rezka

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.alaev.rezkatv.R

/** Текстовые пункты: строки панелей, клавиши поиска. Выбранный подсвечивается через activated. */
class ChipAdapter(
    private val labels: List<String>,
    private var activated: Int = -1,
    private val layout: Int = R.layout.item_list,
    private val onFocus: ((Int) -> Unit)? = null,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<ChipAdapter.Holder>() {

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    fun activate(position: Int) {
        val old = activated
        activated = position
        if (old >= 0) notifyItemChanged(old)
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false) as TextView)
        holder.text.setOnFocusChangeListener { _, focused ->
            val position = holder.bindingAdapterPosition
            if (focused && position != RecyclerView.NO_POSITION) onFocus?.invoke(position)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.text.text = labels[position]
        holder.text.isActivated = position == activated
        holder.text.setOnClickListener { onClick(position) }
    }

    override fun getItemCount() = labels.size
}
