//package com.anthonymandra.rawdroid.ui
//
//import android.arch.paging.PagedList
//import android.support.v4.app.FragmentManager
//import android.support.v4.app.FragmentStatePagerAdapter
//
//class PagedFragmentStatePagerAdapter<T>(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
//    private var mPagedList: PagedList<T>? = null
//
//    /**
//     * Set the new list to be displayed.
//     * @param pagedList The new list to be displayed.
//     */
//    fun submitList(pagedList: PagedList<T>) {
//        mPagedList = pagedList
//    }
//
//    override fun getItem(position: Int): T? {
//        val image = mPagedList?.get(position)
//
//    }
//
//    override fun getCount(): Int {
//        return mPagedList?.size ?: 0
//    }
//}