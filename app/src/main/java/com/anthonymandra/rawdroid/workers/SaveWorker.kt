package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import com.anthonymandra.imageprocessor.ImageProcessor
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.Util

class SaveWorker(context: Context, params: WorkerParameters): Worker(context, params) {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getLongArray(KEY_IMAGE_IDS)
		val destination = inputData.getString(KEY_DEST_URI)?.toUri()
		val saveType = inputData.getString(KEY_TYPE)
		val config = inputData.getString(KEY_CONFIG)
		val insert = inputData.getBoolean(KEY_INSERT, false)

		if (images == null || destination == null || saveType == null) return Result.failure()

		val imageConfiguration = ImageConfiguration.from(ImageConfiguration.ImageType.valueOf(saveType), config)
		val parentFile = UsefulDocumentFile.fromUri(applicationContext, destination)

		Util.createNotificationChannel(
			applicationContext,
			"save",
			"Saving...",
			"Notifications for save tasks.")

		val builder = Util.createNotification(
			applicationContext,
			"copy",
			applicationContext.getString(R.string.savingImages),
			applicationContext.getString(R.string.preparing))

		val notifications = NotificationManagerCompat.from(applicationContext)
		notifications.notify(builder.build())

		val metadata = repo.synchImages(images)

		// TODO: Test all this!...and move to util (Copy/Save)
		// We don't know the size beforehand anyway...
//		val freeSpace = applicationContext.contentResolver.openFileDescriptor(destination, "r").use {
//			if (it == null) return@use Long.MAX_VALUE   // We'll blindly start saving
//
//			val stats = Os.fstatvfs(it.fileDescriptor)
//			return@use stats.f_bavail * stats.f_bsize
//		}
//
//		val spaceRequired: Long = metadata
//			.asSequence()
//			.filterNotNull()
//			.map { it.size }
//			.sum()
//
//		if (freeSpace < spaceRequired) {
//			builder.setContentText(applicationContext.getString(R.string.warningNotEnoughSpace))
//			notifications.notify(builder.build())
//			return Result.FAILURE
//		}

		// TODO: We could have an xmp field to save the xmp file check error, although that won't work if not processed
		metadata.forEachIndexed { index, value ->
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

			val source = UsefulDocumentFile.fromUri(applicationContext, Uri.parse(value.uri))
			val desiredName = FileUtil.swapExtention(source.name, imageConfiguration.extension)	// Can't be null, but maybe redesign to avoid ?
			val destinationFile = parentFile.createFile(null, desiredName) ?: return@forEachIndexed

			applicationContext.contentResolver.openFileDescriptor(source.uri, "r")?.use { inputPfd ->
			applicationContext.contentResolver.openFileDescriptor(destinationFile.uri, "w")?.use { outputPfd ->
				when (imageConfiguration.type) {
					ImageConfiguration.ImageType.JPEG -> {
						val quality = (imageConfiguration as JpegConfiguration).quality
						ImageProcessor.writeThumb(inputPfd.fd, quality, outputPfd.fd)
					}
					ImageConfiguration.ImageType.TIFF -> {
						val compress = (imageConfiguration as TiffConfiguration).compress
						ImageProcessor.writeTiff(desiredName, inputPfd.fd, outputPfd.fd, compress)
					}
					else -> throw UnsupportedOperationException("unimplemented save type.")
				}
			}}

			if (insert) {
				// TODO: reuse the image meta and replace uri/id...?
				val insertion = ImageInfo()
				insertion.uri = destinationFile.uri.toString()
				repo.insertMeta(insertion)
			}
		}

		builder
			.setContentText("Complete")
			.setProgress(0,0,false)
			.priority = NotificationCompat.PRIORITY_HIGH
		notifications.notify(builder.build())

		return Result.success()
	}

	private fun NotificationManagerCompat.notify(notification: Notification) {
		this.notify(JOB_TAG, 0, notification)
	}

	companion object {
		const val JOB_TAG = "save_job"
		const val KEY_IMAGE_IDS = "image uris"
		const val KEY_DEST_URI = "destination"
		const val KEY_TYPE = "type"
		const val KEY_CONFIG = "config"
		const val KEY_INSERT = "insert"

		@JvmStatic
		fun buildRequest(images: LongArray, destination: Uri, config: ImageConfiguration, insert: Boolean): OneTimeWorkRequest {
			val data = workDataOf(
					KEY_IMAGE_IDS to images,
					KEY_DEST_URI to destination.toString(),
					KEY_TYPE to config.type.toString(),
					KEY_CONFIG to config.parameters,
					KEY_INSERT to insert
			)

			return OneTimeWorkRequestBuilder<SaveWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}