package com.anthonymandra.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

import com.anthonymandra.framework.Histogram

class HistogramView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val redPath = Path()
    private val bluePath = Path()
    private val greenPath = Path()

    private val p = Paint()

    fun updateHistogram(hist: IntArray) {
        clear()

        var i = 0
        while (i < GRIDLINES * 4) {
            val x = (i / 4 + 1) * GRID_SPACING
            GRID_POINTS[i] = x.toFloat()
            GRID_POINTS[++i] = 0f
            GRID_POINTS[++i] = x.toFloat()
            GRID_POINTS[++i] = HIST_HEIGHT.toFloat()
            ++i
        }

        // Bottom-right corner
        redPath.moveTo((HIST_WIDTH - BORDER_WIDTH).toFloat(), START.toFloat())
        greenPath.moveTo((HIST_WIDTH - BORDER_WIDTH).toFloat(), START.toFloat())
        bluePath.moveTo((HIST_WIDTH - BORDER_WIDTH).toFloat(), START.toFloat())

        // Create baseline
        redPath.lineTo(BORDER_WIDTH.toFloat(), START.toFloat())
        greenPath.lineTo(BORDER_WIDTH.toFloat(), START.toFloat())
        bluePath.lineTo(BORDER_WIDTH.toFloat(), START.toFloat())

        var max = 0
        for ((index, value) in hist.withIndex()) {
            redPath.lineTo((index + BORDER_WIDTH).toFloat(), START - Color.red(value).toFloat())
            greenPath.lineTo((index + BORDER_WIDTH).toFloat(), START - Color.green(value).toFloat())
            bluePath.lineTo((index + BORDER_WIDTH).toFloat(), START - Color.blue(value).toFloat())

            max = maxOf(max,
                    maxOf(Color.red(value),
                        maxOf(Color.green(value), Color.blue(value))))
        }

        val scale = COLOR_HEIGHT / max.toFloat()
        val matrix = Matrix()
        matrix.postScale(width.toFloat() / HIST_WIDTH,
                height.toFloat() / HIST_HEIGHT /** scale*/)

        redPath.transform(matrix)
        greenPath.transform(matrix)
        bluePath.transform(matrix)
        matrix.mapPoints(GRID_POINTS)
        matrix.mapPoints(BORDER_POINTS)

        invalidate()
    }

    fun updateHistogram(hist: Histogram.ColorBins) {
        clear()

        val matrix = Matrix()
        matrix.postScale(width.toFloat() / HIST_WIDTH,
                height.toFloat() / HIST_HEIGHT)

        val max = hist.maxFreq
        val scale = COLOR_HEIGHT / max.toFloat()

        // Bottom-right corner
        redPath.moveTo((HIST_WIDTH - BORDER_WIDTH).toFloat(), START.toFloat())
        greenPath.moveTo((HIST_WIDTH - BORDER_WIDTH).toFloat(), START.toFloat())
        bluePath.moveTo((HIST_WIDTH - BORDER_WIDTH).toFloat(), START.toFloat())

        // Create baseline
        redPath.lineTo(BORDER_WIDTH.toFloat(), START.toFloat())
        greenPath.lineTo(BORDER_WIDTH.toFloat(), START.toFloat())
        bluePath.lineTo(BORDER_WIDTH.toFloat(), START.toFloat())

        for (band in 0 until COLOR_WIDTH) {
            // All graphs use the same scale (max color)
            redPath.lineTo((band + BORDER_WIDTH).toFloat(), START - hist.red[band] * scale)
            greenPath.lineTo((band + BORDER_WIDTH).toFloat(), START - hist.green[band] * scale)
            bluePath.lineTo((band + BORDER_WIDTH).toFloat(), START - hist.blue[band] * scale)
        }

        var i = 0
        while (i < GRIDLINES * 4) {
            val x = (i / 4 + 1) * GRID_SPACING
            GRID_POINTS[i] = x.toFloat()
            GRID_POINTS[++i] = 0f
            GRID_POINTS[++i] = x.toFloat()
            GRID_POINTS[++i] = HIST_HEIGHT.toFloat()
            ++i
        }

        redPath.transform(matrix)
        greenPath.transform(matrix)
        bluePath.transform(matrix)
        matrix.mapPoints(GRID_POINTS)
        matrix.mapPoints(BORDER_POINTS)

        invalidate()
    }

    fun clear() {
        redPath.reset()
        greenPath.reset()
        bluePath.reset()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        p.style = Paint.Style.STROKE
        p.color = Color.argb(200, 255, 255, 255)
        canvas.drawLines(GRID_POINTS, p)
        //        canvas.drawLines(BORDER_POINTS, p);

        p.style = Paint.Style.FILL
        p.color = BLUE
        canvas.drawPath(bluePath, p)
        p.color = RED
        canvas.drawPath(redPath, p)
        p.color = GREEN
        canvas.drawPath(greenPath, p)

    }

    companion object {
        // Step alpha down for stacking layers G above R above B
        private val BLUE = Color.argb(220, 80, 80, 255) //pure blue is hard to see on black
        private val RED = Color.argb(200, 255, 0, 0)
        private val GREEN = Color.argb(180, 0, 255, 0)

        private val BORDER_WIDTH = 1    // Put this in because it was starting before the BORDER_POINTS...seems to be a space but higher creates garble
        private val COLOR_HEIGHT = 150
        private val COLOR_WIDTH = 256
        private val HIST_WIDTH = COLOR_WIDTH + BORDER_WIDTH * 2
        private val HIST_HEIGHT = COLOR_HEIGHT + BORDER_WIDTH * 2

        private val GRIDLINES = 4
        private val GRID_SPACING = COLOR_WIDTH / 5

        private val START = HIST_HEIGHT - BORDER_WIDTH

        private val GRID_POINTS = FloatArray(16 + GRIDLINES * 4)
        private val BORDER_POINTS = floatArrayOf(0f, 0f, (HIST_WIDTH - 1).toFloat(), 0f, (HIST_WIDTH - 1).toFloat(), 0f, (HIST_WIDTH - 1).toFloat(), (HIST_HEIGHT - 1).toFloat(), (HIST_WIDTH - 1).toFloat(), (HIST_HEIGHT - 1).toFloat(), 0f, (HIST_HEIGHT - 1).toFloat(), 0f, (HIST_HEIGHT - 1).toFloat(), 0f, 0f)
    }
}
