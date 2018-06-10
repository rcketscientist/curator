package com.anthonymandra.rawdroid.ui

import android.content.Context
import android.net.Uri
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.ImageUtil
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import java.io.ByteArrayInputStream
import java.io.InputStream

internal class RawModelLoader(private val context: Context) : ModelLoader<MetadataTest, InputStream> {

    override fun buildLoadData(metadataTest: MetadataTest, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        val diskCacheKey = ObjectKey(metadataTest.uri)
        return ModelLoader.LoadData(diskCacheKey, RawFetcher(context, metadataTest))
    }

    override fun handles(image: MetadataTest): Boolean {
        // If processed and OS supported allow glide to handle natively.
        //		return Meta.ImageType.fromInt(image.getType()) != Meta.ImageType.COMMON;
        return true
    }

    private inner class RawFetcher internal constructor(context: Context, private val source: MetadataTest) : DataFetcher<InputStream> {
        private val context: Context = context.applicationContext

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            try {
                val image = ImageUtil.getThumb(
                        context, Uri.parse(source.uri), Meta.ImageType.fromInt(source.type))
                callback.onDataReady(ByteArrayInputStream(image))
            } catch (e: Exception) {
                callback.onLoadFailed(e)
                e.printStackTrace()
            }

        }

        override fun cleanup() {
            // Do nothing. It's safe to leave a ByteArrayInputStream open.
        }

        override fun cancel() {
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
