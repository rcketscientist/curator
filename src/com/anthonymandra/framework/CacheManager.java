package com.anthonymandra.framework;

import android.support.v4.app.FragmentManager;

public abstract class CacheManager
{
	@SuppressWarnings("unused")
	private static final String TAG = "CacheManager";

	protected ImageCache mImageCache;
	private ImageCache.ImageCacheParams mImageCacheParams;
	protected boolean mExitTasksEarly = false;
	protected boolean mPauseWork = false;
	protected final Object mPauseWorkLock = new Object();

	private static final int MESSAGE_CLEAR = 0;
	private static final int MESSAGE_INIT_DISK_CACHE = 1;
	private static final int MESSAGE_FLUSH = 2;
	private static final int MESSAGE_CLOSE = 3;

	protected CacheManager()
	{
	}

	/**
	 * Adds an {@link ImageCache} to this worker in the background (to prevent disk access on UI thread).
	 * 
	 * @param fragmentManager
	 * @param cacheParams
	 */
	public void addImageCache(FragmentManager fragmentManager, ImageCache.ImageCacheParams cacheParams)
	{
		mImageCacheParams = cacheParams;
		setImageCache(ImageCache.findOrCreateCache(fragmentManager, mImageCacheParams));
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	/**
	 * Sets the {@link ImageCache} object to use with this ImageWorker. Usually you will not need to call this directly, instead use
	 * {@link ImageWorker#addImageCache} which will create and add the {@link ImageCache} object in a background thread (to ensure no disk access on
	 * the main/UI thread).
	 * 
	 * @param imageCache
	 */
	public void setImageCache(ImageCache imageCache)
	{
		mImageCache = imageCache;
	}

	public void setExitTasksEarly(boolean exitTasksEarly)
	{
		mExitTasksEarly = exitTasksEarly;
	}

	public void setPauseWork(boolean pauseWork)
	{
		synchronized (mPauseWorkLock)
		{
			mPauseWork = pauseWork;
			if (!mPauseWork)
			{
				mPauseWorkLock.notifyAll();
			}
		}
	}

	protected class CacheAsyncTask extends AsyncTask<Object, Void, Void>
	{
		@Override
		protected Void doInBackground(Object... params)
		{
			switch ((Integer) params[0])
			{
				case MESSAGE_CLEAR:
					clearCacheInternal();
					break;
				case MESSAGE_INIT_DISK_CACHE:
					initDiskCacheInternal();
					break;
				case MESSAGE_FLUSH:
					flushCacheInternal();
					break;
				case MESSAGE_CLOSE:
					closeCacheInternal();
					break;
			}
			return null;
		}
	}

	protected void initDiskCacheInternal()
	{
		if (mImageCache != null)
		{
			mImageCache.initDiskCache();
		}
	}

	protected void clearCacheInternal()
	{
		if (mImageCache != null)
		{
			mImageCache.clearCache();
		}
	}

	protected void flushCacheInternal()
	{
		if (mImageCache != null)
		{
			mImageCache.flush();
		}
	}

	protected void closeCacheInternal()
	{
		if (mImageCache != null)
		{
			mImageCache.close();
			mImageCache = null;
		}
	}

	public void clearCache()
	{
		new CacheAsyncTask().execute(MESSAGE_CLEAR);
	}

	public void flushCache()
	{
		new CacheAsyncTask().execute(MESSAGE_FLUSH);
	}

	public void closeCache()
	{
		new CacheAsyncTask().execute(MESSAGE_CLOSE);
	}
}
