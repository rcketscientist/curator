package com.anthonymandra.rawdroid.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.paging.PagedList
import androidx.viewpager2.adapter.FragmentStateAdapter

abstract class PagedListPagerAdapter<T>(activity: FragmentActivity)
	: FragmentStateAdapter(activity) {

	var pagedList: PagedList<T>? = null
		private set
	private var callback = PagerCallback()

	override fun getItemCount() = pagedList?.size ?: 0

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
	}

	private inner class PagerCallback : PagedList.Callback() {
		override fun onChanged(position: Int, count: Int) = notifyDataSetChanged()
		override fun onInserted(position: Int, count: Int) = notifyDataSetChanged()
		override fun onRemoved(position: Int, count: Int) = notifyDataSetChanged()
	}
}