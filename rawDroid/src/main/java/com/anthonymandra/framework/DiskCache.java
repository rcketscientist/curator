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

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This class holds our bitmap caches (memory and disk).
 */
public class DiskCache
{
	private static final String TAG = "ImageCache";

	// Default disk cache size
	private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 25; // 25MB

	// Compression settings when writing images to disk cache
	private static final CompressFormat DEFAULT_COMPRESS_FORMAT = CompressFormat.JPEG;
	private static final int DEFAULT_COMPRESS_QUALITY = 70;
	private static final int DISK_CACHE_INDEX = 0;

	// Constants to easily toggle various caches
	private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;
	private static final boolean DEFAULT_CLEAR_DISK_CACHE_ON_START = false;
	private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE = false;

	private DiskLruCache mDiskLruCache;
	private CacheParams mCacheParams;
	private final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;

	/**
	 * Creating a new ImageCache object using the specified parameters.
	 *
	 * @param cacheParams
	 *            The cache parameters to use to initialize the cache
	 */
	public DiskCache(CacheParams cacheParams)
	{
		init(cacheParams);
	}

	/**
	 * Creating a new ImageCache object using the default parameters.
	 *
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique name that will be appended to the cache directory
	 */
	public DiskCache(Context context, String uniqueName)
	{
		init(new CacheParams(context, uniqueName));
	}

	/**
	 * Find and return an existing ImageCache stored in a {@link RetainFragment}, if not found a new one is created using the supplied params and
	 * saved to a {@link RetainFragment}.
	 *
	 * @param fragmentManager
	 *            The fragment manager to use when dealing with the retained fragment.
	 * @param cacheParams
	 *            The cache parameters to use if creating the ImageCache
	 * @return An existing retained ImageCache object or a new one if one did not exist
	 */
	public static DiskCache findOrCreateCache(FragmentManager fragmentManager, CacheParams cacheParams)
	{

		// Search for, or create an instance of the non-UI RetainFragment
		final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

		// See if we already have an ImageCache stored in RetainFragment
		DiskCache diskCache = (DiskCache) mRetainFragment.getObject();

		// No existing ImageCache, create one and store it in RetainFragment
		if (diskCache == null)
		{
			diskCache = new DiskCache(cacheParams);
			mRetainFragment.setObject(diskCache);
		}

		return diskCache;
	}

	/**
	 * Initialize the cache, providing all parameters.
	 *
	 * @param cacheParams
	 *            The cache parameters to initialize the cache
	 */
	private void init(CacheParams cacheParams)
	{
		mCacheParams = cacheParams;

		// By default the disk cache is not initialized here as it should be initialized
		// on a separate thread due to disk access.
		if (cacheParams.initDiskCacheOnCreate)
		{
			// Set up disk cache
			initDiskCache();
		}
	}

	/**
	 * Initializes the disk cache. Note that this includes disk access so this should not be executed on the main/UI thread. By default an ImageCache
	 * does not initialize the disk cache when it is created, instead you should call initDiskCache() to initialize it on a background thread.
	 */
	public void initDiskCache()
	{
		// Set up disk cache
		synchronized (mDiskCacheLock)
		{
			if (mDiskLruCache == null || mDiskLruCache.isClosed())
			{
				File diskCacheDir = mCacheParams.diskCacheDir;
				if (mCacheParams.diskCacheEnabled && diskCacheDir != null)
				{
					if (!diskCacheDir.exists())
					{
						diskCacheDir.mkdirs();
					}
					if (FileUtil.getUsableSpace(diskCacheDir) > mCacheParams.diskCacheSize)
					{
						try
						{
							mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, mCacheParams.diskCacheSize);
						}
						catch (final IOException e)
						{
							mCacheParams.diskCacheDir = null;
							Log.e(TAG, "initDiskCache - " + e);
						}
					}
				}
			}
			mDiskCacheStarting = false;
			mDiskCacheLock.notifyAll();
		}
	}

	/**
	 * Adds a bitmap to both memory and disk cache.
	 *
	 * @param data
	 *            Unique identifier for the bitmap to store
	 * @param bitmap
	 *            The bitmap to store
	 */
	public void addBitmapToCache(String data, Bitmap bitmap)
	{
		if (data == null || bitmap == null)
		{
			return;
		}

		synchronized (mDiskCacheLock)
		{
			// Add to disk cache
			if (mDiskLruCache != null)
			{
				final String key = hashKeyForDisk(data);
				OutputStream out = null;
				try
				{
					DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
					if (snapshot == null)
					{
						final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
						if (editor != null)
						{
							out = editor.newOutputStream(DISK_CACHE_INDEX);
							bitmap.compress(mCacheParams.compressFormat, mCacheParams.compressQuality, out);
							editor.commit();
							out.close();
						}
					}
					else
					{
						snapshot.getInputStream(DISK_CACHE_INDEX).close();
					}
				}
				catch (final IOException e)
				{
					Log.e(TAG, "addBitmapToCache - " + e);
				}
				finally
				{
					Utils.closeSilently(out);
				}
			}
		}
	}

	/**
	 * Get from disk cache.
	 *
	 * @param data
	 *            Unique identifier for which item to get
	 * @return The bitmap if found in cache, null otherwise
	 */
	public InputStream getStreamFromDiskCache(String data)
	{
		final String key = hashKeyForDisk(data);
		synchronized (mDiskCacheLock)
		{
			while (mDiskCacheStarting)
			{
				try
				{
					mDiskCacheLock.wait();
				}
				catch (InterruptedException e)
				{
				}
			}
			if (mDiskLruCache != null)
			{
				InputStream inputStream = null;
				try
				{
					final DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
					if (snapshot != null)
					{
						return snapshot.getInputStream(DISK_CACHE_INDEX);
					}
				}
				catch (final IOException e)
				{
					Log.e(TAG, "getBitmapFromDiskCache - " + e);
				}
				finally
				{
					try
					{
						if (inputStream != null)
						{
							inputStream.close();
						}
					}
					catch (IOException e)
					{
					}
				}
			}
			return null;
		}
	}

	/**
	 * Clears both the memory and disk cache associated with this ImageCache object. Note that this includes disk access so this should not be
	 * executed on the main/UI thread.
	 */
	public void clearCache()
	{
		synchronized (mDiskCacheLock)
		{
			mDiskCacheStarting = true;
			if (mDiskLruCache != null && !mDiskLruCache.isClosed())
			{
				try
				{
					mDiskLruCache.delete();
				}
				catch (IOException e)
				{
					Log.e(TAG, "clearCache - " + e);
				}
				mDiskLruCache = null;
				initDiskCache();
			}
		}
	}

	/**
	 * Flushes the disk cache associated with this ImageCache object. Note that this includes disk access so this should not be executed on the
	 * main/UI thread.
	 */
	public void flush()
	{
		synchronized (mDiskCacheLock)
		{
			if (mDiskLruCache != null)
			{
				try
				{
					mDiskLruCache.flush();
				}
				catch (IOException e)
				{
					Log.e(TAG, "flush - " + e);
				}
			}
		}
	}

	/**
	 * Closes the disk cache associated with this ImageCache object. Note that this includes disk access so this should not be executed on the main/UI
	 * thread.
	 */
	public void close()
	{
		synchronized (mDiskCacheLock)
		{
			if (mDiskLruCache != null)
			{
				try
				{
					if (!mDiskLruCache.isClosed())
					{
						mDiskLruCache.close();
						mDiskLruCache = null;
					}
				}
				catch (IOException e)
				{
					Log.e(TAG, "close - " + e);
				}
			}
		}
	}

	/**
	 * A holder class that contains cache parameters.
	 */
	public static class CacheParams
	{
		public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;
		public File diskCacheDir;
		public CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
		public int compressQuality = DEFAULT_COMPRESS_QUALITY;
		public boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;
		public boolean clearDiskCacheOnStart = DEFAULT_CLEAR_DISK_CACHE_ON_START;
		public boolean initDiskCacheOnCreate = DEFAULT_INIT_DISK_CACHE_ON_CREATE;

		public CacheParams(Context context, String uniqueName)
		{
			diskCacheDir = FileUtil.getDiskCacheDir(context, uniqueName);
		}

		public CacheParams(File diskCacheDir)
		{
			this.diskCacheDir = diskCacheDir;
		}
	}

	/**
	 * A hashing method that changes a string (like a URL) into a hash suitable for using as a disk filename.
	 */
	public static String hashKeyForDisk(String key)
	{
		String cacheKey;
		try
		{
			final MessageDigest mDigest = MessageDigest.getInstance("MD5");
			mDigest.update(key.getBytes());
			cacheKey = bytesToHexString(mDigest.digest());
		}
		catch (NoSuchAlgorithmException e)
		{
			cacheKey = String.valueOf(key.hashCode());
		}
		return cacheKey;
	}

	private static String bytesToHexString(byte[] bytes)
	{
		// http://stackoverflow.com/questions/332079
		StringBuilder sb = new StringBuilder();
		for (byte aByte : bytes)
		{
			String hex = Integer.toHexString(0xFF & aByte);
			if (hex.length() == 1)
			{
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	/**
	 * Locate an existing instance of this Fragment or if not found, create and add it using FragmentManager.
	 *
	 * @param fm
	 *            The FragmentManager manager to use.
	 * @return The existing instance of the Fragment or the new instance if just created.
	 */
	public static RetainFragment findOrCreateRetainFragment(FragmentManager fm)
	{
		// Check to see if we have retained the worker fragment.
		RetainFragment mRetainFragment = (RetainFragment) fm.findFragmentByTag(TAG);

		// If not retained (or first time running), we need to create and add it.
		if (mRetainFragment == null)
		{
			mRetainFragment = new RetainFragment();
			fm.beginTransaction().add(mRetainFragment, TAG).commitAllowingStateLoss();
		}

		return mRetainFragment;
	}

	/**
	 * A simple non-UI Fragment that stores a single Object and is retained over configuration changes. It will be used to retain the ImageCache
	 * object.
	 */
	public static class RetainFragment extends Fragment
	{
		private Object mObject;

		/**
		 * Empty constructor as per the Fragment documentation
		 */
		public RetainFragment()
		{
		}

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			// Make sure this Fragment is retained over a configuration change
			setRetainInstance(true);
		}

		/**
		 * Store a single object in this Fragment.
		 *
		 * @param object
		 *            The object to store
		 */
		public void setObject(Object object)
		{
			mObject = object;
		}

		/**
		 * Get the stored object.
		 *
		 * @return The stored object
		 */
		public Object getObject()
		{
			return mObject;
		}
	}

}
