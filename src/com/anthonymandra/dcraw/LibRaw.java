package com.anthonymandra.dcraw;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.format.Time;
import android.util.Log;

import com.android.gallery3d.common.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LibRaw
{
	private static final String TAG = "LibRaw";
	private static final int numThreads = 4;

	public static final Executor EXECUTOR = Executors.newFixedThreadPool(numThreads);

	static
	{
		try
		{
			Log.i("JNI", "Trying to load libraw.so");
			System.loadLibrary("raw_r");
			System.loadLibrary("raw");
		}
		catch (UnsatisfiedLinkError ule)
		{
			Log.e("JNI", "WARNING: Could not load libraw.so");
		}
	}

	// Can decode
	private static native boolean canDecodeFromBuffer(byte[] buffer);

	private static native boolean canDecodeFromFile(String filePath);

	/**
	 * Gets the thumbnail from a raw image.  Thumbnail will be returned as a jpeg.  In the case of a 
	 * thumbnail that is in rgb format it will be converted using {@link config}, {@link quality}, and {@link format}.
	 * @param filePath Path to the file
	 * @param exif Array will be populated with exif information
	 * @param quality Quality at which to compress an rgb thumbnail
	 * @param config Configuration of the generated bitmap
	 * @param compressFormat Format of  the compression
	 * @return Thumbnail image data in jpeg format
	 */
	private static native byte[] getThumbFromFile(String filePath, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
	private static native byte[] getThumbWithWatermark(String filePath, byte[] watermark, int[] margins, int waterWidth, int waterHeight);
	private static native byte[] getThumbFromBuffer(byte[] buffer, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);

	// Write thumb
	public static native boolean writeThumbFromBuffer(byte[] buffer, String destination);
	public static native boolean writeThumbFromFile(String source, String destination);

	// Get raw bitmap
	private static native byte[] getHalfImageFromFile(String filePath, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
	private static native BitmapRegionDecoder getHalfDecoderFromFile(String filePath, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
	private static native byte[] getImageFromFile(String filePath, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);		// For testing
	private static native BitmapRegionDecoder getDecoderFromFile(String filePath, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
	private static native BitmapRegionDecoder getRawFromBuffer(byte[] buffer, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);

	// Write raw tiff
	public static native boolean writeRawFromBuffer(byte[] buffer, String destination);

	public static native boolean writeRawFromFile(String source, String destination);

	public static boolean canDecode(byte[] buffer)
	{
		if (buffer == null)
			return false;
		boolean result = canDecodeFromBuffer(buffer);
		Log.i(TAG, "Received canDecode: " + result);
		return result;
	}

	public static boolean canDecode(File file)
	{
		return canDecodeFromFile(file.getPath());
	}

	public static byte[] getThumb(byte[] buffer, String[] exif)
	{
		return getThumbFromBuffer(buffer, exif, 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG);
	}
	
	public static byte[] getThumbWithWatermark(File file, byte[] watermark, Margins margins, int waterWidth, int waterHeight)
	{
		long start = System.currentTimeMillis();
		byte[] image = getThumbWithWatermark(file.getPath(), watermark, margins.getArray(), waterWidth, waterHeight);
		Log.d(TAG, "DB: WaterThumb took " + (System.currentTimeMillis() - start) + "ms");
		return image;
	}

	public static byte[] getThumb(File file, String[] exif)
	{	
//		for (StackTraceElement ste : new Throwable().getStackTrace()) {
//		Log.d(TAG, "DB: " + ste.toString());
//	}
		
//		long start = System.currentTimeMillis();
//		byte[] image = getHalfImageFromFile(file.getPath(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG);
//		Log.d(TAG, "DB: Half raw took " + (System.currentTimeMillis() - start) + "ms");
		
//		long start = System.currentTimeMillis();
//		byte[] image = getImageFromFile(file.getPath(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG);
//		Log.d(TAG, "DB: Full raw took " + (System.currentTimeMillis() - start) + "ms");
		
		long start = System.currentTimeMillis();
		byte[] image = getThumbFromFile(file.getPath(), exif, 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG);
		Log.d(TAG, "DB: Thumbnail took " + (System.currentTimeMillis() - start) + "ms");
		
		return image;
	}
	
	public static class Margins
	{
		int left = -1;
		int right = -1;
		int top = -1;
		int bottom = -1;
		
		public static Margins Center = new Margins();
		
		/**
		 * Defaults to center
		 */
		private Margins() {}
		
		public Margins(int top, int left, int bottom, int right)
		{
			this.top = top;
			this.left = left;
			this.bottom = bottom;
			this.right = right;
		}
		
		public int[] getArray()
		{
			return new int[] { top, left, bottom, right };
		}
	}
}
