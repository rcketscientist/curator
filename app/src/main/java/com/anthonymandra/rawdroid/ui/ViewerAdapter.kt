package com.anthonymandra.rawdroid.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.anthonymandra.rawdroid.data.ImageInfo

class ViewerAdapter(fm: FragmentManager)
	: PagedListPagerAdapter<ImageInfo>(fm) {

	override var isSmoothScroll = false

	override fun createItem(position: Int): Fragment {
		val fragment = ViewPagerFragment()
		fragment.source = getValue(position)
		return fragment
	}
}