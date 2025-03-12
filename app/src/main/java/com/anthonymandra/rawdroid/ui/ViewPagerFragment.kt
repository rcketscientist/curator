package com.anthonymandra.rawdroid.ui

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
import com.anthonymandra.framework.Histogram
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.rawdroid.databinding.FullImageBinding
import com.anthonymandra.rawdroid.databinding.MetaPanelBinding
import com.anthonymandra.util.AppExecutors
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
//import kotlinx.android.synthetic.main.full_image.*
//import kotlinx.android.synthetic.main.meta_panel.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ForkJoinPool

class ViewPagerFragment : Fragment() {
    var source: ImageInfo? = null
    private var histogramSubscription: Disposable? = null
    private val viewModel: GalleryViewModel by lazy {
        ViewModelProvider(this).get(GalleryViewModel::class.java)
    }

    private var _binding: FullImageBinding? = null
    private val binding get() = _binding!!

    private var _metaBinding: MetaPanelBinding? = null
    private val metaBinding get() = _metaBinding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FullImageBinding.inflate(inflater, container, false)
        val view = binding.root

        if (savedInstanceState != null) {
            if (source == null && savedInstanceState.containsKey(BUNDLE_SOURCE)) {
                source = savedInstanceState.getParcelable(BUNDLE_SOURCE)
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            binding.imageView.setRegionDecoderClass(RawImageRegionDecoder::class.java)
            binding.imageView.setImage(RawImageSource(image))
            binding.imageView.setOnImageEventListener(object: SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onImageLoaded(bitmap: WeakReference<Bitmap>) {
                    binding.textViewScale.post {
                        binding.textViewScale.text = (binding.imageView.scale * 100).toInt().toString() + "%"
                    }

                    //TODO: Is there really value to the reference?
                    bitmap.get()?.let {
                        updateHistogram(it)
                    }
                }
            })
            binding.imageView.setOnStateChangedListener(object: SubsamplingScaleImageView.DefaultOnStateChangedListener() {
                override fun onScaleChanged(newScale: Float, origin: Int) {
                    binding.textViewScale.post {
                        binding.textViewScale.text = (newScale * 100).toInt().toString() + "%"
                    }
                }
            })
            binding.imageView.setOnClickListener {
                viewModel.toggleInterface() //TODO: This needs to cancel the
            }
        }

        val viewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        viewModel.isZoomLocked.observe(viewLifecycleOwner, Observer {
            binding.imageView.isZoomEnabled = !it!!
        })

        viewModel.metadataVisibility.observe(viewLifecycleOwner, Observer { visible ->
            binding.metaPanel.root.visibility = visible
        })

        viewModel.histogramVisibility.observe(viewLifecycleOwner, Observer { visible ->
            metaBinding.histogramView.visibility = visible
        })

        binding.zoomButton.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onZoomLockChanged(isChecked)
        }

        val metaRowVisibility = viewModel.metaVisibility
        metaBinding.rowAltitude.visibility = metaRowVisibility.Altitude
        metaBinding.rowAperture.visibility = metaRowVisibility.Aperture
        metaBinding.rowDate.visibility = metaRowVisibility.Date
        metaBinding.rowDimensions.visibility = metaRowVisibility.Dimensions
        metaBinding.rowDriveMode.visibility = metaRowVisibility.DriveMode
        metaBinding.rowExposure.visibility = metaRowVisibility.Exposure
        metaBinding.rowExposureMode.visibility = metaRowVisibility.ExposureMode
        metaBinding.rowExposureProgram.visibility = metaRowVisibility.ExposureProgram
        metaBinding.rowFlash.visibility = metaRowVisibility.Flash
        metaBinding.rowFocal.visibility = metaRowVisibility.Focal
        metaBinding.rowIso.visibility = metaRowVisibility.Iso
        metaBinding.rowLatitude.visibility = metaRowVisibility.Latitude
        metaBinding.rowLongitude.visibility = metaRowVisibility.Longitude
        metaBinding.rowLens.visibility = metaRowVisibility.Lens
        metaBinding.rowModel.visibility = metaRowVisibility.Model
        metaBinding.rowName.visibility = metaRowVisibility.Name
        metaBinding.rowWhiteBalance.visibility = metaRowVisibility.WhiteBalance
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
                metaBinding.textViewDate.text = df.format(d) + " " + tf.format(d)
            }
            metaBinding.textViewModel.text = image.model
            metaBinding.textViewIso.text = image.iso
            metaBinding.textViewExposure.text = image.exposure
            metaBinding.textViewAperture.text = image.aperture
            metaBinding.textViewFocal.text = image.focalLength
            metaBinding.textViewDimensions.text = "${image.width} x ${image.height}"
            metaBinding.textViewAlt.text = image.altitude
            metaBinding.textViewFlash.text = image.flash
            metaBinding.textViewLat.text = image.latitude
            metaBinding.textViewLon.text = image.longitude
            metaBinding.textViewName.text = image.name
            metaBinding.textViewWhiteBalance.text = image.whiteBalance
            metaBinding.textViewLens.text = image.lens
            metaBinding.textViewDriveMode.text = image.driveMode
            metaBinding.textViewExposureMode.text = image.exposureMode
            metaBinding.textViewExposureProgram.text = image.exposureProgram
        }
    }

    private fun updateHistogram(bitmap: Bitmap) {
        metaBinding.histogramView.clear()

        // TODO: Need some way to cancel?
        histogramSubscription = Single.create<Histogram.ColorBins> {
//        histogramSubscription = Single.create<IntArray> {
            val hist = calculateHisto(bitmap)
//            val hist = calculateHistogram(bitmap)
            it.onSuccess(hist)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))    // TODO: Memory based pool
         .observeOn(Schedulers.from(AppExecutors.MAIN))
         .subscribeBy (
             onSuccess = { metaBinding.histogramView.updateHistogram(it) },
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