package com.anthonymandra.framework;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.anthonymandra.rawdroid.BuildConfig;

public class ImageDecoder extends ImageResizer
{
	private static final String TAG = "GalleryDecoder";

	/**
	 * Initialize providing a target image width and height for the processing images.
	 * 
	 * @param context
	 * @param imageWidth
	 * @param imageHeight
	 */
	public ImageDecoder(Context context, int imageWidth, int imageHeight)
	{
		super(context, imageWidth, imageHeight);
	}

	/**
	 * Initialize providing a single target image size (used for both width and height);
	 * 
	 * @param context
	 * @param imageSize
	 */
	public ImageDecoder(Context context, int imageSize)
	{
		super(context, imageSize);
	}

	/**
	 * The main processing method. This happens in a background task. In this case we are just sampling down the bitmap and returning it from a
	 * resource.
	 * 
	 * @param resId
	 * @return
	 */
	private Bitmap processBitmap(MediaObject media)
	{
		if (BuildConfig.DEBUG)
		{
			Log.d(TAG, "processBitmap - " + media.getName());
		}

//		// Image for folders
//		if (media.isDirectory())
//		{
//			return mFolderBitmap;
//		}
//
		byte[] imageData = media.getThumb();
		if (imageData == null)
		{
			return null;
		}
//		if (imageData == null || imageData.length == 0)
//		{
//			return mUnknownBitmap;
//		}

		Bitmap b = decodeSampledBitmapFromByteArray(imageData, mImageWidth, mImageHeight);
		imageData = null;

		return b;
	}

	@Override
	protected Bitmap processBitmap(Object data)
	{
		return processBitmap((MediaObject) data);
	}
}
