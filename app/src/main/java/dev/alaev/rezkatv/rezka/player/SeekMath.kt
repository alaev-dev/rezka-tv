package dev.alaev.rezkatv.rezka.player

import dev.alaev.rezkatv.rezka.Tile

object SeekMath {

    fun positions(durationMs: Long, stepMs: Long): LongArray {
        if (durationMs <= 0 || stepMs <= 0) return LongArray(0)
        val count = ((durationMs - 1) / stepMs + 1).toInt()
        return LongArray(count) { it * stepMs }
    }

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
        return if (tiles.isNotEmpty() && positionMs >= tiles.last().startMs) tiles.size - 1 else -1
    }

    fun extraSteps(repeatCount: Int): Int = when {
        repeatCount < 8 -> 0
        repeatCount < 24 -> 1
        repeatCount < 48 -> 3
        else -> 7
    }
}
