package com.anthonymandra.dcraw;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.util.Log;

import com.anthonymandra.rawdroid.FullSettingsActivity;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LibRaw
{
	private static final String TAG = "LibRaw";
	private static final int numThreads = 4;

	public static final Executor EXECUTOR = Executors./*newCachedThreadPool();*/newFixedThreadPool(numThreads);

	static
	{
		try
		{
			Log.i("JNI", "Trying to load libraw.so");
//			System.loadLibrary("stlport_shared");
//			System.loadLibrary("iconv");
//			System.loadLibrary("gnustl_shared");
//			System.loadLibrary("libjpeg");
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
	public static native String[] canDecodeDirectory(String directory);

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
	private static native byte[] getThumbFile
        (String filePath, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
	private static native byte[] getThumbFileWatermark
        (String filePath, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat, byte[] watermark, int[] margins, int waterWidth, int waterHeight);
	private static native byte[] getThumbBuffer
        (byte[] buffer, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);

	// Write thumb
	public static native boolean writeThumbBuffer
        (byte[] buffer, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat, String destination);
	public static native boolean writeThumbFile
        (String source, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat, String destination);
	public static native boolean writeThumbFileWatermark
        (String source, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat, String destination, byte[] watermark, int[] margins, int waterWidth, int waterHeight);

	// Get raw bitmap
    private static native byte[] getImageFile
        (String filePath, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
    private static native byte[] getImageFileWatermark
        (String filePath, String[] exif, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat, byte[] watermark, int[] margins, int waterWidth, int waterHeight);
    private static native byte[] getHalfImageFile
        (String filePath, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);

    // For future testing:
    private static native BitmapRegionDecoder getHalfDecoder(String filePath, int quality, Bitmap.Config config, Bitmap.CompressFormat compressFormat);
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
		return getThumbBuffer(buffer, exif, 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG);
	}
	
	public static byte[] getThumbWithWatermark(File file, byte[] watermark, Margins margins, int waterWidth, int waterHeight)
	{
		long start = System.currentTimeMillis();
		byte[] image = getThumbFileWatermark(file.getPath(), null, 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, watermark, margins.getArray(), waterWidth, waterHeight);
		Log.d(TAG, "DB: WaterThumb took " + (System.currentTimeMillis() - start) + "ms");
		return image;
	}

	private static int sum = 0;
	private static int entries = 0;
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
		byte[] image = getThumbFile(file.getPath(), exif, 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG);
//		Log.d(TAG, "DB: Thumbnail took " + (System.currentTimeMillis() - start) + "ms");
		sum += System.currentTimeMillis() - start;
		entries++;
		
		Log.d(TAG, "DB: Thumbnail avg = " + sum / entries + "ms");
		
		return image;
	}
	
//	public static boolean writeThumb(File source, File destination, int quality)
//	{
//		return writeThumbFromFile(source, destination, quality);
//	}
//	
//	public static boolean writeThumbWatermark(File source, File destination, byte[] watermark, int[] margins, int waterWidth, int waterHeight, int quality)
//	{
//		return writeThumbWatermark(source.getPath(), destination, watermark, margins, waterWidth, waterHeight, quality);
//	}
	
	public static class Margins
	{
		int left = -1;
		int right = -1;
		int top = -1;
		int bottom = -1;
		
		public static Margins Center = new Margins();
		public static Margins LowerRight = new Margins(-1, -1, 100, 100);

		
		/**
		 * Defaults to center
		 */
		private Margins() {}
		
		public Margins(SharedPreferences pref)
		{
			top = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkTopMargin, "-1"));
			bottom = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkBottomMargin, "-1"));
			right = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkRightMargin, "-1"));
			left = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkLeftMargin, "-1"));			
		}
		
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
