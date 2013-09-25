package com.anthonymandra.dcraw;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.util.Log;

import com.android.gallery3d.common.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

	// Get thumb bitmap
	private static native byte[] getThumbFromFile(String filePath);

	private static native Object getThumbFromFile2(String filePath);

	private static native byte[] getThumbFromFile3(String filePath);

	private static native byte[] getThumbFromFile4(String filePath, int[] results);

	private static native byte[] getThumbFromFile5(String filePath, int[] results, String[] exif);

	private static native byte[] getThumbFromBuffer(byte[] buffer);

	// Write thumb
	public static native int writeThumbFromBuffer(byte[] buffer, String destination);

	public static native boolean writeThumbFromFile(String source, String destination);

	// Get raw bitmap
	private static native Object getRawFromFile(String filePath);

	private static native Object getRawFromBuffer(byte[] buffer);

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

	public static byte[] getThumb(byte[] buffer)
	{
		byte[] result = getThumbFromBuffer(buffer);
		return result;
	}

	public static InputStream getThumb(File file, String[] exif)
	{
		int[] results = new int[3];
		final byte[] result = getThumbFromFile5(file.getPath(), results, exif);

		if (result == null)
			return null;

		if (results[0] == 0)
		{
            BufferedInputStream reader = new BufferedInputStream(new ByteArrayInputStream(result));
            ByteArrayOutputStream byteStream = null;

            try
            {
                Log.i(TAG, file.getName() + " is RGB thumb.");
                int width = results[1];
                int height = results[2];

                final int[] colors = new int[width * height];

                // Read in the pixels
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        try
                        {
                            int r = reader.read();
                            int g = reader.read();
                            int b = reader.read();
                            colors[y * width + x] = Color.rgb(r, g, b);
                        }
                        catch (IOException e)
                        {
                            return null;
                        }
                    }
                }

                Bitmap bmp = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
                byteStream = new ByteArrayOutputStream();
                bmp.compress(CompressFormat.JPEG, 100, byteStream);
                final byte[] resultBytes = byteStream.toByteArray();
                return new ByteArrayInputStream(resultBytes);
            }
            catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
            finally {
                Utils.closeSilently(byteStream);
                Utils.closeSilently(reader);
            }
		}
		else
		{
            return new ByteArrayInputStream(result);
		}
	}

	// Converts an rgb bitmap of width and height to an int array of colors
	static int[] getColors(byte[] rgb, int width, int height)
	{
		int subpixel = 0;
		int[] colors = new int[height * width];

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int r = rgb[subpixel++];
				int g = rgb[subpixel++];
				int b = rgb[subpixel++];
				colors[y * width + x] = Color.rgb(g, b, r);
			}
		}

		return colors;
	}
}
