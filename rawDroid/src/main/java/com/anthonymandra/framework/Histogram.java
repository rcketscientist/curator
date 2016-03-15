package com.anthonymandra.framework;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

public class Histogram
{
	@SuppressWarnings("unused")
	private static final String TAG = Histogram.class.getSimpleName();
	protected final int sThreshold = 100000;
	protected static final int COLOR_DEPTH = 256;

	private AtomicInteger[] red = new AtomicInteger[COLOR_DEPTH];
	private AtomicInteger[] green = new AtomicInteger[COLOR_DEPTH];
	private AtomicInteger[] blue = new AtomicInteger[COLOR_DEPTH];

	private AtomicInteger maxRed = new AtomicInteger();
	private AtomicInteger maxGreen = new AtomicInteger();
	private AtomicInteger maxBlue = new AtomicInteger();

	public Histogram()
	{
		initArray(red);
		initArray(green);
		initArray(blue);
	}

	private void initArray(AtomicInteger[] array)
	{
		for (int i = 0; i < COLOR_DEPTH; i++)
			array[i] = new AtomicInteger();
	}

	public void addFreqR(int band)
	{
		int level = red[band].incrementAndGet();
		if (maxRed.get() < level)
			maxRed.set(level);
	}

	public void addFreqG(int band)
	{
		int level = green[band].incrementAndGet();
		if (maxGreen.get() < level)
			maxGreen.set(level);
	}

	public void addFreqB(int band)
	{
		int level = blue[band].incrementAndGet();
		if (maxBlue.get() < level)
			maxBlue.set(level);
	}

	public void processPixel(final int color)
	{
		int red = Color.red(color);
		addFreqR(red);
		int green = Color.green(color);
		addFreqG(green);
		int blue = Color.blue(color);
		addFreqB(blue);
	}

	public void processBitmap(final Bitmap bmp)
	{
		int[] pixels = new int[bmp.getWidth() * bmp.getHeight()];
		bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
		ForkProcess fp = new ForkProcess(pixels, 0, pixels.length);
		ForkJoinPool pool = new ForkJoinPool();
		pool.invoke(fp);
	}

	public int getFreqR(int band)
	{
		return red[band].get();
	}

	public int getFreqG(int band)
	{
		return green[band].get();
	}

	public int getFreqB(int band)
	{
		return blue[band].get();
	}

	public int getMaxFreqR()
	{
		return maxRed.get();
	}

	public int getMaxFreqG()
	{
		return maxGreen.get();
	}

	public int getMaxFreqB()
	{
		return maxBlue.get();
	}

	public int getMaxFreq()
	{
		final int maxR = maxRed.get();
		final int maxB = maxBlue.get();
		final int maxG = maxGreen.get();
		return Math.max(Math.max(maxR, maxG), maxB);
	}

	public class ForkProcess extends RecursiveAction
	{
		private final int[] mSource;
		private final int mStart;
		private final int mLength;

		public ForkProcess(final int[] src, final int start, final int length)
		{
			mSource = src;
			mStart = start;
			mLength = length;
		}

		protected void computeDirectly()
		{
			for (int index = mStart; index < mStart + mLength; index++)
			{
				processPixel(mSource[index]);
			}
		}

		protected void compute()
		{
			if (mLength < sThreshold)
			{
				computeDirectly();
				return;
			}

			int split = mLength / 2;

			invokeAll(new ForkProcess(mSource, mStart, split),
					new ForkProcess(mSource, mStart + split, mLength - split));
		}
	}
}
