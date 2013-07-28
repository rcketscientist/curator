package com.anthonymandra.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.anthonymandra.framework.Histogram;

public class HistogramView extends View
{
	Path redPath;
	Path bluePath;
	Path greenPath;

	Paint p = new Paint();

	private static final int histHeight = 100;
	private static final int histWidth = 256;
	private static final int histSpacer = 5;

	private static final int redBias = 0;
	private static final int greenBias = 100 + histSpacer;
	private static final int blueBias = 200 + 2 * histSpacer;

	private int totalHeight = histHeight * 3;
	private int redStart = histHeight + redBias;
	private int greenStart = histHeight + greenBias;
	private int blueStart = histHeight + blueBias;

	public HistogramView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		p.setStyle(Paint.Style.FILL);
		redPath = new Path();
		bluePath = new Path();
		greenPath = new Path();
	}

	public void updateHistogram(Histogram hist)
	{
		redPath.reset();
		greenPath.reset();
		bluePath.reset();

		// Bottom-right corner
		redPath.moveTo(histWidth, redStart);
		greenPath.moveTo(histWidth, greenStart);
		bluePath.moveTo(histWidth, blueStart);

		// Create baseline
		redPath.lineTo(0f, redStart);
		greenPath.lineTo(0f, greenStart);
		bluePath.lineTo(0f, blueStart);

		int width = getWidth();
		int height = getHeight();
		Matrix matrix = new Matrix();
		matrix.postScale(width / histWidth, height / totalHeight);

		int scale = hist.getMaxFreq();

		for (int band = 0; band <= 255; band++)
		{
			// Colors use their own max scale
//			redPath.lineTo(band, redBias + histHeight - histHeight * (hist.getRedFreq(band) / (float)hist.getRedMax()));
//			greenPath.lineTo(band, greenBias + histHeight - histHeight * (hist.getGreenFreq(band) / (float)hist.getGreenMax()));
//			bluePath.lineTo(band, blueBias + histHeight - histHeight * (hist.getBlueFreq(band) / (float)hist.getBlueMax()));
			// All graphs use the same scale (max color)
			redPath.lineTo(band, redBias + histHeight - histHeight * (hist.getFreqR(band) / (float) scale));
			greenPath.lineTo(band, greenBias + histHeight - histHeight * (hist.getFreqG(band) / (float) scale));
			bluePath.lineTo(band, blueBias + histHeight - histHeight * (hist.getFreqB(band) / (float) scale));
		}

		redPath.transform(matrix);
		greenPath.transform(matrix);
		bluePath.transform(matrix);
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		p.setColor(Color.RED);
		canvas.drawPath(redPath, p);
		p.setColor(Color.GREEN);
		canvas.drawPath(greenPath, p);
		p.setColor(Color.BLUE);
		canvas.drawPath(bluePath, p);
	}
}
