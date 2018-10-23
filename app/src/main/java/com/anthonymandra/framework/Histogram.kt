package com.anthonymandra.framework

import android.graphics.Bitmap
import android.graphics.Color

import java.util.concurrent.RecursiveTask

class Histogram(private val mSource: IntArray, private val mStart: Int, private val mLength: Int) : RecursiveTask<Histogram.ColorBins>() {

    private fun computeDirectly(): ColorBins {
        val bins = ColorBins()
        for (index in mStart until mStart + mLength) {
            bins.red[Color.red(mSource[index])]++
            bins.green[Color.green(mSource[index])]++
            bins.blue[Color.blue(mSource[index])]++
        }
        return bins
    }

    override fun compute(): ColorBins {
        if (mLength < sThreshold) {
            return computeDirectly()
        }

        val split = mLength / 2
        val left = Histogram(mSource, mStart, split)
        val right = Histogram(mSource, split, mLength - split)
        left.fork()
        val r = right.compute()
        val l = left.join()
        r.merge(l)
        return r
    }

    class ColorBins {
        var red = IntArray(COLOR_DEPTH)
        var green = IntArray(COLOR_DEPTH)
        var blue = IntArray(COLOR_DEPTH)

        internal var maxRed: Int = 0
        internal var maxGreen: Int = 0
        internal var maxBlue: Int = 0

        val maxFreq: Int
            get() = Math.max(Math.max(maxRed, maxGreen), maxBlue)

        internal fun merge(b: ColorBins) {
            for (i in 0 until COLOR_DEPTH) {
                red[i] += b.red[i]
                green[i] += b.green[i]
                blue[i] += b.blue[i]

                maxRed = Math.max(red[i], maxRed)
                maxGreen = Math.max(green[i], maxGreen)
                maxBlue = Math.max(blue[i], maxBlue)
            }
        }
    }

    companion object {
        private val TAG = Histogram::class.java.simpleName
        private const val sThreshold = 10000
        private const val COLOR_DEPTH = 256

        fun createHistogram(bmp: Bitmap): Histogram {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            return Histogram(pixels, 0, pixels.size)
        }
    }
}
