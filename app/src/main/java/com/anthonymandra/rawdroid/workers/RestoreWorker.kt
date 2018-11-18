package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.framework.RecycleBin
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.MetaUtil
import com.anthonymandra.util.Util

class RestoreWorker(context: Context, params: WorkerParameters): Worker(context, params) {
    override fun doWork(): Result {
	    val repo = DataRepository.getInstance(this.applicationContext)
	    val recycleKeys = inputData.getStringArray(RestoreWorker.KEY_RECYCLE_KEYS) ?: return Result.FAILURE
		 val recycleBin = RecycleBin.getInstance(applicationContext)
		 val failedRestores = mutableListOf<String>()

	    Util.createNotificationChannel(
		    applicationContext,
		    "restore",
		    "Restoring...",
		    "Notifications for restore tasks.")

	    val builder = Util.createNotification(
		    applicationContext,
		    "restore",
		    applicationContext.getString(R.string.restoringFiles),
		    applicationContext.getString(R.string.preparing))

	    val notifications = NotificationManagerCompat.from(applicationContext)
	    notifications.notify(builder.build())

		 val parentMap = repo.parents.associateByTo(mutableMapOf(), {it.documentUri}, {it.id})

		 recycleKeys.forEachIndexed { index, key ->
			 val restoredUri = key.toUri()
		    if (isStopped) {
			    builder
				    .setContentText("Cancelled")
					 .priority = NotificationCompat.PRIORITY_HIGH

				 if (failedRestores.size > 0) {
						 builder.setStyle(NotificationCompat.BigTextStyle()
							 .bigText(failedRestores.joinToString(", ")))
				 }
			    notifications.notify(builder.build())

			    return Result.SUCCESS
		    }

			 builder
				 .setProgress(recycleKeys.size, index, false)
				 .setContentText(restoredUri.lastPathSegment)
				 .priority = NotificationCompat.PRIORITY_DEFAULT

			 if (failedRestores.size > 0) {
				 builder.setStyle(NotificationCompat.BigTextStyle()
					 .bigText(failedRestores.joinToString(", ")))
			 }
			 notifications.notify(builder.build())

			 val recycledFile = recycleBin.getFile(key)
			 if (recycledFile == null) {
				 failedRestores.add(key)
				 return@forEachIndexed
			 }
			 val recycledUri = Uri.fromFile(recycledFile)
			 FileUtil.copy(applicationContext, recycledUri, restoredUri)
			 recycleBin.remove(key)
			 val info = MetaUtil.getImageFileInfo(
				 applicationContext,
				 UsefulDocumentFile.fromUri(applicationContext, restoredUri),
				 parentMap)
			 if (info != null) {
				 val parsed = MetaUtil.readMetadata(applicationContext, ImageInfo.fromMetadataEntity(info))
				 repo.insertMeta(parsed)
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

	@WorkerThread
	private fun deleteFile(file: Uri): Boolean {
		val document = UsefulDocumentFile.fromUri(applicationContext, file)
		return document.delete()
	}

	private fun NotificationManagerCompat.notify(notification: Notification) {
		this.notify(RestoreWorker.JOB_TAG, 0, notification)
	}

    companion object {
        const val JOB_TAG = "delete_job"
	    const val KEY_RECYCLE_KEYS = "recycle keys"

	    @JvmStatic
	    fun buildRequest(imagesToRestore: Array<String>): OneTimeWorkRequest {
		    val data = workDataOf(
				 RestoreWorker.KEY_RECYCLE_KEYS to imagesToRestore
		    )

		    return OneTimeWorkRequestBuilder<RestoreWorker>()
			    .addTag(JOB_TAG)
			    .setInputData(data)
			    .build()
	    }
    }
}