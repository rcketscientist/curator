package com.anthonymandra.rawdroid.ui

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.ImageUtil
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder

class RawImageRegionDecoder(
    private var bitmapConfig: Bitmap.Config = getPreferredBitmapConfig()
    ) : ImageRegionDecoder<RawImageSource> {

    private var decoder: BitmapRegionDecoder? = null

    override fun isReady(): Boolean {
        return decoder?.isRecycled == true
    }

    override fun init(context: Context?, source: RawImageSource): Point {
        val imageData = ImageUtil.getThumb(context, source.source)
        decoder = BitmapRegionDecoder.newInstance(imageData, 0, imageData.size, false)
        return Point(decoder!!.width, decoder!!.height)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        if (decoder?.isRecycled == true) {
            throw IllegalStateException("Cannot decode region after decoder has been recycled")
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize
        options.inPreferredConfig = bitmapConfig
        return decoder?.decodeRegion(sRect, options)
                ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
    }

    override fun recycle() {
        decoder?.recycle()
        decoder = null
    }

    override fun getWidth(): Int {
        return decoder?.width ?: 0
    }

    override fun getHeight(): Int {
        return decoder?.height ?: 0
    }

    companion object {
        fun getPreferredBitmapConfig(): Bitmap.Config {
            return SubsamplingScaleImageView.getPreferredBitmapConfig() ?: Bitmap.Config.ARGB_8888
        }
    }
}