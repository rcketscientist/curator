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

package com.android.gallery3d.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.data.BytesBufferPool.BytesBuffer;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.anthonymandra.framework.MetaMedia;

public abstract class ImageCacheRequest implements Job<Bitmap>
{
	private static final String TAG = "ImageCacheRequest";

	protected GalleryActivity mApplication;
	private Uri mUri;
	private int mType;
	private int mTargetSize;

	public ImageCacheRequest(GalleryActivity application, Uri uri, int type, int targetSize)
	{
		mApplication = application;
		mUri = uri;
		mType = type;
		mTargetSize = targetSize;
	}

	@Override
	public Bitmap run(JobContext jc)
	{
		String debugTag = mUri + "," + ((mType == MetaMedia.TYPE_THUMBNAIL) ? "THUMB" : (mType == MetaMedia.TYPE_MICROTHUMBNAIL) ? "MICROTHUMB" : "?");
		ImageCacheService cacheService = mApplication.getImageCacheService();

		BytesBuffer buffer = MetaMedia.getBytesBufferPool().get();
		try
		{
			boolean found = cacheService.getImageData(mUri, mType, buffer);
			if (jc.isCancelled())
				return null;
			if (found)
			{
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inPreferredConfig = Bitmap.Config.ARGB_8888;
				Bitmap bitmap;
				if (mType == MetaMedia.TYPE_MICROTHUMBNAIL)
				{
					bitmap = MetaMedia.getMicroThumbPool().decode(jc, buffer.data, buffer.offset, buffer.length, options);
				}
				else
				{
					bitmap = MetaMedia.getThumbPool().decode(jc, buffer.data, buffer.offset, buffer.length, options);
				}
				if (bitmap == null && !jc.isCancelled())
				{
					Log.w(TAG, "decode cached failed " + debugTag);
				}
				return bitmap;
			}
		}
		finally
		{
			MetaMedia.getBytesBufferPool().recycle(buffer);
		}
		Bitmap bitmap = onDecodeOriginal(jc, mType);
		if (jc.isCancelled())
			return null;

		if (bitmap == null)
		{
			Log.w(TAG, "decode orig failed " + debugTag);
			return null;
		}

		if (mType == MetaMedia.TYPE_MICROTHUMBNAIL)
		{
			bitmap = BitmapUtils.resizeAndCropCenter(bitmap, mTargetSize, true);
		}
		else
		{
			bitmap = BitmapUtils.resizeDownBySideLength(bitmap, mTargetSize, true);
		}
		if (jc.isCancelled())
			return null;

		byte[] array = BitmapUtils.compressToBytes(bitmap);
		if (jc.isCancelled())
			return null;

		cacheService.putImageData(mUri, mType, array);
		return bitmap;
	}

	public abstract Bitmap onDecodeOriginal(JobContext jc, int targetSize);
}
