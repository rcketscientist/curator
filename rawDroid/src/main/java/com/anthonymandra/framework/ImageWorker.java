/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.anthonymandra.framework;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.anthonymandra.rawprocessor.LibRaw;
import com.anthonymandra.widget.LoadingImageView;

import java.lang.ref.WeakReference;

/**
 * This class wraps up completing some arbitrary long running work when loading a bitmap to an ImageView. It handles things like using a memory and
 * disk cache, running the work in a background thread and setting a placeholder image.
 */
public abstract class ImageWorker extends CacheManager
{
	@SuppressWarnings("unused")
	private static final String TAG = ImageWorker.class.getSimpleName();
	// private static final int FADE_IN_TIME = 200;

	//TODO: Should i not store these?
	protected Bitmap mFolderBitmap;
	protected Bitmap mUnknownBitmap;
	// private boolean mFadeInBitmap = true;

	protected Resources mResources;

	protected ImageWorker(Context context)
	{
		mResources = context.getResources();
	}

	/**
	 * Load an image specified by the data parameter into an ImageView (override {@link ImageWorker#processBitmap(Object)} to define the processing
	 * logic). A memory and disk cache will be used if an {@link ImageCache} has been set using {@link ImageWorker#setImageCache(ImageCache)}. If the
	 * image is found in the memory cache, it is set immediately, otherwise an {@link AsyncTask} will be created to asynchronously load the bitmap.
	 * 
	 * @param image
	 *            The raw image to decode
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 */
	public void loadImage(RawObject image, LoadingImageView imageView)
	{
		imageView.setLoadingSpinner();
		if (image == null)
		{
			return;
		}

		Bitmap bitmap = null;

		if (mImageCache != null)
		{
			bitmap = mImageCache.getBitmapFromMemCache(String.valueOf(image.getUri()));// String.valueOf(image));
		}

		if (bitmap != null)
		{
			// Bitmap found in memory cache
			imageView.setImageBitmap(bitmap);
		}
		else if (cancelPotentialWork(image, imageView))
		{
//			Log.d(TAG, "DB:" + "Task created");
			final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(mResources, null/*mLoadingBitmap*/, task);
            imageView.setImageDrawable(asyncDrawable);

			// NOTE: This uses a custom version of AsyncTask that has been pulled from the
			// framework and slightly modified. Refer to the docs at the top of the class
			// for more info on what was changed.
			task.executeOnExecutor(LibRaw.EXECUTOR, image);
//			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, image);
		}
	}

	public void setFolderImage(int resId)
	{
		mFolderBitmap = BitmapFactory.decodeResource(mResources, resId);
	}

	public void setUnknownImage(int resId)
	{
		mUnknownBitmap = BitmapFactory.decodeResource(mResources, resId);
	}

	// /**
	// * If set to true, the image will fade-in once it has been loaded by the background thread.
	// */
	// public void setImageFadeIn(boolean fadeIn)
	// {
	// mFadeInBitmap = fadeIn;
	// }

	/**
	 * Cancels any pending work attached to the provided ImageView.
	 * 
	 * @param imageView
	 */
	public static void cancelWork(LoadingImageView imageView)
	{
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
		if (bitmapWorkerTask != null)
		{
			bitmapWorkerTask.cancel(true);
		}
	}

	/**
	 * Returns true if the current work has been canceled or if there was no work in progress on this image view. Returns false if the work in
	 * progress deals with the same data. The work is not stopped in that case.
	 */
	public static boolean cancelPotentialWork(RawObject data, LoadingImageView imageView)
	{
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

//		for (StackTraceElement ste : new Throwable().getStackTrace()) {
//			Log.d(TAG, "DB: " + ste.toString());
//		}
		
//		Log.d(TAG, "DB:" + "null =  " + (bitmapWorkerTask == null));
		if (bitmapWorkerTask != null)
		{
			final RawObject bitmapData = bitmapWorkerTask.data;
//			if (bitmapData != null)
//				Log.d(TAG, "DB:" + "current =  " + bitmapData.getUri());
//			else
//				Log.d(TAG, "DB:" + "bitmapData =  null");
//			Log.d(TAG, "DB:" + "request =  " + data.getUri());
			if (bitmapData == null || !bitmapData.getUri().equals(data.getUri()))
			{
//				Log.d(TAG, "DB:" + "cancel");
				bitmapWorkerTask.cancel(true);
			}
			else
			{
				// The same work is already in progress.
				return false;
			}
		}
		return true;
	}

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active work task (if any) associated with this imageView.
     * null if there is no such task.
     */
    private static BitmapWorkerTask getBitmapWorkerTask(LoadingImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

	/**
	 * Subclasses should override this to define any processing or work that must happen to produce the final bitmap. This will be executed in a
	 * background thread and be long running. For example, you could resize a large bitmap here, or pull down an image from the network.
	 * 
	 * @param data
	 *            The data to identify which image to process, as provided by {@link ImageWorker#loadImage(RawObject, com.anthonymandra.widget.LoadingImageView)}
	 * @return The processed bitmap
	 */
	protected abstract Bitmap processBitmap(Object data);

	/**
	 * The actual AsyncTask that will asynchronously process the image.
	 */
	public class BitmapWorkerTask extends AsyncTask<Object, Void, Bitmap>
	{
		private RawObject data;
		private final WeakReference<LoadingImageView> imageViewReference;

		public BitmapWorkerTask(LoadingImageView imageView)
		{
			imageViewReference = new WeakReference<>(imageView);
		}

		/**
		 * Background processing.
		 */
		@Override
		protected Bitmap doInBackground(Object... params)
		{
			data = (RawObject) params[0];
			final String dataString = String.valueOf(data.getUri());// String.valueOf(data);
			Bitmap bitmap = null;

			// Wait here if work is paused and the task is not cancelled
			synchronized (mPauseWorkLock)
			{
				while (mPauseWork && !isCancelled())
				{
					try
					{
						mPauseWorkLock.wait();
					}
					catch (InterruptedException e)
					{
					}
				}
			}

			// If the image cache is available and this task has not been cancelled by another
			// thread and the ImageView that was originally bound to this task is still bound back
			// to this task and our "exit early" flag is not set then try and fetch the bitmap from
			// the cache
			if (mImageCache != null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly)
			{
				bitmap = mImageCache.getBitmapFromDiskCache(dataString);
			}

			// If the bitmap was not found in the cache and this task has not been cancelled by
			// another thread and the ImageView that was originally bound to this task is still
			// bound back to this task and our "exit early" flag is not set, then call the main
			// process method (as implemented by a subclass)
			if (bitmap == null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly)
			{
				bitmap = processBitmap(data);
				// We don't want to cache the default images
				if (bitmap == mFolderBitmap || bitmap == mUnknownBitmap)
					return bitmap;
			}

			// If the bitmap was processed and the image cache is available, then add the processed
			// bitmap to the cache for future use. Note we don't check if the task was cancelled
			// here, if it was, and the thread is still running, we may as well add the processed
			// bitmap to our cache as it might be used again in the future
			if (bitmap != null && mImageCache != null)
			{
				mImageCache.addBitmapToCache(dataString, bitmap);
			}

			return bitmap;
		}

		/**
		 * Once the image is processed, associates it to the imageView
		 */
		@Override
		protected void onPostExecute(Bitmap bitmap)
		{
			// if cancel was called on this task or the "exit early" flag is set then we're done
			if (isCancelled() || mExitTasksEarly)
			{
				bitmap = null;
			}

			if (bitmap == null)
			{
				bitmap = mUnknownBitmap;
			}

			final LoadingImageView imageView = getAttachedImageView();
			if (imageView != null)
			{
				setImageBitmap(imageView, bitmap);
			}
		}

		@Override
		protected void onCancelled(Bitmap bitmap)
		{
			super.onCancelled(bitmap);
			synchronized (mPauseWorkLock)
			{
				mPauseWorkLock.notifyAll();
			}
		}

		/**
		 * Returns the ImageView associated with this task as long as the ImageView's task still points to this task as well. Returns null otherwise.
		 */
		private LoadingImageView getAttachedImageView()
		{
			final LoadingImageView imageView = imageViewReference.get();
			final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

			if (this == bitmapWorkerTask)
			{
				return imageView;
			}

			return null;
		}
	}
	
    /**
     * A custom Drawable that will be attached to the imageView while the work is in progress.
     * Contains a reference to the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can bind its result,
     * independently of the finish order.
     */
    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

	/**
	 * Called when the processing is complete and the final bitmap should be set on the ImageView.
	 * 
	 * @param imageView
	 * @param bitmap
	 */
	private void setImageBitmap(LoadingImageView imageView, Bitmap bitmap)
	{
		imageView.setImageBitmap(bitmap);
	}
}
