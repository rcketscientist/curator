package com.anthonymandra.framework;

import android.graphics.Color;

import java.util.Arrays;

public class Histogram
{
	@SuppressWarnings("unused")
	private static final String TAG = Histogram.class.getSimpleName();

	private int[] red = new int[256];
	private int[] green = new int[256];
	private int[] blue = new int[256];

	private int maxRed = 1;
	private int maxGreen = 1;
	private int maxBlue = 1;

	public Histogram()
	{
		Arrays.fill(red, 0);
		Arrays.fill(green, 0);
		Arrays.fill(blue, 0);
	}

	public void addFreqR(int band)
	{
		red[band]++;
		maxRed = Math.max(red[band], maxRed);
	}

	public void addFreqG(int band)
	{
		green[band]++;
		maxGreen = Math.max(green[band], maxGreen);
	}

	public void addFreqB(int band)
	{
		blue[band]++;
		maxBlue = Math.max(blue[band], maxBlue);
	}

	public void processPixel(int color)
	{
		int red = Color.red(color);
		addFreqR(red);
		int green = Color.green(color);
		addFreqG(green);
		int blue = Color.blue(color);
		addFreqB(blue);
	}

	public int getFreqR(int band)
	{
		return red[band];
	}

	public int getFreqG(int band)
	{
		return green[band];
	}

	public int getFreqB(int band)
	{
		return blue[band];
	}

	public int getMaxFreqR()
	{
		return maxRed;
	}

	public int getMaxFreqG()
	{
		return maxGreen;
	}

	public int getMaxFreqB()
	{
		return maxBlue;
	}

	public int getMaxFreq()
	{
		return Math.max(Math.max(maxRed, maxGreen), maxBlue);
	}
}
