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

import com.android.gallery3d.anim.StateTransitionAnimation;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PreparePageFadeoutTexture;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.rawdroid.R;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public abstract class AbstractGalleryActivity extends ViewerActivity implements GalleryApp {
    @SuppressWarnings("unused")
    private static final String TAG = "AbstractGalleryActivity";
    private GLRootView mGLRootView;
    private ImageCacheService mImageCacheService;
    private Object mLock = new Object();
    private ThreadPool mThreadPool;
//    private StateManager mStateManager;
//    private GalleryActionBar mActionBar;
    private OrientationManager mOrientationManager;
    private TransitionStore mTransitionStore = new TransitionStore();
    private boolean mDisableToggleStatusBar;
//    private PanoramaViewHelper mPanoramaViewHelper;

    private AlertDialog mAlertDialog = null;
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getExternalCacheDir() != null) onStorageReady();
        }
    };
    private IntentFilter mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GalleryUtils.initialize(this);  // AJM: From GalleryAppImpl
        mOrientationManager = new OrientationManager(this);
        toggleStatusBarByOrientation();
        getWindow().setBackgroundDrawable(null);
        mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(
                getResources().getColor(getBackgroundColorId()));   //AJM: From ActivityState
//        mPanoramaViewHelper = new PanoramaViewHelper(this);
//        mPanoramaViewHelper.onCreate();
//        doBindBatchService();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
//            getStateManager().saveState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
//        mStateManager.onConfigurationChange(config);
//        getGalleryActionBar().onConfigurationChanged();
        invalidateOptionsMenu();
        toggleStatusBarByOrientation();
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        super.onCreateOptionsMenu(menu);
//        return getStateManager().createOptionsMenu(menu);
//    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

//    @Override
//    public DataManager getDataManager() {
//        return ((GalleryApp) getApplication()).getDataManager();
//    }

    @Override
    public synchronized ThreadPool getThreadPool() {
        if (mThreadPool == null) {
            mThreadPool = new ThreadPool();
        }
        return mThreadPool;
    }
//    public synchronized StateManager getStateManager() {
//        if (mStateManager == null) {
//            mStateManager = new StateManager(this);
//        }
//        return mStateManager;
//    }

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
    protected void onStart() {
        super.onStart();
//        if (getExternalCacheDir() == null) {
//            OnCancelListener onCancel = new OnCancelListener() {
//                @Override
//                public void onCancel(DialogInterface dialog) {
//                    finish();
//                }
//            };
//            OnClickListener onClick = new OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    dialog.cancel();
//                }
//            };
//            AlertDialog.Builder builder = new AlertDialog.Builder(this)
//                    .setTitle(R.string.no_external_storage_title)
//                    .setMessage(R.string.no_external_storage)
//                    .setNegativeButton(android.R.string.cancel, onClick)
//                    .setOnCancelListener(onCancel);
//            if (ApiHelper.HAS_SET_ICON_ATTRIBUTE) {
//                setAlertDialogIconAttribute(builder);
//            } else {
//                builder.setIcon(android.R.drawable.ic_dialog_alert);
//            }
//            mAlertDialog = builder.show();
//            registerReceiver(mMountReceiver, mMountFilter);
//        }
//        mPanoramaViewHelper.onStart();
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static void setAlertDialogIconAttribute(
            AlertDialog.Builder builder) {
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
    }

    @Override
    protected void onStop() {
        super.onStop();
//        if (mAlertDialog != null) {
//            unregisterReceiver(mMountReceiver);
//            mAlertDialog.dismiss();
//            mAlertDialog = null;
//        }
//        mPanoramaViewHelper.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mGLRootView.lockRenderThread();
//        try {
//            getStateManager().resume();
            // From ActivityState, represents the getStateManager().resume()
//            RawTexture fade = getTransitionStore().get(
//                    PreparePageFadeoutTexture.KEY_FADE_TEXTURE);
//            mNextTransition = getTransitionStore().get(
//                    KEY_TRANSITION_IN, StateTransitionAnimation.Transition.None);
//            mNextTransition = StateTransitionAnimation.Transition.PhotoIncoming;
//            if (mNextTransition != StateTransitionAnimation.Transition.None) {
//                mIntroAnimation = new StateTransitionAnimation(mNextTransition, fade);
//                mNextTransition = StateTransitionAnimation.Transition.None;
//            }
//            getDataManager().resume();
//        } finally {
//            mGLRootView.unlockRenderThread();
//        }
        setScreenFlags();
        mGLRootView.onResume();
        mOrientationManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mOrientationManager.pause();
        mGLRootView.onPause();
//        mGLRootView.lockRenderThread();
//        try {
//            getStateManager().pause();
//            getDataManager().pause();
//        } finally {
//            mGLRootView.unlockRenderThread();
//        }
        GalleryBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLRootView.lockRenderThread();
//        try {
//            getStateManager().destroy();
//        } finally {
            mGLRootView.unlockRenderThread();
//        }
//        doUnbindBatchService();
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        mGLRootView.lockRenderThread();
//        try {
//            getStateManager().notifyActivityResult(
//                    requestCode, resultCode, data);
//        } finally {
//            mGLRootView.unlockRenderThread();
//        }
//    }

//    @Override
//    public void onBackPressed() {
//        // send the back event to the top sub-state
//        GLRoot root = getGLRoot();
//        root.lockRenderThread();
//        try {
//            getStateManager().onBackPressed();
//        } finally {
//            root.unlockRenderThread();
//        }
//    }

//    public GalleryActionBar getGalleryActionBar() {
//        if (mActionBar == null) {
//            mActionBar = new GalleryActionBar(this);
//        }
//        return mActionBar;
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        GLRoot root = getGLRoot();
//        root.lockRenderThread();
//        try {
//            return getStateManager().itemSelected(item);
//        } finally {
//            root.unlockRenderThread();
//        }
//    }

    protected void disableToggleStatusBar() {
        mDisableToggleStatusBar = true;
    }

    // Shows status bar in portrait view, hide in landscape view
    private void toggleStatusBarByOrientation() {
        if (mDisableToggleStatusBar) return;

        Window win = getWindow();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public TransitionStore getTransitionStore() {
        return mTransitionStore;
    }

//    public PanoramaViewHelper getPanoramaViewHelper() {
//        return mPanoramaViewHelper;
//    }

    protected boolean isFullscreen() {
        return (getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

//    private BatchService mBatchService;
//    private boolean mBatchServiceIsBound = false;
//    private ServiceConnection mBatchServiceConnection = new ServiceConnection() {
//        public void onServiceConnected(ComponentName className, IBinder service) {
//            mBatchService = ((BatchService.LocalBinder)service).getService();
//        }
//
//        public void onServiceDisconnected(ComponentName className) {
//            mBatchService = null;
//        }
//    };

//    private void doBindBatchService() {
//        bindService(new Intent(this, BatchService.class), mBatchServiceConnection, Context.BIND_AUTO_CREATE);
//        mBatchServiceIsBound = true;
//    }

//    private void doUnbindBatchService() {
//        if (mBatchServiceIsBound) {
//            // Detach our existing connection.
//            unbindService(mBatchServiceConnection);
//            mBatchServiceIsBound = false;
//        }
//    }

//    public ThreadPool getBatchServiceThreadPoolIfAvailable() {
//        if (mBatchServiceIsBound && mBatchService != null) {
//            return mBatchService.getThreadPool();
//        } else {
//            throw new RuntimeException("Batch service unavailable");
//        }
//    }

    // //////////////////////////////////////////////////////////////////////////
    // Imported ActivityState Methods
    // //////////////////////////////////////////////////////////////////////////

    protected static final int FLAG_HIDE_ACTION_BAR = 1;
    protected static final int FLAG_HIDE_STATUS_BAR = 2;
    protected static final int FLAG_SCREEN_ON_WHEN_PLUGGED = 4;
    protected static final int FLAG_SCREEN_ON_ALWAYS = 8;
    protected static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 16;
    protected static final int FLAG_SHOW_WHEN_LOCKED = 32;

    protected int mFlags;
    private boolean mPlugged = false;

    private static final String KEY_TRANSITION_IN = "transition-in";

    private StateTransitionAnimation.Transition mNextTransition =
            StateTransitionAnimation.Transition.None;
//    private StateTransitionAnimation mIntroAnimation;
    private GLView mContentPane;
    protected float[] mBackgroundColor;

    protected void setContentPane(GLView content) {
        mContentPane = content;
//        if (mIntroAnimation != null) {
//            mContentPane.setIntroAnimation(mIntroAnimation);
//            mIntroAnimation = null;
//        }
        mContentPane.setBackgroundColor(getBackgroundColor());
        getGLRoot().setContentPane(mContentPane);
    }

    protected int getBackgroundColorId() {
        return R.color.default_background;
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

    private void setScreenFlags() {
        final Window win = getWindow();
        final WindowManager.LayoutParams params = win.getAttributes();
        if ((0 != (mFlags & FLAG_SCREEN_ON_ALWAYS)) ||
                (mPlugged && 0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED))) {
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        if (0 != (mFlags & FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)) {
            params.flags |= WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        }
        if (0 != (mFlags & FLAG_SHOW_WHEN_LOCKED)) {
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        }
        win.setAttributes(params);
    }
}


