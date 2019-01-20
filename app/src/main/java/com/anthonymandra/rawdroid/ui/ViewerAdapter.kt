package com.anthonymandra.rawdroid.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager
import com.anthonymandra.rawdroid.data.ImageInfo

class ViewerAdapter(activity: FragmentActivity,
						  pager: ViewPager?,
						  startPos: Int? = 0)
	: PagedListPagerAdapter<ImageInfo>(activity.supportFragmentManager, pager, startPos) {

	override var isSmoothScroll = false

	init {
		activity.lifecycle.addObserver(this)
	}

	override fun createItem(position: Int): Fragment {
		val fragment = ViewPagerFragment()
		fragment.source = getValue(position)
		return fragment
	}
}