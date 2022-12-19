package com.anthonymandra.curator.workers

import android.content.Context
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.curator.data.DataRepository

class CleanWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)

		val uriToRemove = mutableListOf<String>()
		val allUris = repo.synchImageUris()
		allUris.forEach {
			val file = UsefulDocumentFile.fromUri(applicationContext, it.toUri())
			if (!file.exists()) {
				uriToRemove.add(it)
			}
		}

		repo.deleteImages(uriToRemove.toTypedArray())

		return Result.success()
	}

	companion object {
		const val JOB_TAG = "clean_job"

		@JvmStatic
		fun buildRequest(): OneTimeWorkRequest {

			return OneTimeWorkRequestBuilder<CleanWorker>()
				.addTag(JOB_TAG)
				.build()
		}
	}
}