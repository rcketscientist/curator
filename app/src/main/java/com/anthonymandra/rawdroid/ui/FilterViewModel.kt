package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.data.FolderEntity

class FilterViewModel(app: Application) : AndroidViewModel(app) {

    // MediatorLiveData can observe other LiveData objects and react on their emissions.
    private val mObservableProducts: MediatorLiveData<List<FolderEntity>> = MediatorLiveData()
    private val dataSource = (app as App).database.folderDao()

    /**
     * Expose the LiveData keywords query so the UI can observe it.
     */
    val folders: LiveData<List<FolderEntity>>
        get() = mObservableProducts

    init {
        // set by default null, until we get data from the database.
        mObservableProducts.value = null

        val folders = dataSource.all

        // observe the changes of the products from the database and forward them
        mObservableProducts.addSource<List<FolderEntity>>(folders, { mObservableProducts.setValue(it) })
    }
}