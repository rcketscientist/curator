package com.anthonymandra.rawdroid.ui

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.android.synthetic.main.full_image.*

class ViewPagerFragment : Fragment() {
    var source: MetadataTest? = null
    private val viewModel: GalleryViewModel by lazy {
        ViewModelProviders.of(activity!!).get(GalleryViewModel::class.java) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.full_image, container, false)

        if (savedInstanceState != null) {
            if (source == null && savedInstanceState.containsKey(BUNDLE_SOURCE)) {
                source = savedInstanceState.getParcelable(BUNDLE_SOURCE)
            }
        }
        return rootView
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (source != null) {
            imageView.setRegionDecoderClass(RawImageRegionDecoder::class.java)
            imageView.setImage(RawImageSource(source!!))
            imageView.setOnImageEventListener(object: SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onImageLoaded() {
                    textViewScale?.post {
                        textViewScale.text = (imageView.scale * 100).toInt().toString() + "%"
                    }
                }
            })
            imageView.setOnStateChangedListener(object: SubsamplingScaleImageView.DefaultOnStateChangedListener() {
                override fun onScaleChanged(newScale: Float, origin: Int) {
                    textViewScale?.post {
                        textViewScale.text = (newScale * 100).toInt().toString() + "%"
                    }
                }
            })
            imageView.setOnClickListener {  }
        }

        val viewModel = ViewModelProviders.of(activity!!).get(GalleryViewModel::class.java)
        viewModel.isZoomLocked.observe(this, Observer {
            imageView.isZoomEnabled = !it
        })

        zoomButton.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onZoomLockChanged(isChecked)
        }
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