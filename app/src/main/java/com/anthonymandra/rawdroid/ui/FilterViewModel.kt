package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.data.FolderEntity

class FilterViewModel(app: Application) : AndroidViewModel(app) {
    private val dataRepo = (app as App).dataRepo

    val folders: LiveData<List<FolderEntity>>
        get() = dataRepo.lifecycleParents

    fun insertFolders(vararg folders: FolderEntity) {
        dataRepo.insertParents(*folders).subscribe()
    }

    fun updateFolders(vararg folders: FolderEntity) {
        dataRepo.updateParents(*folders).subscribe()
    }
}