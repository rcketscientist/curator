package com.anthonymandra.rawdroid.workers

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthonymandra.framework.RecycleBin
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.MetaUtil
import com.anthonymandra.util.Util

class RestoreWorker(context: Context, params: WorkerParameters) : CoreWorker(context, params) {
	override val channelId = "restore"
	override val channelName = "Restore Channel"
	override val channelDescription = "Notifications for restore tasks."
	override val notificationTitle: String = applicationContext.getString(R.string.restoringFiles)

	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val recycleKeys = inputData.getStringArray(RestoreWorker.KEY_RECYCLE_KEYS)
			?: return Result.failure()
		val recycleBin = RecycleBin.getInstance(applicationContext)
		val failedRestores = mutableListOf<String>()

		sendPeekNotification()

		val parentMap = repo.parents.associateByTo(mutableMapOf(), { it.documentUri }, { it.id })

		recycleKeys.forEachIndexed { index, key ->
			val restoredUri = key.toUri()
			if (isStopped) {
				sendCancelledNotification()
				// TODO: More insight into failures
//				 if (failedRestores.size > 0) {
//						 builder.setStyle(NotificationCompat.BigTextStyle()
//							 .bigText(failedRestores.joinToString(", ")))
//				 }
//			    notifications.notify(builder.build())

				return Result.success()
			}

			sendUpdateNotification(restoredUri.lastPathSegment ?: "", index, recycleKeys.size)

			// TODO: More insight into failures
//			 if (failedRestores.size > 0) {
//				 builder.setStyle(NotificationCompat.BigTextStyle()
//					 .bigText(failedRestores.joinToString(", ")))
//			 }
//			 notifications.notify(builder.build())

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

		sendCompletedNotification()
		return Result.success()
	}

	@WorkerThread
	private fun deleteFile(file: Uri): Boolean {
		val document = UsefulDocumentFile.fromUri(applicationContext, file)
		return document.delete()
	}

	companion object {
		const val JOB_TAG = "restore_job"
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