package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.framework.RecycleBin
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.FullSettingsActivity
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.Util

class RecycleWorker(context: Context, params: WorkerParameters): Worker(context, params) {
    override fun doWork(): Result {
	    val repo = DataRepository.getInstance(this.applicationContext)
	    val imagesIds = inputData.getLongArray(RecycleWorker.KEY_RECYCLE_IDS) ?: return Result.failure()

		 val binSizeMb: Int = try {
			 PreferenceManager.getDefaultSharedPreferences(applicationContext).getInt(
				 FullSettingsActivity.KEY_RecycleBinSize,
				 FullSettingsActivity.defRecycleBin)
		 } catch (e: NumberFormatException) {
			 FullSettingsActivity.defRecycleBin
		 }
		 val recycleBin = RecycleBin.getInstance(applicationContext, binSizeMb * 1024 * 1024L)

	    Util.createNotificationChannel(
		    applicationContext,
		    "recycle",
		    "Recycling...",
		    "Notifications for recycle tasks.")

	    val builder = Util.createNotification(
		    applicationContext,
		    "copy",
		    applicationContext.getString(R.string.recyclingFiles),
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

			    return Result.success()
		    }

		    builder
			    .setProgress(images.size, index, false)
			    .setContentText(value.name)
			    .priority = NotificationCompat.PRIORITY_DEFAULT
		    notifications.notify(builder.build())

			 recycleBin.addFileSynch(applicationContext, value.uri.toUri())
			 deleteAssociatedFiles(value)
			 repo.deleteImage(value)
	    }

	    builder
		    .setContentText("Complete")
		    .setProgress(0,0,false)
		    .priority = NotificationCompat.PRIORITY_HIGH
	    notifications.notify(builder.build())
        return Result.success()
    }

	private fun deleteAssociatedFiles(image: ImageInfo): Boolean {
		val associatedFiles = ImageUtil.getAssociatedFiles(applicationContext, Uri.parse(image.uri))
		for (file in associatedFiles)
			deleteFile(file)
		return deleteFile(image.uri.toUri())
	}

	@WorkerThread   // TODO: Move to fileutil
	private fun deleteFile(file: Uri): Boolean {
		val document = UsefulDocumentFile.fromUri(applicationContext, file)
		return document.delete()
	}

	private fun NotificationManagerCompat.notify(notification: Notification) {
		this.notify(RecycleWorker.JOB_TAG, 0, notification)
	}

    companion object {
        const val JOB_TAG = "delete_job"
	    const val KEY_RECYCLE_IDS = "recycle uris"

	    @JvmStatic
	    fun buildRequest(imagesToRecycle: LongArray): OneTimeWorkRequest {
		    val data = workDataOf(
				 RecycleWorker.KEY_RECYCLE_IDS to imagesToRecycle
		    )

		    return OneTimeWorkRequestBuilder<RecycleWorker>()
			    .addTag(JOB_TAG)
			    .setInputData(data)
			    .build()
	    }
    }
}