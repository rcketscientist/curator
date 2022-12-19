package com.anthonymandra.curator.ui

import android.content.Context

import com.anthonymandra.curator.data.ImageInfo
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

import java.io.InputStream

@GlideModule
class CuratorGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        //        builder.setLogLevel(Log.VERBOSE);
    }

    override fun registerComponents(/*application*/context: Context, glide: Glide, registry: Registry) {
        registry.append(ImageInfo::class.java, InputStream::class.java, RawModelLoaderFactory(context))
    }
}
