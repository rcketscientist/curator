package com.anthonymandra.rawdroid.workers

import android.util.Log
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.Worker
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.FolderEntity
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.util.ImageUtil

class SearchWorker: Worker() {
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
			getImageFileInfo(it, parentMap, repo)
		}

		if (images.isNotEmpty()) {
			repo.insertImages(*images.toTypedArray())
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

	// TODO: This should be a custom DocumentProvider
	private fun getImageFileInfo(file: UsefulDocumentFile,
	                             parentMap: MutableMap<String, Long>,
	                             dataRepo: DataRepository): MetadataEntity {
		val fd = file.cachedData
		val metadata = MetadataEntity()

		var parent: String? = null
		if (fd != null) {
			metadata.name = fd.name
			parent = fd.parent.toString()
			metadata.timestamp = fd.lastModified
			metadata.size = fd.length
		} else {
			val docParent = file.parentFile
			if (docParent != null) {
				parent = docParent.uri.toString()
			}
		}

		if (parent != null) {
			metadata.parentId = parentMap.getOrPut(parent) { dataRepo.insertParent(FolderEntity(parent)) }
		}

		metadata.documentId = file.documentId
		metadata.uri = file.uri.toString()
		metadata.type = ImageUtil.getImageType(applicationContext, file.uri).value
		return metadata
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