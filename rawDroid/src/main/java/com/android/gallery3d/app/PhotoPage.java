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

import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.SynchronizedHandler;

public abstract class PhotoPage extends AbstractCoreActivity implements
        PhotoView.Listener, GalleryApp{
    private static final String TAG = "PhotoPage";

//    private static final int MSG_HIDE_BARS = 1;
//    private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
//    private static final int MSG_UPDATE_ACTION_BAR = 5;
    private static final int MSG_UNFREEZE_GLROOT = 6;
//    private static final int MSG_WANT_BARS = 7;
//    private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;
//    private static final int MSG_ON_CAMERA_CENTER = 9;
//    private static final int MSG_ON_PICTURE_CENTER = 10;
    private static final int MSG_REFRESH_IMAGE = 11;
    private static final int MSG_UPDATE_PHOTO_UI = 12;
//    private static final int MSG_UPDATE_PROGRESS = 13;
    private static final int MSG_UPDATE_DEFERRED = 14;
//    private static final int MSG_UPDATE_SHARE_URI = 15;
//    private static final int MSG_UPDATE_PANORAMA_UI = 16;

    private static final int HIDE_BARS_TIMEOUT = 3500;
    private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

    protected PhotoView mPhotoView;
    protected PhotoDataAdapter mModel;

    private Handler mHandler;
    private MediaItem mCurrentPhoto = null;
    private boolean mIsActive;
    private OrientationManager mOrientationManager;

    private boolean mSkipUpdateCurrentPhoto = false;

    private static final long DEFERRED_UPDATE_MS = 250;
    private boolean mDeferredUpdateWaiting = false;
    private long mDeferUpdateUntil = Long.MAX_VALUE;

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mPhotoView.layout(0, 0, right - left, bottom - top);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPhotoView = new PhotoView(this);
        mPhotoView.setListener(this);
        mPhotoView.setScaleListener(this);
        mRootPane.addComponent(mPhotoView);
        mOrientationManager = getOrientationManager();
        getGLRoot().setOrientationSource(mOrientationManager);

        mHandler = new SynchronizedHandler(getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UNFREEZE_GLROOT: {
                        getGLRoot().unfreeze();
                        break;
                    }
                    case MSG_UPDATE_DEFERRED: {
                        long nextUpdate = mDeferUpdateUntil - SystemClock.uptimeMillis();
                        if (nextUpdate <= 0) {
                            mDeferredUpdateWaiting = false;
                            updateUIForCurrentPhoto();
                        } else {
                            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, nextUpdate);
                        }
                        break;
                    }
                    case MSG_REFRESH_IMAGE: {
                        final MediaItem photo = mCurrentPhoto;
                        mCurrentPhoto = null;
                        updateCurrentPhoto(photo);
                        break;
                    }
                    case MSG_UPDATE_PHOTO_UI: {
                        updateUIForCurrentPhoto();
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };

        mModel = new PhotoDataAdapter(this, mPhotoView, mMediaItems, mImageIndex, getIntent().getData());
        mModel.setDataListener(this);
        mPhotoView.setModel(mModel);
    }

    @Override
    public void onPhotoChanged(int index, Uri item)
    {
        super.onPhotoChanged(index, item);
        mImageIndex = index;

        if (!mSkipUpdateCurrentPhoto) {
            if (item != null) {
                MediaItem photo = mModel.getMediaItem(0);
                if (photo != null) updateCurrentPhoto(photo);
            }
        }
    }

    @Override
    public void onLoadingFinished(boolean loadingFailed) {
        if (!mModel.isEmpty()) {
            MediaItem photo = mModel.getMediaItem(0);
            if (photo != null) updateCurrentPhoto(photo);
        } else if (mIsActive) {
            finish();
        }
    }

    @Override
    public void onLoadingStarted() {
    }

    private void requestDeferredUpdate() {
        mDeferUpdateUntil = SystemClock.uptimeMillis() + DEFERRED_UPDATE_MS;
        if (!mDeferredUpdateWaiting) {
            mDeferredUpdateWaiting = true;
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, DEFERRED_UPDATE_MS);
        }
    }

    private void updateUIForCurrentPhoto() {
        if (mCurrentPhoto == null) return;

        // If by swiping or deletion the user ends up on an action item
        // and zoomed in, zoom out so that the context of the action is
        // more clear
        if (!mPhotoView.getFilmMode()) {
            mPhotoView.setWantPictureCenterCallbacks(true);
        }
    }

    private void updateCurrentPhoto(MediaItem photo) {
        if (mCurrentPhoto == photo) return;
        mCurrentPhoto = photo;
        if (mPhotoView.getFilmMode()) {
            requestDeferredUpdate();
        } else {
            updateUIForCurrentPhoto();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Callbacks from PhotoView
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onSingleTapUp(int x, int y) { }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;

        getGLRoot().unfreeze();
        mHandler.removeMessages(MSG_UNFREEZE_GLROOT);

        if (mModel != null) {
            mModel.pause();
        }
        mPhotoView.pause();
    }

    @Override
    public void onCurrentImageUpdated() {
        getGLRoot().unfreeze();
    }

    @Override
    public void onFilmModeChanged(boolean enabled) {
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mModel == null) {
            finish();
        }

        getGLRoot().freeze();
        mIsActive = true;
        setContentPane(mRootPane);
        mPhotoView.resume();
        mModel.resume();

        mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);
    }

    @Override
    protected void onZoomLockChanged(boolean locked)
    {
        mPhotoView.setZoomLock(locked);
    }

    @Override
    protected void onDestroy() {
        getGLRoot().setOrientationSource(null);

        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
