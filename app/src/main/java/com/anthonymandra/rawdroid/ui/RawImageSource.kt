package com.anthonymandra.rawdroid.ui

import com.anthonymandra.rawdroid.data.ImageInfo
import com.davemorrissey.labs.subscaleview.ImageSource

class RawImageSource constructor(val source: ImageInfo) : ImageSource<ImageInfo>(source) {
    init {
        useOnlyRegionDecoder = true
    }

    override fun getWidth(): Int {
        return source.width
    }

    override fun getHeight(): Int {
        return source.height
    }
}

