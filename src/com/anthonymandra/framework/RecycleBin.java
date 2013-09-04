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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

/**
 * This class holds our bitmap caches (memory and disk).
 */
public class RecycleBin
{
	private static final String TAG = RecycleBin.class.getSimpleName();
	private static final String pathReplacement = "~";
	private static final String spaceReplacement = "`";
	public static final String localPrefix = "file:~";
	public static final String mtpPrefix = "mtp:~";
	private static final int MESSAGE_CLEAR = 0;
	private static final int MESSAGE_INIT_DISK_CACHE = 1;
	private static final int MESSAGE_FLUSH = 2;
	private static final int MESSAGE_CLOSE = 3;

	// Default disk cache size
	private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 20; // 20MB
	private static final int DISK_CACHE_INDEX = 0;

	private DiskLruCache mDiskLruCache;
	private final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;
	private int mDiskCacheSize = DEFAULT_DISK_CACHE_SIZE;
	private File mDiskCacheDir;
	private List<ImageUpdateListener> listeners = new ArrayList<ImageUpdateListener>();

	/**
	 * Creating a new ImageCache object using the default parameters.
	 * 
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique name that will be appended to the cache directory
	 */
	public RecycleBin(Context context, String uniqueName, int maxSize)
	{
		mDiskCacheSize = maxSize;
		mDiskCacheDir = Util.getDiskCacheDir(context, uniqueName);
		initDiskCache();
	}

	/**
	 * {@link RecycleBin#addFile(RawObject)}
	 * 
	 * @param recycledItem
	 */
	private void addFileInternal(RawObject recycledItem)
	{
		if (recycledItem == null)
		{
			return;
		}

		synchronized (mDiskCacheLock)
		{
			// Add to disk cache
			final DiskLruCache bin = getDiskCache();
			if (bin != null)
			{
				final String key = fileToKey(recycledItem.getFilePath());
				BufferedOutputStream out = null;
				BufferedInputStream bis = null;
				try
				{
					DiskLruCache.Snapshot snapshot = bin.get(key);
					if (snapshot != null)
					{
						bin.remove(key);
					}
					final DiskLruCache.Editor editor = bin.edit(key);
					if (editor != null)
					{
						out = new BufferedOutputStream(editor.newOutputStream(DISK_CACHE_INDEX));
						bis = recycledItem.getImageInputStream();
						byte[] buffer = new byte[bis.available()];
						bis.read(buffer);
						out.write(buffer);
						editor.commit();
						recycledItem.delete();
					}
				}
				catch (final IOException e)
				{
					Log.e(TAG, "addFileToBin - " + e);
				}
				catch (Exception e)
				{
					Log.e(TAG, "addFileToBin - " + e);
				}
				finally
				{
					try
					{
						if (bis != null)
						{
							bis.close();
						}
						if (out != null)
						{
							out.close();
						}
					}
					catch (IOException e)
					{
					}
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
	public InputStream getFile(String data)
	{
		final String key = fileToKey(data);
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

			final DiskLruCache bin = getDiskCache();
			if (bin != null)
			{
				InputStream inputStream = null;
				try
				{
					final DiskLruCache.Snapshot snapshot = bin.get(key);
					if (snapshot != null)
					{
						return snapshot.getInputStream(DISK_CACHE_INDEX);
					}
				}
				catch (final IOException e)
				{
					Log.e(TAG, "getFileFromBin - " + e);
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

//	/**
//	 * A hashing method that changes a string (like a URL) into a hash suitable for using as a disk filename.
//	 */
//	public static String hashKeyForDisk(String key)
//	{
//		String cacheKey;
//		try
//		{
//			final MessageDigest mDigest = MessageDigest.getInstance("MD5");
//			mDigest.update(key.getBytes());
//			cacheKey = bytesToHexString(mDigest.digest());
//		}
//		catch (NoSuchAlgorithmException e)
//		{
//			cacheKey = String.valueOf(key.hashCode());
//		}
//		return cacheKey;
//	}
//
//	private static String bytesToHexString(byte[] bytes)
//	{
//		// http://stackoverflow.com/questions/332079
//		StringBuilder sb = new StringBuilder();
//		for (int i = 0; i < bytes.length; i++)
//		{
//			String hex = Integer.toHexString(0xFF & bytes[i]);
//			if (hex.length() == 1)
//			{
//				sb.append('0');
//			}
//			sb.append(hex);
//		}
//		return sb.toString();
//	}

	public static String keyToFile(String key)
	{
		return key.replaceAll(pathReplacement, File.separator).replaceAll(spaceReplacement, " ");
	}

	public static String fileToKey(String file)
	{
		return file.replaceAll(File.separator, pathReplacement).replaceAll(" ", spaceReplacement);
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

	public List<String> getKeys()
	{
		List<String> keys = new ArrayList<String>();
		final DiskLruCache bin = getDiskCache();
		if (bin == null)
		{
			return keys;
		}

		Set<String> coded = bin.getKeys();
		for (String key : coded)
		{
			keys.add(keyToFile(key));
		}
		return keys;
	}

	/**
	 * Returns the maximum size of the bin in bytes
	 * 
	 * @return
	 */
	public long getBinSize()
	{
		final DiskLruCache bin = getDiskCache();
		if (bin == null)
			return 0;
		return bin.maxSize();
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

	private DiskLruCache getDiskCache()
	{
		// It's not ideal to possibly call this in a UI thread, but it ensures we won't pull a null after resume
		if (mDiskLruCache == null)
			initDiskCacheInternal();
		return mDiskLruCache;
	}

	protected void initDiskCacheInternal()
	{
		// Set up disk cache
		synchronized (mDiskCacheLock)
		{
			if (mDiskLruCache == null || mDiskLruCache.isClosed())
			{
				File diskCacheDir = mDiskCacheDir;
				if (diskCacheDir != null)
				{
					if (!diskCacheDir.exists())
					{
						diskCacheDir.mkdirs();
					}
					if (Util.getUsableSpace(diskCacheDir) > mDiskCacheSize)
					{
						try
						{
							mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, mDiskCacheSize);
						}
						catch (final IOException e)
						{
							mDiskCacheDir = null;
							Log.e(TAG, "initDiskCache - " + e);
						}
					}
				}
			}
			mDiskCacheStarting = false;
			mDiskCacheLock.notifyAll();
		}
	}

	protected void clearCacheInternal()
	{
		if (mDiskLruCache != null)
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
	}

	/**
	 * Flushes the disk cache associated with this ImageCache object. Note that this includes disk access so this should not be executed on the
	 * main/UI thread.
	 */
	protected void flushCacheInternal()
	{
		if (mDiskLruCache != null)
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
	}

	/**
	 * Closes the disk cache associated with this ImageCache object. Note that this includes disk access so this should not be executed on the main/UI
	 * thread.
	 */
	protected void closeCacheInternal()
	{
		if (mDiskLruCache != null)
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
			mDiskLruCache = null;
		}
	}

	protected void removeFileInternal(String file)
	{
		try
		{
			final DiskLruCache bin = getDiskCache();
			if (bin != null)
				bin.remove(file);
		}
		catch (IOException e)
		{
			Log.e(TAG, e.toString());
			e.printStackTrace();
		}
	}

	public class AddFileTask extends AsyncTask<RawObject, Void, Void>
	{
		@Override
		protected Void doInBackground(RawObject... params)
		{
			addFileInternal(params[0]);
			return null;
		}
	}

	public class RemoveFileTask extends AsyncTask<String, Void, Void>
	{
		@Override
		protected Void doInBackground(String... params)
		{
			removeFileInternal(params[0]);
			return null;
		}
	}

	public void initDiskCache()
	{
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	/**
	 * Adds a file to the recycling bin, then finally deletes it. Operates on its an {@link AsyncTask} for file IO
	 * 
	 * @param recycledItem
	 *            File to recycle and delete
	 */
	public void addFile(RawObject recycledItem)
	{
		new AddFileTask().execute(recycledItem);
	}

	public void removeFile(String key)
	{
		new RemoveFileTask().execute(key);
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

	public void setOnImageUpdateListener(ImageUpdateListener listener)
	{
		this.listeners.add(listener);
	}
}
