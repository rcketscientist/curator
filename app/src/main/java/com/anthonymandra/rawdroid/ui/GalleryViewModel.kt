package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.data.MetadataTest

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val dataRepo = (app as App).dataRepo

    val imageList : LiveData<PagedList<MetadataTest>>

    init {  // TODO: Will need the ability to change filter.
        imageList = LivePagedListBuilder(dataRepo.galleryStream, 30).build()
    }

    fun updateFilter(filter: XmpFilter) {   // TODO: Does this update -existing- gallery?
        dataRepo.updateGalleryStream(filter)
    }
}