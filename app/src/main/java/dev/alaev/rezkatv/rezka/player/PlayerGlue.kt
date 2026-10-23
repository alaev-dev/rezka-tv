package dev.alaev.rezkatv.rezka.player

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow
import androidx.leanback.widget.PlaybackRowPresenter
import androidx.leanback.widget.PlaybackTransportRowPresenter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.SinglePresenterSelector
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import dev.alaev.rezkatv.R

/**
 * Стандартная связка leanback (PlaybackTransportControlGlue + LeanbackPlayerAdapter Media3),
 * дополненная кнопками серий, озвучки, качества и субтитров.
 * Над полосой — «Предыдущая», пауза, «Следующая»; под ней — серии, озвучка, качество, субтитры.
 */
@UnstableApi
class PlayerGlue(
    context: Context,
    adapter: LeanbackPlayerAdapter,
    private val series: Boolean,
    private val onAction: (Long) -> Unit,
) : PlaybackTransportControlGlue<LeanbackPlayerAdapter>(context, adapter) {

    private val prevAction = action(ACTION_PREV, R.string.action_prev, R.drawable.ic_skip_prev)
    private val nextAction = action(ACTION_NEXT, R.string.action_next, R.drawable.ic_skip_next, iconAtEnd = true)
    private val subtitlesAction = action(ACTION_SUBTITLES, R.string.action_subtitles, R.drawable.ic_subtitles)
    private val presenter = ActionPresenter()

    private val row = PlaybackControlsRow(this)

    init {
        // Ряд создаём сами, чтобы до подключения к фрагменту поставить свой вид кнопок
        setControlsRow(row)
        (row.primaryActionsAdapter as ArrayObjectAdapter).presenterSelector = SinglePresenterSelector(presenter)
        (row.secondaryActionsAdapter as ArrayObjectAdapter).presenterSelector = SinglePresenterSelector(presenter)
    }

    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        if (series) adapter.add(prevAction)
        super.onCreatePrimaryActions(adapter)
        if (series) adapter.add(nextAction)
    }

    override fun onCreateSecondaryActions(adapter: ArrayObjectAdapter) {
        if (series) adapter.add(action(ACTION_EPISODES, R.string.action_episodes, R.drawable.ic_episodes))
        adapter.add(action(ACTION_TRANSLATOR, R.string.action_translator, R.drawable.ic_voice))
        adapter.add(action(ACTION_QUALITY, R.string.action_quality, R.drawable.ic_quality))
        adapter.add(subtitlesAction)
        adapter.add(action(ACTION_SETTINGS, R.string.action_settings, R.drawable.ic_settings, iconOnly = true))
    }

    override fun onCreateRowPresenter(): PlaybackRowPresenter =
        (super.onCreateRowPresenter() as PlaybackTransportRowPresenter).apply {
            progressColor = context.getColor(R.color.accent)
            secondaryProgressColor = 0x66FFFFFF
        }

    override fun onActionClicked(action: Action) {
        if (action.id in ACTION_PREV..ACTION_SETTINGS) onAction(action.id) else super.onActionClicked(action)
    }

    /** Кнопка «Субтитры» — только если у этой серии и озвучки они есть. */
    fun setSubtitlesAvailable(available: Boolean) {
        val adapter = row.secondaryActionsAdapter as ArrayObjectAdapter
        val index = adapter.indexOf(subtitlesAction)
        if (available && index < 0) adapter.add(adapter.size() - 1, subtitlesAction)
        if (!available && index >= 0) adapter.removeItems(index, 1)
    }

    override fun next() = onAction(ACTION_NEXT)

    override fun previous() = onAction(ACTION_PREV)

    private fun action(id: Long, label: Int, icon: Int, iconOnly: Boolean = false, iconAtEnd: Boolean = false) =
        TextAction(id, context.getString(label), context.getDrawable(icon), iconOnly, iconAtEnd)

    /** Кнопка с иконкой и (не всегда) подписью; у «Следующей» стрелка справа. */
    class TextAction(id: Long, label: String, icon: Drawable?, val iconOnly: Boolean, val iconAtEnd: Boolean) :
        Action(id, label, null, icon)

    /** Одна «таблетка» на все кнопки плеера: иконка, подпись — только у текстовых. */
    private class ActionPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_action, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, item: Any?) {
            val action = item as Action
            val view = holder.view as TextView
            val textual = action is TextAction && !action.iconOnly
            view.text = if (textual) action.label1 else null
            view.contentDescription = action.label1
            val size = view.resources.getDimensionPixelSize(if (textual) R.dimen.player_icon_small else R.dimen.player_icon)
            val icon = action.icon?.mutate()?.apply { setBounds(0, 0, size, size) }
            view.compoundDrawablePadding = if (textual) view.resources.getDimensionPixelSize(R.dimen.chip_spacing) else 0
            if (action is TextAction && action.iconAtEnd) view.setCompoundDrawables(null, null, icon, null)
            else view.setCompoundDrawables(icon, null, null, null)
        }

        override fun onUnbindViewHolder(holder: ViewHolder) = Unit
    }

    companion object {
        const val ACTION_PREV = 1L
        const val ACTION_NEXT = 2L
        const val ACTION_EPISODES = 3L
        const val ACTION_TRANSLATOR = 4L
        const val ACTION_QUALITY = 5L
        const val ACTION_SUBTITLES = 6L
        const val ACTION_SETTINGS = 7L
    }
}
