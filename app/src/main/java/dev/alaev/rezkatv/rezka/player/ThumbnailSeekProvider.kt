package dev.alaev.rezkatv.rezka.player

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.leanback.widget.PlaybackSeekDataProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.alaev.rezkatv.rezka.Tile

class ThumbnailSeekProvider(
    context: Context,
    private val scope: CoroutineScope,
    durationMs: Long,
    stepMs: Long,
    private val tiles: List<Tile>,
    private val onSeekStateChanged: (Boolean) -> Unit,
    private val onSeekPosition: (Long) -> Unit,
) : PlaybackSeekDataProvider() {

    private val context = context.applicationContext
    private val positions = SeekMath.positions(durationMs, stepMs)
    private val crops = LruCache<Int, Bitmap>(CACHED_THUMBS)

    var seeking = false
        private set

    init {
        if (tiles.isNotEmpty()) scope.launch {
            delay(PREFETCH_DELAY_MS)
            withContext(Dispatchers.IO) {
                tiles.map { it.url }.distinct().forEach { url ->
                    runCatching { Glide.with(this@ThumbnailSeekProvider.context).downloadOnly().load(url).submit().get() }
                }
            }
        }
    }

    override fun getSeekPositions(): LongArray {
        seeking = true
        onSeekStateChanged(true)
        return positions
    }

    override fun getThumbnail(index: Int, callback: ResultCallback) {
        val position = positions.getOrNull(index) ?: return
        onSeekPosition(position)
        val tileIndex = SeekMath.tileIndex(tiles, position)
        if (tileIndex < 0) return
        crops.get(tileIndex)?.let { return callback.onThumbnailLoaded(it, index) }
        val tile = tiles[tileIndex]
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { crop(tile) } ?: return@launch
            crops.put(tileIndex, bitmap)
            callback.onThumbnailLoaded(bitmap, index)
        }
    }

    override fun reset() {
        seeking = false
        onSeekStateChanged(false)
    }

    private fun crop(tile: Tile): Bitmap? {
        val target = Glide.with(context).asBitmap().load(tile.url).format(DecodeFormat.PREFER_RGB_565).submit()
        return try {
            val sprite = target.get()
            val x = tile.x.coerceIn(0, sprite.width - 1)
            val y = tile.y.coerceIn(0, sprite.height - 1)
            val crop = Bitmap.createBitmap(sprite, x, y, tile.w.coerceAtMost(sprite.width - x), tile.h.coerceAtMost(sprite.height - y))
            if (crop === sprite) sprite.copy(sprite.config ?: Bitmap.Config.RGB_565, false) else crop
        } catch (e: Exception) {
            Log.w("RezkaTv", "thumbnail failed: $e")
            null
        } finally {
            Glide.with(context).clear(target)
        }
    }

    private companion object {
        const val CACHED_THUMBS = 24
        const val PREFETCH_DELAY_MS = 15_000L
    }
}
