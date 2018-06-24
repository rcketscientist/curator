package com.anthonymandra.rawdroid.ui

import android.arch.paging.PagedList
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.anthonymandra.rawdroid.data.MetadataTest

class ViewerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
    private var images: PagedList<MetadataTest>? = null

    override fun getItem(position: Int): Fragment {
        val fragment = ViewPagerFragment()
        fragment.setAsset(images.get(position)) // TODO: Use the livedata somehow
        return fragment
    }

    override fun getCount(): Int {
        return images?.size ?: 0
    }

    fun submitList(pagedList: PagedList<T>) {
        images = pagedList
    }
}