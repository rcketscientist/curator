package com.anthonymandra.curator.workers

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.curator.R
import com.anthonymandra.curator.data.DataRepository
import com.anthonymandra.curator.data.ImageInfo
import com.anthonymandra.util.ImageUtil

class DeleteWorker(context: Context, params: WorkerParameters) : CoreWorker(context, params) {
	override val channelId = "delete"
	override val channelName = "Deletion Channel"
	override val channelDescription = "Notifications for delete tasks."
	override val notificationTitle: String = applicationContext.getString(R.string.deletingFiles)

	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val imagesIds = inputData.getLongArray(DeleteWorker.KEY_DELETE_IDS) ?: return Result.failure()

		sendPeekNotification()

		val images = repo.synchImages(imagesIds)

		images.forEachIndexed { index, value ->
			if (isStopped) {
				sendCancelledNotification()
				return Result.success()
			}

			sendUpdateNotification(value.name, index, images.size)

			if (deleteAssociatedFiles(value)) {
				repo.deleteImage(value)
			}
		}

		sendCompletedNotification()
		return Result.success()
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