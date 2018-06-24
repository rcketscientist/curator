package com.anthonymandra.rawdroid.ui

import com.anthonymandra.rawdroid.R.id.imageView
import android.os.Bundle
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View


class ViewPagerFragment {
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(layout.view_pager_page, container, false)

        if (savedInstanceState != null) {
            if (asset == null && savedInstanceState.containsKey(BUNDLE_ASSET)) {
                asset = savedInstanceState.getString(BUNDLE_ASSET)
            }
        }
        if (asset != null) {
            val imageView = rootView.findViewById(id.imageView)
            imageView.setImage(ImageSource.asset(asset))
        }

        return rootView
    }
}