package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.ViewPager
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.anthonymandra.content.Meta
import com.anthonymandra.framework.CoreActivity
import com.anthonymandra.framework.MetaService
import com.anthonymandra.framework.MetaWakefulReceiver
import com.anthonymandra.framework.SwapProvider
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.rawdroid.ui.GalleryViewModel
import com.anthonymandra.rawdroid.ui.ViewerAdapter
import com.anthonymandra.util.ImageUtil
import com.eftimoff.viewpagertransformers.DepthPageTransformer
import kotlinx.android.synthetic.main.meta_panel.*
import kotlinx.android.synthetic.main.nav_panel.*
import kotlinx.android.synthetic.main.viewer_pager.*
import java.util.*

class ViewerActivity : CoreActivity() {
    override val contentView = R.layout.viewer_pager
    private lateinit var viewerAdapter: ViewerAdapter
    private val responseIntentFilter = IntentFilter()
    private var currentImage: MetadataTest? = null

    private var autoHide: Timer? = null
    private var isInterfaceHidden: Boolean = false
    private var shouldShowInterface = true

    override val selectedImages: Collection<MetadataTest>
        get() = listOfNotNull(viewerAdapter.getImage(pager.currentItem))

    private var displayWidth = 0
    private var displayHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(FullSettingsActivity.getMetaStyle(this), true)   //must be called before setContentView

        super.onCreate(savedInstanceState)

        setSupportActionBar(viewerToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setMetaVisibility()
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
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

        isImmersive = settings.getBoolean(FullSettingsActivity.KEY_UseImmersive, true)
        shouldShowInterface = settings.getBoolean(FullSettingsActivity.KEY_ShowImageInterface, true)

        viewerAdapter = ViewerAdapter(supportFragmentManager)

        val viewModel = ViewModelProviders.of(this).get(GalleryViewModel::class.java)
        viewModel.imageList.observe(this, Observer {
            viewerAdapter.submitList(it)
            // TODO: Can we get an update?  Will the entries reorder on changes?
            pager.setCurrentItem(intent.getIntExtra(EXTRA_START_INDEX, 0), false)
        })

        viewModel.setFilter(intent.getParcelableExtra(EXTRA_FILTER))

        pager.adapter = viewerAdapter
        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                currentImage = viewerAdapter.getImage(position)
                updateImageDetails()
            }
        })
        pager.setPageTransformer(true, DepthPageTransformer())

        responseIntentFilter.addAction(MetaService.BROADCAST_REQUESTED_META)
        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    MetaService.BROADCAST_REQUESTED_META -> {
                        val uri = intent.getStringExtra(MetaService.EXTRA_URI)
                        val meta = intent.getParcelableExtra<MetadataTest>(MetaService.EXTRA_METADATA)
                        currentImage?.let {
                            if (it.uri == uri) {
                                populateMeta(meta)
                            }
                        }
                    }
                }
            }
        }, responseIntentFilter)
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
        autoHide?.cancel()

        if (isInterfaceHidden)
            showPanels()
        else
            hidePanels()
    }

    protected fun updateMetaData() {
        clearMeta()    // clear panel during load to avoid confusion

        currentImage?.let {
            if (!it.processed)
                MetaWakefulReceiver.startPriorityMetaService(this@ViewerActivity, Uri.parse(it.uri))
            else {
                populateMeta(it)
            }
        }
    }

    private fun updateImageDetails() {
        updateMetaData()
//        updateHistogram(currentBitmap)
        if(shouldShowInterface)
            showPanels()
    }

    // TODO: Histogram needs to be created elsewise
//    protected fun updateHistogram(bitmap: Bitmap?) {
//        if (bitmap == null) {
//            mRequiresHistogramUpdate = true
//            return
//        }
//        mRequiresHistogramUpdate = false
//
//        mHistogramTask?.cancel(true)
//        val histogramTask = HistogramTask()
//        histogramTask.execute(bitmap)
//        mHistogramTask = histogramTask
//    }

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

        val rating = image.rating
        // TODO: Lots of hacks here
        xmpEditFragment.initXmp(
                rating?.toInt(),
                Collections.emptyList()/*image.subjectIds*/,
                image.label ?: "")

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

    private inner class AutoHideMetaTask : TimerTask() {
        override fun run() {
            hidePanels()
        }
    }

//    protected inner class HistogramTask : AsyncTask<Bitmap, Void, Histogram.ColorBins>() {
//        override fun onPreExecute() {
//            super.onPreExecute()
//            histogramView.clear()
//        }
//
//        // FIXME: Use rx
//        override fun doInBackground(vararg params: Bitmap): Histogram.ColorBins? {
//            val input = params[0]
//
//            if (input.isRecycled) return null
//
//            val pool = ForkJoinPool()
//            val result = Histogram.createHistogram(input)
//            return pool.invoke<Histogram.ColorBins>(result)
//        }
//
//        override fun onPostExecute(result: Histogram.ColorBins?) {
//            if (result != null && !isCancelled)
//                histogramView.updateHistogram(result)
//        }
//    }

    // Pretty sure this is unneeded.
    //    @Override
    //    public void onConfigurationChanged(Configuration newConfig) {
    //        super.onConfigurationChanged(newConfig);
    //        updateImageDetails();   // For small screens this will fix the meta panel shape
    //    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_options, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        autoHide?.cancel()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.view_edit -> {
                editImage()
                true
            }
            R.id.view_wallpaper -> {
                setWallpaper()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun editImage() {
        val media = currentImage

        val format = PreferenceManager.getDefaultSharedPreferences(this).getString(
                FullSettingsActivity.KEY_EditFormat,
                resources.getStringArray(R.array.shareFormats)[0])

        val intent = Intent(Intent.ACTION_EDIT)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        if ("JPG" == format) {
            intent.setDataAndType(SwapProvider.createSwapUri(this, Uri.parse(media?.uri)), "image/jpeg")
        } else if ("RAW" == format) {
            intent.setDataAndType(Uri.parse(media?.uri), "image/*")
        }

        val chooser = Intent.createChooser(intent, resources.getString(R.string.editWith))
        startActivity(chooser)
    }

    private fun setWallpaper() {
        try {
            WallpaperManager.getInstance(this).setBitmap(ImageUtil.createBitmapToSize(
                    ImageUtil.getThumb(this, Uri.parse(currentImage?.uri)), displayWidth, displayHeight))
        } catch (e: Exception) {
            Log.e(ViewerActivity.TAG, e.toString())
            Toast.makeText(this@ViewerActivity, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show()
        }

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

    override fun setMaxProgress(max: Int) {}    //TODO: Clearly progress no longer belongs in core activity

    override fun incrementProgress() {}

    override fun endProgress() {}

    override fun updateMessage(message: String?) {}

    override fun onImageAdded(item: MetadataTest) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onImageRemoved(item: MetadataTest) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onImageSetChanged() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        val TAG = ViewerActivity::class.java.simpleName
        const val EXTRA_START_INDEX = "viewer_index"
        const val EXTRA_FILTER = "viewer_filter"
    }
}