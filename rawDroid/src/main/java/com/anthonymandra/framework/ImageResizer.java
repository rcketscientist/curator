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
import android.util.Log;

import com.anthonymandra.rawdroid.BuildConfig;
import com.crashlytics.android.Crashlytics;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple subclass of {@link ImageWorker} that resizes images from resources given a target width and height. Useful for when the input images might
 * be too large to simply load directly into memory.
 */
public class ImageResizer extends ImageWorker
{
	private static final String TAG = "GalleryResizer";
	protected int mImageWidth;
	protected int mImageHeight;

	/**
	 * Initialize providing a single target image size (used for both width and height);
	 * 
	 * @param context
	 * @param imageWidth
	 * @param imageHeight
	 */
	public ImageResizer(Context context, int imageWidth, int imageHeight)
	{
		super(context);
		setImageSize(imageWidth, imageHeight);
	}

	/**
	 * Initialize providing a single target image size (used for both width and height);
	 * 
	 * @param context
	 * @param imageSize
	 */
	public ImageResizer(Context context, int imageSize)
	{
		super(context);
		setImageSize(imageSize);
	}

	/**
	 * Set the target image width and height.
	 * 
	 * @param width
	 * @param height
	 */
	public void setImageSize(int width, int height)
	{
		mImageWidth = width;
		mImageHeight = height;
	}

	/**
	 * Set the target image size (width and height will be the same).
	 * 
	 * @param size
	 */
	public void setImageSize(int size)
	{
		setImageSize(size, size);
	}

	/**
	 * The main processing method. This happens in a background task. In this case we are just sampling down the bitmap and returning it from a
	 * resource.
	 * 
	 * @param resId
	 * @return
	 */
	private Bitmap processBitmap(int resId)
	{
		if (BuildConfig.DEBUG)
		{
			Log.d(TAG, "processBitmap - " + resId);
		}
		return decodeSampledBitmap(mResources, resId, mImageWidth, mImageHeight);
	}

	@Override
	protected Bitmap processBitmap(Object data)
	{
		return processBitmap(Integer.parseInt(String.valueOf(data)));
	}

	/**
	 * Decode and sample down a bitmap from resources to the requested width and height.
	 * 
	 * @param res
	 *            The resources object containing the image data
	 * @param resId
	 *            The resource id of the image data
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions that are equal to or greater than the requested width
	 *         and height
	 */
	public static Bitmap decodeSampledBitmap(Resources res, int resId, int reqWidth, int reqHeight)
	{

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(res, resId, options);

		// Calculate inSampleSize
		options.inSampleSize = Util.getExactSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeResource(res, resId, options);
	}

	/**
	 * Decode and sample down a bitmap from a file to the requested width and height.
	 * 
	 * @param filename
	 *            The full path of the file to decode
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions that are equal to or greater than the requested width
	 *         and height
	 */
	public static Bitmap decodeSampledBitmap(String filename, int reqWidth, int reqHeight)
	{

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(filename, options);

		// Calculate inSampleSize
		options.inSampleSize = Util.getExactSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(filename, options);
	}

	/**
	 * Decode and sample down a bitmap from a file input stream to the requested width and height.
	 * 
	 * @param fileDescriptor
	 *            The file descriptor to read from
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions that are equal to or greater than the requested width
	 *         and height
	 */
	public static Bitmap decodeSampledBitmap(FileDescriptor fileDescriptor, int reqWidth, int reqHeight)
	{

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

		// Calculate inSampleSize
		options.inSampleSize = Util.getExactSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
	}

	/**
	 * Decode and sample down a bitmap from a file to the requested width and height.
	 * 
	 * @param data
	 *            The binary image
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions that are equal to or greater than the requested width
	 *         and height
	 */
	public static Bitmap decodeSampledBitmap(byte[] data, int reqWidth, int reqHeight)
	{
		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);

		// Calculate inSampleSize
		options.inSampleSize = Util.getExactSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;

		return BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	/**
	 * Decode and sample down a bitmap from a file to the requested width and height.
	 *
	 * @param is
	 *            The image stream which must support mark and reset.
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions that are equal to or greater than the requested width
	 *         and height
	 */
	public static Bitmap decodeSampledBitmap(InputStream is, int reqWidth, int reqHeight)
	{
		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(is, null, options);

		try
		{
			is.reset();
		} catch (IOException e)
		{
			Crashlytics.logException(new Exception(
					"InputStream does not support mark: " + is.getClass().getName(), e));
			return null;
		}

		// Calculate inSampleSize
		options.inSampleSize = Util.getExactSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;

		return BitmapFactory.decodeStream(is, null, options);

	}

	/**
	 * Decode and sample down a bitmap from a file to the requested width and height.
	 * 
	 * @param data
	 *            The binary image
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions that are equal to or greater than the requested width
	 *         and height
	 */
	public static Bitmap decodeSampledBitmapFromInputStream(InputStream data, int reqWidth, int reqHeight)
	{
		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(data, null, options);

		// Calculate inSampleSize
		options.inSampleSize = Util.getExactSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;

        try {
            // TODO: This works, but is there a better way?
            if (data instanceof FileInputStream)
                ((FileInputStream)data).getChannel().position(0);
            else
                data.reset();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return BitmapFactory.decodeStream(data, null, options);
	}
}
