package com.anthonymandra.rawdroid.workers

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.FolderEntity
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.MetaUtil

class SearchWorker(context: Context, params: WorkerParameters): Worker(context, params) {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val excludedFolders = repo.parents.filter { it.excluded }

		val parentMap = repo.parents.associateByTo(mutableMapOf(), {it.documentUri}, {it.id})

		val uriRoots = applicationContext.contentResolver.persistedUriPermissions
		val foldersToSearch = uriRoots.asSequence().map {
			UsefulDocumentFile.fromUri(applicationContext, it.uri)
		}.filter { root ->
			// Filter out roots that start with any of the excluded folders
			!excludedFolders.any { exclusion ->
				root.uri.toString().startsWith(exclusion.documentUri, true)
			}
		}.toList()

		val images = search(foldersToSearch).map {
			MetaUtil.getImageFileInfo(applicationContext, it, parentMap)
		}

		if (images.isNotEmpty()) {
			repo.synchInsertImages(*images.toTypedArray())
		}

		return Result.SUCCESS
	}

	fun search(files: List<UsefulDocumentFile>): List<UsefulDocumentFile> {
		//.nomedia cancels any results
		if (files.any { ".nomedia" == it.name }) return emptyList()

		val folders = files.filter {
			it.cachedData?.let { fileInfo ->
				fileInfo.isDirectory && fileInfo.canRead
			} ?: false
		}

		val images = mutableListOf<UsefulDocumentFile>()
		folders.forEach { folder ->
			folder.listFiles()?.let{ files ->
				images.addAll(search(files.toList()))
			}
		}

		return images.plus(files.filter { ImageUtil.isImage(it.cachedData?.name) })
	}

	companion object {
		const val JOB_TAG = "search_job"

		@JvmStatic
		fun buildRequest(): OneTimeWorkRequest {
			return OneTimeWorkRequestBuilder<SearchWorker>()
				.addTag(JOB_TAG)
				.build()
		}
	}
}