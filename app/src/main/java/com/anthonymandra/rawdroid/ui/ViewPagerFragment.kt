package com.anthonymandra.rawdroid.ui

import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.rawdroid.R
import com.davemorrissey.labs.subscaleview.ImageSource
import kotlinx.android.synthetic.main.full_image.*

class ViewPagerFragment : Fragment() {
    var source: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.full_image, container, false)

        if (savedInstanceState != null) {
            if (source == null && savedInstanceState.containsKey(BUNDLE_SOURCE)) {
                source = Uri.parse(savedInstanceState.getString(BUNDLE_SOURCE))
            }
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (source != null) {
            imageView.setRegionDecoderClass(RawImageRegionDecoder::class.java)
            imageView.setImage(ImageSource.uri(source!!))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (view != null) {
            outState.putString("source", source.toString())
        }
    }

    companion object {
        private const val BUNDLE_SOURCE = "source"
    }
}