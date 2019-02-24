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
import android.os.AsyncTask;
import android.util.Log;

import com.anthonymandra.rawdroid.data.DataRepository;
import com.anthonymandra.util.AppExecutors;
import com.anthonymandra.util.FileUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

/**
 * This class holds our discarded images
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class RecycleBin {
	private static final String TAG = RecycleBin.class.getSimpleName();
	// TODO: Single char replacements are not sufficient
	private static final String pathReplacement = "~";
	private static final String spaceReplacement = "`";
	private static final int MESSAGE_CLEAR = 0;
	private static final int MESSAGE_INIT_DISK_CACHE = 1;
	private static final int MESSAGE_FLUSH = 2;
	private static final int MESSAGE_CLOSE = 3;
	private static final int DISK_CACHE_INDEX = 0;

	private static long mDiskCacheSize = 1024 * 1024 * 50;
	private static RecycleBin INSTANCE = null;

	private DiskLruCache mDiskLruCache;
	private final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;
	private File mDiskCacheDir;
	private DataRepository mDatabase;

	/**
	 * Create new recycling bin with the default parameters.
	 * @param context    The context to use
	 */
	private RecycleBin(Context context) {
		mDiskCacheDir = FileUtil.getDiskCacheDir(context, "recycle");
		mDatabase = DataRepository.Companion.getInstance(context);
		initDiskCache();
	}

	public static RecycleBin getInstance(Context c) {
		if (INSTANCE == null) {
			INSTANCE = new RecycleBin(c);
		}
		return INSTANCE;
	}

	public static RecycleBin getInstance(Context c, long maxSize) {
		setMaxSize(maxSize);
		return getInstance(c);
	}

	public static void setMaxSize(long maxSize) {
		mDiskCacheSize = maxSize;
		if (INSTANCE != null) {
			INSTANCE.getDiskCache().setMaxSize(maxSize);
		}
	}

	/**
	 * Adds a file to the recycling bin synchronously.
	 */
	@WorkerThread
	public void addFileSynch(Context c, Uri toRecycle) throws IOException {
		if (toRecycle == null) {
			return;
		}

		synchronized (mDiskCacheLock) {
			// Add to disk cache
			final DiskLruCache bin = getDiskCache();
			if (bin != null) {
				/* TODO: This is less than ideal, we could insert a failed recycle
				* We could remove in catch, but even that might not be enough, investigate
				* For now we're safe because we display recycled files directly from bin `getKeys` */
				final String key = Long.toString(mDatabase.addRecycledImage(toRecycle.toString()));
				BufferedOutputStream out = null;
				BufferedInputStream bis = null;
				try {
					DiskLruCache.Snapshot snapshot = bin.get(key);
					if (snapshot != null) {
						bin.remove(key);
					}
					final DiskLruCache.Editor editor = bin.edit(key);
					if (editor != null) {
						bis = new BufferedInputStream(FileUtil.getInputStream(c, toRecycle));
						out = new BufferedOutputStream(editor.newOutputStream(DISK_CACHE_INDEX));

						Util.copy(bis, out);
						editor.commit();

						UsefulDocumentFile df = UsefulDocumentFile.fromUri(c, toRecycle);
						df.delete();
					}
				} finally {
					Util.closeSilently(bis);
					Util.closeSilently(out);
				}
			}
		}
	}

	/**
	 * Get from disk cache.
	 *
	 * @param recycledId Unique identifier for which item to get
	 * @return The bitmap if found in cache, null otherwise
	 */
	@Nullable
	public File getFile(Long recycledId) {
		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					mDiskCacheLock.wait();
				} catch (InterruptedException e) {
				}
			}

			final DiskLruCache bin = getDiskCache();
			if (bin != null) {
				try {
					return bin.getFile(String.valueOf(recycledId), DISK_CACHE_INDEX);
				} catch (final IOException e) {
					Log.e(TAG, "getFileFromBin - " + e);
				}
			}
			return null;
		}
	}

	public List<Long> getKeys() {
		List<Long> keys = new ArrayList<>();
		final DiskLruCache bin = getDiskCache();
		if (bin == null) {
			return keys;
		}

		Set<String> coded = bin.getKeys();
		for (String key : coded) {
			keys.add(Long.valueOf(key));
		}
		return keys;
	}

	/**
	 * Returns the maximum size of the bin in bytes
	 *
	 * @return
	 */
	public long getBinSize() {
		final DiskLruCache bin = getDiskCache();
		if (bin == null)
			return 0;
		return bin.getMaxSize();
	}

	protected class CacheAsyncTask extends AsyncTask<Object, Void, Void> {
		@Override
		protected Void doInBackground(Object... params) {
			switch ((Integer) params[0]) {
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

	private DiskLruCache getDiskCache() {
		// It's not ideal to possibly call this in a UI thread, but it ensures we won't pull a null after resume
		if (mDiskLruCache == null)
			initDiskCacheInternal();
		return mDiskLruCache;
	}

	protected void initDiskCacheInternal() {
		// Set up disk cache
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
				File diskCacheDir = mDiskCacheDir;
				if (diskCacheDir != null) {
					if (!diskCacheDir.exists()) {
						diskCacheDir.mkdirs();
					}
					if (FileUtil.getUsableSpace(diskCacheDir) > mDiskCacheSize) {
						try {
							mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, mDiskCacheSize);
						} catch (final IOException e) {
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

	protected void clearCacheInternal() {
		if (mDiskLruCache != null) {
			synchronized (mDiskCacheLock) {
				mDiskCacheStarting = true;
				if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
					try {
						mDiskLruCache.delete();
						mDatabase.clearRecycledImages();
					} catch (IOException e) {
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
	protected void flushCacheInternal() {
		if (mDiskLruCache != null) {
			synchronized (mDiskCacheLock) {
				if (mDiskLruCache != null) {
					try {
						mDiskLruCache.flush();
					} catch (IOException e) {
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
	protected void closeCacheInternal() {
		if (mDiskLruCache != null) {
			synchronized (mDiskCacheLock) {
				if (mDiskLruCache != null) {
					try {
						if (!mDiskLruCache.isClosed()) {
							mDiskLruCache.close();
							mDiskLruCache = null;
						}
					} catch (IOException e) {
						Log.e(TAG, "close - " + e);
					}
				}
			}
			mDiskLruCache = null;
		}
	}

	protected void removeFileInternal(Long recycledId) {
		try {
			final DiskLruCache bin = getDiskCache();
			if (bin != null) {
				bin.remove(String.valueOf(recycledId));
				mDatabase.deleteRecycledImage(recycledId);
			}
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
		}
	}

	public void addFile(Context c, Uri uri) {
		Completable.fromAction(() -> addFileSynch(c, uri))
			.subscribeOn(Schedulers.from(AppExecutors.Companion.getDISK()))
			.subscribe();
	}

	public void remove(Long recycledId) {
		Completable.fromAction(() -> removeFileInternal(recycledId))
			.subscribeOn(Schedulers.from(AppExecutors.Companion.getDISK()))
			.subscribe();
	}

	public void initDiskCache() {
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	public void clearCache() {
		new CacheAsyncTask().execute(MESSAGE_CLEAR);
	}

	public void flushCache() {
		new CacheAsyncTask().execute(MESSAGE_FLUSH);
	}

	public void closeCache() {
		new CacheAsyncTask().execute(MESSAGE_CLOSE);
	}
}
