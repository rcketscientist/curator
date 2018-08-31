package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.ViewPager
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
import kotlinx.android.synthetic.main.viewer_pager.*
import java.util.*

class ViewerActivity : CoreActivity() {
    override val contentView = R.layout.viewer_pager
    private lateinit var viewerAdapter: ViewerAdapter
    private val responseIntentFilter = IntentFilter()
    private var currentImage: MetadataTest? = null

    private var autoHide = Timer()
    private var isInterfaceHidden: Boolean = false  //TODO: viewmodel
    private var shouldShowInterface = true

    override val selectedImages: Collection<MetadataTest>
        get() = listOfNotNull(viewerAdapter.getImage(pager.currentItem))

    private val viewModel: GalleryViewModel by lazy {
        ViewModelProviders.of(this).get(GalleryViewModel::class.java) }

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

//        val viewModel = ViewModelProviders.of(this).get(GalleryViewModel::class.java)
        viewModel.imageList.observe(this, Observer {
            viewerAdapter.submitList(it)
            // TODO: Can we get an update?  Will the entries reorder on changes?
            pager.setCurrentItem(intent.getIntExtra(EXTRA_START_INDEX, 0), false)
        })

        viewModel.isInterfaceVisible.observe(this, Observer { visible ->
            val comparator = if (visible) "Never" else "Always"
            if (settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic") != comparator) {
                layoutNavButtons.visibility = View.VISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowToolbar, "Always") != comparator) {
                supportActionBar?.show()
            }

            if (settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic") != comparator) {
                layoutNavButtons.visibility = View.INVISIBLE
            }
            if (settings.getString(FullSettingsActivity.KEY_ShowToolbar, "Always") != comparator) {
                supportActionBar?.hide()
            }
        })

        viewModel.setFilter(intent.getParcelableExtra(EXTRA_FILTER))

        pager.adapter = viewerAdapter
        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                currentImage = viewerAdapter.getImage(position)
                currentImage?.let {
                    xmpEditFragment.initXmp(
                            it.rating?.toInt(),
                            Collections.emptyList()/*it.subjectIds*/,
                            it.label)
                }
                autoHide.cancel()
                autoHide = Timer()
                autoHide.schedule(AutoHideMetaTask(), 3000)
                updateImageDetails()
            }
        })
        // TODO: Jetifier not working on page transformer
//        pager.setPageTransformer(true, DepthPageTransformer())
        pager.offscreenPageLimit = 2

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

        imageButtonNext.setOnClickListener { pager.currentItem = pager.currentItem + 1 }
        imageButtonPrevious.setOnClickListener { pager.currentItem = pager.currentItem - 1 }
    }

    override fun onBackPressed() {
        val data = Intent()
        data.putExtra(GalleryActivity.GALLERY_INDEX_EXTRA, pager.currentItem)
        setResult(RESULT_OK, data)
        super.onBackPressed()
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
        autoHide.cancel()

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_options, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        autoHide.cancel()
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

    private inner class AutoHideMetaTask : TimerTask() {
        override fun run() {
            viewModel.onInterfaceVisibilityChanged(false)
        }
    }

    companion object {
        val TAG = ViewerActivity::class.java.simpleName
        const val EXTRA_START_INDEX = "viewer_index"
        const val EXTRA_FILTER = "viewer_filter"
    }
}