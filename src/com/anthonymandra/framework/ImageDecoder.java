package com.anthonymandra.framework;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.rawdroid.BuildConfig;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

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
	private Bitmap processBitmap(RawObject media)
	{
		if (BuildConfig.DEBUG)
		{
			Log.d(TAG, "processBitmap - " + media.getName());
		}

        InputStream imageData = media.getThumb();
		if (imageData == null)
		{
			return null;
		}

        try
        {
		    Bitmap b = decodeSampledBitmapFromInputStream(imageData, mImageWidth, mImageHeight);
            return b;
        }
        catch(Exception e)
        {
            return null;
        }
        finally
        {
            Utils.closeSilently(imageData);
        }
	}

	@Override
	protected Bitmap processBitmap(Object data)
	{
		return processBitmap((RawObject) data);
	}
}
