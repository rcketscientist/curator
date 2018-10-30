package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.Util

class CopyWorker(context: Context, params: WorkerParameters): Worker(context, params) {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getLongArray(KEY_COPY_URIS)
		val destination = inputData.getString(KEY_DEST_URI)?.toUri()

		if (images == null || destination == null) return Result.FAILURE

		val parentFile = UsefulDocumentFile.fromUri(applicationContext, destination)

		Util.createNotificationChannel(
			applicationContext,
			"copy",
			"Copying...",
			"Notifications for copy tasks.")

		val builder = Util.createNotification(
			applicationContext,
			"copy",
			applicationContext.getString(R.string.copyingImages),
			applicationContext.getString(R.string.preparing))

		val notifications = NotificationManagerCompat.from(applicationContext)
		notifications.notify(builder.build())

		// TODO: We could have an xmp field to save the xmp file check error, although that won't work if not processed
		val metadata = repo.synchImages(images)
		metadata.forEachIndexed { index, value ->
			if (isCancelled) {
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

			val destinationFile = parentFile.createFile(null, value.name)
			copyAssociatedFiles(value, destinationFile.uri)
		}

		builder
			.setContentText("Complete")
			.setProgress(0,0,false)
			.priority = NotificationCompat.PRIORITY_HIGH
		notifications.notify(builder.build())

		return Result.SUCCESS
	}

	private fun NotificationManagerCompat.notify(notification: Notification) {
		this.notify(JOB_TAG, 0, notification)
	}

	/**
	 * Copies an image and corresponding xmp and jpeg (ex: src/a.[cr2,xmp,jpg] -> dest/a.[cr2,xmp,jpg])
	 * @param fromImage source image
	 * @param toImage target image
	 * @return success
	 */
	private fun copyAssociatedFiles(fromImage: MetadataEntity, toImage: Uri): Boolean {
		val sourceUri = Uri.parse(fromImage.uri)
		if (ImageUtil.hasXmpFile(applicationContext, sourceUri)) {
			FileUtil.copy(applicationContext,
				ImageUtil.getXmpFile(applicationContext, sourceUri).uri,
				ImageUtil.getXmpFile(applicationContext, toImage).uri)
		}
		if (ImageUtil.hasJpgFile(applicationContext, sourceUri)) {
			FileUtil.copy(applicationContext,
				ImageUtil.getJpgFile(applicationContext, sourceUri).uri,
				ImageUtil.getJpgFile(applicationContext, toImage).uri)
		}

		fromImage.uri = toImage.toString()  // update copied uri

		return FileUtil.copy(applicationContext, sourceUri, toImage)
	}

	companion object {
		const val JOB_TAG = "copy_job"
		const val KEY_COPY_URIS = "copy uris"
		const val KEY_DEST_URI = "destination"

		@JvmStatic
		fun buildRequest(imagesToCopy: LongArray, destination: Uri): OneTimeWorkRequest {
			val data = workDataOf(
				KEY_COPY_URIS to imagesToCopy,
				KEY_DEST_URI to destination.toString()
			)

			return OneTimeWorkRequestBuilder<CopyWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}