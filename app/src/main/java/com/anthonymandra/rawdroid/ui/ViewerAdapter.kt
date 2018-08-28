package com.anthonymandra.rawdroid.ui

import androidx.paging.PagedList
import androidx.core.app.Fragment
import androidx.core.app.FragmentManager
import androidx.core.app.FragmentStatePagerAdapter
import com.anthonymandra.rawdroid.data.MetadataTest

class ViewerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
    private var images: PagedList<MetadataTest>? = null

    override fun getItem(position: Int): Fragment {
        val fragment = ViewPagerFragment()
        fragment.source = images?.get(position)
        return fragment
    }

    override fun getCount(): Int {
        return images?.size ?: 0
    }

    fun getImage(position: Int): MetadataTest? {
        return images?.get(position)
    }

    fun submitList(pagedList: PagedList<MetadataTest>?) {
        images = pagedList
        notifyDataSetChanged()
    }
}