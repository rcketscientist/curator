package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.workDataOf
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.Util

class MetaWriterWorker: Worker() {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getLongArray(KEY_IMAGE_IDS)
		val subject = inputData.getStringArray(KEY_SUBJECT)
		val label = inputData.getString(KEY_LABEL)
		val rating = inputData.getInt(KEY_RATING, 0)

		if (images == null) return Result.FAILURE

		Util.createNotificationChannel(
			applicationContext,
			"writeMeta",
			"Writing...",
			"Notifications for metadata write tasks.")

		val builder = Util.createNotification(
			applicationContext,
			"writeMeta",
			applicationContext.getString(R.string.writingMetadata),
			applicationContext.getString(R.string.preparing))

		val notifications = NotificationManagerCompat.from(applicationContext)
		notifications.notify(builder.build())

		// TODO: We could have an xmp field to save the xmp file check error, although that won't work if not processed
		val metadata = repo._images(images)
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



			val parentFile = UsefulDocumentFile.fromUri(applicationContext, destination)
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
	private fun copyAssociatedFiles(fromImage: MetadataEntity, toImage: Uri) {
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
		const val JOB_TAG = "metawriter_job"
		const val KEY_IMAGE_IDS = "image ids"
		const val KEY_SUBJECT = "subject"
		const val KEY_RATING = "rating"
		const val KEY_LABEL = "label"

		@JvmStatic
		fun buildRequest(imagesToCopy: List<Long>, subject: List<String>, label: String, rating: Int): OneTimeWorkRequest {
			val data = workDataOf(
				KEY_IMAGE_IDS to imagesToCopy.toLongArray(),
				KEY_SUBJECT to subject.toTypedArray(),
				KEY_LABEL to label,
				KEY_RATING to rating
			)

			return OneTimeWorkRequestBuilder<MetaWriterWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}