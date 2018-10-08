package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import androidx.work.WorkStatus
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.workers.*

abstract class CoreViewModel(app: Application) : AndroidViewModel(app) {
    private val workManager: WorkManager = WorkManager.getInstance()
    private val metaWorkStatus: LiveData<List<WorkStatus>>
    private val searchWorkStatus: LiveData<List<WorkStatus>>
    private val copyWorkStatus: LiveData<List<WorkStatus>>
    private val deleteWorkStatus: LiveData<List<WorkStatus>>
    private val cleanWorkStatus: LiveData<List<WorkStatus>>

    val filter: MutableLiveData<XmpFilter> = MutableLiveData()

    init {
        searchWorkStatus = workManager.getStatusesByTag(SearchWorker.JOB_TAG)
        metaWorkStatus = workManager.getStatusesByTag(MetaReaderWorker.JOB_TAG)
        copyWorkStatus = workManager.getStatusesByTag(CopyWorker.JOB_TAG)
        deleteWorkStatus = workManager.getStatusesByTag(DeleteWorker.JOB_TAG)
        cleanWorkStatus = workManager.getStatusesByTag(CleanWorker.JOB_TAG)
    }

    val metadataStatus get() = metaWorkStatus
    val searchStatus get() = searchWorkStatus
    val copyStatus get() = copyWorkStatus
    val cleanStatus get() = copyWorkStatus
    val deleteStatus get() = deleteWorkStatus

    fun startMetaWorker() {
		val input = filter.value ?: XmpFilter()
		workManager.enqueue(MetaReaderWorker.buildRequest(input))
	}

	fun startSearchWorker() {
		workManager.enqueue(SearchWorker.buildRequest())
	}

    fun startCleanWorker() {
        workManager.enqueue(CleanWorker.buildRequest())
    }

    fun startSearchChain() {
        val input = filter.value ?: XmpFilter()

        workManager
            .beginWith(SearchWorker.buildRequest())
            .then(MetaReaderWorker.buildRequest(input))
            .enqueue()
    }

    fun startCleanSearchChain() {
        val input = filter.value ?: XmpFilter()

        workManager
            .beginWith(CleanWorker.buildRequest())
            .then(SearchWorker.buildRequest())
            .then(MetaReaderWorker.buildRequest(input))
            .enqueue()
    }

    fun startCopyWorker(sources: List<Long>, destination: Uri) {
        workManager.enqueue(CopyWorker.buildRequest(sources, destination))
    }

    fun startDeleteWorker(sources: List<Long>) {
        workManager.enqueue(DeleteWorker.buildRequest(sources))
    }

    fun setFilter(filter: XmpFilter) {
        this.filter.value = filter
    }
}