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

package com.android.legacy.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.android.legacy.ui.UploadedTexture.DeadBitmapException;
import com.android.legacy.util.Utils;
import com.anthonymandra.rawdroid.LegacyViewerActivity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ImageViewer extends GLView
{
	public static final int SIZE_UNKNOWN = -1;

	private static final String TAG = "ImageViewer";

	// TILE_SIZE must be 2^N - 2. We put one pixel border in each side of the
	// texture to avoid seams between tiles.
	private static final int TILE_SIZE = 254;
	private static final int TILE_BORDER = 1;
	private static final int UPLOAD_LIMIT = 1;

	private static final int ENTRY_CURRENT = Model.INDEX_CURRENT;
	private static final int ENTRY_PREVIOUS = Model.INDEX_PREVIOUS;
	private static final int ENTRY_NEXT = Model.INDEX_NEXT;

	private static final int IMAGE_GAP = 64;
	private static final int SWITCH_THRESHOLD = 96;

	private static final int THRESHOLD_TO_DOWNSCALE = 480;

	// the previous/current/next image entries
	private final ScreenNailEntry mScreenNails[] = new ScreenNailEntry[3];

	private BitmapRegionDecoder mLargeBitmap;
	private int mLevelCount; // cache the value of mScaledBitmaps.length

	// The mLevel variable indicates which level of bitmap we should use.
	// Level 0 means the original full-sized bitmap, and a larger value means
	// a smaller scaled bitmap (The width and height of each scaled bitmap is
	// half size of the previous one). If the value is in [0, mLevelCount), we
	// use the bitmap in mScaledBitmaps[mLevel] for display, otherwise the value
	// is mLevelCount, and that means we use mBackupTexture for display.
	private int mLevel = 0;

	// The offsets of the (left, top) of the upper-left tile to the (left, top)
	// of the view.
	private int mOffsetX;
	private int mOffsetY;

	private int mUploadQuota;
	private boolean mRenderComplete;

	private final RectF mSourceRect = new RectF();
	private final RectF mTargetRect = new RectF();

	private final ScaleGestureDetector mScaleDetector;
	private final GestureDetector mGestureDetector;
	private final DownUpDetector mDownUpDetector;

	private final HashMap<Long, Tile> mActiveTiles = new HashMap<Long, Tile>();
	private Iterator<Tile> mUploadIter;

	private Tile mRecycledHead = null;

	// The width and height of the full-sized bitmap
	private int mImageWidth;
	private int mImageHeight;
	private int mViewWidth;
	private int mViewHeight;

	private int mCenterX;
	private int mCenterY;
	private float mScale;

	// Temp variables to avoid memory allocation
	private final Rect mTileRange = new Rect();
	private final Rect mActiveRange[] =
	{ new Rect(), new Rect() };

	private final Uploader mUploader = new Uploader();
	private final AnimationController mAnimationController;

	private Model mModel;
	private int mSwitchIndex;
	private int mRotation;
	private boolean rotated;

	private Context mContext;

	private boolean isTextureFreed = false;
	/* private */boolean mInTransition = false;
	private boolean mIgnoreUpEvent = false;

	private ScaleChangedListener mScaleChangedListener;

	public ImageViewer(Context context)
	{
		mContext = context;
		mGestureDetector = new GestureDetector(context, new MyGestureListener(), null);
		mScaleDetector = new ScaleGestureDetector(context, new MyScaleListener());
		mDownUpDetector = new DownUpDetector(new MyDownUpListener());

		if (context instanceof ScaleChangedListener)
		{
			mScaleChangedListener = (ScaleChangedListener) mContext;
		}

		mAnimationController = new AnimationController(this);

		for (int i = 0, n = mScreenNails.length; i < n; ++i)
		{
			mScreenNails[i] = new ScreenNailEntry();
		}
	}

	public void setRotation(int rotation)
	{
//		// Ghetto
//		if (mRotation != 0)
//		{
//			mRotation = -mRotation;
//			resetCurrentImagePosition();
//			notifyScreenNailInvalidated(ENTRY_CURRENT);
//		}
//
//		mRotation = rotation;
//		rotated = false;
//		resetCurrentImagePosition();
//		notifyScreenNailInvalidated(ENTRY_CURRENT);
//		// Maybe reset current image
	}

	public void setModel(Model model)
	{
		mModel = model;
		resetImages();

		resetCurrentImagePosition();
	}

	private void resetImages()
	{
		notifyScreenNailInvalidated(ENTRY_CURRENT);
		notifyScreenNailInvalidated(ENTRY_PREVIOUS);
		notifyScreenNailInvalidated(ENTRY_NEXT);

		notifyLargeBitmapInvalidated();
	}

	public void notifyLargeBitmapInvalidated()
	{
		mLargeBitmap = mModel.getLargeBitmap();
		Bitmap screennail = mScreenNails[ENTRY_CURRENT].mBitmap;
		int minLength = THRESHOLD_TO_DOWNSCALE;
		if (screennail != null)
		{
			minLength = Math.max(screennail.getWidth(), screennail.getHeight());
		}
		if (mLargeBitmap != null)
		{
			mLevelCount = calcuateLevelCount(mLargeBitmap.getWidth(), mLargeBitmap.getHeight(), minLength);
			layoutTiles(mCenterX, mCenterY, mScale, mRotation);
			invalidate();
		}
		else
		{
			mLevelCount = 0;
		}
	}

	public int calcuateLevelCount(int width, int height, int minLength)
	{
		int side = width < height ? width : height;
		return Math.max(0, ceilLog2((float) side / minLength));
	}

	// Not sure why I'd need this.
	public static Bitmap rotateToPortrait(Bitmap bitmap, int ori)
	{
		Matrix matrix = new Matrix();
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		// if (ori == ORI_ROTATE_90 || ori == ORI_ROTATE_270 || ori == ORI_TRANSPOSE || ori == ORI_TRANSVERSE)
		if (ori == 90 || ori == 270)// || ori == ORI_TRANSPOSE || ori == ORI_TRANSVERSE)
		{
			int tmp = w;
			w = h;
			h = tmp;
		}
		switch (ori)
		{
			case 90:// ORI_ROTATE_90:
				matrix.setRotate(90, w / 2f, h / 2f);
				break;
			case 180:// ORI_ROTATE_180:
				matrix.setRotate(180, w / 2f, h / 2f);
				break;
			case 270:// ORI_ROTATE_270:
				matrix.setRotate(270, w / 2f, h / 2f);
				break;
			// case ORI_FLIP_HOR:
			// matrix.preScale(-1, 1);
			// break;
			// case ORI_FLIP_VERT:
			// matrix.preScale(1, -1);
			// break;
			// case ORI_TRANSPOSE:
			// matrix.setRotate(90,w/2f,h/2f);
			// matrix.preScale(1, -1);
			// break;
			// case ORI_TRANSVERSE:
			// matrix.setRotate(270,w/2f,h/2f);
			// matrix.preScale(1, -1);
			// break;
			case 0:
			default:
				return bitmap;
		}

		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
	}

	public void notifyScreenNailInvalidated(int which)
	{
		ScreenNailEntry entry = mScreenNails[which];

		ImageData data = mModel.getImageData(which);
		if (data == null)
		{
			try
			{
				entry.set(0, 0, null);
			}
			catch (DeadBitmapException e)
			{
				Log.d(TAG, "DeadBitmapException", e);
				resetImages();
				return;
			}
		}
		else
		{
			try
			{
				entry.set(data.fullWidth, data.fullHeight, data.screenNail);
			}
			catch (DeadBitmapException e)
			{
				Log.d(TAG, "DeadBitmapException", e);
				resetImages();
				return;
			}
			if (which == ENTRY_CURRENT)
				resetCurrentImagePosition();
		}
		layoutScreenNails(mCenterX, mCenterY, mScale);
		if (entry.mVisible)
			invalidate();
	}

	private void resetCurrentImagePosition()
	{
		ScreenNailEntry entry = mScreenNails[ENTRY_CURRENT];
		mImageWidth = entry.mWidth;
		mImageHeight = entry.mHeight;
		// TODO: Why did I add this line when it's done in onNewImage?
		// mScaleMin = Math.min(1f, Math.min((float) mViewWidth / mImageWidth, (float) mViewHeight / mImageHeight));
		mAnimationController.onNewImage(mImageWidth, mImageHeight);
	}

	@Override
	protected boolean onTouch(MotionEvent event)
	{
		mGestureDetector.onTouchEvent(event);
		mScaleDetector.onTouchEvent(event);
		mDownUpDetector.onTouchEvent(event);
		return true;
	}

	private static int ceilLog2(float value)
	{
		int i;
		for (i = 0; i < 30; i++)
		{
			if ((1 << i) >= value)
				break;
		}
		return i;
	}

	@Override
	protected void onLayout(boolean changeSize, int l, int t, int r, int b)
	{
		mViewWidth = getWidth();
		mViewHeight = getHeight();
		if (changeSize)
		{
			mAnimationController.updateViewSize(mViewWidth, mViewHeight);
		}
	}

	private static int gapToSide(int imageWidth, int viewWidth, float scale)
	{
		return Math.max(0, Math.round(viewWidth - imageWidth * scale) / 2);
	}

	/*
	 * Here is how we layout the screen nails previous current next ___________ ________________ __________ | _______ | | __________ | | ______ | | |
	 * | | | | right->| | | | | | | | | |<--->| |<--left | | | | | | | |_______| | | | |__________| | | |______| | |___________| | |________________|
	 * |__________| | <--> gapToSide() | IMAGE_GAP
	 */
	private void layoutScreenNails(int centerX, int centerY, float scale)
	{
		int width = getWidth();
		int height = getHeight();

		int left = Math.round(width / 2 - centerX * scale);
		int right = Math.round(left + mImageWidth * scale);

		int gap = IMAGE_GAP + gapToSide(mImageWidth, width, scale);
		;

		// layout the previous image
		ScreenNailEntry entry = mScreenNails[ENTRY_PREVIOUS];
		entry.mVisible = left > gap && entry.mBitmap != null;
		if (entry.mBitmap != null)
		{
			float s = Math.min(1, Math.min((float) width / entry.mWidth, (float) height / entry.mHeight));
			entry.mDrawWidth = Math.round(entry.mWidth * s);
			entry.mDrawHeight = Math.round(entry.mHeight * s);
			entry.mX = left - gap - gapToSide(entry.mWidth, width, s) - entry.mDrawWidth;
			entry.mY = (height - entry.mDrawHeight) / 2;
			entry.mVisible = (entry.mX + entry.mDrawWidth) > 0;

		}
		else
		{
			entry.mVisible = false;
		}

		// layout the next image
		entry = mScreenNails[ENTRY_NEXT];
		if (entry.mBitmap != null)
		{
			float s = Utils
                    .clamp(Math.min((float) width / entry.mWidth, (float) height / entry.mHeight),
                            0, 2);
			entry.mDrawWidth = Math.round(entry.mWidth * s);
			entry.mDrawHeight = Math.round(entry.mHeight * s);
			entry.mX = right + gap + gapToSide(entry.mWidth, width, s);
			entry.mY = (height - entry.mDrawHeight) / 2;
			entry.mVisible = entry.mX < width;
		}
		else
		{
			entry.mVisible = false;
		}

		// Layout the current screen nail
		entry = mScreenNails[ENTRY_CURRENT];
		entry.mVisible = mLevel == mLevelCount && entry.mBitmap != null;
		if (entry.mVisible)
		{
			entry.mX = (int) (width / 2 - centerX * scale);
			entry.mY = (int) (height / 2 - centerY * scale);
			entry.mDrawWidth = Math.round(mImageWidth * scale);
			entry.mDrawHeight = Math.round(mImageHeight * scale);
		}
	}

	// Prepare the tiles we want to use for display.
	//
	// 1. Decide the tile level we want to use for display.
	// 2. Decide the tile levels we want to keep as texture (in addition to
	// the one we use for display).
	// 3. Recycle unused tiles.
	// 4. Activate the tiles we want.
	private void layoutTiles(int centerX, int centerY, float scale, int rotation)
	{

		// The width and height of this view.
		int width = getWidth();
		int height = getHeight();

		// The tile levels we want to keep as texture is in the range
		// [fromLevel, endLevel).
		int fromLevel;
		int endLevel;

		// We want to use a texture smaller than the display size to avoid
		// displaying artifacts.
		mLevel = Utils.clamp(ceilLog2(1f / scale), 0, mLevelCount);

		// We want to keep one more tile level as texture in addition to what
		// we use for display. So it can be faster when the scale moves to the
		// next level. We choose a level closer to the current scale.
		if (mLevel != mLevelCount)
		{
			Rect range = mTileRange;
			getRange(range, centerX, centerY, mLevel, scale, rotation);
			mOffsetX = Math.round(width / 2f + (range.left - centerX) * scale);
			mOffsetY = Math.round(height / 2f + (range.top - centerY) * scale);
			fromLevel = scale * (1 << mLevel) > 1.5f ? mLevel - 1 : mLevel;
		}
		else
		{
			// If mLevel == mLevelCount, we will use the backup texture for
			// display, so keep two smallest levels of tiles.
			fromLevel = mLevel - 2;
			// Pulled from TileImageView (not sure of need)
			// mOffsetX = Math.round(width / 2f - centerX * scale);
			// mOffsetY = Math.round(height / 2f - centerY * scale);
		}

		fromLevel = Math.max(fromLevel, 0);
		endLevel = Math.min(fromLevel + 2, mLevelCount);
		Rect range[] = mActiveRange;
		for (int i = fromLevel; i < endLevel; ++i)
		{
			getRange(range[i - fromLevel], centerX, centerY, i, rotation);
		}

		// Recycle unused tiles: if the level of the active tile is outside the
		// range [fromLevel, endLevel) or not in the visible range.
		Iterator<Map.Entry<Long, Tile>> iter = mActiveTiles.entrySet().iterator();
		while (iter.hasNext())
		{
			Tile tile = iter.next().getValue();
			int level = tile.mTileLevel;
			if (level < fromLevel || level >= endLevel || !range[level - fromLevel].contains(tile.mX, tile.mY))
			{
				iter.remove();
				recycleTile(tile);
			}
		}

		for (int i = fromLevel; i < endLevel; ++i)
		{
			int size = TILE_SIZE << i;
			Rect r = range[i - fromLevel];
			for (int y = r.top, bottom = r.bottom; y < bottom; y += size)
			{
				for (int x = r.left, right = r.right; x < right; x += size)
				{
					activateTile(x, y, i);
				}
			}
		}
		mUploadIter = mActiveTiles.values().iterator();
	}

	private void invalidateAllTiles()
	{
		Iterator<Map.Entry<Long, Tile>> iter = mActiveTiles.entrySet().iterator();
		while (iter.hasNext())
		{
			Tile tile = iter.next().getValue();
			recycleTile(tile);
		}
		mActiveTiles.clear();
	}

	private void getRange(Rect out, int cX, int cY, int level, int rotation)
	{
		getRange(out, cX, cY, level, 1f / (1 << (level + 1)), rotation);
	}

	// If the bitmap is scaled by the given factor "scale", return the
	// rectangle containing visible range. The left-top coordinate returned is
	// aligned to the tile boundary.
	//
	// (cX, cY) is the point on the original bitmap which will be put in the
	// center of the ImageViewer.
	private void getRange(Rect out, int cX, int cY, int level, float scale, int rotation)
	{
		double radians = Math.toRadians(-rotation);
		double w = getWidth();
		double h = getHeight();
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		int width = (int) Math.ceil(Math.max(Math.abs(cos * w - sin * h), Math.abs(cos * w + sin * h)));
		int height = (int) Math.ceil(Math.max(Math.abs(sin * w + cos * h), Math.abs(sin * w - cos * h)));

		int left = (int) Math.floor(cX - width / (2f * scale));
		int top = (int) Math.floor(cY - height / (2f * scale));
		int right = (int) Math.ceil(left + width / scale);
		int bottom = (int) Math.ceil(top + height / scale);

		// align the rectangle to tile boundary
		int size = TILE_SIZE << level;
		left = Math.max(0, size * (left / size));
		top = Math.max(0, size * (top / size));
		right = Math.min(mImageWidth, right);
		bottom = Math.min(mImageHeight, bottom);

		out.set(left, top, right, bottom);
	}

	public void setPosition(int centerX, int centerY, float scale)
	{
		mCenterX = centerX;
		mCenterY = centerY;
		mScale = scale;
		mScaleChangedListener.onScaleChanged(mScale);

		layoutTiles(centerX, centerY, scale, mRotation);
		layoutScreenNails(centerX, centerY, scale);
		invalidate();
	}

	private static class AnimationController
	{
		private long mAnimationStartTime = NO_ANIMATION;
		private static final long NO_ANIMATION = -1;
		private static final long LAST_ANIMATION = -2;

		// Animation time in milliseconds.
		private static final float ANIM_TIME_SCROLL = 0;
		private static final float ANIM_TIME_SCALE = 50;
		private static final float ANIM_TIME_SNAPBACK = 600;
		private static final float ANIM_TIME_SWITCHIMAGE = 400;

		private int mAnimationKind;
		private final static int ANIM_KIND_SCROLL = 0;
		private final static int ANIM_KIND_SCALE = 1;
		private final static int ANIM_KIND_SNAPBACK = 2;
		private final static int ANIM_KIND_SWITCHIMAGE = 800;

		private ImageViewer mViewer;
		private int mImageW, mImageH;
		private int mViewW, mViewH;

		// The X, Y are the coordinate on bitmap which shows on the center of
		// the view. We always keep the mCurrent{X,Y,SCALE} sync with the actual
		// values used currently.
		private int mCurrentX, mFromX, mToX;
		private int mCurrentY, mFromY, mToY;
		private float mCurrentScale, mFromScale, mToScale;

		// The offsets from the center of the view to the user's focus point,
		// converted to the bitmap domain.
		private float mPrevOffsetX;
		private float mPrevOffsetY;
		private boolean mInScale;

		// The limits for position and scale.
		private float mScaleMin, mScaleMax = 4f;

		AnimationController(ImageViewer viewer)
		{
			mViewer = viewer;
		}

		public void onNewImage(int width, int height)
		{
			mImageW = width;
			mImageH = height;

			mScaleMin = Math.min(1f, Math.min((float) mViewW / mImageW, (float) mViewH / mImageH));

			mCurrentScale = mScaleMin;
			mCurrentX = mImageW / 2;
			mCurrentY = mImageH / 2;

			mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
		}

		public void updateViewSize(int viewW, int viewH)
		{
			mViewW = viewW;
			mViewH = viewH;

			if (mImageW == 0 || mImageH == 0)
				return;

			mScaleMin = Math.min((float) viewW / mImageW, (float) viewH / mImageH);
			mScaleMin = Math.min(1f, mScaleMin);

			mCurrentScale = Utils.clamp(mCurrentScale, mScaleMin, mScaleMax);
			mCurrentX = mImageW / 2;
			mCurrentY = mImageH / 2;

			mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
		}

		public void scrollBy(float dx, float dy)
		{
			startAnimation(getTargetX() + Math.round(dx / mCurrentScale), getTargetY() + Math.round(dy / mCurrentScale), mCurrentScale, ANIM_KIND_SCROLL);
		}

		public void beginScale(float focusX, float focusY)
		{
			mInScale = true;
			mPrevOffsetX = (focusX - mViewW / 2f) / mCurrentScale;
			mPrevOffsetY = (focusY - mViewH / 2f) / mCurrentScale;
		}

		public void scaleBy(float s, float focusX, float focusY)
		{
			// The focus point should keep this position on the ImageView.
			// So, mCurrentX + mPrevOffsetX = mCurrentX' + offsetX.
			// mCurrentY + mPrevOffsetY = mCurrentY' + offsetY.
			float offsetX = (focusX - mViewW / 2f) / mCurrentScale;
			float offsetY = (focusY - mViewH / 2f) / mCurrentScale;
			startAnimation(getTargetX() - Math.round(offsetX - mPrevOffsetX), getTargetY() - Math.round(offsetY - mPrevOffsetY), getTargetScale() * s,
					ANIM_KIND_SCALE);
			mPrevOffsetX = offsetX;
			mPrevOffsetY = offsetY;
		}

		public void endScale()
		{
			mInScale = false;
			startSnapbackIfNeeded();
		}

		public void up()
		{
			startSnapbackIfNeeded();
		}

		public void startSwitchTransition(int targetX)
		{
			startAnimation(targetX, mCurrentY, mCurrentScale, ANIM_KIND_SWITCHIMAGE);
		}

		private void startAnimation(int centerX, int centerY, float scale, int kind)
		{
			if (centerX == mCurrentX && centerY == mCurrentY && scale == mCurrentScale)
			{
				return;
			}

			mFromX = mCurrentX;
			mFromY = mCurrentY;
			mFromScale = mCurrentScale;

			mToX = centerX;
			mToY = centerY;
			mToScale = Utils.clamp(scale, 0.6f * mScaleMin, 1.4f * mScaleMax);

			// If the scaled dimension is smaller than the view,
			// force it to be in the center.
			if (Math.floor(mImageH * mToScale) <= mViewH)
			{
				mToY = mImageH / 2;
			}

			mAnimationStartTime = SystemClock.uptimeMillis();
			mAnimationKind = kind;
			advanceAnimation();
		}

		// Returns true if redraw is needed.
		public boolean advanceAnimation()
		{
			if (mAnimationStartTime == NO_ANIMATION)
			{
				return false;
			}
			else if (mAnimationStartTime == LAST_ANIMATION)
			{
				mAnimationStartTime = NO_ANIMATION;
				if (mViewer.mInTransition)
				{
					mViewer.onTransitionComplete();
					return false;
				}
				else
				{
					return startSnapbackIfNeeded();
				}
			}

			float animationTime;
			if (mAnimationKind == ANIM_KIND_SCROLL)
			{
				animationTime = ANIM_TIME_SCROLL;
			}
			else if (mAnimationKind == ANIM_KIND_SCALE)
			{
				animationTime = ANIM_TIME_SCALE;
			}
			else if (mAnimationKind == ANIM_KIND_SWITCHIMAGE)
			{
				animationTime = ANIM_TIME_SWITCHIMAGE;
			}
			else
			/* if (mAnimationKind == ANIM_KIND_SNAPBACK) */{
				animationTime = ANIM_TIME_SNAPBACK;
			}

			float progress;
			if (animationTime == 0)
			{
				progress = 1;
			}
			else
			{
				long now = SystemClock.uptimeMillis();
				progress = (now - mAnimationStartTime) / animationTime;
			}

			if (progress >= 1)
			{
				progress = 1;
				mCurrentX = mToX;
				mCurrentY = mToY;
				mCurrentScale = mToScale;
				mAnimationStartTime = LAST_ANIMATION;
			}
			else
			{
				float f = 1 - progress;
				if (mAnimationKind == ANIM_KIND_SCROLL)
				{
					f = 1 - f; // linear
				}
				else if (mAnimationKind == ANIM_KIND_SCALE)
				{
					f = 1 - f * f; // quadratic
				}
				else
				/* if mAnimationKind is ANIM_KIND_SNAPBACK or ANIM_KIND_SWITCHIMAGE */{
					f = 1 - f * f * f * f * f; // x^5
				}
				mCurrentX = Math.round(mFromX + f * (mToX - mFromX));
				mCurrentY = Math.round(mFromY + f * (mToY - mFromY));
				mCurrentScale = mFromScale + f * (mToScale - mFromScale);
			}
			mViewer.setPosition(mCurrentX, mCurrentY, mCurrentScale);
			return true;
		}

		// Returns true if redraw is needed.
		private boolean startSnapbackIfNeeded()
		{
			if (mAnimationStartTime != NO_ANIMATION)
				return false;
			if (mInScale)
				return false;
			if (mAnimationKind == ANIM_KIND_SCROLL && mViewer.isDown())
			{
				return false;
			}

			boolean needAnimation = false;
			int x = mCurrentX;
			int y = mCurrentY;
			float scale = mCurrentScale;

			if (mCurrentScale < mScaleMin || mCurrentScale > mScaleMax)
			{
				needAnimation = true;
				scale = Utils.clamp(mCurrentScale, mScaleMin, mScaleMax);
			}

			// The number of pixels when the edge is aliged.
			int left = (int) Math.ceil(mViewW / (2 * scale));
			int right = mImageW - left;
			int top = (int) Math.ceil(mViewH / (2 * scale));
			int bottom = mImageH - top;

			if (mImageW * scale > mViewW)
			{
				if (mCurrentX < left)
				{
					needAnimation = true;
					x = left;
				}
				else if (mCurrentX > right)
				{
					needAnimation = true;
					x = right;
				}
			}
			else
			{
				if (mCurrentX > left)
				{
					needAnimation = true;
					x = left;
				}
				else if (mCurrentX < right)
				{
					needAnimation = true;
					x = right;
				}
			}

			if (mImageH * scale > mViewH)
			{
				if (mCurrentY < top)
				{
					needAnimation = true;
					y = top;
				}
				else if (mCurrentY > bottom)
				{
					needAnimation = true;
					y = bottom;
				}
			}
			else
			{
				if (mCurrentY > top)
				{
					needAnimation = true;
					y = top;
				}
				else if (mCurrentY < bottom)
				{
					needAnimation = true;
					y = bottom;
				}
			}

			if (needAnimation)
			{
				startAnimation(x, y, scale, ANIM_KIND_SNAPBACK);
			}

			return needAnimation;
		}

		private float getTargetScale()
		{
			if (mAnimationStartTime == NO_ANIMATION || mAnimationKind == ANIM_KIND_SNAPBACK)
				return mCurrentScale;
			return mToScale;
		}

		// public void setTargetScale(float scale)
		// {
		// mToScale = scale;
		// }

		private int getTargetX()
		{
			if (mAnimationStartTime == NO_ANIMATION || mAnimationKind == ANIM_KIND_SNAPBACK)
				return mCurrentX;
			return mToX;
		}

		private int getTargetY()
		{
			if (mAnimationStartTime == NO_ANIMATION || mAnimationKind == ANIM_KIND_SNAPBACK)
				return mCurrentY;
			return mToY;
		}
	}

	public void freeTextures()
	{
		isTextureFreed = true;
		mUploadIter = null;
		for (Tile texture : mActiveTiles.values())
		{
			texture.recycle();
		}
		mTileRange.set(0, 0, 0, 0);
		mActiveTiles.clear();
		freeRecycledTile();

		for (ScreenNailEntry nail : mScreenNails)
		{
			if (nail.mTexture != null)
				nail.mTexture.recycle();
			if (nail.mBitmap != null)
				nail.mBitmap.recycle();
		}
	}

	public void onResume()
	{
		resetImages();
		prepareTextures();
	}

	public void prepareTextures()
	{
		// To avoid resume exceptions we'll reset everything if any are
		// recycled.
		for (ScreenNailEntry entry : mScreenNails)
		{
			if (entry.mBitmap == null || entry.mBitmap.isRecycled() || entry.mTexture == null || entry.mTexture.mBitmap == null
					|| entry.mTexture.mBitmap.isRecycled())
			{
				resetImages();
				break;
			}
		}

		if (isTextureFreed)
		{
			layoutTiles(mCenterX, mCenterY, mScale, mRotation);
			isTextureFreed = false;
		}
	}

	@Override
	protected void render(GLCanvas canvas)
	{
		if (mScreenNails[ENTRY_CURRENT].mBitmap == null)
			return;
		// // Check if we have any recycled images
		// for (ScreenNailEntry entry : mScreenNails)
		// {
		// if (entry.mBitmap != null && entry.mBitmap.isRecycled())
		// {
		// resetImages();
		// break;
		// }
		// }
		// TODO: remove this line
		canvas.clearBuffer();

		mUploadQuota = UPLOAD_LIMIT;
		mRenderComplete = true;

		int level = mLevel;
//		int rotation = mRotation;
//		int flags = 0;
//		if (rotation != 0)
//			flags |= GLCanvas.SAVE_FLAG_MATRIX;
//
//		if (flags != 0)
//		{
//			canvas.save(flags);
//			if (rotation != 0 && !rotated)
//			{
//				rotated = true;
//				int centerX = getWidth() / 2, centerY = getHeight() / 2;
//				canvas.translate(centerX, centerY);
//				canvas.rotate(rotation, 0, 0, 1);
//				canvas.translate(-centerX, -centerY);
//			}
//		}

		if (level != mLevelCount)
		{
			int size = (TILE_SIZE << level);
			float length = size * mScale;
			Rect r = mTileRange;
			for (int ty = r.top, i = 0; ty < r.bottom; ty += size, i++)
			{
				float y = mOffsetY + i * length;
				for (int tx = r.left, j = 0; tx < r.right; tx += size, j++)
				{
					float x = mOffsetX + j * length;
					Tile tile = getTile(tx, ty, level);
					try
					{
						// Not sure how this happens, can it inf. loop?
						if (tile == null)
						{
							this.resetImages();
							return;
						}
						tile.drawTile(canvas, x, y, length);
					}
					catch (DeadBitmapException e)
					{
						Log.d(TAG, "DeadBitmapException", e);
						this.resetImages();
						return;
					}
				}
			}
		}

		for (ScreenNailEntry entry : mScreenNails)
		{
			if (entry.mVisible)
			{
				try
				{
					entry.draw(canvas);
				}
				catch (DeadBitmapException e)
				{
					resetImages();
					return;
				}
			}
		}

		if (mAnimationController.advanceAnimation())
		{
			mRenderComplete = false;
		}

		if (mRenderComplete)
		{
			if (mUploadIter != null && mUploadIter.hasNext() && !mUploader.mActive)
			{
				mUploader.mActive = true;
				getGLRoot().addOnGLIdleListener(mUploader);
			}
		}
		else
		{
			invalidate();
		}
	}

	private Tile obtainTile(int x, int y, int level)
	{
		Tile tile;
		if (mRecycledHead != null)
		{
			tile = mRecycledHead;
			mRecycledHead = tile.mNextFree;
			tile.update(x, y, level);
		}
		else
		{
			tile = new Tile(x, y, level);
		}
		return tile;
	}

	private void recycleTile(Tile tile)
	{
		tile.mNextFree = mRecycledHead;
		mRecycledHead = tile;
	}

	private void freeRecycledTile()
	{
		Tile tile = mRecycledHead;
		while (tile != null)
		{
			tile.recycle();
			tile = tile.mNextFree;
		}
		mRecycledHead = null;
	}

	private void activateTile(int x, int y, int level)
	{
		Long key = makeTileKey(x, y, level);
		Tile tile = mActiveTiles.get(key);
		if (tile != null)
			return;
		tile = obtainTile(x, y, level);
		mActiveTiles.put(key, tile);
	}

	private Tile getTile(int x, int y, int level)
	{
		return mActiveTiles.get(makeTileKey(x, y, level));
	}

	public static Long makeTileKey(int x, int y, int level)
	{
		long result = x;
		result = (result << 16) | y;
		result = (result << 16) | level;
		return Long.valueOf(result);
	}

	// TODO: avoid drawing the unused part of the textures.
	static boolean drawTile(Tile tile, GLCanvas canvas, RectF source, RectF target)
	{
		while (true)
		{
			if (tile.isContentValid(canvas))
			{
				// offset source rectangle for the texture border.
				source.offset(TILE_BORDER, TILE_BORDER);
				try
				{
					canvas.drawTexture(tile, source, target);
				}
				catch (DeadBitmapException e)
				{
					Log.d(TAG, "DeadBitmapException", e);
					// e.printStackTrace();
				}
				return true;
			}

			// Parent can be divided to four quads and tile is one of the four.
			Tile parent = tile.getParentTile();
			if (parent == null)
				return false;
			if (tile.mX == parent.mX)
			{
				source.left /= 2f;
				source.right /= 2f;
			}
			else
			{
				source.left = (TILE_SIZE + source.left) / 2f;
				source.right = (TILE_SIZE + source.right) / 2f;
			}
			if (tile.mY == parent.mY)
			{
				source.top /= 2f;
				source.bottom /= 2f;
			}
			else
			{
				source.top = (TILE_SIZE + source.top) / 2f;
				source.bottom = (TILE_SIZE + source.bottom) / 2f;
			}
			tile = parent;
		}
	}

	private class Uploader implements GLRoot.OnGLIdleListener
	{

		protected boolean mActive;

		public boolean onGLIdle(GLRoot root, GLCanvas canvas) throws DeadBitmapException
		{
			int quota = UPLOAD_LIMIT;

			if (mUploadIter == null)
				return false;
			Iterator<Tile> iter = mUploadIter;
			while (iter.hasNext() && quota > 0)
			{
				Tile tile = iter.next();
				if (!tile.isContentValid(canvas))
				{
					tile.updateContent(canvas);
					Log.v(TAG, String.format("update tile in background: %s %s %s", tile.mX / TILE_SIZE, tile.mY / TILE_SIZE, tile.mTileLevel));
					--quota;
				}
			}
			mActive = iter.hasNext();
			return mActive;
		}
	}

	private class Tile extends UploadedTexture
	{
		int mX;
		int mY;
		int mTileLevel;
		Tile mNextFree;

		public Tile(int x, int y, int level)
		{
			mX = x;
			mY = y;
			mTileLevel = level;
		}

		@Override
		protected void onFreeBitmap(Bitmap bitmap)
		{
			bitmap.recycle();
		}

		@Override
		protected Bitmap onGetBitmap() throws DeadBitmapException
		{
			if (mLargeBitmap == null || mLargeBitmap.isRecycled())
				throw new DeadBitmapException();

			// Get a tile from the original image.
			// The tile is down-scaled by (1 << mTilelevel) from a region in the
			// original image.
			int level = mTileLevel;
			int regionLength = (TILE_SIZE + 2 * TILE_BORDER) << level;
			int borderLength = TILE_BORDER << level;
			Rect rect = new Rect(0, 0, mLargeBitmap.getWidth(), mLargeBitmap.getHeight());

			// Get the intersected rect of the requested region and the image.
			boolean intersected = rect.intersect(mX - borderLength, mY - borderLength, mX + regionLength, mY + regionLength);

			Bitmap region = null;
			if (intersected)
			{
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 1 << level;
				// Matrix matrix = new Matrix();
				// matrix.setRotate(degrees, px, py)
				region = mLargeBitmap.decodeRegion(rect, options);
				// region = Bitmap.createBitmap(region, 0, 0, region.get)
				// Test to see what happens with rotate, I'm guessing nonsense.
				// It is nonsense because this is tile by tile of the original,
				// not sure how to rearrange this.
				// Matrix matrix = new Matrix();
				// matrix.postRotate(90);
				// region = Bitmap.createBitmap(region, 0, 0, region.getWidth(),
				// region.getHeight(), matrix, true);
				int targetLength = TILE_SIZE + 2 * TILE_BORDER;
				int left = rect.left == 0 ? TILE_BORDER : 0;
				int top = rect.top == 0 ? TILE_BORDER : 0;

				// The returned region may not match with the targetLength.
				// If so, we fill black pixels on it.
				if ((region.getWidth() != targetLength) || (region.getHeight() != targetLength) || left != 0 || top != 0)
				{
					Bitmap tile = Bitmap.createBitmap(targetLength, targetLength, region.hasAlpha() ? Config.ARGB_8888 : Config.RGB_565);
					Canvas canvas = new Canvas(tile);
					canvas.drawBitmap(region, left, top, null);
					region.recycle();
					return tile;
				}
			}
			else
			{
				throw new AssertionError("The requested tile is not within the image!");
			}
			return region;
		}

		public void update(int x, int y, int level)
		{
			mX = x;
			mY = y;
			mTileLevel = level;
			invalidateContent();
		}

		// Draw the tile to a square at canvas that locates at (x, y) and
		// has a side length of length.
		public void drawTile(GLCanvas canvas, float x, float y, float length) throws
                DeadBitmapException
		{
			RectF source = mSourceRect;
			RectF target = mTargetRect;
			target.set(x, y, x + length, y + length);
			source.set(0, 0, TILE_SIZE, TILE_SIZE);
			drawTile(canvas, source, target);
		}

		public Tile getParentTile()
		{
			if (mTileLevel + 1 == mLevelCount)
				return null;
			int size = TILE_SIZE << (mTileLevel + 1);
			int x = size * (mX / size);
			int y = size * (mY / size);
			return getTile(x, y, mTileLevel + 1);
		}

		// Draw the tile to target at canvas.
		public void drawTile(GLCanvas canvas, RectF source, RectF target) throws DeadBitmapException
		{
			if (!isContentValid(canvas))
			{
				if (mUploadQuota > 0)
				{
					--mUploadQuota;
					updateContent(canvas);
				}
				else
				{
					mRenderComplete = false;
				}
			}
			if (!ImageViewer.drawTile(this, canvas, source, target))
			{
				BitmapTexture backup = mScreenNails[ENTRY_CURRENT].mTexture;
				int width = mImageWidth;
				int height = mImageHeight;
				float scaleX = (float) backup.getWidth() / width;
				float scaleY = (float) backup.getHeight() / height;
				int size = TILE_SIZE << mTileLevel;

				source.set(mX * scaleX, mY * scaleY, (mX + size) * scaleX, (mY + size) * scaleY);

				canvas.drawTexture(backup, source, target);
			}
		}
	}

	private class MyGestureListener extends GestureDetector.SimpleOnGestureListener
	{
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy)
		{
			lockRendering();
			try
			{
				if (mInTransition)
					return true;
				mAnimationController.scrollBy(dx, dy);
			}
			finally
			{
				unlockRendering();
			}
			return true;
		}

		@Override
		public boolean onDoubleTap(MotionEvent e)
		{
			// onDoubleTap happened on the second ACTION_DOWN.
			// We need to ignore the next UP event.
			mIgnoreUpEvent = true;

			if (mScale < 1)
			{
				float imageOffsetX = Math.max(0, (mViewWidth - (mImageWidth * mScale)) / 2);
				float imageOffsetY = Math.max(0, (mViewHeight - (mImageHeight * mScale)) / 2);

				int centerX = (int) ((int) (e.getX() - imageOffsetX) / mScale);
				int centerY = (int) ((int) (e.getY() - imageOffsetY) / mScale);

				// Only accept zoom within image frame
				if (centerX >= 0 && centerY >= 0 && centerX <= mImageWidth && centerY <= mImageHeight)
				{
					mAnimationController.startAnimation(centerX, centerY, 1, AnimationController.ANIM_KIND_SCALE);
				}
			}
			else
			{
				// Reset the image
				notifyScreenNailInvalidated(ENTRY_CURRENT);
			}
			// mScaleChangedListener.onScaleChanged(mScale);
			return true;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e)
		{
			((LegacyViewerActivity) mContext).togglePanels();
			return true;
		}
	}

	private class MyScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
	{

		@Override
		public boolean onScale(ScaleGestureDetector detector)
		{
			float scale = detector.getScaleFactor();
			if (Float.isNaN(scale) || Float.isInfinite(scale) || mInTransition)
				return true;
			mAnimationController.scaleBy(scale, detector.getFocusX(), detector.getFocusY());
			// mScaleChangedListener.onScaleChanged(mScale);
			return true;
		}

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector)
		{
			if (mInTransition)
				return false;
			mAnimationController.beginScale(detector.getFocusX(), detector.getFocusY());
			// mScaleChangedListener.onScaleChanged(mScale);
			return true;
		}

		@Override
		public void onScaleEnd(ScaleGestureDetector detector)
		{
			mAnimationController.endScale();
			// mScaleChangedListener.onScaleChanged(mScale);
		}
	}

	private class MyDownUpListener implements DownUpDetector.DownUpListener
	{
		public void onDown(MotionEvent e)
		{
		}

		public void onUp(MotionEvent e)
		{
			if (mInTransition)
				return;
			if (mIgnoreUpEvent)
			{
				mIgnoreUpEvent = false;
				return;
			}

			ScreenNailEntry next = mScreenNails[ENTRY_NEXT];
			ScreenNailEntry prev = mScreenNails[ENTRY_PREVIOUS];

			int width = getWidth();

			int threshold = SWITCH_THRESHOLD + gapToSide(mImageWidth, width, mScale);
			int left = Math.round(width / 2 - mCenterX * mScale);
			int right = Math.round(left + mImageWidth * mScale);

			if (next.mBitmap != null && threshold < width - right)
			{
				mInTransition = true;
				mSwitchIndex = ENTRY_NEXT;
				float targetX = next.mX + next.mDrawWidth / 2;
				targetX = mImageWidth + (targetX - right) / mScale;
				mAnimationController.startSwitchTransition(Math.round(targetX));
			}
			else if (prev.mBitmap != null && threshold < left)
			{
				mInTransition = true;
				mSwitchIndex = ENTRY_PREVIOUS;
				float targetX = prev.mX + prev.mDrawWidth / 2;
				targetX = (targetX - left) / mScale;
				mAnimationController.startSwitchTransition(Math.round(targetX));
			}
			else
			{
				mAnimationController.up();
			}
		}
	}

	public void initiateTransition()
	{
		onTransitionComplete();
	}

	public void initiateDeleteTransition()
	{
		if (mModel == null)
			return;

		mInTransition = false;
		// ScreenNailEntry entry = mScreenNails[ENTRY_CURRENT];
		invalidateAllTiles();

		notifyScreenNailInvalidated(ENTRY_CURRENT);
		notifyLargeBitmapInvalidated();

		// resetCurrentImagePosition();
	}

	private void onTransitionComplete()
	{
		if (mModel == null)
			return;

		mInTransition = false;

		invalidateAllTiles();

		ScreenNailEntry screenNails[] = mScreenNails;

		if (mSwitchIndex == ENTRY_NEXT)
		{
			mModel.next();
		}
		else if (mSwitchIndex == ENTRY_PREVIOUS)
		{
			mModel.previous();
		}
		else
		{
			throw new AssertionError();
		}
		Utils.swap(screenNails, ENTRY_CURRENT, mSwitchIndex);
		Utils.swap(screenNails, ENTRY_PREVIOUS, ENTRY_NEXT);

		notifyScreenNailInvalidated(mSwitchIndex);
		notifyLargeBitmapInvalidated();

		resetCurrentImagePosition();
	}

	private boolean isDown()
	{
		return mDownUpDetector.isDown();
	}

	public static interface Model
	{
		public static final int INDEX_CURRENT = 1;
		public static final int INDEX_PREVIOUS = 0;
		public static final int INDEX_NEXT = 2;

		public void next();

		public void previous();

		// Return null if the specified image is unavailable.
		public ImageData getImageData(int which);

		public BitmapRegionDecoder getLargeBitmap();
	}

	public static class ImageData
	{
		public int fullWidth;
		public int fullHeight;
		public Bitmap screenNail;

		public ImageData(int width, int height, Bitmap screenNail)
		{
			fullWidth = width;
			fullHeight = height;
			this.screenNail = screenNail;
		}
	}

	private static class ScreenNailEntry
	{
		private int mWidth;
		private int mHeight;

		// if mBitmap is null then this entry is not valid
		private Bitmap mBitmap;
		private boolean mVisible;

		private int mX;
		private int mY;
		private int mDrawWidth;
		private int mDrawHeight;

		private BitmapTexture mTexture;

		public void set(int fullWidth, int fullHeight, Bitmap bitmap) throws DeadBitmapException
		{
			mWidth = fullWidth;
			mHeight = fullHeight;
			if (mBitmap != bitmap)
			{
				mBitmap = bitmap;
				if (mTexture != null)
					mTexture.recycle();
				// Not sure why I removed this if, otherwise it causes a deadbitmap on every change
				if (bitmap != null)
					mTexture = new BitmapTexture(bitmap);
				// mTexture = new BitmapTexture(bitmap);

			}
		}

		public void draw(GLCanvas canvas) throws DeadBitmapException
		{
			mTexture.draw(canvas, mX, mY, mDrawWidth, mDrawHeight);
		}
	}

	public interface ScaleChangedListener
	{
		public void onScaleChanged(float currentScale);
	}
}
