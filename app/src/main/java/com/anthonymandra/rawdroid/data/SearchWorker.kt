package com.anthonymandra.rawdroid.data

import android.content.Intent
import android.net.Uri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.workDataOf
import com.anthonymandra.framework.SearchService
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.util.ImageUtil
import java.util.*
import java.util.concurrent.ForkJoinPool

class SearchWorker: Worker() {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val excludedFolders = repo.parents.filter { it.excluded }

		// TODO: This seems fishy...
		val parentMap = repo.parents.associateBy({it.documentUri}, {it.id})

		val uriRoots = applicationContext.contentResolver.persistedUriPermissions
		uriRoots.map {
			UsefulDocumentFile.fromUri(applicationContext, it.uri)
			}.filter { root ->  // Filter out roots that start with any of the excluded folders
				!excludedFolders.any { exclusion ->
					root.uri.toString().startsWith(exclusion.documentUri, true)
				}
			}.forEach{
				it.listFiles()?.forEach {
					//TODO: Need recursion,eligible for tailrec?
				}
				val result = ArrayList<UsefulDocumentFile>(it.size)
				for (file in it) {
					if (file.cachedData == null)
						continue
					val name = file.cachedData!!.name ?: continue
					if (".nomedia" == name)
					// if .nomedia clear results and return
						return null
					if (ImageUtil.isImage(name))
						result.add(file)
				}
				return result.toTypedArray()
			}

		if (foundImages.size > 0) {
			repo.insertImages(*foundImages.toTypedArray())
		}

		return Result.SUCCESS
	}

	companion object {
		const val JOB_TAG = "search_job"

		@JvmStatic
		fun buildRequest(xmpFilter: XmpFilter = XmpFilter()): WorkRequest? {
			val data = workDataOf(
				KEY_FILTER_AND to xmpFilter.andTrueOrFalse,
				KEY_FILTER_ASC to xmpFilter.sortAscending,
				KEY_FILTER_SEGREGATE to xmpFilter.segregateByType,
				KEY_FILTER_SORT to xmpFilter.sortColumn.toString(),
				KEY_FILTER_HIDDEN to xmpFilter.hiddenFolderIds.toLongArray(),
				KEY_FILTER_RATING to xmpFilter.ratings.toIntArray(),
				KEY_FILTER_LABEL to xmpFilter.labels.toTypedArray(),
				KEY_FILTER_SUBJECT to xmpFilter.subjectIds.toLongArray()
			)

			return OneTimeWorkRequestBuilder<MetadataWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}