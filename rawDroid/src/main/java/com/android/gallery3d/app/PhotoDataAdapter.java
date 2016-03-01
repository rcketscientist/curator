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

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.ui.TiledScreenNail;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;
import com.anthonymandra.widget.RawModelLoader;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class PhotoDataAdapter implements Model {
    @SuppressWarnings("unused")
    private static final String TAG = "PhotoDataAdapter";

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;
    private static final int MSG_UPDATE_IMAGE_REQUESTS = 4;

    private static final int MIN_LOAD_COUNT = 16;
    private static final int DATA_CACHE_SIZE = 256;
    private static final int SCREEN_NAIL_MAX = PhotoView.SCREEN_NAIL_MAX;
    private static final int IMAGE_CACHE_SIZE = 2 * SCREEN_NAIL_MAX + 1;

    private static final int BIT_SCREEN_NAIL = 1;
    private static final int BIT_FULL_IMAGE = 2;

    // sImageFetchSeq is the fetching sequence for images.
    // We want to fetch the current screennail first (offset = 0), the next
    // screennail (offset = +1), then the previous screennail (offset = -1) etc.
    // After all the screennail are fetched, we fetch the full images (only some
    // of them because of we don't want to use too much memory).
    private static ImageFetch[] sImageFetchSeq;

    private static class ImageFetch {
        int indexOffset;
        int imageBit;
        public ImageFetch(int offset, int bit) {
            indexOffset = offset;
            imageBit = bit;
        }
    }

    static {
        int k = 0;
        sImageFetchSeq = new ImageFetch[1 + (IMAGE_CACHE_SIZE - 1) * 2 + 3];
        sImageFetchSeq[k++] = new ImageFetch(0, BIT_SCREEN_NAIL);

        for (int i = 1; i < IMAGE_CACHE_SIZE; ++i) {
            sImageFetchSeq[k++] = new ImageFetch(i, BIT_SCREEN_NAIL);
            sImageFetchSeq[k++] = new ImageFetch(-i, BIT_SCREEN_NAIL);
        }

        sImageFetchSeq[k++] = new ImageFetch(0, BIT_FULL_IMAGE);
        sImageFetchSeq[k++] = new ImageFetch(1, BIT_FULL_IMAGE);
        sImageFetchSeq[k++] = new ImageFetch(-1, BIT_FULL_IMAGE);
    }

    private final TileImageViewAdapter mTileProvider = new TileImageViewAdapter();

    // PhotoDataAdapter caches MediaItems (data) and ImageEntries (image).
    //
    // The MediaItems are stored in the mData array, which has DATA_CACHE_SIZE
    // entries. The valid index range are [mContentStart, mContentEnd). We keep
    // mContentEnd - mContentStart <= DATA_CACHE_SIZE, so we can use
    // (i % DATA_CACHE_SIZE) as index to the array.
    //
    // The valid MediaItem window size (mContentEnd - mContentStart) may be
    // smaller than DATA_CACHE_SIZE because we only update the window and reload
    // the MediaItems when there are significant changes to the window position
    // (>= MIN_LOAD_COUNT).
    private final Uri mData[] = new Uri[DATA_CACHE_SIZE];
    private int mContentStart = 0;
    private int mContentEnd = 0;

    // The ImageCache is a Path-to-ImageEntry map. It only holds the
    // ImageEntries in the range of [mActiveStart, mActiveEnd).  We also keep
    // mActiveEnd - mActiveStart <= IMAGE_CACHE_SIZE.  Besides, the
    // [mActiveStart, mActiveEnd) range must be contained within
    // the [mContentStart, mContentEnd) range.
    private HashMap<Uri, ImageEntry> mImageCache =
            new HashMap<>();
    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    // mCurrentIndex is the "center" image the user is viewing. The change of
    // mCurrentIndex triggers the data loading and image loading.
    private int mCurrentIndex;

    // mPaths keeps the corresponding Path (of MediaItem) for the images. This
    // is used to determine the item movement.
    private final Uri mPaths[] = new Uri[IMAGE_CACHE_SIZE];

    private final Handler mMainHandler;
    private final ThreadPool mThreadPool;

    private final PhotoView mPhotoView;
    private final List<Uri> mSource;

	/**
     * Stores image data so multiple resolver calls are unneeded
     */
    private final HashMap<Uri, ImageData> imageData = new HashMap<>();

    private class ImageData
    {
        public int width;
        public int height;
        public int orientation;
    }
    private ReloadTask mReloadTask;

    private Uri mItemPath;
    private boolean mIsActive;
    private boolean mNeedFullImage;

    private DataListener mDataListener;

    private final SourceListener mSourceListener = new SourceListener();
    private final TiledTexture.Uploader mUploader;

    private final GalleryApp mActivity;

//    private final Target initialImage = new Target()
//    {
//        @Override
//        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from)
//        {
//            ImageEntry startupImage = new ImageEntry();
//            startupImage.screenNail = new TiledScreenNail(bitmap);
//            startupImage.screenNailProcessed = true;
//            updateTileProvider(startupImage);
//        }
//
//        @Override
//        public void onBitmapFailed(Drawable errorDrawable)
//        {
//            return;
//        }
//
//        @Override
//        public void onPrepareLoad(Drawable placeHolderDrawable)
//        {
//            return;
//        }
//    };

    public PhotoDataAdapter(GalleryApp activity, PhotoView view,
        List<Uri> mediaSet, int position)
    {
		mActivity = activity;
        mSource = Utils.checkNotNull(mediaSet);
        mPhotoView = Utils.checkNotNull(view);
        mCurrentIndex = position;
        mThreadPool = activity.getThreadPool();
        mNeedFullImage = true;

        mUploader = new TiledTexture.Uploader(activity.getGLRoot());

        mMainHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @SuppressWarnings("unchecked")
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RUN_OBJECT:
                        ((Runnable) message.obj).run();
                        return;
                    case MSG_LOAD_START: {
                        if (mDataListener != null) {
                            mDataListener.onLoadingStarted();
                        }
                        return;
                    }
                    case MSG_LOAD_FINISH: {
                        if (mDataListener != null) {
                            mDataListener.onLoadingFinished(false);
                        }
                        return;
                    }
                    case MSG_UPDATE_IMAGE_REQUESTS: {
                        updateImageRequests();
                        return;
                    }
                    default: throw new AssertionError();
                }
            }
        };

        updateSlidingWindow();


    }

    private Uri getItemInternal(int index) {
        if (index < 0 || index >= getCount()) return null;
        if (index >= mContentStart && index < mContentEnd) {
            return mData[index % DATA_CACHE_SIZE];
        }
        return null;
    }

    private Uri getPath(int index) {
        return getItemInternal(index);
    }

    private void fireDataChange() {
        // Now calculate the fromIndex array. fromIndex represents the item
        // movement. It records the index where the picture come from. The
        // special value Integer.MAX_VALUE means it's a new picture.
        final int N = IMAGE_CACHE_SIZE;
        int fromIndex[] = new int[N];

        // Remember the old path array.
        Uri oldPaths[] = new Uri[N];
        System.arraycopy(mPaths, 0, oldPaths, 0, N);

        // Update the mPaths array.
        for (int i = 0; i < N; ++i) {
            mPaths[i] = getPath(mCurrentIndex + i - SCREEN_NAIL_MAX);
        }

        // Calculate the fromIndex array.
        for (int i = 0; i < N; i++) {
            Uri p = mPaths[i];
            if (p == null) {
                fromIndex[i] = Integer.MAX_VALUE;
                continue;
            }

            // Try to find the same path in the old array
            int j;
            for (j = 0; j < N; j++) {
                if (p.equals(oldPaths[j])) {
                    break;
                }
            }
            fromIndex[i] = (j < N) ? j - SCREEN_NAIL_MAX : Integer.MAX_VALUE;
        }

        mPhotoView.notifyDataChange(fromIndex, -mCurrentIndex,
                getCount() - 1 - mCurrentIndex);
    }

    public void setDataListener(DataListener listener) {
        mDataListener = listener;
    }

    private void updateScreenNail(Uri path, Future<ScreenNail> future) {
        ImageEntry entry = mImageCache.get(path);
        ScreenNail screenNail = future.get();

        if (entry == null || entry.screenNailTask != future) {
            if (screenNail != null) screenNail.recycle();
            return;
        }

        entry.screenNailTask = null;

        // Combine the ScreenNails if we already have a BitmapScreenNail
        if (entry.screenNail instanceof TiledScreenNail) {
            TiledScreenNail original = (TiledScreenNail) entry.screenNail;
            screenNail = original.combine(screenNail);
        }

        if (screenNail == null) {
            entry.failToLoad = true;
        } else {
            entry.failToLoad = false;
            entry.screenNail = screenNail;
        }

        for (int i = -SCREEN_NAIL_MAX; i <= SCREEN_NAIL_MAX; ++i) {
			if (path.equals(getPath(mCurrentIndex + i))) {
                if (i == 0) updateTileProvider(entry);
                mPhotoView.notifyImageChange(i);
                break;
            }
        }
        updateImageRequests();
        updateScreenNailUploadQueue();
    }

    private void updateFullImage(Uri path, Future<BitmapRegionDecoder> future) {
        ImageEntry entry = mImageCache.get(path);
        if (entry == null || entry.fullImageTask != future) {
            BitmapRegionDecoder fullImage = future.get();
            if (fullImage != null) fullImage.recycle();
            return;
        }

        entry.fullImageTask = null;
        entry.fullImage = future.get();
        if (entry.fullImage != null) {
            if (path.equals(getPath(mCurrentIndex))) {
                updateTileProvider(entry);
                mPhotoView.notifyImageChange(0);
            }
        }
        updateImageRequests();
    }

    @Override
    public void resume() {
        mIsActive = true;
        TiledTexture.prepareResources();

        mActivity.addContentListener(mSourceListener);
        updateImageCache();
        updateImageRequests();

        mReloadTask = new ReloadTask();
        mReloadTask.start();

//        SimpleTarget target = new SimpleTarget<Bitmap>(1000, 1000)
//        {
//            @Override
//            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation)
//            {
//                ImageEntry startupImage = new ImageEntry();
//                startupImage.screenNail = new TiledScreenNail(resource);
//                startupImage.screenNailProcessed = true;
//                updateTileProvider(startupImage);
//            }
//        };
//        Glide.with(mActivity.getAndroidContext())
//                .using(new RawModelLoader(mActivity.getAndroidContext()))
//                .load(mSource.get(mCurrentIndex))
//                .asBitmap()
//                .into(new SimpleTarget<Bitmap>(1000, 1000)
//                {
//                    @Override
//                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation)
//                    {
//                        ImageEntry startupImage = new ImageEntry();
//                        startupImage.screenNail = new TiledScreenNail(resource);
//                        startupImage.screenNailProcessed = true;
//                        updateTileProvider(startupImage);
//                    }
//                });

        fireDataChange();
    }

    @Override
    public void pause() {
        mIsActive = false;

        mReloadTask.terminate();
        mReloadTask = null;

        mActivity.removeContentListener(mSourceListener);

        for (ImageEntry entry : mImageCache.values()) {
            if (entry.fullImageTask != null) entry.fullImageTask.cancel();
            if (entry.screenNailTask != null) entry.screenNailTask.cancel();
            if (entry.screenNail != null) entry.screenNail.recycle();
        }
        mImageCache.clear();
        mTileProvider.clear();

        mUploader.clear();
        TiledTexture.freeResources();
    }

    private Uri getImage(int index) {
        if (index < 0 || index >= getCount() || !mIsActive) return null;
        Utils.assertTrue(index >= mActiveStart && index < mActiveEnd);

        if (index >= mContentStart && index < mContentEnd) {
            return mData[index % DATA_CACHE_SIZE];
        }
        return null;
    }

    private void updateCurrentIndex(int index) {
        if (mCurrentIndex == index) return;
        mCurrentIndex = index;
        updateSlidingWindow();

        mItemPath = mData[index % DATA_CACHE_SIZE];

        updateImageCache();
        updateImageRequests();
        updateTileProvider();

        if (mDataListener != null) {
            mDataListener.onPhotoChanged(index, mItemPath);
        }

        fireDataChange();
    }

    private void uploadScreenNail(int offset) {
        int index = mCurrentIndex + offset;
        if (index < mActiveStart || index >= mActiveEnd) return;

        Uri item = getImage(index);
        if (item == null) return;

        ImageEntry e = mImageCache.get(item);
        if (e == null) return;

        ScreenNail s = e.screenNail;
        if (s instanceof TiledScreenNail) {
            TiledTexture t = ((TiledScreenNail) s).getTexture();
            if (t != null && !t.isReady()) mUploader.addTexture(t);
        }
    }

    private void updateScreenNailUploadQueue() {
        mUploader.clear();
        uploadScreenNail(0);
        for (int i = 1; i < IMAGE_CACHE_SIZE; ++i) {
            uploadScreenNail(i);
            uploadScreenNail(-i);
        }
    }

    @Override
    public void moveTo(int index) {
        updateCurrentIndex(index);
    }

    @Override
    public ScreenNail getScreenNail(int offset) {
        int index = mCurrentIndex + offset;
        if (index < 0 || index >= getCount() || !mIsActive) return null;

//        Utils.assertTrue(index >= mActiveStart && index < mActiveEnd);
        if (!(index >= mActiveStart && index < mActiveEnd))
        {
            // Lets log this but attempt to allow adapter to continue
            Crashlytics.setInt("index", index);
            Crashlytics.setInt("mActiveStart", mActiveStart);
            Crashlytics.setInt("mActiveEnd", mActiveEnd);
            Crashlytics.logException(new Exception("Utils.assertTrue(index >= mActiveStart && index < mActiveEnd)"));
            Log.e(TAG, "Utils.assertTrue(index >= mActiveStart && index < mActiveEnd) [" + mActiveStart + "," + index + "," + mActiveEnd + "]");
            return null;
        }

        Uri item = getImage(index);
        if (item == null) return null;

        ImageEntry entry = mImageCache.get(item);
        if (entry == null) return null;

        // Create a default ScreenNail if the real one is not available yet,
        // except for camera that a black screen is better than a gray tile.
        if (entry.screenNail == null) {
            entry.screenNail = newPlaceholderScreenNail(item);
            if (offset == 0) updateTileProvider(entry);
        }

        return entry.screenNail;
    }

    @Override
    public void getImageSize(int offset, PhotoView.Size size) {
        Uri uri = getImage(mCurrentIndex + offset);
        if (uri == null)
            return;

        getImageSize(uri, size);
    }

    public void getImageSize(Uri uri, PhotoView.Size size)
    {
        if (!imageData.containsKey(uri))
        {
            populateImageData(uri);
        }

        size.height = imageData.get(uri).height;
        size.width = imageData.get(uri).width;
    }

    private void populateImageData(Uri uri)
    {
        Cursor c = getCursor(uri);
        if (c == null)
            return;

        ImageData data = new ImageData();
        data.height = c.getInt(c.getColumnIndex(Meta.Data.HEIGHT));
        data.width = c.getInt(c.getColumnIndex(Meta.Data.WIDTH));
        data.orientation = c.getInt(c.getColumnIndex(Meta.Data.ORIENTATION));

        imageData.put(uri, data);
        c.close();
    }

    private Cursor getCursor(Uri item)
    {
        if (item == null)
            return null;

        Cursor c = ImageUtils.getMetaCursor(mActivity.getAndroidContext(), item);

        return c.moveToFirst() ? c : null;
    }

    @Override
    public int getImageRotation(int offset)
    {
        Uri uri = getImage(mCurrentIndex + offset);
        if (uri == null)
            return 0;

        return getImageRotation(uri);
    }

    public int getImageRotation(Uri uri)
    {
        if (!imageData.containsKey(uri))
        {
            populateImageData(uri);
        }

        return ImageUtils.getRotation(imageData.get(uri).orientation);
    }

    @Override
    public void setNeedFullImage(boolean enabled) {
        mNeedFullImage = enabled;
        mMainHandler.sendEmptyMessage(MSG_UPDATE_IMAGE_REQUESTS);
    }

	@Override
	public boolean isVideo(int offset) {
		return false;
	}

	@Override
    public boolean isDeletable(int offset) {
		return false;
	}

    @Override
    public int getLoadingState(int offset) {
        ImageEntry entry = mImageCache.get(getPath(mCurrentIndex + offset));
        if (entry == null) return LOADING_INIT;
        if (entry.failToLoad) return LOADING_FAIL;
        if (entry.screenNail != null) return LOADING_COMPLETE;
        return LOADING_INIT;
    }

    @Override
    public ScreenNail getScreenNail() {
        return getScreenNail(0);
    }

	public Bitmap getCurrentBitmap()
	{
		ScreenNail sn = getScreenNail();
		if (sn instanceof TiledScreenNail)
			return ((TiledScreenNail) sn).getBitmap();
		else
			return null;
	}

    @Override
    public int getImageHeight() {
        return mTileProvider.getImageHeight();
    }

    @Override
    public int getImageWidth() {
        return mTileProvider.getImageWidth();
    }

    @Override
    public int getLevelCount() {
        return mTileProvider.getLevelCount();
    }

    @Override
    public Bitmap getTile(int level, int x, int y, int tileSize) {
        return mTileProvider.getTile(level, x, y, tileSize);
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    @Override
    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    @Override
	public Uri getMediaItem(int offset) {
		int index = mCurrentIndex + offset;
		if (index >= mContentStart && index < mContentEnd) {
			return mData[index % DATA_CACHE_SIZE];
		}
		return null;
	}

    @Override
	public void setCurrentPhoto(Uri path, int position) {
        if (mItemPath == path) return;
		mItemPath = path;
		mCurrentIndex = position;
		updateSlidingWindow();
		updateImageCache();
		fireDataChange();

		// We need to reload content if the path doesn't match.
		Uri item = getMediaItem(0);
		if (item != null && !item.equals(path)) {
            if (mReloadTask != null) mReloadTask.notifyDirty();
		}
	}

    @Override
    public Uri getCurrentItem()
    {
        return getMediaItem(0);
    }

    private void updateTileProvider() {
        ImageEntry entry = mImageCache.get(getPath(mCurrentIndex));
        if (entry == null) { // in loading
            mTileProvider.clear();
        } else {
            updateTileProvider(entry);
        }
    }

    private void updateTileProvider(ImageEntry entry) {
        ScreenNail screenNail = entry.screenNail;
        BitmapRegionDecoder fullImage = entry.fullImage;
        if (screenNail != null) {
            if (fullImage != null) {
                mTileProvider.setScreenNail(screenNail,
                        fullImage.getWidth(), fullImage.getHeight());
                mTileProvider.setRegionDecoder(fullImage);
            } else {
                int width = screenNail.getWidth();
                int height = screenNail.getHeight();
                mTileProvider.setScreenNail(screenNail, width, height);
            }
        } else {
            mTileProvider.clear();
        }
    }

    private void updateSlidingWindow() {
        // 1. Update the image window
        int start = Utils.clamp(mCurrentIndex - IMAGE_CACHE_SIZE / 2,
                0, Math.max(0, getCount() - IMAGE_CACHE_SIZE));
        int end = Math.min(getCount(), start + IMAGE_CACHE_SIZE);

        if (mActiveStart == start && mActiveEnd == end) return;

        mActiveStart = start;
        mActiveEnd = end;

        // 2. Update the data window
        start = Utils.clamp(mCurrentIndex - DATA_CACHE_SIZE / 2,
                0, Math.max(0, getCount() - DATA_CACHE_SIZE));
        end = Math.min(getCount(), start + DATA_CACHE_SIZE);
        if (mContentStart > mActiveStart || mContentEnd < mActiveEnd
                || Math.abs(start - mContentStart) > MIN_LOAD_COUNT) {
            for (int i = mContentStart; i < mContentEnd; ++i) {
                if (i < start || i >= end) {
                    mData[i % DATA_CACHE_SIZE] = null;
                }
            }
            mContentStart = start;
            mContentEnd = end;
            if (mReloadTask != null) mReloadTask.notifyDirty();
        }
    }

    private void updateImageRequests() {
        if (!mIsActive) return;

		int currentIndex = mCurrentIndex;
		Uri item = mData[currentIndex % DATA_CACHE_SIZE];
		if (item == null || !item.equals(mItemPath)) {
			// current item mismatch - don't request image
			return;
		}

		// 1. Find the most wanted request and start it (if not already started).
		Future<?> task = null;
        for (ImageFetch aSImageFetchSeq : sImageFetchSeq)
        {
            int offset = aSImageFetchSeq.indexOffset;
            int bit = aSImageFetchSeq.imageBit;
            if (bit == BIT_FULL_IMAGE && !mNeedFullImage) continue;
            task = startTaskIfNeeded(currentIndex + offset, bit);
            if (task != null) break;
        }

		// 2. Cancel everything else.
        for (ImageEntry entry : mImageCache.values()) {
            if (entry.screenNailTask != null && entry.screenNailTask != task) {
				entry.screenNailTask.cancel();
				entry.screenNailTask = null;
                entry.screenNailProcessed = false;
			}
            if (entry.fullImageTask != null && entry.fullImageTask != task) {
				entry.fullImageTask.cancel();
				entry.fullImageTask = null;
                entry.fullImageProcessed = false;
			}
		}
	}

    private class ScreenNailJob implements Job<ScreenNail> {
		private Uri mItem;

		public ScreenNailJob(Uri item) {
			mItem = item;
		}

		@Override
        public ScreenNail run(JobContext jc) {
			Bitmap bitmap = ImageUtils.requestImage(mActivity, MediaItem.TYPE_THUMBNAIL, mItem).run(jc);
            if (jc.isCancelled()) return null;

//            if (bitmap != null) {
// TODO: Technically the rotations cancel out and the viewer still orients properly, so wtf...just dropping this altogether
//                bitmap = BitmapUtils.rotateBitmap(bitmap,
//                        mItem.getRotation() - mItem.getFullImageRotation(), true);
//                bitmap = BitmapUtils.rotateBitmap(bitmap, 0/*getImageRotation(mItem)*/, true);
//            }
            return bitmap == null ? null : new TiledScreenNail(bitmap);
        }
    }

    private class FullImageJob implements Job<BitmapRegionDecoder> {
		private Uri mItem;

		public FullImageJob(Uri item)	{
			mItem = item;
		}

		@Override
        public BitmapRegionDecoder run(JobContext jc) {
            return ImageUtils.requestLargeImage(mActivity.getAndroidContext(), mItem).run(jc);
        }
    }

    // Create a default ScreenNail when a ScreenNail is needed, but we don't yet
    // have one available (because the image data is still being saved, or the
    // Bitmap is still being loaded.
    private ScreenNail newPlaceholderScreenNail(Uri item) {
        PhotoView.Size s = new PhotoView.Size();
        getImageSize(item, s);
        int width = s.width;
        int height = s.height;
        return new TiledScreenNail(width, height);
    }

    // Returns the task if we started the task or the task is already started.
    private Future<?> startTaskIfNeeded(int index, int which) {
        if (index < mActiveStart || index >= mActiveEnd) return null;

        ImageEntry entry = mImageCache.get(getPath(index));
        if (entry == null) return null;
		Uri item = mData[index % DATA_CACHE_SIZE];
		Utils.assertTrue(item != null);

        if (which == BIT_SCREEN_NAIL && entry.screenNailTask != null
                && entry.screenNailProcessed) {
            return entry.screenNailTask;
        } else if (which == BIT_FULL_IMAGE && entry.fullImageTask != null
                && entry.fullImageProcessed
                ) {
            return entry.fullImageTask;
        }

        if (which == BIT_SCREEN_NAIL && !entry.screenNailProcessed) {
            entry.screenNailProcessed = true;
            entry.screenNailTask = mThreadPool.submit(
                    new ScreenNailJob(item),
                    new ScreenNailListener(item));
            // request screen nail
            return entry.screenNailTask;
        }
        // We assume it's supported
        if (which == BIT_FULL_IMAGE && !entry.fullImageProcessed ) {
            entry.fullImageProcessed = true;
            entry.fullImageTask = mThreadPool.submit(
                    new FullImageJob(item),
                    new FullImageListener(item));
			// request full image
			return entry.fullImageTask;
		}
		return null;
	}

	private void updateImageCache()	{
        HashSet<Uri> toBeRemoved = new HashSet<>(mImageCache.keySet());
        for (int i = mActiveStart; i < mActiveEnd; ++i) {
            Uri path = mData[i % DATA_CACHE_SIZE];
            if (path == null) continue;

			ImageEntry entry = mImageCache.get(path);
			toBeRemoved.remove(path);
            if (entry != null) {
                if (Math.abs(i - mCurrentIndex) > 1) {
                    if (entry.fullImageTask != null) {
                        entry.fullImageTask.cancel();
                        entry.fullImageTask = null;
                    }
                    entry.fullImage = null;
					entry.fullImageProcessed = false;
				}
                if (!entry.screenNailProcessed) {
                    // This ScreenNail is outdated, we want to update it if it's
                    // still a placeholder.
                    if (entry.screenNail instanceof TiledScreenNail) {
                        TiledScreenNail s = (TiledScreenNail) entry.screenNail;
//						s.updatePlaceholderSize(
//                                item.getThumbWidth(), item.getThumbHeight());
                        PhotoView.Size size = new PhotoView.Size();
                        getImageSize(path, size);
						s.updatePlaceholderSize(size.width, size.height);
					}
				}
            } else {
				entry = new ImageEntry();
				mImageCache.put(path, entry);
			}
		}

		// Clear the data and requests for ImageEntries outside the new window.
		for (Uri path : toBeRemoved) {
			ImageEntry entry = mImageCache.remove(path);
            if (entry.fullImageTask != null) entry.fullImageTask.cancel();
            if (entry.screenNailTask != null) entry.screenNailTask.cancel();
            if (entry.screenNail != null) entry.screenNail.recycle();
		}
        updateScreenNailUploadQueue();
	}

    private class FullImageListener
            implements Runnable, FutureListener<BitmapRegionDecoder> {
		private final Uri mPath;
		private Future<BitmapRegionDecoder> mFuture;

		public FullImageListener(Uri item) {
			mPath = item;
		}

        @Override
        public void onFutureDone(Future<BitmapRegionDecoder> future) {
            mFuture = future;
            mMainHandler.sendMessage(
                    mMainHandler.obtainMessage(MSG_RUN_OBJECT, this));
        }

        @Override
        public void run() {
            updateFullImage(mPath, mFuture);
        }
    }

    private class ScreenNailListener
            implements Runnable, FutureListener<ScreenNail> {
		private final Uri mPath;
		private Future<ScreenNail> mFuture;

		public ScreenNailListener(Uri item) {
			mPath = item;
		}

        @Override
        public void onFutureDone(Future<ScreenNail> future) {
            mFuture = future;
            mMainHandler.sendMessage(
                    mMainHandler.obtainMessage(MSG_RUN_OBJECT, this));
        }

        @Override
        public void run() {
            updateScreenNail(mPath, mFuture);
        }
    }

    private static class ImageEntry {
		public BitmapRegionDecoder fullImage;
		public ScreenNail screenNail;
		public Future<ScreenNail> screenNailTask;
		public Future<BitmapRegionDecoder> fullImageTask;
        public boolean screenNailProcessed;
        public boolean fullImageProcessed;
		public boolean failToLoad = false;
	}

    private class SourceListener implements ContentListener {
        @Override
        public void onContentDirty() {
            if (mReloadTask != null) mReloadTask.notifyDirty();
        }
    }

    private <T> T executeAndWait(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        mMainHandler.sendMessage(
                mMainHandler.obtainMessage(MSG_RUN_OBJECT, task));
        try {
            return task.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static class UpdateInfo {
//		public long version;
		public boolean reloadContent;
		public Uri target;
		public int contentStart;
		public int contentEnd;

		public int size;
		public ArrayList<Uri> items;
	}

    private class GetUpdateInfo implements Callable<UpdateInfo> {

        private boolean needContentReload() {
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                if (mData[i % DATA_CACHE_SIZE] == null) return true;
            }
			Uri current = mData[mCurrentIndex % DATA_CACHE_SIZE];
			return current == null || !current.equals(mItemPath);
		}

		@Override
        public UpdateInfo call() throws Exception {
            // TODO: Try to load some data in first update
            UpdateInfo info = new UpdateInfo();
            info.reloadContent = needContentReload();
            info.target = mItemPath;
            info.contentStart = mContentStart;
            info.contentEnd = mContentEnd;
            info.size = getCount();
            return info;
        }
    }

    private class UpdateContent implements Callable<Void> {
        UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo updateInfo) {
            mUpdateInfo = updateInfo;
        }

        @Override
        public Void call() throws Exception {
            UpdateInfo info = mUpdateInfo;

            if (info.size != getCount()) {
                if (mContentEnd > getCount()) mContentEnd = getCount();
                if (mActiveEnd > getCount()) mActiveEnd = getCount();
            }

            updateSlidingWindow();

            if (info.items != null) {
                int start = Math.max(info.contentStart, mContentStart);
                int end = Math.min(info.contentStart + info.items.size(), mContentEnd);
                int dataIndex = start % DATA_CACHE_SIZE;
                for (int i = start; i < end; ++i) {
                    mData[dataIndex] = info.items.get(i - info.contentStart);
                    if (++dataIndex == DATA_CACHE_SIZE) dataIndex = 0;
                }
            }

            mItemPath = mData[mCurrentIndex % DATA_CACHE_SIZE];

			updateImageCache();
			updateTileProvider();
			updateImageRequests();

            if (mDataListener != null) {
                mDataListener.onPhotoChanged(mCurrentIndex, mItemPath);
            }

			fireDataChange();
			return null;
		}
	}

	public ArrayList<Uri> getMediaItem(int start, int count)
	{
		ArrayList<Uri> result = new ArrayList<>();

		for (int i = start; i <= start + count; i++)
		{
			if (i >= 0 && i < getCount())
			{
				result.add(mSource.get(i));
			}
		}

		return result;
	}

    /*
     * The thread model of ReloadTask
     *      *
     * [Reload Task]       [Main Thread]
     *       |                   |
     * getUpdateInfo() -->       |           (synchronous call)
     *     (wait) <----    getUpdateInfo()
     *       |                   |
     *   Load Data               |
     *       |                   |
     * updateContent() -->       |           (synchronous call)
     *     (wait)          updateContent()
     *       |                   |
     *       |                   |
     */
    private class ReloadTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;

        private boolean mIsLoading = false;

        private void updateLoading(boolean loading) {
            if (mIsLoading == loading) return;
            mIsLoading = loading;
            mMainHandler.sendEmptyMessage(loading ? MSG_LOAD_START : MSG_LOAD_FINISH);
        }

        @Override
        public void run() {
            while (mActive) {
                synchronized (this) {
                    if (!mDirty && mActive) {
                        updateLoading(false);
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }

                mDirty = false;
                UpdateInfo info = executeAndWait(new GetUpdateInfo());
				updateLoading(true);

				info.items = getMediaItem(
					info.contentStart, info.contentEnd);

                executeAndWait(new UpdateContent(info));
            }
		}

        public synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
	}

    private int getCount()
    {
        return mSource.size();
    }
}
