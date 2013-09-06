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
	private Path redPath;
	private Path bluePath;
	private Path greenPath;

	private Paint p = new Paint();

    private static final int BORDER_WIDTH = 1;    // Put this in because it was starting before the BORDER_POINTS...seems to be a space but higher creates garble
	private static final int COLOR_HEIGHT = 100;
    private static final int COLOR_WIDTH = 256;
	private static final int HIST_SPACER = 2;
    private static final int HIST_WIDTH = COLOR_WIDTH + BORDER_WIDTH * 2;
    private static final int HIST_HEIGHT = COLOR_HEIGHT * 3 + HIST_SPACER * 2 + BORDER_WIDTH * 2;

    private static final int GRIDLINES = 4;
    private static final int GRID_SPACING = COLOR_WIDTH / 5;

    private static final int BLUE_START = HIST_HEIGHT - BORDER_WIDTH;
    private static final int GREEN_START = BLUE_START - COLOR_HEIGHT - HIST_SPACER;
    private static final int RED_START = GREEN_START - COLOR_HEIGHT - HIST_SPACER;

    private static final float[] GRID_POINTS = new float[16 + GRIDLINES * 4];
    // Only works the first time, disabled for now
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
		redPath = new Path();
		bluePath = new Path();
		greenPath = new Path();
	}

	public void updateHistogram(Histogram hist)
	{
        clear();

		Matrix matrix = new Matrix();
		matrix.postScale((float)getWidth() / HIST_WIDTH,
                (float)getHeight() / HIST_HEIGHT);

		int max = hist.getMaxFreq();
        float scale = COLOR_HEIGHT / (float) max;

        // Bottom-right corner
        redPath.moveTo(HIST_WIDTH - BORDER_WIDTH, RED_START);
        greenPath.moveTo(HIST_WIDTH - BORDER_WIDTH, GREEN_START);
        bluePath.moveTo(HIST_WIDTH - BORDER_WIDTH, BLUE_START);

        // Create baseline
        redPath.lineTo(BORDER_WIDTH, RED_START);
        greenPath.lineTo(BORDER_WIDTH, GREEN_START);
        bluePath.lineTo(BORDER_WIDTH, BLUE_START);

		for (int band = 0; band < COLOR_WIDTH; band++)
		{
			// All graphs use the same scale (max color)
			redPath.lineTo(band + BORDER_WIDTH, RED_START - hist.getFreqR(band) * scale);
			greenPath.lineTo(band + BORDER_WIDTH, GREEN_START - hist.getFreqG(band) * scale);
			bluePath.lineTo(band + BORDER_WIDTH, BLUE_START - hist.getFreqB(band) * scale);
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
		p.setColor(Color.argb(190, 255, 0, 0));
		canvas.drawPath(redPath, p);
        p.setColor(Color.argb(190, 0, 255, 0));
        canvas.drawPath(greenPath, p);
        p.setColor(Color.argb(190, 0, 0, 255));
        canvas.drawPath(bluePath, p);
	}
}
