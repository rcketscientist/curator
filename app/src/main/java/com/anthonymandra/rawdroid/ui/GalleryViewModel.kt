package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.FullSettingsActivity
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.data.MetadataTest
import kotlinx.android.synthetic.main.meta_panel.*

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    //TODO: Split out viewer viewmodel
    private val dataRepo = (app as App).dataRepo

    val imageList: LiveData<PagedList<MetadataTest>>
    val filter: MutableLiveData<XmpFilter> = MutableLiveData()
    private val _isZoomLocked: MutableLiveData<Boolean> = MutableLiveData()
    val isZoomLocked: LiveData<Boolean>
        get() = _isZoomLocked

    private val _isInterfaceVisible: MutableLiveData<Boolean> = MutableLiveData()
    val isInterfaceVisible: LiveData<Boolean>
        get() = _isInterfaceVisible

    val metaVisibility = MetaVisibility()

    private var shouldShowInterface = true;

    init {
        imageList = Transformations.switchMap(filter) { filter ->
            LivePagedListBuilder(dataRepo.getGalleryLiveData(filter), 30).build() }
        _isZoomLocked.value = false

        val settings = PreferenceManager.getDefaultSharedPreferences(app)
        settings.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            updatePreferences(sharedPreferences)
        }
    }

    fun onZoomLockChanged(zoomLocked: Boolean) {
        _isZoomLocked.value = zoomLocked
    }

    fun onInterfaceVisibilityChanged(visible: Boolean) {
        _isInterfaceVisible.value = visible
    }

    fun setFilter(filter: XmpFilter) {
        this.filter.value = filter
    }
    
    private fun updatePreferences(prefs: SharedPreferences) {
        shouldShowInterface = prefs.getBoolean(FullSettingsActivity.KEY_ShowImageInterface, true)
        // Default true
        metaVisibility.Aperture = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifAperture, true)) View.VISIBLE else View.GONE
        metaVisibility.Date = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifDate, true)) View.VISIBLE else View.GONE
        metaVisibility.Exposure = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifExposure, true)) View.VISIBLE else View.GONE
        metaVisibility.Focal = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifFocal, true)) View.VISIBLE else View.GONE
        metaVisibility.Model = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifModel, true)) View.VISIBLE else View.GONE
        metaVisibility.Iso = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifIso, true)) View.VISIBLE else View.GONE
        metaVisibility.Lens = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifLens, true)) View.VISIBLE else View.GONE
        metaVisibility.Name = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifName, true)) View.VISIBLE else View.GONE

        // Default false
        metaVisibility.Altitude = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifAltitude, false)) View.VISIBLE else View.GONE
        metaVisibility.Dimensions = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifDimensions, false)) View.VISIBLE else View.GONE
        metaVisibility.DriveMode = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifDriveMode, false)) View.VISIBLE else View.GONE
        metaVisibility.ExposureMode = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifExposureMode, false)) View.VISIBLE else View.GONE
        metaVisibility.ExposureProgram = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifExposureProgram, false)) View.VISIBLE else View.GONE
        metaVisibility.Flash = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifFlash, false)) View.VISIBLE else View.GONE
        metaVisibility.Latitude = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifLatitude, false)) View.VISIBLE else View.GONE
        metaVisibility.Longitude = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifLongitude, false)) View.VISIBLE else View.GONE
        metaVisibility.WhiteBalance = if (prefs.getBoolean(FullSettingsActivity.KEY_ExifWhiteBalance, false)) View.VISIBLE else View.GONE

        val comparator = if (shouldShowInterface) "Never" else "Always"
        val visibility = if(shouldShowInterface) View.VISIBLE else View.INVISIBLE
        if (prefs.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic") != comparator) {
            metaVisibility.Metadata =visibility
        }
        if (prefs.getString(FullSettingsActivity.KEY_ShowHist, "Automatic") != comparator) {
            histogramView.visibility = visibility
        }
    }
    
    data class MetaVisibility(
        var Histogram: Int = View.VISIBLE,
        var Metadata: Int = View.VISIBLE,
        var Aperture: Int = View.VISIBLE,
        var Date: Int = View.VISIBLE,
        var Exposure: Int = View.VISIBLE,
        var Focal: Int = View.VISIBLE,
        var Model: Int = View.VISIBLE,
        var Iso: Int = View.VISIBLE,
        var Lens: Int = View.VISIBLE,
        var Name: Int = View.VISIBLE,

        // Default false
        var Altitude: Int = View.GONE,
        var Dimensions: Int = View.GONE,
        var DriveMode: Int = View.GONE,
        var ExposureMode: Int = View.GONE,
        var ExposureProgram: Int = View.GONE,
        var Flash: Int = View.GONE,
        var Latitude: Int = View.GONE,
        var Longitude: Int = View.GONE,
        var WhiteBalance: Int = View.GONE
    )
}