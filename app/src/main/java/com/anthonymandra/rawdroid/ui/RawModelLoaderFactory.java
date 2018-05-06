package com.anthonymandra.rawdroid.ui;

import android.content.Context;
import android.support.annotation.NonNull;

import com.anthonymandra.rawdroid.data.MetadataTest;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

public class RawModelLoaderFactory implements ModelLoaderFactory<MetadataTest, InputStream> {

    private Context context;    //FIXME: This should not be held!
    RawModelLoaderFactory(Context c) {
        context = c;
    }

    @NonNull
    @Override
    public ModelLoader<MetadataTest, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
        RawModelLoader loader = new RawModelLoader(context);
        context = null;
        return loader;
    }

    @Override
    public void teardown() {
        context = null;
    }
}
