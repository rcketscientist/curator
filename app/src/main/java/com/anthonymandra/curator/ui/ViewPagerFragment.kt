package com.anthonymandra.curator.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.anthonymandra.curator.databinding.FullImageBinding
import com.anthonymandra.framework.Histogram
import com.anthonymandra.curator.data.ImageInfo
import com.anthonymandra.util.AppExecutors
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ForkJoinPool

class ViewPagerFragment : Fragment() {
    private var _ui: FullImageBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val ui get() = _ui!!

    var source: ImageInfo? = null
    private var histogramSubscription: Disposable? = null
    private val viewModel: GalleryViewModel by lazy {
        ViewModelProvider(this).get(GalleryViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) {
            if (source == null && savedInstanceState.containsKey(BUNDLE_SOURCE)) {
                source = savedInstanceState.getParcelable(BUNDLE_SOURCE)
            }
        }
        _ui = FullImageBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: this let never enters...
        source?.let { image ->
            if (!image.processed) {
                // TODO: update meta
            } else {
                populateMeta()
            }
            ui.imageView.setRegionDecoderClass(RawImageRegionDecoder::class.java)
            ui.imageView.setImage(RawImageSource(image))
            ui.imageView.setOnImageEventListener(object: SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onImageLoaded(bitmap: WeakReference<Bitmap>) {
                    ui.textViewScale?.post {
                        ui.textViewScale?.text = (ui.imageView.scale * 100).toInt().toString() + "%"
                    }

                    //TODO: Is there really value to the reference?
                    bitmap.get()?.let {
                        updateHistogram(it)
                    }
                }
            })
            ui.imageView.setOnStateChangedListener(object: SubsamplingScaleImageView.DefaultOnStateChangedListener() {
                override fun onScaleChanged(newScale: Float, origin: Int) {
                    ui.textViewScale?.post {
                        ui.textViewScale.text = (newScale * 100).toInt().toString() + "%"
                    }
                }
            })
            ui.imageView.setOnClickListener {
                viewModel.toggleInterface() //TODO: This needs to cancel the
            }
        }

        val viewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        viewModel.isZoomLocked.observe(viewLifecycleOwner, Observer {
            ui.imageView.isZoomEnabled = !it!!
        })

        viewModel.metadataVisibility.observe(viewLifecycleOwner, Observer { visible ->
            ui.metaPanel.root.visibility = visible
        })

        viewModel.histogramVisibility.observe(viewLifecycleOwner, Observer { visible ->
            ui.metaPanel.histogramView.visibility = visible
        })

        ui.zoomButton.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onZoomLockChanged(isChecked)
        }

        val metaRowVisibility = viewModel.metaVisibility
        ui.metaPanel.rowAltitude.visibility = metaRowVisibility.Altitude
        ui.metaPanel.rowAperture.visibility = metaRowVisibility.Aperture
        ui.metaPanel.rowDate.visibility = metaRowVisibility.Date
        ui.metaPanel.rowDimensions.visibility = metaRowVisibility.Dimensions
        ui.metaPanel.rowDriveMode.visibility = metaRowVisibility.DriveMode
        ui.metaPanel.rowExposure.visibility = metaRowVisibility.Exposure
        ui.metaPanel.rowExposureMode.visibility = metaRowVisibility.ExposureMode
        ui.metaPanel.rowExposureProgram.visibility = metaRowVisibility.ExposureProgram
        ui.metaPanel.rowFlash.visibility = metaRowVisibility.Flash
        ui.metaPanel.rowFocal.visibility = metaRowVisibility.Focal
        ui.metaPanel.rowIso.visibility = metaRowVisibility.Iso
        ui.metaPanel.rowLatitude.visibility = metaRowVisibility.Latitude
        ui.metaPanel.rowLongitude.visibility = metaRowVisibility.Longitude
        ui.metaPanel.rowLens.visibility = metaRowVisibility.Lens
        ui.metaPanel.rowModel.visibility = metaRowVisibility.Model
        ui.metaPanel.rowName.visibility = metaRowVisibility.Name
        ui.metaPanel.rowWhiteBalance.visibility = metaRowVisibility.WhiteBalance
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (view != null) {
            outState.putParcelable(BUNDLE_SOURCE, source)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateMeta() {
        source?.let { image ->
            val timestamp = image.timestamp
            if (timestamp != null) {
                val d = Date(timestamp)
                val df = DateFormat.getDateFormat(activity)
                val tf = DateFormat.getTimeFormat(activity)
                ui.metaPanel.textViewDate.text = df.format(d) + " " + tf.format(d)
            }
            ui.metaPanel.textViewModel.text = image.model
            ui.metaPanel.textViewIso.text = image.iso
            ui.metaPanel.textViewExposure.text = image.exposure
            ui.metaPanel.textViewAperture.text = image.aperture
            ui.metaPanel.textViewFocal.text = image.focalLength
            ui.metaPanel.textViewDimensions.text = "${image.width} x ${image.height}"
            ui.metaPanel.textViewAlt.text = image.altitude
            ui.metaPanel.textViewFlash.text = image.flash
            ui.metaPanel.textViewLat.text = image.latitude
            ui.metaPanel.textViewLon.text = image.longitude
            ui.metaPanel.textViewName.text = image.name
            ui.metaPanel.textViewWhiteBalance.text = image.whiteBalance
            ui.metaPanel.textViewLens.text = image.lens
            ui.metaPanel.textViewDriveMode.text = image.driveMode
            ui.metaPanel.textViewExposureMode.text = image.exposureMode
            ui.metaPanel.textViewExposureProgram.text = image.exposureProgram
        }
    }

    private fun updateHistogram(bitmap: Bitmap) {
        ui.metaPanel.histogramView?.clear()

        // TODO: Need some way to cancel?
        histogramSubscription = Single.create<Histogram.ColorBins> {
//        histogramSubscription = Single.create<IntArray> {
            val hist = calculateHisto(bitmap)
//            val hist = calculateHistogram(bitmap)
            it.onSuccess(hist)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))    // TODO: Memory based pool
         .observeOn(Schedulers.from(AppExecutors.MAIN))
         .subscribeBy (
             onSuccess = { ui.metaPanel.histogramView?.updateHistogram(it) },
             onError = { it.printStackTrace() }     // TODO: Handle error state in histogramView
         )
    }

    @WorkerThread
    private fun calculateHisto(bitmap: Bitmap): Histogram.ColorBins {
        if (bitmap.isRecycled)
            throw Exception("Histogram bitmap was recycled.")
        val pool = ForkJoinPool()
        val histoTask = Histogram.createHistogram(bitmap)
        return pool.invoke<Histogram.ColorBins>(histoTask)
    }

    companion object {
        private const val BUNDLE_SOURCE = "source"
    }
}