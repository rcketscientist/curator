package com.anthonymandra.rawdroid.ui

import android.arch.paging.PagedList
import android.net.Uri
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
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