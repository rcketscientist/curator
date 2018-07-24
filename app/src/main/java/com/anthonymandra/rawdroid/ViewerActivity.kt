package com.anthonymandra.rawdroid

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import com.anthonymandra.framework.CoreActivity
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.rawdroid.ui.DepthTransformer
import com.anthonymandra.rawdroid.ui.GalleryViewModel
import com.anthonymandra.rawdroid.ui.ViewerAdapter
import kotlinx.android.synthetic.main.meta_panel.*
import kotlinx.android.synthetic.main.nav_panel.*
import kotlinx.android.synthetic.main.viewer_pager.*

class ViewerActivity : CoreActivity(),
        SharedPreferences.OnSharedPreferenceChangeListener {
    override val contentView = R.layout.viewer_pager
    private lateinit var viewerAdapter: ViewerAdapter

    override val selectedImages: Collection<MetadataTest>
        get() = listOfNotNull(viewerAdapter.getImage(pager.currentItem))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        settings.registerOnSharedPreferenceChangeListener(this)

        viewerAdapter = ViewerAdapter(supportFragmentManager)

        val viewModel = ViewModelProviders.of(this).get(GalleryViewModel::class.java)
        viewModel.imageList.observe(this, Observer {
            viewerAdapter.submitList(it)
        })

        viewModel.setFilter(intent.getParcelableExtra(EXTRA_FILTER))

        pager.adapter = viewerAdapter
        val transformer = DepthTransformer()
        pager.setPageTransformer(true, transformer)
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(intent.getIntExtra(EXTRA_START_INDEX, 0), false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        setMetaVisibility()
        if ("prefKeyMetaSize" == key) {
            recreate() // Change to style requires a restart, theming is handled in initialize()
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
        const val EXTRA_START_INDEX = "viewer_index"
        const val EXTRA_FILTER = "viewer_filter"
    }
}