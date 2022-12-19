package com.anthonymandra.curator.ui;

import android.content.Context;
import androidx.annotation.NonNull;

import com.anthonymandra.curator.data.ImageInfo;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

public class RawModelLoaderFactory implements ModelLoaderFactory<ImageInfo, InputStream> {

    private Context applicationContext;
    RawModelLoaderFactory(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @NonNull
    @Override
    public ModelLoader<ImageInfo, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
        return new RawModelLoader(applicationContext);
    }

    @Override
    public void teardown() {
        applicationContext = null;
    }
}
