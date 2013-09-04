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
	private static final int histSpacer = 2;
    private static final int numGridlines = 4;

	private static final int redBias = 0;
	private static final int greenBias = 100 + histSpacer;
	private static final int blueBias = 200 + 2 * histSpacer;
    private static final int borderBias = 1;    // Put this in because it was starting before the border...seems to be a space but higher creates garble

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

		int width = getWidth();
		int height = getHeight();
		Matrix matrix = new Matrix();
		matrix.postScale(width / histWidth, 1);

		int scale = hist.getMaxFreq();
        int histHeight = (getHeight() - 4 - histSpacer * 2) / 3; // 1 px border requires 4...not sure why (implies 2 px border if you ask me...

        int blueStart = height - 2;
        int greenStart = blueStart - histHeight - histSpacer;
        int redStart = greenStart - histHeight  - histSpacer;

        // Bottom-right corner
        redPath.moveTo(histWidth, redStart);
        greenPath.moveTo(histWidth, greenStart);
        bluePath.moveTo(histWidth, blueStart);

        // Create baseline, bias assumes border
        redPath.lineTo(borderBias, redStart);
        greenPath.lineTo(borderBias, greenStart);
        bluePath.lineTo(borderBias, blueStart);

		for (int band = 0; band < histWidth; band++)
		{
			// All graphs use the same scale (max color)
			redPath.lineTo(band + borderBias, redStart - histHeight * (hist.getFreqR(band) / (float) scale));
			greenPath.lineTo(band + borderBias, greenStart - histHeight * (hist.getFreqG(band) / (float) scale));
			bluePath.lineTo(band + borderBias, blueStart - histHeight * (hist.getFreqB(band) / (float) scale));
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

        int width = canvas.getWidth();
        int height = canvas.getHeight();
        int spacing = width / 5;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(200, 255, 255, 255));

        for (int i = 1; i <= numGridlines; ++i)
        {
            int x = i * spacing;
            canvas.drawLine(x, 0, x, height, p);
        }

        p.setStyle(Paint.Style.FILL);
		p.setColor(Color.argb(190, 255, 0, 0));
		canvas.drawPath(redPath, p);
        p.setColor(Color.argb(190, 0, 255, 0));
        canvas.drawPath(greenPath, p);
        p.setColor(Color.argb(190, 0, 0, 255));
        canvas.drawPath(bluePath, p);
	}
}
