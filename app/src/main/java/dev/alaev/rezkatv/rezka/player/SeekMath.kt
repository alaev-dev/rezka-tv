package dev.alaev.rezkatv.rezka.player

import dev.alaev.rezkatv.rezka.Tile

/** Арифметика перемотки, отдельно от UI — чтобы проверять тестами. */
object SeekMath {

    /** Точки перемотки с шагом [stepMs] от нуля до конца (конец не включаем — до него доводит leanback). */
    fun positions(durationMs: Long, stepMs: Long): LongArray {
        if (durationMs <= 0 || stepMs <= 0) return LongArray(0)
        val count = ((durationMs - 1) / stepMs + 1).toInt()
        return LongArray(count) { it * stepMs }
    }

    /** Индекс кадра превью, который покрывает момент [positionMs], или -1. */
    fun tileIndex(tiles: List<Tile>, positionMs: Long): Int {
        var lo = 0
        var hi = tiles.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val tile = tiles[mid]
            when {
                positionMs < tile.startMs -> hi = mid - 1
                positionMs >= tile.endMs -> lo = mid + 1
                else -> return mid
            }
        }
        // Последний кадр обычно заканчивается чуть раньше конца видео
        return if (tiles.isNotEmpty() && positionMs >= tiles.last().startMs) tiles.size - 1 else -1
    }

    /**
     * Ускорение при удержании стрелки: сколько дополнительных шагов сделать на этот повтор.
     * Повторы идут ~10–20 раз в секунду; первые полсекунды — по шагу, дальше всё быстрее.
     */
    fun extraSteps(repeatCount: Int): Int = when {
        repeatCount < 8 -> 0
        repeatCount < 24 -> 1
        repeatCount < 48 -> 3
        else -> 7
    }
}
