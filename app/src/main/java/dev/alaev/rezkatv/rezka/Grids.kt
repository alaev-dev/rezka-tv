package dev.alaev.rezkatv.rezka

import androidx.leanback.widget.BaseGridView

/**
 * Фокус «приклеен» к началу сетки, как у Netflix: выбранный ряд — сверху,
 * выбранная карточка — на [offsetPx] от левого края (обычно это padding, чтобы увеличенная
 * карточка не обрезалась), остальное прокручивается.
 */
fun BaseGridView.pinFocusToStart(offsetPx: Int) {
    itemAnimator = null // анимации изменений ломают фокус и тратят CPU
    windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
    windowAlignmentOffsetPercent = 0f
    windowAlignmentOffset = offsetPx
    itemAlignmentOffsetPercent = 0f
    itemAlignmentOffset = 0
}
