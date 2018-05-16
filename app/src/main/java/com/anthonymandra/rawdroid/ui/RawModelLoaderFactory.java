package com.anthonymandra.rawdroid.ui;

import android.content.Context;
import android.support.annotation.NonNull;

import com.anthonymandra.rawdroid.data.MetadataTest;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

public class RawModelLoaderFactory implements ModelLoaderFactory<MetadataTest, InputStream> {

    private Context applicationContext;
    RawModelLoaderFactory(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @NonNull
    @Override
    public ModelLoader<MetadataTest, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
        return new RawModelLoader(applicationContext);
    }

    @Override
    public void teardown() {
        applicationContext = null;
    }
}
