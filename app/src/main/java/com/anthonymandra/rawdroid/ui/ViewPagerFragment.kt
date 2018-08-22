package com.anthonymandra.rawdroid.ui

import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.android.synthetic.main.full_image.*
import java.lang.Exception

class ViewPagerFragment : Fragment() {
    var source: MetadataTest? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.full_image, container, false)

        if (savedInstanceState != null) {
            if (source == null && savedInstanceState.containsKey(BUNDLE_SOURCE)) {
                source = savedInstanceState.getParcelable(BUNDLE_SOURCE)
            }
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (source != null) {
            imageView.setRegionDecoderClass(RawImageRegionDecoder::class.java)
            imageView.setImage(RawImageSource(source!!))
            imageView.setOnImageEventListener(object: SubsamplingScaleImageView.OnImageEventListener {
                override fun onImageLoaded() {
                    textViewScale.post {
                        textViewScale.text = (imageView.scale * 100).toInt().toString() + "%"
                    }
                }

                override fun onReady() {}

                override fun onTileLoadError(e: Exception?) {}

                override fun onPreviewReleased() {}

                override fun onImageLoadError(e: Exception?) {}

                override fun onPreviewLoadError(e: Exception?) {}
            })
            imageView.setOnStateChangedListener(object: SubsamplingScaleImageView.OnStateChangedListener {
                override fun onCenterChanged(newCenter: PointF?, origin: Int) {}

                override fun onScaleChanged(newScale: Float, origin: Int) {
                    textViewScale.post {
                        textViewScale.text = (newScale * 100).toInt().toString() + "%"
                    }
                }
            })
        }

        zoomButton.setOnCheckedChangeListener { _, isChecked -> /* TODO: */ }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (view != null) {
            outState.putParcelable(BUNDLE_SOURCE, source)
        }
    }

    companion object {
        private const val BUNDLE_SOURCE = "source"
    }
}