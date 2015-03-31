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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.RawDroid;

public abstract class PhotoPage extends AbstractGalleryActivity implements
        PhotoView.Listener, GalleryApp, PhotoDataAdapter.DataListener{
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
    protected Model mModel;

    private int mCurrentIndex = 0;
    private Handler mHandler;
    private MediaItem mCurrentPhoto = null;
    private boolean mIsActive;
    private OrientationManager mOrientationManager;

    private boolean mSkipUpdateCurrentPhoto = false;

    private static final long DEFERRED_UPDATE_MS = 250;
    private boolean mDeferredUpdateWaiting = false;
    private long mDeferUpdateUntil = Long.MAX_VALUE;

    protected int mContentView;

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

        setContentView(mContentView);
        Toolbar toolbar = (Toolbar) findViewById(R.id.viewerToolbar);
        setSupportActionBar(toolbar);

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

        mCurrentIndex = setPathFromIntent();
        if (mCurrentIndex == -1)
        {
            Toast.makeText(this, "Unable to locate imported image, restarting...", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, RawDroid.class));
            finish();
        }

        PhotoDataAdapter pda = new PhotoDataAdapter(
                this, mPhotoView, mVisibleItems, mCurrentIndex,
                -1, false, false);
        mModel = pda;
        mPhotoView.setModel(mModel);

        pda.setDataListener(this);

        mPhotoView.setFilmMode(mVisibleItems.size() > 1);
    }

    @Override
    public void onPhotoChanged(int index, Uri item) {
        mCurrentIndex = index;

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
    public void onSingleTapUp(int x, int y) {
        MediaItem item = mModel.getMediaItem(0);
        if (item == null) {
            // item is not ready or it is camera preview, ignore
            return;
        }

        int supported = item.getSupportedOperations();
        boolean playVideo = ((supported & MediaItem.SUPPORT_PLAY) != 0);
        boolean goBack = ((supported & MediaItem.SUPPORT_BACK) != 0);

        if (playVideo) {
            // determine if the point is at center (1/6) of the photo view.
            // (The position of the "play" icon is at center (1/6) of the photo)
            int w = mPhotoView.getWidth();
            int h = mPhotoView.getHeight();
            playVideo = (Math.abs(x - w / 2) * 12 <= w)
                && (Math.abs(y - h / 2) * 12 <= h);
        }

        if (playVideo) {
//            if (mSecureAlbum == null) {
//                playVideo(mActivity, item.getPlayUri(), item.getName());
//            } else {
//                mActivity.getStateManager().finishState(this);
//            }
        } else if (goBack) {
            onBackPressed();
        }
    }

    public void playVideo(Activity activity, Uri uri, String title) {
//        try {
//            Intent intent = new Intent(Intent.ACTION_VIEW)
//                    .setDataAndType(uri, "video/*")
//                    .putExtra(Intent.EXTRA_TITLE, title)
//                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true);
//            activity.startActivityForResult(intent, REQUEST_PLAY_VIDEO);
//        } catch (ActivityNotFoundException e) {
//            Toast.makeText(activity, activity.getString(R.string.video_err),
//                    Toast.LENGTH_SHORT).show();
//        }
    }

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

    private void transitionFromAlbumPageIfNeeded() {
        mModel.moveTo(mCurrentIndex);
        mPhotoView.setFilmMode(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mModel == null) {
            finish();
        }

        transitionFromAlbumPageIfNeeded();

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
