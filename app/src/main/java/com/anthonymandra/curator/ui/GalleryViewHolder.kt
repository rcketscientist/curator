package com.anthonymandra.curator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anthonymandra.curator.R
import com.anthonymandra.curator.data.ImageInfo
import com.anthonymandra.curator.databinding.FileviewBinding
import kotlinx.android.extensions.LayoutContainer

@Suppress("DEPRECATION")
class GalleryViewHolder(override val containerView: View)
	: RecyclerView.ViewHolder(containerView), LayoutContainer {
	private lateinit var ui: FileviewBinding

	private var image: ImageInfo? = null

	private val purple: Int = containerView.resources.getColor(R.color.startPurple)
	private val blue: Int = containerView.resources.getColor(R.color.startBlue)
	private val yellow: Int = containerView.resources.getColor(R.color.startYellow)
	private val green: Int = containerView.resources.getColor(R.color.startGreen)
	private val red: Int = containerView.resources.getColor(R.color.startRed)

	fun bind(image: ImageInfo?) {
		this.image = image

		ui.filenameView.text = image?.name
		ui.xmp.visibility = if (image?.subjectIds.orEmpty().isEmpty()) View.INVISIBLE else View.VISIBLE

		if (image?.rating != null) {
			ui.galleryRatingBar.rating = image.rating ?: 0f
			ui.galleryRatingBar.visibility = View.VISIBLE
		} else {
			ui.galleryRatingBar.visibility = View.INVISIBLE
		}

		if (image?.label != null) {
			ui.label.visibility = View.VISIBLE
			when (image.label?.toLowerCase()) {
				"purple" -> ui.label.setBackgroundColor(purple)
				"blue" -> ui.label.setBackgroundColor(blue)
				"yellow" -> ui.label.setBackgroundColor(yellow)
				"green" -> ui.label.setBackgroundColor(green)
				"red" -> ui.label.setBackgroundColor(red)
				else -> ui.label.visibility = View.INVISIBLE
			}
		} else {
			ui.label.visibility = View.INVISIBLE
		}

		// FIXME: Pretty sure this is deprecated, also it clear on fail (this will leave image remnant)
		image?.let {
			GlideApp.with(itemView.context)
					.load(it)
					.centerCrop()
					.into(ui.galleryImageView)
			// TODO: Glide handles exif orientation, how do we align behavior with non-exif thumbs?
//            galleryImageView.rotation = MetaUtil.getRotation(it.orientation).toFloat()
		}
	}

	companion object {
		fun create(parent: ViewGroup): GalleryViewHolder {
			val view = FileviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			return GalleryViewHolder(view.root)
		}
	}
}