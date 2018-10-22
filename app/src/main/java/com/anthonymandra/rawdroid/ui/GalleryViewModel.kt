package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.anthonymandra.rawdroid.FullSettingsActivity
import com.anthonymandra.rawdroid.data.MetadataTest
import io.reactivex.Single

class GalleryViewModel(app: Application) : CoreViewModel(app) {
    //TODO: Split out viewer viewmodel
    val imageList: LiveData<PagedList<MetadataTest>>
    val filteredCount: LiveData<Int>
    val filteredProcessedCount: LiveData<Int>
    private val _isZoomLocked: MutableLiveData<Boolean> = MutableLiveData()
    val isZoomLocked: LiveData<Boolean>
        get() = _isZoomLocked

    /**
     * Overall visibility state of the interface
     */
    private var isInterfaceVisible = true

    private val _metadataVisibility: MutableLiveData<Int> = MutableLiveData()
    val metadataVisibility: LiveData<Int>
        get() = _metadataVisibility

    private val _navigationVisibility: MutableLiveData<Int> = MutableLiveData()
    val navigationVisibility: LiveData<Int>
        get() = _navigationVisibility

    private val _toolbarVisibility: MutableLiveData<Boolean> = MutableLiveData()
    val toolbarVisibility: LiveData<Boolean>
        get() = _toolbarVisibility

    private val _histogramVisibility: MutableLiveData<Int> = MutableLiveData()
    val histogramVisibility: LiveData<Int>
        get() = _histogramVisibility

    val metaVisibility = MetaVisibility()

    init {
        imageList = Transformations.switchMap(filter) { filter ->
            LivePagedListBuilder(dataRepo.getGalleryLiveData(filter), 30).build()
        }

        filteredCount = Transformations.switchMap(filter) { filter ->
            dataRepo.getImageCount(filter)
        }

        filteredProcessedCount = Transformations.switchMap(filter) { filter ->
            dataRepo.getProcessedCount(filter)
        }

        _isZoomLocked.value = false

        val settings = PreferenceManager.getDefaultSharedPreferences(app)
        settings.registerOnSharedPreferenceChangeListener { sharedPreferences, _ ->
            updatePreferences(sharedPreferences)
        }
    }

    fun selectAll(): Single<LongArray> {
        return dataRepo.selectAll()
    }

    fun onZoomLockChanged(zoomLocked: Boolean) {
        _isZoomLocked.value = zoomLocked
    }

    fun hideInterface() {
        isInterfaceVisible = false
        updateInterfaceVisibility()
    }

    fun showInterface() {
        isInterfaceVisible = true
        updateInterfaceVisibility()
    }

    fun toggleInterface() {
        isInterfaceVisible = !isInterfaceVisible
        updateInterfaceVisibility()
    }

    private fun updateInterfaceVisibility() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication()) // TODO: store?
        // If visible show everything but "never
        // If hidden hide everything but "always"
        val comparator = if (isInterfaceVisible) "Never" else "Always"
        val visibility = if(isInterfaceVisible) View.VISIBLE else View.INVISIBLE
        // TODO: Change this to always set the value based on isInterfaceVisible && state
        if (prefs.getString(FullSettingsActivity.KEY_ShowHist, "Automatic") != comparator) {
            _histogramVisibility.postValue(visibility)
        }
        if (prefs.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic") != comparator) {
            _metadataVisibility.postValue(visibility)
        }
        if (prefs.getString(FullSettingsActivity.KEY_ShowNav, "Automatic") != comparator) {
            _navigationVisibility.postValue(visibility)
        }
        if (prefs.getString(FullSettingsActivity.KEY_ShowToolbar, "Automatic") != comparator) {
            _toolbarVisibility.postValue(isInterfaceVisible)
        }
    }

    // TODO: Remove android framework references
    private fun updatePreferences(prefs: SharedPreferences) {
//        shouldShowInterface = prefs.getBoolean(FullSettingsActivity.KEY_ShowImageInterface, true)
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
    }
    
    data class MetaVisibility(
        // Default meta
            var Aperture: Int = View.VISIBLE,
            var Date: Int = View.VISIBLE,
            var Exposure: Int = View.VISIBLE,
            var Focal: Int = View.VISIBLE,
            var Model: Int = View.VISIBLE,
            var Iso: Int = View.VISIBLE,
            var Lens: Int = View.VISIBLE,
            var Name: Int = View.VISIBLE,

        // Default hidden
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