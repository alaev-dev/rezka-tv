package dev.alaev.rezkatv.rezka

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import dev.alaev.rezkatv.R

object Images {
    /** RGB_565 вдвое экономнее по памяти, для постеров разница незаметна. */
    fun poster(view: ImageView, url: String) {
        Glide.with(view)
            .load(url.ifEmpty { null })
            .format(DecodeFormat.PREFER_RGB_565)
            .placeholder(R.color.tile)
            .centerCrop()
            .into(view)
    }

    /**
     * Большая обложка (герой, страница тайтла). Пока грузится крупная — показываем маленький постер
     * из карточки: он уже в памяти, так что смена тайтла мгновенная.
     */
    fun art(view: ImageView, url: String, preview: String? = null) {
        val glide = Glide.with(view)
        var request = glide.load(url.ifEmpty { preview }).format(DecodeFormat.PREFER_RGB_565).centerCrop()
        if (!preview.isNullOrEmpty() && preview != url) {
            request = request.thumbnail(glide.load(preview).format(DecodeFormat.PREFER_RGB_565).centerCrop())
        }
        request.transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_MS)).into(view)
    }

    private const val CROSSFADE_MS = 180
}
