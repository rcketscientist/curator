package com.anthonymandra.rawdroid.ui;

import android.content.Context;
import android.support.annotation.NonNull;

import com.anthonymandra.rawdroid.data.MetadataTest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

@GlideModule
public final class CuratorGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
//        builder.setLogLevel(Log.VERBOSE);
    }

    @Override
    public void registerComponents(@NonNull Context /*application*/context, @NonNull Glide glide, Registry registry) {
        registry.append(MetadataTest.class, InputStream.class, new RawModelLoaderFactory(context));
    }
}
