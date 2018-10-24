package com.anthonymandra.rawdroid.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.paging.PagedList
import com.anthonymandra.rawdroid.data.ImageInfo

class ViewerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
    private var images: PagedList<ImageInfo>? = null

    override fun getItem(position: Int): Fragment {
        val fragment = ViewPagerFragment()
        fragment.source = images?.get(position)
        return fragment
    }

    override fun getCount(): Int {
        return images?.size ?: 0
    }

    fun getImage(position: Int): ImageInfo? {
        return images?.get(position)
    }

    fun submitList(pagedList: PagedList<ImageInfo>?) {
        images = pagedList
        notifyDataSetChanged()
    }
}