package com.anthonymandra.rawdroid.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.paging.PagedList
import androidx.viewpager.widget.ViewPager

abstract class PagedListPagerAdapter<T>(private val fm: FragmentManager,
													 private val pager: ViewPager?,
													 private val startPos: Int? = 0)
	: FragmentStatePagerAdapter(fm), LifecycleObserver {

	var pagedList: PagedList<T>? = null
		private set
	private var callback = PagerCallback()

	private var isSecondTimes = false

	override fun getCount() = pagedList?.size ?: 0

	abstract fun createItem(position: Int): Fragment

	abstract var isSmoothScroll: Boolean

	override fun getItem(position: Int): Fragment {
		pagedList?.loadAround(position)
		return createItem(position)
	}

	fun getValue(position: Int): T? {
		return pagedList?.get(position)
	}

	fun submitList(pagedList: PagedList<T>?) {
		this.pagedList?.removeWeakCallback(callback)
		this.pagedList = pagedList
		this.pagedList?.addWeakCallback(null, callback)
		notifyDataSetChanged()
		if (pager?.adapter?.count ?: 0 > 0) {
			scrollFirstToPos()
		}
	}

	private inner class PagerCallback : PagedList.Callback() {
		override fun onChanged(position: Int, count: Int) =
			analyzeCount(position, count)

		override fun onInserted(position: Int, count: Int) =
			analyzeCount(position, count)

		override fun onRemoved(position: Int, count: Int) =
			analyzeCount(position, count)

		private fun analyzeCount(start: Int, count: Int) = analyzeRange(start, start + count)

		private fun analyzeRange(start: Int, end: Int) {
			notifyDataSetChanged()
			scrollFirstToPos()
		}
	}

	private fun scrollFirstToPos() {
		if (!isSecondTimes) {
			isSecondTimes = true
			pager?.setCurrentItem(startPos ?: 0, isSmoothScroll)
		}
	}

	// TODO: Is this necessary?
	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	private fun cleanStack() {
		pagedList?.removeWeakCallback(callback)	// TODO: Added, seems like a good idea to clean ref
//		fm.beginTransaction()
//			.remove(fm.fragments.removeAt(fm.fragments.size-3))
//			.remove(fm.fragments.removeAt(fm.fragments.size-2))
//			.remove(fm.fragments.removeAt(fm.fragments.size-1))
//			.commit()
	}
}