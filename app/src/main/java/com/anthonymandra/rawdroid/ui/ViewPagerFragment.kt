package com.anthonymandra.rawdroid.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.anthonymandra.framework.Histogram
import com.anthonymandra.rawdroid.FullSettingsActivity
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
import kotlinx.android.synthetic.main.viewer_pager.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ForkJoinPool

class ViewPagerFragment : Fragment() {
    var source: MetadataTest? = null
    private var histogramSubscription: Disposable? = null
    private val viewModel: GalleryViewModel by lazy {
        ViewModelProviders.of(activity!!).get(GalleryViewModel::class.java) }
    private var isInterfaceHidden: Boolean = false  //TODO: viewmodel

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
            imageView.setOnClickListener {  }

            val settings = PreferenceManager.getDefaultSharedPreferences(context)
            settings.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
                setMetaVisibility()

                when (key) {
                    FullSettingsActivity.KEY_MetaSize -> {
                        recreate()
                    }
                    FullSettingsActivity.KEY_ShowImageInterface -> {
                        shouldShowInterface = sharedPreferences?.getBoolean(FullSettingsActivity.KEY_ShowImageInterface, true) ?: true
                    }
                }
            }
        }

        val viewModel = ViewModelProviders.of(activity!!).get(GalleryViewModel::class.java)
        viewModel.isZoomLocked.observe(this, Observer {
            imageView.isZoomEnabled = !it!!
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

    private fun showPanels() {
        isInterfaceHidden = false
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        runOnUiThread {
            if (settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic") != "Never") {
                layoutNavButtons.visibility = View.VISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic") != "Never") {
                tableLayoutMeta.visibility = View.VISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowHist, "Automatic") != "Never") {
                histogramView.visibility = View.VISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowToolbar, "Always") != "Never") {
                supportActionBar?.show()
            }
        }
    }

    private fun hidePanels() {
        isInterfaceHidden = true
        val settings = PreferenceManager.getDefaultSharedPreferences(this)

        runOnUiThread {
            if (settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic") != "Always") {
                layoutNavButtons.visibility = View.INVISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic") != "Always") {
                tableLayoutMeta.visibility = View.INVISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowHist, "Automatic") != "Always") {
                histogramView.visibility = View.INVISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowToolbar, "Always") != "Always") {
                supportActionBar?.hide()
            }
        }
    }

    fun togglePanels() {
//        autoHide?.cancel()    //TODO: viewmodel

        if (isInterfaceHidden)
            showPanels()
        else
            hidePanels()
    }

    private fun setMetaVisibility() {
        // Initially set the interface to GONE to allow settings to implement
        tableLayoutMeta.visibility = View.GONE
        layoutNavButtons.visibility = View.GONE
        histogramView.visibility = View.GONE

        val settings = PreferenceManager.getDefaultSharedPreferences(this)

        // Default true
        rowAperture.visibility =
                if (settings.getBoolean(FullSettingsActivity.KEY_ExifAperture, true))
                    View.VISIBLE
                else
                    View.GONE

        rowDate.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifDate, true)) View.VISIBLE else View.GONE
        rowExposure.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifExposure, true)) View.VISIBLE else View.GONE
        rowFocal.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifFocal, true)) View.VISIBLE else View.GONE
        rowModel.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifModel, true)) View.VISIBLE else View.GONE
        rowIso.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifIso, true)) View.VISIBLE else View.GONE
        rowLens.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifLens, true)) View.VISIBLE else View.GONE
        rowName.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifName, true)) View.VISIBLE else View.GONE

        // Default false
        rowAltitude.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifAltitude, false)) View.VISIBLE else View.GONE
        rowDimensions.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifDimensions, false)) View.VISIBLE else View.GONE
        rowDriveMode.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifDriveMode, false)) View.VISIBLE else View.GONE
        rowExposureMode.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifExposureMode, false)) View.VISIBLE else View.GONE
        rowExposureProgram.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifExposureProgram, false)) View.VISIBLE else View.GONE
        rowFlash.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifFlash, false)) View.VISIBLE else View.GONE
        rowLatitude.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifLatitude, false)) View.VISIBLE else View.GONE
        rowLongitude.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifLongitude, false)) View.VISIBLE else View.GONE
        rowWhiteBalance.visibility = if (settings.getBoolean(FullSettingsActivity.KEY_ExifWhiteBalance, false)) View.VISIBLE else View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun populateMeta(image: MetadataTest) {
        autoHide?.cancel()

        if (textViewDate == null) {
            Toast.makeText(this,
                    "Could not access metadata views, please email me!",
                    Toast.LENGTH_LONG).show()
            return
        }

        val timestamp = image.timestamp
        if (timestamp != null) {
            val d = Date(timestamp)
            val df = DateFormat.getDateFormat(this)
            val tf = DateFormat.getTimeFormat(this)
            textViewDate.text = df.format(d) + " " + tf.format(d)
        }
        textViewModel.text = image.model
        textViewIso.text = image.iso
        textViewExposure.text = image.exposure
        textViewAperture.text = image.exposure
        textViewFocal.text = image.focalLength
        textViewDimensions.text = "$image.width x $image.height"
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

        val timer = Timer()
        timer.schedule(AutoHideMetaTask(), 3000)
        autoHide = timer
    }


    private fun clearMeta() {
        textViewDate.text = ""
        textViewModel.text = ""
        textViewIso.text = ""
        textViewExposure.text = ""
        textViewAperture.text = ""
        textViewFocal.text = ""
        textViewDimensions.text = ""
        textViewAlt.text = ""
        textViewFlash.text = ""
        textViewLat.text = ""
        textViewLon.text = ""
        textViewName.text = ""
        textViewWhiteBalance.text = ""
        textViewLens.text = ""
        textViewDriveMode.text = ""
        textViewExposureMode.text = ""
        textViewExposureProgram.text = ""
    }

    private fun updateHistogram(bitmap: Bitmap) {
        histogramView.clear()

        // TODO: Need some way to cancel?
        val pool = ForkJoinPool()
        histogramSubscription = Single.create<Histogram.ColorBins> {
            if (bitmap.isRecycled)
                throw Exception("Histogram bitmap was recycled.")
            val histoTask = Histogram.createHistogram(bitmap)
            val colorBins = pool.invoke<Histogram.ColorBins>(histoTask)
            it.onSuccess(colorBins)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))    // TODO: Memory based pool
         .observeOn(Schedulers.from(AppExecutors.MAIN))
         .subscribeBy (
             onSuccess = { histogramView.updateHistogram(it) },
             onError = { it.printStackTrace() }     // TODO: Handle error state in histogramView
         )
    }

    companion object {
        private const val BUNDLE_SOURCE = "source"
    }
}