package com.anthonymandra.rawdroid.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.MetaUtil
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.fileview.*

@Suppress("DEPRECATION")
class GalleryViewHolder(override val containerView: View/*, private val glide: RequestManager*/)
    : RecyclerView.ViewHolder(containerView), LayoutContainer {
    private var image: MetadataTest? = null

    private val purple: Int = containerView.resources.getColor(R.color.startPurple)
    private val blue: Int = containerView.resources.getColor(R.color.startBlue)
    private val yellow: Int = containerView.resources.getColor(R.color.startYellow)
    private val green: Int = containerView.resources.getColor(R.color.startGreen)
    private val red: Int = containerView.resources.getColor(R.color.startRed)

    fun bind(image: MetadataTest?) {
        this.image = image

        filenameView.text = image?.name
        xmp.visibility = if (image?.subjectIds.orEmpty().isEmpty()) View.INVISIBLE else View.VISIBLE

        if (image?.rating != null) {
            galleryRatingBar.rating = image.rating ?: 0f
            galleryRatingBar.visibility = View.VISIBLE
        }
        else {
            galleryRatingBar.visibility = View.INVISIBLE
        }

        if (image?.label != null) {
            label.visibility = View.VISIBLE
            when (image.label?.toLowerCase()) {
                "purple" -> label.setBackgroundColor(purple)
                "blue" -> label.setBackgroundColor(blue)
                "yellow" -> label.setBackgroundColor(yellow)
                "green" -> label.setBackgroundColor(green)
                "red" -> label.setBackgroundColor(red)
                else -> label.visibility = View.INVISIBLE
            }
        } else {
            label.visibility = View.INVISIBLE
        }

        // FIXME: Pretty sure this is deprecated, also it clear on fail (this will leave image remnant)
        image?.let {
            GlideApp.with(itemView.context)
                .load(image)
                .centerCrop()
                .into(galleryImageView)
            galleryImageView.rotation = MetaUtil.getRotation(it.orientation).toFloat()
        }
    }

    companion object {
        fun create(parent: ViewGroup/*, glide: RequestManager*/): GalleryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fileview, parent, false)
            return GalleryViewHolder(view/*, glide*/)
        }
    }
}