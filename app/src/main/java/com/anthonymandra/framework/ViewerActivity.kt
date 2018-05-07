package com.anthonymandra.framework

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.WallpaperManager
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.widget.Toolbar
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.android.gallery3d.app.DataListener
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.Constants
import com.anthonymandra.rawdroid.FullSettingsActivity
import com.anthonymandra.rawdroid.GalleryActivity
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.ImageUtil
import kotlinx.android.synthetic.main.meta_panel.*
import kotlinx.android.synthetic.main.nav_panel.*
import java.util.*
import java.util.concurrent.ForkJoinPool

abstract class ViewerActivity : CoreActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    ScaleChangedListener,
    DataListener {

    private var autoHide: Timer? = null

    private var isInterfaceHidden: Boolean = false

    protected var mImageIndex: Int = 0

    private var mCurrrentImage: MetadataTest? = null

    abstract val currentItem: MetadataTest?
    abstract val currentBitmap: Bitmap

    protected val mMediaItems: MutableList<MetadataTest> = ArrayList()

    /**
     * Since initial image configuration can occur BEFORE image generation
     * this flag allows us to specifically update a null histogram.  Without
     * flag, histogram could be regenerated for each layer (thumb, big, full raw, etc)
     */
    protected var mRequiresHistogramUpdate: Boolean = false
    private var mHistogramTask: HistogramTask? = null

    private val mResponseIntentFilter = IntentFilter()
    abstract fun goToPrevPicture()
    abstract fun goToNextPicture()

    override val selectedImages: Collection<MetadataTest> = listOfNotNull(mCurrrentImage)

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(FullSettingsActivity.getMetaStyle(this), true)   //must be called before setContentView
        super.onCreate(savedInstanceState)
        val toolbar = findViewById<View>(R.id.viewerToolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        initialize()

/*        if (intent.hasExtra(CoreActivity.EXTRA_META_BUNDLE)) {
            mImageIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

            // FIXME: Need new backing array
            val dbQuery = intent.getBundleExtra(CoreActivity.EXTRA_META_BUNDLE)
            contentResolver.query(
                Meta.CONTENT_URI,
                arrayOf(Meta.URI),
                dbQuery.getString(CoreActivity.META_SELECTION_KEY),
                dbQuery.getStringArray(CoreActivity.META_SELECTION_ARGS_KEY),
                dbQuery.getString(CoreActivity.META_SORT_ORDER_KEY))!!.use { c ->
                if (c.count < 1)
                    return
                else {
                    val columnIndex = c.getColumnIndex(Meta.URI)
                    while (c.moveToNext()) {
                        try {
                            val uri = c.getString(columnIndex) ?: continue
                            mMediaItems.add(Uri.parse(uri))
                        } catch (e: Exception) {
                            Crashlytics.logException(e)
                        }

                    }
                }
            }
        } else */if (intent.hasExtra(Intent.EXTRA_STREAM))
        // Correct share intent using extras
        {
            // TODO: Disabled due to need to convert uri to metadata
//            mImageIndex = 0
//            val action = intent.action
//            if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
//                mMediaItems.add(intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri)
//            } else if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
//                val images = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
//                mMediaItems.addAll(images)
//            }
        } else
        // Simple external intent, try data
        {
            // TODO: Disabled due to need to convert uri to metadata

//            mImageIndex = 0
//            val data = intent.data
//            if (data == null)
//                finish()
//            mMediaItems.add(data)
        }

        mResponseIntentFilter.addAction(MetaService.BROADCAST_REQUESTED_META)
        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    MetaService.BROADCAST_REQUESTED_META -> {
                        val uri = intent.getStringExtra(MetaService.EXTRA_URI)
                        val response = mMediaItems.first { it.uri == uri }
                        if (response == currentItem) {
                            // TODO: This is broken
                            val values = intent.getParcelableExtra<ContentValues>(MetaService.EXTRA_METADATA)
                            populateMeta(values)
                        }
                    }
                }
            }
        }, mResponseIntentFilter)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            val settings = PreferenceManager.getDefaultSharedPreferences(this)
            val useImmersive = settings.getBoolean(FullSettingsActivity.KEY_UseImmersive, true)
            if (Util.hasKitkat() && useImmersive) {
                setImmersive()
            } else {
                this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Constants.VariantCode < 12) {
            setWatermark(true)
        }
    }

    override fun onPhotoChanged(index: Int, item: MetadataTest?) {
        mCurrrentImage = item
        updateImageDetails()
    }

    @TargetApi(19)
    private fun setImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun initialize() {
        setMetaVisibility()
        setDisplayMetrics()

        imageButtonPrevious.setOnClickListener(PreviousImageClickListener())
        imageButtonNext.setOnClickListener(NextImageClickListener())
        zoomButton.setOnCheckedChangeListener { _, isChecked -> onZoomLockChanged(isChecked) }
    }

    override fun onImageAdded(item: MetadataTest) {
        mMediaItems.add(item)
    }

    override fun onImageRemoved(item: MetadataTest) {
        mMediaItems.remove(item)
    }

    private fun setWatermark(demo: Boolean) {
        val watermark = findViewById<View>(R.id.watermark)
        if (!demo)
            watermark.visibility = View.INVISIBLE
        else
            watermark.visibility = View.VISIBLE
    }

    private fun setDisplayMetrics() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
    }

    override fun onBackPressed() {
        setImageFocus()
        super.onBackPressed()
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

    protected open fun onZoomLockChanged(locked: Boolean) {
        // no base implementation
    }

    //    @Override
    //    public void onSubUiVisibilityChanged(boolean isVisible) {
    //        if (isVisible && autoHide != null)
    //            autoHide.cancel();
    //    }

    private inner class PreviousImageClickListener : View.OnClickListener {

        override fun onClick(v: View) {
            goToPrevPicture()
        }
    }

    private inner class NextImageClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            goToNextPicture()
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
        autoHide?.cancel()

        if (isInterfaceHidden)
            showPanels()
        else
            hidePanels()
    }

    protected fun updateMetaData() {
        val image = currentItem ?: return

        clearMeta()    // clear panel during load to avoid confusion

        // Start a meta check/process on a high priority.
        MetaWakefulReceiver.startPriorityMetaService(this@ViewerActivity, Uri.parse(image.uri))
    }

    private fun updateImageDetails() {
        updateMetaData()
        updateHistogram(currentBitmap)
        if (PreferenceManager.getDefaultSharedPreferences(this@ViewerActivity)
                .getBoolean(FullSettingsActivity.KEY_ShowImageInterface, true)) {
            showPanels()
        }
    }

    protected fun updateHistogram(bitmap: Bitmap?) {
        if (bitmap == null) {
            mRequiresHistogramUpdate = true
            return
        }
        mRequiresHistogramUpdate = false

        mHistogramTask?.cancel(true)
        val histogramTask = HistogramTask()
        histogramTask.execute(bitmap)
        mHistogramTask = histogramTask
    }

    @SuppressLint("SetTextI18n")
    private fun populateMeta(values: ContentValues?) {
        if (values == null)
            return

        autoHide?.cancel()

        if (textViewDate == null) {
            Toast.makeText(this,
                "Could not access metadata views, please email me!",
                Toast.LENGTH_LONG).show()
            return
        }

        val timestamp = values.getAsLong(Meta.TIMESTAMP)
        if (timestamp != null) {
            val d = Date(timestamp)
            val df = DateFormat.getDateFormat(this)
            val tf = DateFormat.getTimeFormat(this)
            textViewDate.text = df.format(d) + " " + tf.format(d)
        }
        textViewModel.text = values.getAsString(Meta.MODEL)
        textViewIso.text = values.getAsString(Meta.ISO)
        textViewExposure.text = values.getAsString(Meta.EXPOSURE)
        textViewAperture.text = values.getAsString(Meta.APERTURE)
        textViewFocal.text = values.getAsString(Meta.FOCAL_LENGTH)
        textViewDimensions.text = values.getAsString(Meta.WIDTH) + " x " + values.getAsString(Meta.HEIGHT)
        textViewAlt.text = values.getAsString(Meta.ALTITUDE)
        textViewFlash.text = values.getAsString(Meta.FLASH)
        textViewLat.text = values.getAsString(Meta.LATITUDE)
        textViewLon.text = values.getAsString(Meta.LONGITUDE)
        textViewName.text = values.getAsString(Meta.NAME)
        textViewWhiteBalance.text = values.getAsString(Meta.WHITE_BALANCE)
        textViewLens.text = values.getAsString(Meta.LENS_MODEL)
        textViewDriveMode.text = values.getAsString(Meta.DRIVE_MODE)
        textViewExposureMode.text = values.getAsString(Meta.EXPOSURE_MODE)
        textViewExposureProgram.text = values.getAsString(Meta.EXPOSURE_PROGRAM)

        val rating = values.getAsString(Meta.RATING)  //Use string since double returns 0 for null
        xmpEditFragment.initXmp(
            if (rating == null) null else java.lang.Double.parseDouble(rating).toInt(),
            Collections.emptyList()/*DbUtil.convertStringToArray(values.getAsString(Meta.SUBJECT))*/, //FIXME:!!!
            values.getAsString(Meta.LABEL))

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

    protected inner class HistogramTask : AsyncTask<Bitmap, Void, Histogram.ColorBins>() {
        override fun onPreExecute() {
            super.onPreExecute()
            histogramView.clear()
        }

        // FIXME: Use rx
        override fun doInBackground(vararg params: Bitmap): Histogram.ColorBins? {
            val input = params[0]

            if (input.isRecycled) return null

            val pool = ForkJoinPool()
            val result = Histogram.createHistogram(input)
            return pool.invoke<Histogram.ColorBins>(result)
        }

        override fun onPostExecute(result: Histogram.ColorBins?) {
            if (result != null && !isCancelled)
                histogramView.updateHistogram(result)
        }
    }

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
        val media = currentItem

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
                ImageUtil.getThumb(this, Uri.parse(currentItem?.uri)), displayWidth, displayHeight))
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            Toast.makeText(this@ViewerActivity, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show()
        }

    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        setMetaVisibility()
        if ("prefKeyMetaSize" == key) {
            recreate() // Change to style requires a restart, theming is handled in initialize()
        }
        //        Uri media = getCurrentItem();
        //
        //        if (key.equals(FullSettingsActivity.KEY_UseLegacyViewer))
        //        {
        //            Intent viewer = getViewerIntent();
        //            viewer.setData(media);
        //            //TODO: finish() before startActivity???
        //            finish();
        //            startActivity(viewer);
        //        }
    }

    private fun setImageFocus() {
        val data = Intent()
        data.putExtra(GalleryActivity.GALLERY_INDEX_EXTRA, mImageIndex)
        setResult(RESULT_OK, data)
    }

    override fun onScaleChanged(currentScale: Float) {
        val zoom = (currentScale * 100).toInt().toString() + "%"
        textViewScale.post { textViewScale.text = zoom }
    }

    // TODO: Do we need this?
//    internal class ViewerLicenseHandler(viewer: ViewerActivity) : CoreActivity.LicenseHandler(viewer) {
//        private val mViewer: WeakReference<ViewerActivity> = WeakReference(viewer)
//
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            val state = msg.data.getSerializable(License.KEY_LICENSE_RESPONSE) as License.LicenseState
//            mViewer.get()?.setLicenseState(state)
//        }
//    }

    protected open fun setLicenseState(state: License.LicenseState) {
        val isPro = state == License.LicenseState.pro
        setWatermark(!isPro)
        zoomButton.isEnabled = isPro
    }

    companion object {
        private val TAG = ViewerActivity::class.java.simpleName

        const val EXTRA_START_INDEX = "viewer_index"

        var displayWidth: Int = 0
        var displayHeight: Int = 0
    }
}
