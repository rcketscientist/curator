package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import androidx.work.WorkStatus
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.ImageFilter
import com.anthonymandra.rawdroid.XmpUpdateField
import com.anthonymandra.rawdroid.XmpValues
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.rawdroid.workers.*
import io.reactivex.Single

abstract class CoreViewModel(app: Application) : AndroidViewModel(app) {
	protected val dataRepo = (app as App).dataRepo

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
		searchWorkStatus = workManager.getStatusesByTagLiveData(SearchWorker.JOB_TAG)
		metaReaderWorkStatus = workManager.getStatusesByTagLiveData(MetaReaderWorker.JOB_TAG)
		metaWriterWorkStatus = workManager.getStatusesByTagLiveData(MetaWriterWorker.JOB_TAG)
		copyWorkStatus = workManager.getStatusesByTagLiveData(CopyWorker.JOB_TAG)
		saveWorkStatus = workManager.getStatusesByTagLiveData(SaveWorker.JOB_TAG)
		deleteWorkStatus = workManager.getStatusesByTagLiveData(DeleteWorker.JOB_TAG)
		cleanWorkStatus = workManager.getStatusesByTagLiveData(CleanWorker.JOB_TAG)
	}

	val searchStatus get() = searchWorkStatus
	val metaReaderStatus get() = metaReaderWorkStatus
	val metaWriterStatus get() = metaWriterWorkStatus
	val copyStatus get() = copyWorkStatus
	val saveStatus get() = saveWorkStatus
	val cleanStatus get() = copyWorkStatus
	val deleteStatus get() = deleteWorkStatus

	fun startMetaReaderWorker() {
		val input = filter.value ?: ImageFilter()
		workManager.enqueue(MetaReaderWorker.buildRequest(input))
	}

	fun startMetaWriterWorker(images: LongArray, xmp: XmpValues, field: XmpUpdateField) {
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

	fun startCopyWorker(sources: LongArray, destination: Uri) {
		workManager.enqueue(CopyWorker.buildRequest(sources, destination))
	}

	fun startSaveWorker(sources: LongArray, destination: Uri, config: ImageConfiguration, insert: Boolean) {
		workManager.enqueue(SaveWorker.buildRequest(sources, destination, config, insert))
	}

	fun startDeleteWorker(sources: LongArray) {
		workManager.enqueue(DeleteWorker.buildRequest(sources))
	}

	fun setFilter(filter: ImageFilter) {
		this.filter.value = filter
	}

	fun images(ids: LongArray): Single<List<ImageInfo>> {
		return dataRepo.images(ids)
	}
}