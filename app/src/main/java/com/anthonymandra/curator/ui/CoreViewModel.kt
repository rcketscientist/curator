package com.anthonymandra.curator.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.curator.App
import com.anthonymandra.curator.ImageFilter
import com.anthonymandra.curator.XmpUpdateField
import com.anthonymandra.curator.XmpValues
import com.anthonymandra.curator.data.ImageInfo
import com.anthonymandra.curator.data.RecycleBinEntity
import com.anthonymandra.curator.workers.*
import io.reactivex.Single

abstract class CoreViewModel(app: Application) : AndroidViewModel(app) {
	protected val dataRepo = (app as App).dataRepo

	private val workManager: WorkManager = WorkManager.getInstance()
	private val metaReaderWorkInfo: LiveData<List<WorkInfo>>
	private val metaWriterWorkInfo: LiveData<List<WorkInfo>>
	private val searchWorkInfo: LiveData<List<WorkInfo>>
	private val copyWorkInfo: LiveData<List<WorkInfo>>
	private val saveWorkInfo: LiveData<List<WorkInfo>>
	private val deleteWorkInfo: LiveData<List<WorkInfo>>
	private val cleanWorkInfo: LiveData<List<WorkInfo>>
	private val recycleWorkInfo: LiveData<List<WorkInfo>>
	private val restoreWorkInfo: LiveData<List<WorkInfo>>

	val filter: MutableLiveData<ImageFilter> = MutableLiveData()

	init {
		searchWorkInfo = workManager.getWorkInfosByTagLiveData(SearchWorker.JOB_TAG)
		metaReaderWorkInfo = workManager.getWorkInfosByTagLiveData(MetaReaderWorker.JOB_TAG)
		metaWriterWorkInfo = workManager.getWorkInfosByTagLiveData(MetaWriterWorker.JOB_TAG)
		copyWorkInfo = workManager.getWorkInfosByTagLiveData(CopyWorker.JOB_TAG)
		saveWorkInfo = workManager.getWorkInfosByTagLiveData(SaveWorker.JOB_TAG)
		deleteWorkInfo = workManager.getWorkInfosByTagLiveData(DeleteWorker.JOB_TAG)
		cleanWorkInfo = workManager.getWorkInfosByTagLiveData(CleanWorker.JOB_TAG)
		recycleWorkInfo = workManager.getWorkInfosByTagLiveData(RecycleWorker.JOB_TAG)
		restoreWorkInfo = workManager.getWorkInfosByTagLiveData(RestoreWorker.JOB_TAG)
	}

	val searchStatus get() = searchWorkInfo
	val metaReaderStatus get() = metaReaderWorkInfo
	val metaWriterStatus get() = metaWriterWorkInfo
	val copyStatus get() = copyWorkInfo
	val saveStatus get() = saveWorkInfo
	val cleanStatus get() = copyWorkInfo
	val deleteStatus get() = deleteWorkInfo
	val recycleStatus get() = copyWorkInfo
	val restoreStatus get() = deleteWorkInfo

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

	fun startRecycleWorker(sources: LongArray) {
		workManager.enqueue(RecycleWorker.buildRequest(sources))
	}

	fun startRestoreWorker(sources: Array<Long>) {
		workManager.enqueue(RestoreWorker.buildRequest(sources))
	}

	fun setFilter(filter: ImageFilter) {
		// On configuration changes this could cause a state restart, so only set when differs
		if (filter != this.filter.value) {
			this.filter.value = filter
		}
	}

	fun images(ids: LongArray): Single<List<ImageInfo>> {
		return dataRepo.images(ids)
	}

	fun recycledImages(ids: LongArray): Single<List<RecycleBinEntity>> {
		return dataRepo.recycledImages(*ids)
	}
}