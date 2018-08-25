package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.data.MetadataTest

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val dataRepo = (app as App).dataRepo

    val imageList: LiveData<PagedList<MetadataTest>>
    val filter: MutableLiveData<XmpFilter> = MutableLiveData()
    val zoom: MutableLiveData<Boolean> = MutableLiveData()

    init {
        imageList = Transformations.switchMap(filter) { filter ->
            LivePagedListBuilder(dataRepo.getGalleryLiveData(filter), 30).build() }
        zoom.value = false
    }

    fun setFilter(filter: XmpFilter) {
        this.filter.value = filter
    }
}