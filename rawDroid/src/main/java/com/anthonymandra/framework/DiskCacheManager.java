package com.anthonymandra.framework;

import android.app.FragmentManager;

public abstract class DiskCacheManager
{
	@SuppressWarnings("unused")
	private static final String TAG = "CacheManager";

	protected DiskCache mCache;
	private DiskCache.CacheParams mCacheParams;
	protected boolean mExitTasksEarly = false;
	protected boolean mPauseWork = false;
	protected final Object mPauseWorkLock = new Object();

	private static final int MESSAGE_CLEAR = 0;
	private static final int MESSAGE_INIT_DISK_CACHE = 1;
	private static final int MESSAGE_FLUSH = 2;
	private static final int MESSAGE_CLOSE = 3;

	protected DiskCacheManager()
	{
	}

	/**
	 * Adds an {@link ImageCache} to this worker in the background (to prevent disk access on UI thread).
	 * 
	 * @param fragmentManager
	 * @param cacheParams
	 */
	public void addImageCache(FragmentManager fragmentManager, DiskCache.CacheParams cacheParams)
	{
		mCacheParams = cacheParams;
		setCache(DiskCache.findOrCreateCache(fragmentManager, mCacheParams));
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	public void setCache(DiskCache diskCache)
	{
		mCache = diskCache;
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
		if (mCache != null)
		{
			mCache.initDiskCache();
		}
	}

	protected void clearCacheInternal()
	{
		if (mCache != null)
		{
			mCache.clearCache();
		}
	}

	protected void flushCacheInternal()
	{
		if (mCache != null)
		{
			mCache.flush();
		}
	}

	protected void closeCacheInternal()
	{
		if (mCache != null)
		{
			mCache.close();
			mCache = null;
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
