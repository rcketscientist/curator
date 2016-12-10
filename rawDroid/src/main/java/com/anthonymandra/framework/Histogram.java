package com.anthonymandra.framework;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.concurrent.RecursiveTask;

public class Histogram extends RecursiveTask<Histogram.ColorBins>
{
	@SuppressWarnings("unused")
	private static final String TAG = Histogram.class.getSimpleName();
	private static final int sThreshold = 10000;
	private static final int COLOR_DEPTH = 256;
	private final int[] mSource;
	private final int mStart;
	private final int mLength;

	public Histogram(final int[] src, final int start, final int length)
	{
		mSource = src;
		mStart = start;
		mLength = length;
	}

	private ColorBins computeDirectly()
	{
		ColorBins bins = new ColorBins();
		for (int index = mStart; index < mStart + mLength; index++)
		{
			bins.red[Color.red(mSource[index])]++;
			bins.green[Color.green(mSource[index])]++;
			bins.blue[Color.blue(mSource[index])]++;
		}
		return bins;
	}

	protected ColorBins compute()
	{
		if (mLength < sThreshold)
		{
			return computeDirectly();
		}

		int split = mLength / 2;
		Histogram left = new Histogram(mSource, mStart, split);
		Histogram right = new Histogram(mSource, split, mLength - split);
		left.fork();
		ColorBins r = right.compute();
		ColorBins l = left.join();
		r.merge(l);
		return r;
	}

	static Histogram createHistogram(final Bitmap bmp)
	{
		int[] pixels = new int[bmp.getWidth() * bmp.getHeight()];
		bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
		return new Histogram(pixels, 0, pixels.length);
	}

	public static class ColorBins
	{
		public int[] red = new int[COLOR_DEPTH];
		public int[] green = new int[COLOR_DEPTH];
		public int[] blue = new int[COLOR_DEPTH];

		int maxRed;
		int maxGreen;
		int maxBlue;

		void merge(ColorBins b)
		{
			for (int i = 0; i < COLOR_DEPTH; i++)
			{
				red[i] += b.red[i];
				green[i] += b.green[i];
				blue[i] += b.blue[i];

				maxRed = Math.max(red[i], maxRed);
				maxGreen = Math.max(green[i], maxGreen);
				maxBlue = Math.max(blue[i], maxBlue);
			}
		}

		public int getMaxFreq()
		{
			return Math.max(Math.max(maxRed, maxGreen), maxBlue);
		}
	}
}
