package com.anthonymandra.rawdroid.workers

import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.data.DataRepository

class CleanWorker: Worker() {
    override fun doWork(): Result {
        val repo = DataRepository.getInstance(this.applicationContext)

	    val uriToRemove = mutableListOf<String>()
        val allUris = repo._uris()
	    allUris.forEach {
		    val file = UsefulDocumentFile.fromUri(applicationContext, it.toUri())
			if (!file.exists()) {
				uriToRemove.add(it)
			}
	    }

	    repo.deleteImages(uriToRemove.toTypedArray())

        return Result.SUCCESS
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