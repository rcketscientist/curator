package com.anthonymandra.rawdroid.ui

import android.support.v4.view.ViewPager
import android.view.View

class DepthTransformer: ViewPager.PageTransformer {
    override fun transformPage(page: View, position: Float) {

        when {
            position < -1 -> // [-Infinity,-1)
                // This page is way off-screen to the left.
                page.alpha = 0F
            position <= 0 -> {    // [-1,0]
                page.alpha = 1F
                page.translationX = 0F
                page.scaleX = 1F
                page.scaleY = 1F

            }
            position <= 1 -> {    // (0,1]
                page.translationX = -position * page.width
                page.alpha = 1 - Math.abs(position)
                page.scaleX = 1 - Math.abs(position)
                page.scaleY = 1 - Math.abs(position)

            }
            else -> // (1,+Infinity]
                // This page is way off-screen to the right.
                page.alpha = 0F
        }
    }
}