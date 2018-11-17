package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.Util

class DeleteWorker(context: Context, params: WorkerParameters): Worker(context, params) {
    override fun doWork(): Result {
	    val repo = DataRepository.getInstance(this.applicationContext)
	    val imagesIds = inputData.getLongArray(DeleteWorker.KEY_DELETE_IDS) ?: return Result.FAILURE

	    Util.createNotificationChannel(
		    applicationContext,
		    "delete",
		    "Deleting...",
		    "Notifications for delete tasks.")

	    val builder = Util.createNotification(
		    applicationContext,
		    "copy",
		    applicationContext.getString(R.string.deletingFiles),
		    applicationContext.getString(R.string.preparing))

	    val notifications = NotificationManagerCompat.from(applicationContext)
	    notifications.notify(builder.build())

	    val images = repo.synchImages(imagesIds)

	    images.forEachIndexed { index, value ->
		    if (isStopped) {
			    builder
				    .setContentText("Cancelled")
				    .priority = NotificationCompat.PRIORITY_HIGH
			    notifications.notify(builder.build())

			    return Result.SUCCESS
		    }

		    builder
			    .setProgress(images.size, index, false)
			    .setContentText(value.name)
			    .priority = NotificationCompat.PRIORITY_DEFAULT
		    notifications.notify(builder.build())

		    if (deleteAssociatedFiles(value)) {
			    repo.deleteImage(value)
		    }
	    }

	    builder
		    .setContentText("Complete")
		    .setProgress(0,0,false)
		    .priority = NotificationCompat.PRIORITY_HIGH
	    notifications.notify(builder.build())
        return Result.SUCCESS
    }

	private fun deleteAssociatedFiles(image: ImageInfo): Boolean {
		val associatedFiles = ImageUtil.getAssociatedFiles(applicationContext, Uri.parse(image.uri))
		for (file in associatedFiles)
			deleteFile(file)
		return deleteFile(image.uri.toUri())
	}

	@WorkerThread   // TODO: Move to fileutil
	fun deleteFile(file: Uri): Boolean {
		val document = UsefulDocumentFile.fromUri(applicationContext, file)
		return document.delete()
	}

	private fun NotificationManagerCompat.notify(notification: Notification) {
		this.notify(DeleteWorker.JOB_TAG, 0, notification)
	}

    companion object {
        const val JOB_TAG = "delete_job"
	    const val KEY_DELETE_IDS = "delete uris"

	    @JvmStatic
	    fun buildRequest(imagesToDelete: LongArray): OneTimeWorkRequest {
		    val data = workDataOf(
			    DeleteWorker.KEY_DELETE_IDS to imagesToDelete
		    )

		    return OneTimeWorkRequestBuilder<DeleteWorker>()
			    .addTag(JOB_TAG)
			    .setInputData(data)
			    .build()
	    }
    }
}