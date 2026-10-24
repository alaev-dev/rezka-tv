package dev.alaev.rezkatv.rezka

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import dev.alaev.rezkatv.R

object Images {
    fun poster(view: ImageView, url: String) {
        Glide.with(view)
            .load(url.ifEmpty { null })
            .format(DecodeFormat.PREFER_RGB_565)
            .placeholder(R.color.tile)
            .centerCrop()
            .into(view)
    }

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
