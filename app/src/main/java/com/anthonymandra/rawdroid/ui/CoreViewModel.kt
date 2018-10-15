package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import androidx.work.WorkStatus
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.rawdroid.ImageFilter
import com.anthonymandra.rawdroid.XmpUpdateField
import com.anthonymandra.rawdroid.XmpValues
import com.anthonymandra.rawdroid.workers.*

abstract class CoreViewModel(app: Application) : AndroidViewModel(app) {
	private val workManager: WorkManager = WorkManager.getInstance()
	private val metaReaderWorkStatus: LiveData<List<WorkStatus>>
	private val metaWriterWorkStatus: LiveData<List<WorkStatus>>
	private val searchWorkStatus: LiveData<List<WorkStatus>>
	private val copyWorkStatus: LiveData<List<WorkStatus>>
	private val saveWorkStatus: LiveData<List<WorkStatus>>
	private val deleteWorkStatus: LiveData<List<WorkStatus>>
	private val cleanWorkStatus: LiveData<List<WorkStatus>>

	val filter: MutableLiveData<ImageFilter> = MutableLiveData()

	init {
		searchWorkStatus = workManager.getStatusesByTag(SearchWorker.JOB_TAG)
		metaReaderWorkStatus = workManager.getStatusesByTag(MetaReaderWorker.JOB_TAG)
		metaWriterWorkStatus = workManager.getStatusesByTag(MetaWriterWorker.JOB_TAG)
		copyWorkStatus = workManager.getStatusesByTag(CopyWorker.JOB_TAG)
		saveWorkStatus = workManager.getStatusesByTag(SaveWorker.JOB_TAG)
		deleteWorkStatus = workManager.getStatusesByTag(DeleteWorker.JOB_TAG)
		cleanWorkStatus = workManager.getStatusesByTag(CleanWorker.JOB_TAG)
	}

	val metaReaderStatus get() = metaReaderWorkStatus
	val metaWriterStatus get() = metaWriterWorkStatus
	val searchStatus get() = searchWorkStatus
	val copyStatus get() = copyWorkStatus
	val saveStatus get() = saveWorkStatus
	val cleanStatus get() = copyWorkStatus
	val deleteStatus get() = deleteWorkStatus

	fun startMetaReaderWorker() {
		val input = filter.value ?: ImageFilter()
		workManager.enqueue(MetaReaderWorker.buildRequest(input))
	}

	fun startMetaWriterWorker(images: List<Long>, xmp: XmpValues, field: XmpUpdateField) {
		workManager.enqueue(MetaWriterWorker.buildRequest(images, xmp, field))
	}

	fun startSearchWorker() {
		workManager.enqueue(SearchWorker.buildRequest())
	}

	fun startCleanWorker() {
		workManager.enqueue(CleanWorker.buildRequest())
	}

	fun startSearchChain() {
		val input = filter.value ?: ImageFilter()

		workManager
				.beginWith(SearchWorker.buildRequest())
				.then(MetaReaderWorker.buildRequest(input))
				.enqueue()
	}

	fun startCleanSearchChain() {
		val input = filter.value ?: ImageFilter()

		workManager
				.beginWith(CleanWorker.buildRequest())
				.then(SearchWorker.buildRequest())
				.then(MetaReaderWorker.buildRequest(input))
				.enqueue()
	}

	fun startCopyWorker(sources: List<Long>, destination: Uri) {
		workManager.enqueue(CopyWorker.buildRequest(sources, destination))
	}

	fun startSaveWorker(sources: List<Long>, destination: Uri, config: ImageConfiguration, insert: Boolean) {
		workManager.enqueue(SaveWorker.buildRequest(sources, destination, config, insert))
	}

	fun startDeleteWorker(sources: List<Long>) {
		workManager.enqueue(DeleteWorker.buildRequest(sources))
	}

	fun setFilter(filter: ImageFilter) {
		this.filter.value = filter
	}
}