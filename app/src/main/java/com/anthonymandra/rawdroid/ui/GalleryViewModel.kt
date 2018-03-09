package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.data.MetadataTest

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val dataSource = (app as App).database.metadataDao()
    val imageList : LiveData<PagedList<MetadataTest>>

    init {  // TODO: Will need the ability to change filter.
        imageList = LivePagedListBuilder(dataSource.getImageFactory(), 30).build()
    }
}