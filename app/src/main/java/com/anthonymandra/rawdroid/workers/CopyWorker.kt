package com.anthonymandra.rawdroid.workers

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.Util

class CopyWorker(context: Context, params: WorkerParameters): CoreWorker(context, params) {
	override val channelId = "copy"
	override val channelName = "Copy Channel"
	override val channelDescription = "Notifications for copy tasks."
	override val notificationTitle: String = applicationContext.getString(R.string.copyingImages)

	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getLongArray(KEY_COPY_URIS)
		val destination = inputData.getString(KEY_DEST_URI)?.toUri()

		if (images == null || destination == null) return Result.failure()

		val parentFile = UsefulDocumentFile.fromUri(applicationContext, destination)
		
		sendPeekNotification()

		// TODO: We could have an xmp field to save the xmp file check error, although that won't work if not processed
		val metadata = repo.synchImages(images)
		metadata.forEachIndexed { index, value ->
			if (this.isStopped) {
				sendCancelledNotification()
				return Result.success()
			}

			sendUpdateNotification(value.name, index, images.size)

			parentFile.createFile(null, value.name)?.let {
				copyAssociatedFiles(value, it.uri)
			}
		}
		sendCompletedNotification()
		return Result.success()
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