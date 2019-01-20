package com.anthonymandra.rawdroid.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.ImageUtil
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import java.io.ByteArrayInputStream
import java.io.InputStream

internal class RawModelLoader(private val context: Context) : ModelLoader<ImageInfo, InputStream> {

	override fun buildLoadData(imageInfo: ImageInfo, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
		val diskCacheKey = ObjectKey(imageInfo.uri)
		return ModelLoader.LoadData(diskCacheKey, RawFetcher(context, imageInfo))
	}

	override fun handles(image: ImageInfo): Boolean {
		// If processed and OS supported allow glide to handle natively.
		//		return Meta.ImageType.fromInt(image.getType()) != Meta.ImageType.COMMON;
		return true
	}

	private inner class RawFetcher internal constructor(context: Context, private val source: ImageInfo) : DataFetcher<InputStream> {
		private val context: Context = context.applicationContext
		private var stream: ByteArrayInputStream? = null

		override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
			try {
				Log.d("glide", "load: ${source.name}")
				val image = ImageUtil.getThumb(context, source)
				if (image == null || image.isEmpty()) {
					callback.onLoadFailed(Exception("$source could not be processed."))
					return
				}
				stream = ByteArrayInputStream(image)
				callback.onDataReady(stream)
			} catch (e: Exception) {
				callback.onLoadFailed(e)
				e.printStackTrace()
			}

		}

		override fun cleanup() {
			stream?.close()
		}

		override fun cancel() {
			Log.d("glide", "cancel: ${source.name}")
			// Do nothing.
		}

		override fun getDataClass(): Class<InputStream> {
			return InputStream::class.java
		}

		override fun getDataSource(): DataSource {
			// REMOTE stores the original data (in this case full decoded image)
			// LOCAL stores the downsampled transformed data
			// https://bumptech.github.io/glide/tut/custom-modelloader.html#getdatasource
			return DataSource.LOCAL
		}
	}
}
