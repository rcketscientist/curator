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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

/**
 * Class containing some static utility methods.
 */
public class Util
{

	public static boolean hasFroyo()
	{
		// Can use static final constants like FROYO, declared in later versions
		// of the OS since they are inlined at compile time. This is guaranteed behavior.
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
	}

	public static boolean hasGingerbread()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
	}

	public static boolean hasHoneycomb()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
	}

	public static boolean hasHoneycombMR1()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
	}

	public static boolean hasIceCreamSandwich()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
	}

	public static boolean hasJellyBean()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
	}

	/**
	 * Get a usable cache directory (external if available, internal otherwise).
	 * 
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique directory name to append to the cache dir
	 * @return The cache dir
	 */
	public static File getDiskCacheDir(Context context, String uniqueName)
	{
		// Check if media is mounted or storage is built-in, if so, try and use external cache dir
		// otherwise use internal cache dir
		File cache = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable())
		{
			cache = context.getExternalCacheDir();
		}
		if (cache == null)
			cache = context.getCacheDir();

		return new File(cache, uniqueName);
		// final String cachePath =
		// return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable() ?
		// getExternalCacheDir(context).getPath() : context.getCacheDir().getPath();
		//
		// return new File(cachePath, uniqueName);
	}

	public static void debugClearCache(Context context)
	{
		// Check if media is mounted or storage is built-in, if so, try and use external cache dir
		// otherwise use internal cache dir
		File cache = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable())
		{
			cache = context.getExternalCacheDir();
		}
		if (cache == null)
			cache = context.getCacheDir();

		final File oldCache = cache;
		new Runnable()
		{
			@Override
			public void run()
			{
				final File[] swapFiles = oldCache.listFiles();
				for (File toDelete : swapFiles)
				{
					toDelete.delete();
				}
			}
		}.run();
	}

	/**
	 * Check if external storage is built-in or removable.
	 * 
	 * @return True if external storage is removable (like an SD card), false otherwise.
	 */
	@TargetApi(9)
	public static boolean isExternalStorageRemovable()
	{
		if (Util.hasGingerbread())
		{
			return Environment.isExternalStorageRemovable();
		}
		return true;
	}

	// /**
	// * Get the external app cache directory.
	// *
	// * @param context
	// * The context to use
	// * @return The external cache dir
	// */
	// @TargetApi(8)
	// public static File getExternalCacheDir(Context context)
	// {
	// if (Utils.hasFroyo())
	// {
	// return context.getExternalCacheDir();
	// }
	//
	// // Before Froyo we need to construct the external cache dir ourselves
	// final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
	// return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
	// }

	/**
	 * Check how much usable space is available at a given path.
	 * 
	 * @param path
	 *            The path to check
	 * @return The space available in bytes
	 */
	@TargetApi(9)
	public static long getUsableSpace(File path)
	{
		if (Util.hasGingerbread())
		{
			return path.getUsableSpace();
		}
		final StatFs stats = new StatFs(path.getPath());
		return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
	}

	public static String swapExtention(String filename, String ext)
	{
		return filename.replaceFirst("[.][^.]+$", "") + ext;
	}

	public static boolean isTabDelimited(String filepath)
	{
		int numberOfLevels = 0;
		BufferedReader readbuffer = null;
		try
		{
			readbuffer = new BufferedReader(new FileReader(filepath));
			String line;
			while ((line = readbuffer.readLine()) != null)
			{
				String tokens[] = line.split("\t");
				numberOfLevels = Math.max(numberOfLevels, tokens.length);
			}
		}
		catch (IOException e)
		{
			return false;
		}
		finally
		{
			try
			{
				if (readbuffer != null)
					readbuffer.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return numberOfLevels > 0;
	}

	public static boolean isNativeImage(MediaObject file)
	{
		String filename = file.getName();
		int dotposition = filename.lastIndexOf(".");
		String ext = filename.substring(dotposition + 1, filename.length()).toLowerCase();

		// Compare against supported android image formats
		return (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif"));
	}

	/**
	 * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding bitmaps using the decode* methods from
	 * {@link BitmapFactory}. This implementation calculates the closest inSampleSize that will result in the final decoded bitmap having a width and
	 * height equal to or larger than the requested width and height. This implementation does not ensure a power of 2 is returned for inSampleSize
	 * which can be faster when decoding but results in a larger bitmap which isn't as useful for caching purposes.
	 * 
	 * @param options
	 *            An options object with out* params already populated (run through a decode* method with inJustDecodeBounds==true
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return The value to be used for inSampleSize
	 */
	// public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
	// {
	// return getLargeSampleSize(options, reqWidth, reqHeight);
	// // Raw height and width of image
	// final int height = options.outHeight;
	// final int width = options.outWidth;
	// int inSampleSize = 1;
	//
	// if (height > reqHeight || width > reqWidth)
	// {
	// // if (width > height)
	// // {
	// // inSampleSize = Math.round((float) height / (float) reqHeight);
	// // }
	// // else
	// // {
	// // inSampleSize = Math.round((float) width / (float) reqWidth);
	// // }
	// int heightSample = (int) Math.ceil((float) height / (float) reqHeight);
	// int widthSample = (int) Math.ceil((float) width / (float) reqWidth);
	// inSampleSize = Math.max(heightSample, widthSample);
	//
	// // This offers some additional logic in case the image has a strange
	// // aspect ratio. For example, a panorama may have a much larger
	// // width than height. In these cases the total pixels might still
	// // end up being too large to fit comfortably in memory, so we should
	// // be more aggressive with sample down the image (=larger
	// // inSampleSize).
	//
	// final float totalPixels = width * height;
	//
	// // Anything more than 2x the requested pixels we'll sample down
	// // further.
	// final float totalReqPixelsCap = reqWidth * reqHeight * 2;
	//
	// while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap)
	// {
	// inSampleSize++;
	// }
	// }
	// return inSampleSize;
	// }

	/**
	 * Gets an exact sample size, should not be used for large images since certain ratios generate "white images"
	 * 
	 * @param options
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public static int getExactSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
	{
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth)
		{
			if (width > height)
			{
				inSampleSize = Math.round((float) height / (float) reqHeight);
			}
			else
			{
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).
			final float totalPixels = width * height;

			// Anything more than 2x the requested pixels we'll sample down
			// further.
			final float totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap)
			{
				inSampleSize++;
			}
		}
		return inSampleSize;
	}

	/**
	 * Legacy sample size, more reliable for multiple devices (no "white image")
	 * 
	 * @param options
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public static int getLargeSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
	{
		int imageWidth = options.outWidth;
		int imageHeight = options.outHeight;

		int scaleH = 1, scaleW = 1;
		if (imageHeight > reqHeight || imageWidth > reqWidth)
		{
			scaleH = (int) Math.pow(2, (int) Math.ceil(Math.log(reqHeight / (double) imageHeight) / Math.log(0.5)));
			scaleW = (int) Math.pow(2, (int) Math.ceil(Math.log(reqWidth / (double) imageWidth) / Math.log(0.5)));
		}
		return Math.max(scaleW, scaleH);
	}

	public static Bitmap createBitmapLarge(byte[] image, int viewWidth, int viewHeight, boolean minSize)
	{
		Bitmap result = null;
		Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(image, 0, image.length, o);
		o.inSampleSize = Util.getLargeSampleSize(o, viewWidth, viewHeight);
		// setScalingPow2(image, viewWidth, viewHeight, o, minSize);
		o.inJustDecodeBounds = false;
		result = BitmapFactory.decodeByteArray(image, 0, image.length, o);
		return result;
	}

	public static Bitmap createBitmapLarge(InputStream data, int viewWidth, int viewHeight, boolean minSize)
	{
		Bitmap result = null;
		Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(data, null, o);
		o.inSampleSize = Util.getLargeSampleSize(o, viewWidth, viewHeight);
		// setScalingPow2(image, viewWidth, viewHeight, o, minSize);
		o.inJustDecodeBounds = false;
		result = BitmapFactory.decodeStream(data, null, o);
		return result;
	}

	public static Bitmap createBitmapToSize(InputStream data, int width, int height)
	{
		Bitmap result = null;
		Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(data, null, o);
		o.inSampleSize = Util.getExactSampleSize(o, width, height);
		o.inJustDecodeBounds = false;
		result = BitmapFactory.decodeStream(data, null, o);
		return result;
	}

	public static Bitmap createBitmapToSize(byte[] image, int width, int height)
	{
		Bitmap result = null;
		Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(image, 0, image.length, o);
		o.inSampleSize = Util.getExactSampleSize(o, width, height);
		o.inJustDecodeBounds = false;
		result = BitmapFactory.decodeByteArray(image, 0, image.length, o);
		return result;
	}

	/**
	 * Copies a file from source to destination. If copying images see {@link #copy(File, File)}
	 * 
	 * @param source
	 * @param destination
	 * @throws IOException
	 */
	public static boolean copy(InputStream source, File destination)
	{
		byte[] buf = new byte[1024];
		int len;
		OutputStream out = null;
		try
		{
			out = new FileOutputStream(destination);
			while ((len = source.read(buf)) > 0)
			{
				out.write(buf, 0, len);
			}
		}
		catch (IOException e)
		{
			return false;
		}
		finally
		{
			try
			{
				if (out != null)
					out.close();
			}
			catch (IOException e)
			{
				return false;
			}
		}
		return true;
	}
}
