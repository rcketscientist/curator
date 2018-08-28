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
import android.net.Uri;
import androidx.annotation.Nullable;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.util.FileUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This class holds our discarded images
 */
public class RecycleBin
{
	private static final String TAG = RecycleBin.class.getSimpleName();
	// TODO: Single char replacements are not sufficient
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

	private Context mContext;
	private DiskLruCache mDiskLruCache;
	private final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;
	private int mDiskCacheSize = DEFAULT_DISK_CACHE_SIZE;
	private File mDiskCacheDir;

	/**
	 * Create new recycling bin with the default parameters.
	 * 
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique name that will be appended to the cache directory
	 */
	public RecycleBin(Context context, String uniqueName, int maxSize)
	{
		mContext = context;
		mDiskCacheSize = maxSize;
		mDiskCacheDir = FileUtil.getDiskCacheDir(context, uniqueName);
		initDiskCache();
	}

	/**
	 * Deletes the given file.  This should be overridden if necessary to handle file system changes.
	 * @param toDelete file to delete
	 * @return success
	 * @throws IOException the base method does not throw, but the exception is available for subclasses
	 */
	protected boolean deleteFile(Uri toDelete) throws IOException
	{
		// This will likely fail due to write permission on any device needing to use this method.
		// Either handle write permission separately or,
		// Overload with a call to handle requesting write permission if needed.
		UsefulDocumentFile df = UsefulDocumentFile.fromUri(mContext, toDelete);
		return df.delete();
	}

	/**
	 * Adds a file to the recycling bin synchronously.  Recommended to be called asynchronously,
	 * such as with {@link #addFileAsync(Uri), which will automatically run m AsyncTask}
	 */
	public void addFile(Uri toRecycle) throws IOException
	{
		if (toRecycle == null)
		{
			return;
		}

		synchronized (mDiskCacheLock)
		{
			// Add to disk cache
			final DiskLruCache bin = getDiskCache();
			if (bin != null)
			{
				final String key = fileToKey(toRecycle.toString());
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
                        bis = new BufferedInputStream(FileUtil.getInputStream(mContext, toRecycle));
						out = new BufferedOutputStream(editor.newOutputStream(DISK_CACHE_INDEX));

						Util.copy(bis, out);
						editor.commit();

						deleteFile(toRecycle);
					}
				}
				finally
				{
                    Utils.closeSilently(bis);
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
	public InputStream getInputStream(String data)
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
			}
			return null;
		}
	}

	/**
	 * Get from disk cache.
	 *
	 * @param data
	 *            Unique identifier for which item to get
	 * @return The bitmap if found in cache, null otherwise
	 */
	@Nullable
	public File getFile(String data)
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
				try
				{
					return bin.getFile(key, DISK_CACHE_INDEX);
				}
				catch (final IOException e)
				{
					Log.e(TAG, "getFileFromBin - " + e);
				}
			}
			return null;
		}
	}

	public static String keyToFile(String key)
	{
		return key.replaceAll(pathReplacement, File.separator).replaceAll(spaceReplacement, " ");
	}

	public static String fileToKey(String file)
	{
		return file.replaceAll(File.separator, pathReplacement).replaceAll(" ", spaceReplacement);
	}

	// AJM: Removed the retain fragment logic

	public List<String> getKeys()
	{
		List<String> keys = new ArrayList<>();
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
					if (FileUtil.getUsableSpace(diskCacheDir) > mDiskCacheSize)
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

	public class AddFileTask extends AsyncTask<Uri, Void, Void>
	{
		@Override
		protected Void doInBackground(Uri... params)
		{
			try
			{
				// FIXME: It may not be possible to do this with the new permission infrastructure
				addFile(params[0]);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
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
	@Deprecated // Write permission issues would be lost
	public void addFileAsync(Uri recycledItem)
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
}
