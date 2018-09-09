package com.anthonymandra.rawdroid.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.renderscript.*
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.anthonymandra.framework.Histogram
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.AppExecutors
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.full_image.*
import kotlinx.android.synthetic.main.meta_panel.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ForkJoinPool

class ViewPagerFragment : Fragment() {
    var source: MetadataTest? = null
    private var histogramSubscription: Disposable? = null
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
        // TODO: this let never enters...
//        source?.let { image ->
            if (!source!!.processed) {
                // TODO: update meta
            } else {
                populateMeta()
            }
            imageView.setRegionDecoderClass(RawImageRegionDecoder::class.java)
            imageView.setImage(RawImageSource(source!!))
            imageView.setOnImageEventListener(object: SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onImageLoaded(bitmap: WeakReference<Bitmap>) {
                    textViewScale?.post {
                        textViewScale.text = (imageView.scale * 100).toInt().toString() + "%"
                    }

                    //TODO: Is there really value to the reference?
                    bitmap.get()?.let {
                        updateHistogram(it)
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
            imageView.setOnClickListener {
                viewModel.toggleInterface() //TODO: This needs to cancel the
            }
//        }

        val viewModel = ViewModelProviders.of(activity!!).get(GalleryViewModel::class.java)
        viewModel.isZoomLocked.observe(this, Observer {
            imageView.isZoomEnabled = !it!!
        })

        viewModel.metadataVisibility.observe(this, Observer { visible ->
            metaPanel.visibility = visible
        })

        viewModel.histogramVisibility.observe(this, Observer { visible ->
            histogramView.visibility = visible
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

    @SuppressLint("SetTextI18n")
    private fun populateMeta() {
        source?.let { image ->
            val timestamp = image.timestamp
            if (timestamp != null) {
                val d = Date(timestamp)
                val df = DateFormat.getDateFormat(activity)
                val tf = DateFormat.getTimeFormat(activity)
                textViewDate.text = df.format(d) + " " + tf.format(d)
            }
            textViewModel.text = image.model
            textViewIso.text = image.iso
            textViewExposure.text = image.exposure
            textViewAperture.text = image.exposure
            textViewFocal.text = image.focalLength
            textViewDimensions.text = "${image.width} x ${image.height}"
            textViewAlt.text = image.altitude
            textViewFlash.text = image.flash
            textViewLat.text = image.latitude
            textViewLon.text = image.longitude
            textViewName.text = image.name
            textViewWhiteBalance.text = image.whiteBalance
            textViewLens.text = image.lens
            textViewDriveMode.text = image.driveMode
            textViewExposureMode.text = image.exposureMode
            textViewExposureProgram.text = image.exposureProgram
        }
    }

    private fun updateHistogram(bitmap: Bitmap) {
        histogramView.clear()

        // TODO: Need some way to cancel?
        histogramSubscription = Single.create<Histogram.ColorBins> {
//        histogramSubscription = Single.create<IntArray> {
            val hist = calculateHisto(bitmap)
//            val hist = calculateHistogram(bitmap)
            it.onSuccess(hist)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))    // TODO: Memory based pool
         .observeOn(Schedulers.from(AppExecutors.MAIN))
         .subscribeBy (
             onSuccess = { histogramView.updateHistogram(it) },
             onError = { it.printStackTrace() }     // TODO: Handle error state in histogramView
         )
    }

    // TODO: For some reason I'm only getting green and blue from this
    @WorkerThread
    private fun calculateHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256)
        val rsContext = RenderScript.create(context, RenderScript.ContextType.NORMAL)
        val inAlloc = Allocation.createFromBitmap(rsContext, bitmap)
//        val outType = Type.Builder(rsContext, Element.I32(rsContext)).setX(256).create()
        val outType = Type.Builder(rsContext, Element.U32(rsContext)).setX(256).create()
        val outAlloc = Allocation.createTyped(rsContext, outType, Allocation.USAGE_SCRIPT)
        val histoScript = ScriptIntrinsicHistogram.create(rsContext, inAlloc.element)
        histoScript.setOutput(outAlloc)
        histoScript.forEach(inAlloc)
        outAlloc.copyTo(histogram)
        return histogram
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