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

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Message;
import android.view.MotionEvent;
import android.view.animation.AccelerateInterpolator;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.StringTexture;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.RangeArray;
import com.anthonymandra.framework.License;
import com.anthonymandra.framework.ScaleChangedListener;
import com.anthonymandra.rawdroid.R;

public class PhotoView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "PhotoView";
    private final int mPlaceholderColor;
    private boolean mUpdateForContentChange;
    private boolean mIsPro;

    public static class Size {
        public int width;
        public int height;
    }

    public interface Model extends TileImageView.TileSource {
        int getCurrentIndex();
        void moveTo(int index);

        // Returns the size for the specified picture. If the size information is
        // not avaiable, width = height = 0.
        void getImageSize(int offset, Size size);

        // Returns the media item for the specified picture.
        MediaItem getMediaItem(int offset);

        // Returns the rotation for the specified picture.
        int getImageRotation(int offset);

        // This amends the getScreenNail() method of TileImageView.Model to get
        // ScreenNail at previous (negative offset) or next (positive offset)
        // positions. Returns null if the specified ScreenNail is unavailable.
        ScreenNail getScreenNail(int offset);

        // Set this to true if we need the model to provide full images.
        void setNeedFullImage(boolean enabled);

        // Returns true if the item is a Video.
        boolean isVideo(int offset);

        // Returns true if the item can be deleted.
        boolean isDeletable(int offset);

        int LOADING_INIT = 0;
        int LOADING_COMPLETE = 1;
        int LOADING_FAIL = 2;

        int getLoadingState(int offset);

        // When data change happens, we need to decide which MediaItem to focus
        // on.
        //
        // 1. If focus hint path != null, we try to focus on it if we can find
        // it. This is used for undo a deletion, so we can focus on the
        // undeleted item.
        //
        // 2. Otherwise try to focus on the MediaItem that is currently focused,
        // if we can find it.
        //
        // 3. Otherwise try to focus on the previous MediaItem or the next
        // MediaItem, depending on the value of focus hint direction.
        int FOCUS_HINT_NEXT = 0;
        int FOCUS_HINT_PREVIOUS = 1;
        MediaItem getCurrentItem();
    }

    public interface Listener {
        void onSingleTapUp(int x, int y);
        void onCurrentImageUpdated();
        void onFilmModeChanged(boolean enabled);
        void onCommitDeleteImage(MediaItem toDelete);
        void onSingleTapConfirmed();// int x, int y); Don't need the point at the moment don't bother converting
    }

    // The rules about orientation locking:
    //
    // (1) We need to lock the orientation if we are in page mode camera
    // preview, so there is no (unwanted) rotation animation when the user
    // rotates the device.
    //
    // (2) We need to unlock the orientation if we want to show the action bar
    // because the action bar follows the system orientation.
    //
    // The rules about action bar:
    //
    // (1) If we are in film mode, we don't show action bar.
    //
    // (2) If we go from camera to gallery with capture animation, we show
    // action bar.
    private static final int MSG_CANCEL_EXTRA_SCALING = 2;
    private static final int MSG_SWITCH_FOCUS = 3;

    private static final float SWIPE_THRESHOLD = 300f;

    private static final float DEFAULT_TEXT_SIZE = 20;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;
    private static final int ICON_RATIO = 6;

    // whether we want to apply card deck effect in page mode.
    private static final boolean CARD_EFFECT = true;

    // whether we want to apply offset effect in film mode.
    private static final boolean OFFSET_EFFECT = true;

    // Used to calculate the scaling factor for the card deck effect.
    private ZInterpolator mScaleInterpolator = new ZInterpolator(0.5f);

    // Used to calculate the alpha factor for the fading animation.
    private AccelerateInterpolator mAlphaInterpolator =
            new AccelerateInterpolator(0.9f);

    // We keep this many previous ScreenNails. (also this many next ScreenNails)
    public static final int SCREEN_NAIL_MAX = 3;

    // These are constants for the delete gesture.
    private static final int SWIPE_ESCAPE_VELOCITY = 500; // dp/sec
    private static final int MAX_DISMISS_VELOCITY = 2500; // dp/sec
    private static final int SWIPE_ESCAPE_DISTANCE = 150; // dp

    // The picture entries, the valid index is from -SCREEN_NAIL_MAX to
    // SCREEN_NAIL_MAX.
    private final RangeArray<Picture> mPictures =
            new RangeArray<>(-SCREEN_NAIL_MAX, SCREEN_NAIL_MAX);
    private Size[] mSizes = new Size[2 * SCREEN_NAIL_MAX + 1];

    private final MyGestureListener mGestureListener;
    private final GestureRecognizer mGestureRecognizer;
    private final PositionController mPositionController;

    private Listener mListener;
    private ScaleChangedListener mScaleListener;
    private Model mModel;
    private StringTexture mNoThumbnailText;
    private TileImageView mTileView;
    private EdgeView mEdgeView;
    private Texture mVideoPlayIcon;

    private SynchronizedHandler mHandler;

    private boolean mCancelExtraScalingPending;
    private boolean mFilmMode = false;
    private boolean mWantPictureCenterCallbacks = false;
    private int mDisplayRotation = 0;
    private int mCompensation = 0;
    private boolean mFirst = true;

    // [mPrevBound, mNextBound] is the range of index for all pictures in the
    // model, if we assume the index of current focused picture is 0.  So if
    // there are some previous pictures, mPrevBound < 0, and if there are some
    // next pictures, mNextBound > 0.
    private int mPrevBound;
    private int mNextBound;

    // This variable prevents us doing snapback until its values goes to 0. This
    // happens if the user gesture is still in progress or we are in a capture
    // animation.
    private int mHolding;
    private static final int HOLD_TOUCH_DOWN = 1;
    private static final int HOLD_CAPTURE_ANIMATION = 2;
    private static final int HOLD_DELETE = 4;

    // mTouchBoxIndex is the index of the box that is touched by the down
    // gesture in film mode. The value Integer.MAX_VALUE means no box was
    // touched.
    private int mTouchBoxIndex = Integer.MAX_VALUE;

    private boolean mZoomLocked;

    private Context mContext;

    public PhotoView(GalleryApp activity) {
        mTileView = new TileImageView(activity);
        addComponent(mTileView);
        mContext = activity.getAndroidContext();
        mPlaceholderColor = mContext.getResources().getColor(
                R.color.photo_placeholder);
        mEdgeView = new EdgeView(mContext);
        addComponent(mEdgeView);

        mNoThumbnailText = StringTexture.newInstance(
                mContext.getString(R.string.no_thumbnail),
                DEFAULT_TEXT_SIZE, Color.WHITE);

        mHandler = new MyHandler(activity.getGLRoot());

        mGestureListener = new MyGestureListener();
        mGestureRecognizer = new GestureRecognizer(mContext, mGestureListener);

        mPositionController = new PositionController(mContext,
                new PositionController.Listener() {

            @Override
            public void invalidate() {
                PhotoView.this.invalidate();
            }
            @Override
            public boolean isHoldingDown() {
                return (mHolding & HOLD_TOUCH_DOWN) != 0;
            }
            @Override
            public boolean isHoldingDelete() {
                return (mHolding & HOLD_DELETE) != 0;
            }
            @Override
            public void onPull(int offset, int direction) {
                mEdgeView.onPull(offset, direction);
            }
            @Override
            public void onRelease() {
                mEdgeView.onRelease();
            }
            @Override
            public void onAbsorb(int velocity, int direction) {
                mEdgeView.onAbsorb(velocity, direction);
            }

			@Override
        	public void onScaleChanged(float scale) {
            	// Forward it on.
                if (mScaleListener != null)
                    mScaleListener.onScaleChanged(scale);
        	}
        });
        mVideoPlayIcon = new ResourceTexture(mContext,
                android.R.drawable.ic_media_play);// R.drawable.ic_control_play);
        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            if (i == 0) {
                mPictures.put(i, new FullPicture());
            } else {
                mPictures.put(i, new ScreenNailPicture(i));
            }
        }
    }

    private void delete() {
        MediaItem item = mModel.getMediaItem(mTouchBoxIndex);
        if (item == null) return;
        mListener.onCommitDeleteImage(item);
    }

    public void stopScrolling() {
        mPositionController.stopScrolling();
    }

    public void setModel(Model model) {
        mModel = model;
        mTileView.setModel(mModel);
    }

    class MyHandler extends SynchronizedHandler {
        public MyHandler(GLRoot root) {
            super(root);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_CANCEL_EXTRA_SCALING: {
                    mGestureRecognizer.cancelScale();
                    mPositionController.setExtraScalingRange(false);
                    mCancelExtraScalingPending = false;
                    break;
                }
                case MSG_SWITCH_FOCUS: {
                    switchFocus();
                    break;
                }
                default: throw new AssertionError(message.what);
            }
        }
    }

    public void setWantPictureCenterCallbacks(boolean wanted) {
        mWantPictureCenterCallbacks = wanted;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Data/Image change notifications
    ////////////////////////////////////////////////////////////////////////////

    public void notifyDataChange(int[] fromIndex, int prevBound, int nextBound) {
        mPrevBound = prevBound;
        mNextBound = nextBound;

        // Update mTouchBoxIndex
        if (mTouchBoxIndex != Integer.MAX_VALUE) {
            int k = mTouchBoxIndex;
            mTouchBoxIndex = Integer.MAX_VALUE;
            for (int i = 0; i < 2 * SCREEN_NAIL_MAX + 1; i++) {
                if (fromIndex[i] == k) {
                    mTouchBoxIndex = i - SCREEN_NAIL_MAX;
                    break;
                }
            }
        }

        // Update the ScreenNails.
        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            Picture p =  mPictures.get(i);
            p.reload();
            mSizes[i + SCREEN_NAIL_MAX] = p.getSize();
        }

        // Move the boxes
        mPositionController.moveBox(fromIndex, mPrevBound < 0, mNextBound > 0,
                false, mSizes);

        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            setPictureSize(i);
        }

        invalidate();

        if (mUpdateForContentChange)
        {
            mUpdateForContentChange = false;
            snapback();
        }
    }
    
    public void setUpdateForContentChange(boolean updateForContentChange)
    {
        mUpdateForContentChange = updateForContentChange;
    }

    public void notifyImageChange(int index) {
        if (index == 0) {
            mListener.onCurrentImageUpdated();
        }
        mPictures.get(index).reload();
        setPictureSize(index);
        invalidate();
    }

    private void setPictureSize(int index) {
        Picture p = mPictures.get(index);
        mPositionController.setImageSize(index, p.getSize(), null);
    }

    @Override
    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        mTileView.layout(0, 0, w, h);
        mEdgeView.layout(0, 0, w, h);

        GLRoot root = getGLRoot();
        int displayRotation = root.getDisplayRotation();
        int compensation = root.getCompensation();
        if (mDisplayRotation != displayRotation
                || mCompensation != compensation) {
            mDisplayRotation = displayRotation;
            mCompensation = compensation;

            // We need to change the size and rotation of the Camera ScreenNail,
            // but we don't want it to animate because the size doen't actually
            // change in the eye of the user.
            for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
                Picture p = mPictures.get(i);
            }
        }

//        updateCameraRect();
//        mPositionController.setConstrainedFrame(mCameraRect);
        if (changeSize) {
            mPositionController.setViewSize(getWidth(), getHeight());
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Pictures
    ////////////////////////////////////////////////////////////////////////////

    private interface Picture {
        void reload();
        void draw(GLCanvas canvas, Rect r);
        void setScreenNail(ScreenNail s);
        Size getSize();
    }

    class FullPicture implements Picture {
        private int mRotation;
        private boolean mIsVideo;
        private boolean mIsDeletable;
        private int mLoadingState = Model.LOADING_INIT;
        private Size mSize = new Size();

        @Override
        public void reload() {
            // mImageWidth and mImageHeight will get updated
            mTileView.notifyModelInvalidated();

            mIsVideo = mModel.isVideo(0);
            mIsDeletable = mModel.isDeletable(0);
            mLoadingState = mModel.getLoadingState(0);
            setScreenNail(mModel.getScreenNail(0));
            updateSize();
        }

        @Override
        public Size getSize() {
            return mSize;
        }

        private void updateSize() {
            mRotation = mModel.getImageRotation(0);

            int w = mTileView.mImageWidth;
            int h = mTileView.mImageHeight;
            mSize.width = getRotated(mRotation, w, h);
            mSize.height = getRotated(mRotation, h, w);
        }

        @Override
        public void draw(GLCanvas canvas, Rect r) {
            drawTileView(canvas, r);

            // We want to have the following transitions:
            // (1) Move camera preview out of its place: switch to film mode
            // (2) Move camera preview into its place: switch to page mode
            // The extra mWasCenter check makes sure (1) does not apply if in
            // page mode, we move _to_ the camera preview from another picture.

            // Holdings except touch-down prevent the transitions.
            if ((mHolding & ~HOLD_TOUCH_DOWN) != 0) return;
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            mTileView.setScreenNail(s);
        }

        private void drawTileView(GLCanvas canvas, Rect r) {
            float imageScale = mPositionController.getImageScale();
            int viewW = getWidth();
            int viewH = getHeight();
            float cx = r.exactCenterX();
            float cy = r.exactCenterY();
            float scale = 1f; // the scaling factor due to card effect

            canvas.save(GLCanvas.SAVE_FLAG_MATRIX | GLCanvas.SAVE_FLAG_ALPHA);
            float filmRatio = mPositionController.getFilmRatio();
            //TODO: What does all this mean?
            // If you set these false you should encounter less errors (from 4.1.1 version)
            boolean wantsCardEffect = CARD_EFFECT
                    && filmRatio != 1f
                    && !mPositionController.inOpeningAnimation();
            boolean wantsOffsetEffect = OFFSET_EFFECT && mIsDeletable
                    && filmRatio == 1f && r.centerY() != viewH / 2;
            if (wantsCardEffect) {
                // Calculate the move-out progress value.
                int left = r.left;
                int right = r.right;
                float progress = calculateMoveOutProgress(left, right, viewW);
                progress = Utils.clamp(progress, -1f, 1f);

                // We only want to apply the fading animation if the scrolling
                // movement is to the right.
                if (progress < 0) {
                    scale = getScrollScale(progress);
                    float alpha = getScrollAlpha(progress);
                    scale = interpolate(filmRatio, scale, 1f);
                    alpha = interpolate(filmRatio, alpha, 1f);

                    imageScale *= scale;
                    canvas.multiplyAlpha(alpha);

                    float cxPage; // the cx value in page mode
                    if (right - left <= viewW) {
                        // If the picture is narrower than the view, keep it at
                        // the center of the view.
                        cxPage = viewW / 2f;
                    } else {
                        // If the picture is wider than the view (it's
                        // zoomed-in), keep the left edge of the object align
                        // the the left edge of the view.
                        cxPage = (right - left) * scale / 2f;
                    }
                    cx = interpolate(filmRatio, cxPage, cx);
                }
            } else if (wantsOffsetEffect) {
                float offset = (float) (r.centerY() - viewH / 2) / viewH;
                float alpha = getOffsetAlpha(offset);
                canvas.multiplyAlpha(alpha);
            }

            // Draw the tile view.
            setTileViewPosition(cx, cy, viewW, viewH, imageScale);
            renderChild(canvas, mTileView);

            // Draw the play video icon and the message.
            canvas.translate((int) (cx + 0.5f), (int) (cy + 0.5f));
            int s = (int) (scale * Math.min(r.width(), r.height()) + 0.5f);
            if (mIsVideo) drawVideoPlayIcon(canvas, s);
            if (mLoadingState == Model.LOADING_FAIL) {
                drawLoadingFailMessage(canvas);
            }

            // Draw a debug indicator showing which picture has focus (index ==
            // 0).
            //canvas.fillRect(-10, -10, 20, 20, 0x80FF00FF);

            canvas.restore();
        }

        // Set the position of the tile view
        private void setTileViewPosition(float cx, float cy,
                int viewW, int viewH, float scale) {
            // Find out the bitmap coordinates of the center of the view
            int imageW = mPositionController.getImageWidth();
            int imageH = mPositionController.getImageHeight();
            int centerX = (int) (imageW / 2f + (viewW / 2f - cx) / scale + 0.5f);
            int centerY = (int) (imageH / 2f + (viewH / 2f - cy) / scale + 0.5f);

            int inverseX = imageW - centerX;
            int inverseY = imageH - centerY;
            int x, y;
            switch (mRotation) {
                case 0: x = centerX; y = centerY; break;
                case 90: x = centerY; y = inverseX; break;
                case 180: x = inverseX; y = inverseY; break;
                case 270: x = inverseY; y = centerX; break;
                default:
                    throw new RuntimeException(String.valueOf(mRotation));
            }
            mTileView.setPosition(x, y, scale, mRotation);
        }
    }

    private class ScreenNailPicture implements Picture {
        private int mIndex;
        private int mRotation;
        private ScreenNail mScreenNail;
        private boolean mIsVideo;
        private boolean mIsDeletable;
        private int mLoadingState = Model.LOADING_INIT;
        private Size mSize = new Size();

        public ScreenNailPicture(int index) {
            mIndex = index;
        }

        @Override
        public void reload() {
            mIsVideo = mModel.isVideo(mIndex);
            mIsDeletable = mModel.isDeletable(mIndex);
            mLoadingState = mModel.getLoadingState(mIndex);
            setScreenNail(mModel.getScreenNail(mIndex));
            updateSize();
        }

        @Override
        public Size getSize() {
            return mSize;
        }

        @Override
        public void draw(GLCanvas canvas, Rect r) {
            if (mScreenNail == null) {
                // Draw a placeholder rectange if there should be a picture in
                // this position (but somehow there isn't).
                if (mIndex >= mPrevBound && mIndex <= mNextBound) {
                    drawPlaceHolder(canvas, r);
                }
                return;
            }
            int w = getWidth();
            int h = getHeight();
            if (r.left >= w || r.right <= 0 || r.top >= h || r.bottom <= 0) {
                mScreenNail.noDraw();
                return;
            }

            float filmRatio = mPositionController.getFilmRatio();
            boolean wantsCardEffect = CARD_EFFECT && mIndex > 0
                    && filmRatio != 1f;
            boolean wantsOffsetEffect = OFFSET_EFFECT && mIsDeletable
                    && filmRatio == 1f && r.centerY() != h / 2;
            int cx = wantsCardEffect
                    ? (int) (interpolate(filmRatio, w / 2, r.centerX()) + 0.5f)
                    : r.centerX();
            int cy = r.centerY();
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX | GLCanvas.SAVE_FLAG_ALPHA);
            canvas.translate(cx, cy);
            if (wantsCardEffect) {
                float progress = (float) (w / 2 - r.centerX()) / w;
                progress = Utils.clamp(progress, -1, 1);
                float alpha = getScrollAlpha(progress);
                float scale = getScrollScale(progress);
                alpha = interpolate(filmRatio, alpha, 1f);
                scale = interpolate(filmRatio, scale, 1f);
                canvas.multiplyAlpha(alpha);
                canvas.scale(scale, scale, 1);
            } else if (wantsOffsetEffect) {
                float offset = (float) (r.centerY() - h / 2) / h;
                float alpha = getOffsetAlpha(offset);
                canvas.multiplyAlpha(alpha);
            }
            if (mRotation != 0) {
                canvas.rotate(mRotation, 0, 0, 1);
            }
            int drawW = getRotated(mRotation, r.width(), r.height());
            int drawH = getRotated(mRotation, r.height(), r.width());
            mScreenNail.draw(canvas, -drawW / 2, -drawH / 2, drawW, drawH);
            if (isScreenNailAnimating()) {
                invalidate();
            }
            int s = Math.min(drawW, drawH);
            if (mIsVideo) drawVideoPlayIcon(canvas, s);
            if (mLoadingState == Model.LOADING_FAIL) {
                drawLoadingFailMessage(canvas);
            }
            canvas.restore();
        }

        private boolean isScreenNailAnimating() {
            return (mScreenNail instanceof TiledScreenNail)
                    && ((TiledScreenNail) mScreenNail).isAnimating();
        }

        @Override
        public void setScreenNail(ScreenNail s) {
            mScreenNail = s;
        }

        private void updateSize() {
            mRotation = mModel.getImageRotation(mIndex);

            if (mScreenNail != null) {
                mSize.width = mScreenNail.getWidth();
                mSize.height = mScreenNail.getHeight();
            } else {
                // If we don't have ScreenNail available, we can still try to
                // get the size information of it.
                mModel.getImageSize(mIndex, mSize);
            }

            int w = mSize.width;
            int h = mSize.height;
            mSize.width = getRotated(mRotation, w, h);
            mSize.height = getRotated(mRotation, h, w);
        }
    }

    // Draw a gray placeholder in the specified rectangle.
    private void drawPlaceHolder(GLCanvas canvas, Rect r) {
        canvas.fillRect(r.left, r.top, r.width(), r.height(), mPlaceholderColor);
    }

    // Draw the video play icon (in the place where the spinner was)
    private void drawVideoPlayIcon(GLCanvas canvas, int side) {
        int s = side / ICON_RATIO;
        // Draw the video play icon at the center
        mVideoPlayIcon.draw(canvas, -s / 2, -s / 2, s, s);
    }

    // Draw the "no thumbnail" message
    private void drawLoadingFailMessage(GLCanvas canvas) {
        StringTexture m = mNoThumbnailText;
        m.draw(canvas, -m.getWidth() / 2, -m.getHeight() / 2);
    }

    private static int getRotated(int degree, int original, int theother) {
        return (degree % 180 == 0) ? original : theother;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Gestures Handling
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private class MyGestureListener implements GestureRecognizer.Listener {
        private boolean mIgnoreUpEvent = false;
        // If we can change mode for this scale gesture.
        private boolean mCanChangeMode;
        // If we have changed the film mode in this scaling gesture.
        private boolean mModeChanged;
        // If this scaling gesture should be ignored.
        private boolean mIgnoreScalingGesture;
        // whether the down action happened while the view is scrolling.
        private boolean mDownInScrolling;
        // If a scrolling has happened after a down gesture.
        private boolean mScrolledAfterDown;
        // If the first scrolling move is in X direction. In the film mode, X
        // direction scrolling is normal scrolling. but Y direction scrolling is
        // a delete gesture.
        private boolean mFirstScrollX;
        // The accumulated Y delta that has been sent to mPositionController.
        private int mDeltaY;
        // The accumulated scaling change from a scaling gesture.
        private float mAccScale;
        // If an onFling happened after the last onDown
        private boolean mHadFling;

        @Override
        public boolean onSingleTapUp(float x, float y) {
            // On crespo running Android 2.3.6 (gingerbread), a pinch out gesture results in the
            // following call sequence: onDown(), onUp() and then onSingleTapUp(). The correct
            // sequence for a single-tap-up gesture should be: onDown(), onSingleTapUp() and onUp().
            // The call sequence for a pinch out gesture in JB is: onDown(), then onUp() and there's
            // no onSingleTapUp(). Base on these observations, the following condition is added to
            // filter out the false alarm where onSingleTapUp() is called within a pinch out
            // gesture. The framework fix went into ICS. Refer to b/4588114.
            if (Build.VERSION.SDK_INT < ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH) {
                if ((mHolding & HOLD_TOUCH_DOWN) == 0) {
                    return true;
                }
            }

            // We do this in addition to onUp() because we want the snapback of
            // setFilmMode to happen.
            mHolding &= ~HOLD_TOUCH_DOWN;

            if (mFilmMode && !mDownInScrolling) {
                switchToHitPicture((int) (x + 0.5f), (int) (y + 0.5f));
            }

            if (mListener != null) {
                // Do the inverse transform of the touch coordinates.
                Matrix m = getGLRoot().getCompensationMatrix();
                Matrix inv = new Matrix();
                m.invert(inv);
                float[] pts = new float[] {x, y};
                inv.mapPoints(pts);
                mListener.onSingleTapUp((int) (pts[0] + 0.5f), (int) (pts[1] + 0.5f));
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            PositionController controller = mPositionController;
            float scale = controller.getImageScale();
            // onDoubleTap happened on the second ACTION_DOWN.
            // We need to ignore the next UP event.
            mIgnoreUpEvent = true;
//            if (scale <= .75f || controller.isAtMinimalScale()) {
//                controller.zoomIn(x, y, Math.max(1.0f, scale * 1.5f));
//			}
            if (scale < 1.0f)// || controller.isAtMinimalScale())
            {
                controller.zoomIn(x, y, 1.0f);
            } else {
                controller.resetToFullView();
            }
            return true;
        }

        @Override
        public boolean onScroll(float dx, float dy, float totalX, float totalY) {
            if (!mScrolledAfterDown) {
                mScrolledAfterDown = true;
                mFirstScrollX = (Math.abs(dx) > Math.abs(dy));
            }

            int dxi = (int) (-dx + 0.5f);
            int dyi = (int) (-dy + 0.5f);
            if (mFilmMode) {
                if (mFirstScrollX) {
                    mPositionController.scrollFilmX(dxi);
                } else {
                    if (mTouchBoxIndex == Integer.MAX_VALUE) return true;
                    int newDeltaY = calculateDeltaY(totalY);
                    int d = newDeltaY - mDeltaY;
                    if (d != 0) {
                        mPositionController.scrollFilmY(mTouchBoxIndex, d);
                        mDeltaY = newDeltaY;
                    }
                }
            } else {
                mPositionController.scrollPage(dxi, dyi);
            }
            return true;
        }

        private int calculateDeltaY(double delta) {
            // don't let items that can't be deleted be dragged more than
            // maxScrollDistance, and make it harder and harder to drag.
            int size = getHeight();
            float maxScrollDistance = 0.15f * size;
            if (Math.abs(delta) >= size) {
                delta = delta > 0 ? maxScrollDistance : -maxScrollDistance;
            } else {
                delta = maxScrollDistance *
                        Math.sin((delta / size) * (Math.PI / 2));
            }
            return (int) (delta + 0.5f);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mModeChanged) return true;
            if (swipeImages(velocityX, velocityY)) {
                mIgnoreUpEvent = true;
            } else {
                flingImages(velocityX, velocityY, Math.abs(e2.getY() - e1.getY()));
            }
            mHadFling = true;
            return true;
        }

        private boolean flingImages(float velocityX, float velocityY, float dY) {
            int vx = (int) (velocityX + 0.5f);
            int vy = (int) (velocityY + 0.5f);
            if (!mFilmMode) {
                return mPositionController.flingPage(vx, vy);
            }
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                return mPositionController.flingFilmX(vx);
            }

            // If we scrolled in Y direction fast enough, treat it as a delete
            // gesture.
            if (!mFilmMode || mTouchBoxIndex == Integer.MAX_VALUE) {
                return false;
            }

            if (!mIsPro)
                return false;

            int maxVelocity = GalleryUtils.dpToPixel(MAX_DISMISS_VELOCITY);
            int escapeVelocity = GalleryUtils.dpToPixel(SWIPE_ESCAPE_VELOCITY);
            int escapeDistance = GalleryUtils.dpToPixel(SWIPE_ESCAPE_DISTANCE);
            int centerY = mPositionController.getPosition(mTouchBoxIndex)
                    .centerY();
            boolean fastEnough = (Math.abs(vy) > escapeVelocity)
                    && (Math.abs(vy) > Math.abs(vx))
                    && ((vy > 0) == (centerY > getHeight() / 2))
                    && dY >= escapeDistance;
            if (fastEnough) {
                vy = Math.min(vy, maxVelocity);
                int duration = mPositionController.flingFilmY(mTouchBoxIndex, vy);
                if (duration >= 0) {
                    mPositionController.setPopFromTop(vy < 0);
                    delete();
                    // We reset mTouchBoxIndex, so up() won't check if Y
                    // scrolled far enough to be a delete gesture.
                    mTouchBoxIndex = Integer.MAX_VALUE;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            mPositionController.beginScale(focusX, focusY);
            // We can change mode if we are in film mode, or we are in page
            // mode and at minimal scale.
            mCanChangeMode = mFilmMode
                    || mPositionController.isAtMinimalScale();
            mAccScale = 1f;
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            if (mIgnoreScalingGesture) return true;
            if (mModeChanged) return true;
            if (Float.isNaN(scale) || Float.isInfinite(scale)) return false;

            int outOfRange = mPositionController.scaleBy(scale, focusX, focusY);

            // We wait for a large enough scale change before changing mode.
            // Otherwise we may mistakenly treat a zoom-in gesture as zoom-out
            // or vice versa.
            mAccScale *= scale;
            boolean largeEnough = (mAccScale < 0.97f || mAccScale > 1.03f);

            // If mode changes, we treat this scaling gesture has ended.
            if (mCanChangeMode && largeEnough) {
                if ((outOfRange < 0 && !mFilmMode) ||
                        (outOfRange > 0 && mFilmMode)) {
                    stopExtraScalingIfNeeded();

                    // Removing the touch down flag allows snapback to happen
                    // for film mode change.
                    mHolding &= ~HOLD_TOUCH_DOWN;
                    setFilmMode(!mFilmMode);

                    // We need to call onScaleEnd() before setting mModeChanged
                    // to true.
                    onScaleEnd();
                    mModeChanged = true;
                    return true;
                }
            }

            if (outOfRange != 0) {
                startExtraScalingIfNeeded();
            } else {
                stopExtraScalingIfNeeded();
            }
            return true;
        }

        @Override
        public void onScaleEnd() {
            if (mIgnoreScalingGesture) return;
            if (mModeChanged) return;
            mPositionController.endScale();
        }

        private void startExtraScalingIfNeeded() {
            if (!mCancelExtraScalingPending) {
                mHandler.sendEmptyMessageDelayed(
                        MSG_CANCEL_EXTRA_SCALING, 700);
                mPositionController.setExtraScalingRange(true);
                mCancelExtraScalingPending = true;
            }
        }

        private void stopExtraScalingIfNeeded() {
            if (mCancelExtraScalingPending) {
                mHandler.removeMessages(MSG_CANCEL_EXTRA_SCALING);
                mPositionController.setExtraScalingRange(false);
                mCancelExtraScalingPending = false;
            }
        }

        @Override
        public void onDown(float x, float y) {
            // checkHideUndoBar(UNDO_BAR_TOUCHED);

            mDeltaY = 0;
            mModeChanged = false;

            mHolding |= HOLD_TOUCH_DOWN;

            if (mFilmMode && mPositionController.isScrolling()) {
                mDownInScrolling = true;
                mPositionController.stopScrolling();
            } else {
                mDownInScrolling = false;
            }
            mHadFling = false;
            mScrolledAfterDown = false;
            if (mFilmMode) {
                int xi = (int) (x + 0.5f);
                int yi = (int) (y + 0.5f);
                // We only care about being within the x bounds, necessary for
                // handling very wide images which are otherwise very hard to fling
                mTouchBoxIndex = mPositionController.hitTest(xi, getHeight() / 2);
                if (mTouchBoxIndex < mPrevBound || mTouchBoxIndex > mNextBound) {
                    mTouchBoxIndex = Integer.MAX_VALUE;
                }
            } else {
                mTouchBoxIndex = Integer.MAX_VALUE;
            }
        }

        @Override
        public void onUp() {
            mHolding &= ~HOLD_TOUCH_DOWN;
            mEdgeView.onRelease();

            // If we scrolled in Y direction far enough, treat it as a delete
            // gesture.
            if (mFilmMode && mScrolledAfterDown && !mFirstScrollX
                    && mTouchBoxIndex != Integer.MAX_VALUE) {
                Rect r = mPositionController.getPosition(mTouchBoxIndex);
                int h = getHeight();
                if (Math.abs(r.centerY() - h * 0.5f) > 0.4f * h) {
                    int duration = mPositionController
                            .flingFilmY(mTouchBoxIndex, 0);
                    if (duration >= 0) {
                        mPositionController.setPopFromTop(r.centerY() < h * 0.5f);
                    }
                }
            }

            if (mIgnoreUpEvent) {
                mIgnoreUpEvent = false;
                return;
            }

            if (!(mFilmMode && !mHadFling && mFirstScrollX
                    && snapToNeighborImage())) {
            snapback();
            }
        }

		@Override
        public boolean onSingleTapConfirmed(float x, float y) {
            mListener.onSingleTapConfirmed();
            return false;
        }
    }


    public void setFilmMode(boolean enabled) {
        if (mFilmMode == enabled) return;
        mFilmMode = enabled;
        mPositionController.setFilmMode(mFilmMode);
        mModel.setNeedFullImage(!enabled);
        mListener.onFilmModeChanged(enabled);
    }

    public boolean getFilmMode() {
        return mFilmMode;
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Framework events
    ////////////////////////////////////////////////////////////////////////////

    public void pause() {
        mPositionController.skipAnimation();
        mTileView.freeTextures();
        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; i++) {
            mPictures.get(i).setScreenNail(null);
        }
        // hideUndoBar();
    }

    public void resume() {
        mTileView.prepareTextures();
        mPositionController.skipToFinalPosition();
    }

    // move to the camera preview and show controls after resume
    public void resetToFirstPicture() {
        mModel.moveTo(0);
        setFilmMode(false);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Rendering
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void render(GLCanvas canvas) {
        if (mFirst) {
            mPictures.get(0).reload();
        }

        if (mFirst) {
            mFirst = false;
        }

        // Determine how many photos we need to draw in addition to the center
        // one.
        int neighbors;

        // In page mode, we draw only one previous/next photo. But if we are
        // doing capture animation, we want to draw all photos.
        boolean inPageMode = (mPositionController.getFilmRatio() == 0f);
        boolean inCaptureAnimation =
                ((mHolding & HOLD_CAPTURE_ANIMATION) != 0);
        if (inPageMode && !inCaptureAnimation) {
            neighbors = 1;
        } else {
            neighbors = SCREEN_NAIL_MAX;
        }

        // Draw photos from back to front
        for (int i = neighbors; i >= -neighbors; i--) {
            Rect r = mPositionController.getPosition(i);
            mPictures.get(i).draw(canvas, r);
        }

        renderChild(canvas, mEdgeView);

        mPositionController.advanceAnimation();
        checkFocusSwitching();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Film mode focus switching
    ////////////////////////////////////////////////////////////////////////////

    // Runs in GL thread.
    private void checkFocusSwitching() {
        if (!mFilmMode) return;
        if (mHandler.hasMessages(MSG_SWITCH_FOCUS)) return;
        if (switchPosition() != 0) {
            mHandler.sendEmptyMessage(MSG_SWITCH_FOCUS);
        }
    }

    // Runs in main thread.
    private void switchFocus() {
        if (mHolding != 0) return;
        switch (switchPosition()) {
            case -1:
                switchToPrevImage();
                break;
            case 1:
                switchToNextImage();
                break;
        }
    }

    // Returns -1 if we should switch focus to the previous picture, +1 if we
    // should switch to the next, 0 otherwise.
    private int switchPosition() {
        Rect curr = mPositionController.getPosition(0);
        int center = getWidth() / 2;

        if (curr.left > center && mPrevBound < 0) {
            Rect prev = mPositionController.getPosition(-1);
            int currDist = curr.left - center;
            int prevDist = center - prev.right;
            if (prevDist < currDist) {
                return -1;
            }
        } else if (curr.right < center && mNextBound > 0) {
            Rect next = mPositionController.getPosition(1);
            int currDist = center - curr.right;
            int nextDist = next.left - center;
            if (nextDist < currDist) {
                return 1;
            }
        }

        return 0;
    }

    // Switch to the previous or next picture if the hit position is inside
    // one of their boxes. This runs in main thread.
    private void switchToHitPicture(int x, int y) {
        if (mPrevBound < 0) {
            Rect r = mPositionController.getPosition(-1);
            if (r.right >= x) {
                slideToPrevPicture();
                return;
            }
        }

        if (mNextBound > 0) {
            Rect r = mPositionController.getPosition(1);
            if (r.left <= x) {
                slideToNextPicture();
                return;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Page mode focus switching
    //
    //  We slide image to the next one or the previous one in two cases: 1: If
    //  the user did a fling gesture with enough velocity.  2 If the user has
    //  moved the picture a lot.
    ////////////////////////////////////////////////////////////////////////////

    private boolean swipeImages(float velocityX, float velocityY) {
        if (mFilmMode) return false;

        // Avoid swiping images if we're possibly flinging to view the
        // zoomed in picture vertically.
        PositionController controller = mPositionController;
        boolean isMinimal = controller.isAtMinimalScale();
        int edges = controller.getImageAtEdges();
        if (!isMinimal && Math.abs(velocityY) > Math.abs(velocityX))
            if ((edges & PositionController.IMAGE_AT_TOP_EDGE) == 0
                    || (edges & PositionController.IMAGE_AT_BOTTOM_EDGE) == 0)
                return false;

        // If we are at the edge of the current photo and the sweeping velocity
        // exceeds the threshold, slide to the next / previous image.
        if (velocityX < -SWIPE_THRESHOLD && (isMinimal
                || (edges & PositionController.IMAGE_AT_RIGHT_EDGE) != 0)) {
            return slideToNextPicture();
        } else if (velocityX > SWIPE_THRESHOLD && (isMinimal
                || (edges & PositionController.IMAGE_AT_LEFT_EDGE) != 0)) {
            return slideToPrevPicture();
        }

        return false;
    }

    private void snapback() {
        if ((mHolding & ~HOLD_DELETE) != 0) return;
        if (mFilmMode || !snapToNeighborImage()) {
            mPositionController.snapback();
        }
    }

    private boolean snapToNeighborImage() {
        Rect r = mPositionController.getPosition(0);
        int viewW = getWidth();
        // Setting the move threshold proportional to the width of the view
        int moveThreshold = viewW / 5 ;
        int threshold = moveThreshold + gapToSide(r.width(), viewW);

        // If we have moved the picture a lot, switching.
        if (viewW - r.right > threshold) {
            return slideToNextPicture();
        } else if (r.left > threshold) {
            return slideToPrevPicture();
        }

        return false;
    }

    private boolean slideToNextPicture() {
        if (mNextBound <= 0) return false;
        switchToNextImage();
        mPositionController.startHorizontalSlide();
        return true;
    }

    private boolean slideToPrevPicture() {
        if (mPrevBound >= 0) return false;
        switchToPrevImage();
        mPositionController.startHorizontalSlide();
        return true;
    }

    private static int gapToSide(int imageWidth, int viewWidth) {
        return Math.max(0, (viewWidth - imageWidth) / 2);
    }
	
    ////////////////////////////////////////////////////////////////////////////
    //  Focus switching
    ////////////////////////////////////////////////////////////////////////////

    public void switchToImage(int index) {
        mModel.moveTo(index);
    }

    private void switchToNextImage() {
        mModel.moveTo(mModel.getCurrentIndex() + 1);
    }

    private void switchToPrevImage() {
        mModel.moveTo(mModel.getCurrentIndex() - 1);
    }

    private void switchToFirstImage() {
        mModel.moveTo(0);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Card deck effect calculation
    ////////////////////////////////////////////////////////////////////////////

    // Returns the scrolling progress value for an object moving out of a
    // view. The progress value measures how much the object has moving out of
    // the view. The object currently displays in [left, right), and the view is
    // at [0, viewWidth].
    //
    // The returned value is negative when the object is moving right, and
    // positive when the object is moving left. The value goes to -1 or 1 when
    // the object just moves out of the view completely. The value is 0 if the
    // object currently fills the view.
    private static float calculateMoveOutProgress(int left, int right,
            int viewWidth) {
        // w = object width
        // viewWidth = view width
        int w = right - left;

        // If the object width is smaller than the view width,
        //      |....view....|
        //                   |<-->|      progress = -1 when left = viewWidth
        //          |<-->|               progress = 0 when left = viewWidth / 2 - w / 2
        // |<-->|                        progress = 1 when left = -w
        if (w < viewWidth) {
            int zx = viewWidth / 2 - w / 2;
            if (left > zx) {
                return -(left - zx) / (float) (viewWidth - zx);  // progress = (0, -1]
            } else {
                return (left - zx) / (float) (-w - zx);  // progress = [0, 1]
            }
        }

        // If the object width is larger than the view width,
        //             |..view..|
        //                      |<--------->| progress = -1 when left = viewWidth
        //             |<--------->|          progress = 0 between left = 0
        //          |<--------->|                          and right = viewWidth
        // |<--------->|                      progress = 1 when right = 0
        if (left > 0) {
            return -left / (float) viewWidth;
        }

        if (right < viewWidth) {
            return (viewWidth - right) / (float) viewWidth;
        }

        return 0;
    }

    // Maps a scrolling progress value to the alpha factor in the fading
    // animation.
    private float getScrollAlpha(float scrollProgress) {
        return scrollProgress < 0 ? mAlphaInterpolator.getInterpolation(
                     1 - Math.abs(scrollProgress)) : 1.0f;
    }

    // Maps a scrolling progress value to the scaling factor in the fading
    // animation.
    private float getScrollScale(float scrollProgress) {
        float interpolatedProgress = mScaleInterpolator.getInterpolation(
                Math.abs(scrollProgress));
        float scale = (1 - interpolatedProgress) +
                interpolatedProgress * TRANSITION_SCALE_FACTOR;
        return scale;
    }


    // This interpolator emulates the rate at which the perceived scale of an
    // object changes as its distance from a camera increases. When this
    // interpolator is applied to a scale animation on a view, it evokes the
    // sense that the object is shrinking due to moving away from the camera.
    private static class ZInterpolator {
        private float focalLength;

        public ZInterpolator(float foc) {
            focalLength = foc;
        }

        public float getInterpolation(float input) {
            return (1.0f - focalLength / (focalLength + input)) /
                (1.0f - focalLength / (focalLength + 1.0f));
        }
    }

    // Returns an interpolated value for the page/film transition.
    // When ratio = 0, the result is from.
    // When ratio = 1, the result is to.
    private static float interpolate(float ratio, float from, float to) {
        return from + (to - from) * ratio * ratio;
    }

    // Returns the alpha factor in film mode if a picture is not in the center.
    // The 0.03 lower bound is to make the item always visible a bit.
    private float getOffsetAlpha(float offset) {
        offset /= 0.5f;
        float alpha = (offset > 0) ? (1 - offset) : (1 + offset);
        return Utils.clamp(alpha, 0.03f, 1f);
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Simple public utilities
    ////////////////////////////////////////////////////////////////////////////

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setScaleListener(ScaleChangedListener listener) {
        mScaleListener = listener;
    }

	/**
     * Implements a slide animation on demand.  Will animate edge effects for last image.
     */
    public void goToNextPicture() {
        if (mNextBound <= 0) {
            mEdgeView.onPull(mBounds.right, EdgeView.RIGHT);
            return;
        }
        float lockedScale = mPositionController.getImageScale();
        int lockedPlatformX = mPositionController.getPlatformX();
        int lockedPlatformY = mPositionController.getPlatformY();
        int lockedBoxY = mPositionController.getBoxY();

        switchToNextImage();
        if (mZoomLocked)
            mPositionController.updateCurrentImage(lockedPlatformX, lockedPlatformY, lockedBoxY, lockedScale);
        else
            mPositionController.startHorizontalSlide();
    }

    /**
     * Implements a slide animation on demand.  Will animate edge effects for first image.
     */
    public void goToPrevPicture() {
        if (mPrevBound >= 0) {
            mEdgeView.onPull(mBounds.right, EdgeView.LEFT);
            return;
        }
        float lockedScale = mPositionController.getImageScale();
        int lockedPlatformX = mPositionController.getPlatformX();
        int lockedPlatformY = mPositionController.getPlatformY();
        int lockedBoxY = mPositionController.getBoxY();

        switchToPrevImage();

        if (mZoomLocked)
            mPositionController.updateCurrentImage(lockedPlatformX, lockedPlatformY, lockedBoxY, lockedScale);
        else
            mPositionController.startHorizontalSlide();
    }

    public void setZoomLock(boolean locked)
    {
        mZoomLocked = locked;
    }

    public void setLicenseState(License.LicenseState state)
    {
        mIsPro = state == License.LicenseState.pro;
    }
}
