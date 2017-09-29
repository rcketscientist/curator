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
	// Step alpha down for stacking layers G above R above B
	private static final int BLUE = Color.argb(220, 80, 80, 255); //pure blue is hard to see on black
	private static final int RED = Color.argb(200, 255, 0, 0);
	private static final int GREEN = Color.argb(180, 0, 255, 0);

	private final Path redPath = new Path();
	private final Path bluePath = new Path();
	private final Path greenPath = new Path();

	private final Paint p = new Paint();

    private static final int BORDER_WIDTH = 1;    // Put this in because it was starting before the BORDER_POINTS...seems to be a space but higher creates garble
	private static final int COLOR_HEIGHT = 150;
    private static final int COLOR_WIDTH = 256;
    private static final int HIST_WIDTH = COLOR_WIDTH + BORDER_WIDTH * 2;
    private static final int HIST_HEIGHT = COLOR_HEIGHT + BORDER_WIDTH * 2;

    private static final int GRIDLINES = 4;
    private static final int GRID_SPACING = COLOR_WIDTH / 5;

    private static final int START = HIST_HEIGHT - BORDER_WIDTH;

    private static final float[] GRID_POINTS = new float[16 + GRIDLINES * 4];
    private static final float[] BORDER_POINTS = new float[]
            {
                    0,0, HIST_WIDTH - 1, 0,
                    HIST_WIDTH - 1, 0, HIST_WIDTH - 1, HIST_HEIGHT - 1,
                    HIST_WIDTH - 1, HIST_HEIGHT - 1, 0, HIST_HEIGHT - 1,
                    0, HIST_HEIGHT - 1, 0, 0
            };

	public HistogramView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public void updateHistogram(Histogram.ColorBins hist)
	{
        clear();

		Matrix matrix = new Matrix();
		matrix.postScale((float)getWidth() / HIST_WIDTH,
                (float)getHeight() / HIST_HEIGHT);

		int max = hist.getMaxFreq();
        float scale = COLOR_HEIGHT / (float) max;

        // Bottom-right corner
        redPath.moveTo(HIST_WIDTH - BORDER_WIDTH,START);
        greenPath.moveTo(HIST_WIDTH - BORDER_WIDTH, START);
        bluePath.moveTo(HIST_WIDTH - BORDER_WIDTH, START);

        // Create baseline
        redPath.lineTo(BORDER_WIDTH, START);
        greenPath.lineTo(BORDER_WIDTH, START);
        bluePath.lineTo(BORDER_WIDTH, START);

		for (int band = 0; band < COLOR_WIDTH; band++)
		{
			// All graphs use the same scale (max color)
			redPath.lineTo(band + BORDER_WIDTH, START - hist.red[band] * scale);
			greenPath.lineTo(band + BORDER_WIDTH, START - hist.green[band] * scale);
			bluePath.lineTo(band + BORDER_WIDTH, START - hist.blue[band] * scale);
		}

        for (int i = 0; i < GRIDLINES * 4; ++i)
        {
            int x = (i / 4 + 1) * GRID_SPACING;
            GRID_POINTS[i] = x;
            GRID_POINTS[++i] = 0;
            GRID_POINTS[++i] = x;
            GRID_POINTS[++i] = HIST_HEIGHT;
        }

		redPath.transform(matrix);
		greenPath.transform(matrix);
		bluePath.transform(matrix);
        matrix.mapPoints(GRID_POINTS);
        matrix.mapPoints(BORDER_POINTS);

		invalidate();
	}

    public void clear()
    {
        redPath.reset();
        greenPath.reset();
        bluePath.reset();
    }

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(200, 255, 255, 255));
        canvas.drawLines(GRID_POINTS, p);
//        canvas.drawLines(BORDER_POINTS, p);

        p.setStyle(Paint.Style.FILL);
		p.setColor(BLUE);
		canvas.drawPath(bluePath, p);
		p.setColor(RED);
		canvas.drawPath(redPath, p);
        p.setColor(GREEN);
        canvas.drawPath(greenPath, p);

	}
}
