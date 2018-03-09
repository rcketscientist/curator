package com.anthonymandra.rawdroid.ui

import android.net.Uri
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.MetaUtil
import com.bumptech.glide.Glide
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
        image?.rating?.let { galleryRatingBar.rating = it }
        xmp.visibility = if (image?.subjectIds != null) View.VISIBLE else View.GONE

        if (image?.label != null) {
            label.visibility = View.VISIBLE
            when (image.label?.toLowerCase()) {
                "purple" -> label.setBackgroundColor(purple)
                "blue" -> label.setBackgroundColor(blue)
                "yellow" -> label.setBackgroundColor(yellow)
                "green" -> label.setBackgroundColor(green)
                "red" -> label.setBackgroundColor(red)
                else -> label.visibility = View.GONE
            }
        } else {
            label.visibility = View.GONE
        }

        filenameView.text = image?.name

        // FIXME: Pretty sure this is deprecated
        Glide.with(itemView.context)
            .using(RawModelLoader(itemView.context))
            .load(RawModelLoader.ImageInfo(Uri.parse(image?.uri),
                image?.let { Meta.ImageType.fromInt(image.type) }))
            .centerCrop()
            .into(webImageView)

        image?.let {
            webImageView.rotation = MetaUtil.getRotation(image.orientation).toFloat()
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