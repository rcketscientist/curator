package com.anthonymandra.curator.workers

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.curator.data.DataRepository
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

		val images = search(foldersToSearch).mapNotNull {
			MetaUtil.getImageFileInfo(applicationContext, it, parentMap)
		}

		if (images.isNotEmpty()) {
			repo.synchInsertImages(*images.toTypedArray())
		}

		return Result.success()
	}

	fun search(files: List<UsefulDocumentFile>): List<UsefulDocumentFile> {
		//.nomedia cancels any results
		if (files.any { ".nomedia" == it.name }) return emptyList()

		val images = mutableListOf<UsefulDocumentFile>()
		files.filter {
			it.cacheFileData()
			it.isDirectory && it.canRead
		}.forEach { folder ->
			images.addAll(search(folder.listFiles().toList()))
		}

		return images.plus(
			files.filter { ImageUtil.isImage(it.name) })
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