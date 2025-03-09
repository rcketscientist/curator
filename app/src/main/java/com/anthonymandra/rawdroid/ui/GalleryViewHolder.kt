package com.anthonymandra.rawdroid.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.rawdroid.databinding.FileviewBinding
import com.anthonymandra.rawdroid.databinding.FolderVisibilityBinding
import com.anthonymandra.rawdroid.databinding.GalleryBinding
import com.anthonymandra.util.MetaUtil
import kotlinx.android.extensions.LayoutContainer
import java.util.Locale

@Suppress("DEPRECATION")
class GalleryViewHolder(override val containerView: View)
	: RecyclerView.ViewHolder(containerView), LayoutContainer {
	private var image: ImageInfo? = null

	private val purple: Int = containerView.resources.getColor(R.color.startPurple)
	private val blue: Int = containerView.resources.getColor(R.color.startBlue)
	private val yellow: Int = containerView.resources.getColor(R.color.startYellow)
	private val green: Int = containerView.resources.getColor(R.color.startGreen)
	private val red: Int = containerView.resources.getColor(R.color.startRed)

	private lateinit var binding: FileviewBinding

	companion object {
		// FIXME: I think this pattern has changed significantly in the recent RecyclerView
		fun create(parent: ViewGroup): GalleryViewHolder {
			val binding = FileviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			val view = binding.root
			val holder = GalleryViewHolder(view)
			holder.binding = binding
			return holder
		}
	}

	fun bind(image: ImageInfo?) {
		this.image = image

		binding.filenameView.text = image?.name
		binding.xmp.visibility = if (image?.subjectIds.orEmpty().isEmpty()) View.INVISIBLE else View.VISIBLE

		if (image?.rating != null) {
			binding.galleryRatingBar.rating = image.rating ?: 0f
			binding.galleryRatingBar.visibility = View.VISIBLE
		} else {
			binding.galleryRatingBar.visibility = View.INVISIBLE
		}

		if (image?.label != null) {
			binding.label.visibility = View.VISIBLE
			when (image.label?.lowercase(Locale.getDefault())) {
				"purple" -> binding.label.setBackgroundColor(purple)
				"blue" -> binding.label.setBackgroundColor(blue)
				"yellow" -> binding.label.setBackgroundColor(yellow)
				"green" -> binding.label.setBackgroundColor(green)
				"red" -> binding.label.setBackgroundColor(red)
				else -> binding.label.visibility = View.INVISIBLE
			}
		} else {
			binding.label.visibility = View.INVISIBLE
		}

		// FIXME: Pretty sure this is deprecated, also it clear on fail (this will leave image remnant)
		image?.let {
			GlideApp.with(itemView.context)
				.load(it)
				.centerCrop()
				.into(binding.galleryImageView)
			// TODO: Glide handles exif orientation, how do we align behavior with non-exif thumbs?
//            galleryImageView.rotation = MetaUtil.getRotation(it.orientation).toFloat()
		}
	}

}