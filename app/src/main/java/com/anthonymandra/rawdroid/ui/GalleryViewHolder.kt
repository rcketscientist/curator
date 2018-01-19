package com.anthonymandra.rawdroid.ui

import android.net.Uri
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataResult
import com.anthonymandra.util.MetaUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import kotlinx.android.synthetic.main.fileview.view.*

@Suppress("DEPRECATION")
class GalleryViewHolder(view: View, private val glide: RequestManager) : RecyclerView.ViewHolder(view) {
    private var image: MetadataResult? = null

    private val purple: Int = view.resources.getColor(R.color.startPurple)
    private val blue: Int = view.resources.getColor(R.color.startBlue)
    private val yellow: Int = view.resources.getColor(R.color.startYellow)
    private val green: Int = view.resources.getColor(R.color.startGreen)
    private val red: Int = view.resources.getColor(R.color.startRed)

    fun bind(image: MetadataResult?) {
        this.image = image

        image?.rating?.let { itemView.galleryRatingBar.rating = it }
        itemView.xmp.visibility = if (image?.keywords != null) View.VISIBLE else View.GONE
        if (image?.label != null) {
            itemView.label.visibility = View.VISIBLE
            when (image.label?.toLowerCase()) {
                "purple" -> itemView.label.setBackgroundColor(purple)
                "blue" -> itemView.label.setBackgroundColor(blue)
                "yellow" -> itemView.label.setBackgroundColor(yellow)
                "green" -> itemView.label.setBackgroundColor(green)
                "red" -> itemView.label.setBackgroundColor(red)
                else -> itemView.label.visibility = View.GONE
            }
        } else {
            itemView.label.visibility = View.GONE
        }
        image?.keywords.let { itemView.xmp.visibility }
        itemView.filenameView.text = image?.name

        // FIXME: Pretty sure this is deprecated
        Glide.with(itemView.context)
            .using(RawModelLoader(itemView.context))
            .load(RawModelLoader.ImageInfo(Uri.parse(image?.uri),
                image?.let { Meta.ImageType.fromInt(image.type) }))
            .centerCrop()
            .into(itemView.webImageView)

        image?.let {
            itemView.webImageView.rotation = MetaUtil.getRotation(image.orientation).toFloat()
        }
    }

    companion object {
        fun create(parent: ViewGroup, glide: RequestManager): GalleryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fileview, parent, false)
            return GalleryViewHolder(view, glide)
        }
    }
}