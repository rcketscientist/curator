package com.anthonymandra.framework;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.util.ImageUtils;

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
	 */
	private Bitmap processBitmap(Uri media)
	{
		if (BuildConfig.DEBUG)
		{
			Log.d(TAG, "processBitmap - " + media);
		}

	    return decodeSampledBitmap(ImageUtils.getThumb(mContext, media), mImageWidth, mImageHeight);
	}

	@Override
	protected Bitmap processBitmap(Object data)
	{
		return processBitmap((Uri) data);
	}
}
