package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.preference.PreferenceManager
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

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val dataRepo = (app as App).dataRepo

    val imageList: LiveData<PagedList<MetadataTest>>
    val filter: MutableLiveData<XmpFilter> = MutableLiveData()
    private val _isZoomLocked: MutableLiveData<Boolean> = MutableLiveData()
    val isZoomLocked: LiveData<Boolean>
        get() = _isZoomLocked

    init {
        imageList = Transformations.switchMap(filter) { filter ->
            LivePagedListBuilder(dataRepo.getGalleryLiveData(filter), 30).build() }
        _isZoomLocked.value = false

        val settings = PreferenceManager.getDefaultSharedPreferences(app)
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

    fun onZoomLockChanged(zoomLocked: Boolean) {
        _isZoomLocked.value = zoomLocked
    }

    fun setFilter(filter: XmpFilter) {
        this.filter.value = filter
    }
}