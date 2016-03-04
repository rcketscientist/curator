/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.app;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.rawdroid.R;

public abstract class AbstractCoreActivity extends ViewerActivity implements GalleryApp {
    @SuppressWarnings("unused")
    private static final String TAG = "AbstractCoreActivity";

    private GLRootView mGLRootView;
    private ImageCacheService mImageCacheService;
    private final Object mLock = new Object();
    private ThreadPool mThreadPool;
    private OrientationManager mOrientationManager;

    private AlertDialog mAlertDialog = null;
    private final BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getExternalCacheDir() != null) onStorageReady();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GalleryUtils.initialize(this);  // AJM: From GalleryAppImpl
        mOrientationManager = new OrientationManager(this);

        getWindow().setBackgroundDrawable(null);
        //noinspection deprecation
        mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(
                getResources().getColor(R.color.default_background));   //AJM: From ActivityState
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public synchronized ThreadPool getThreadPool() {
        if (mThreadPool == null) {
            mThreadPool = new ThreadPool();
        }
        return mThreadPool;
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
    }

    protected void onStorageReady() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
            unregisterReceiver(mMountReceiver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLRootView.onResume();
        mOrientationManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mOrientationManager.pause();
        mGLRootView.onPause();
        GalleryBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLRootView.lockRenderThread();
        mGLRootView.unlockRenderThread();
    }

    // //////////////////////////////////////////////////////////////////////////
    // Imported ActivityState Methods
    // //////////////////////////////////////////////////////////////////////////

    protected float[] mBackgroundColor;

    protected void setContentPane(GLView content) {
        content.setBackgroundColor(getBackgroundColor());
        getGLRoot().setContentPane(content);
    }

    protected float[] getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public ImageCacheService getImageCacheService() {
        // This method may block on file I/O so a dedicated lock is needed here.
        synchronized (mLock) {
            if (mImageCacheService == null) {
                mImageCacheService = new ImageCacheService(getAndroidContext());
            }
            return mImageCacheService;
        }
    }
}


