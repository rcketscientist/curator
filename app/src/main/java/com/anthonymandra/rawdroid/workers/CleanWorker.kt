package com.anthonymandra.rawdroid.workers

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.util.MetaUtil
import com.anthonymandra.util.Util

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