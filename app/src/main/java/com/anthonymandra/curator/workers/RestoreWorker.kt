package com.anthonymandra.curator.workers

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthonymandra.framework.RecycleBin
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.curator.R
import com.anthonymandra.curator.data.DataRepository
import com.anthonymandra.curator.data.ImageInfo
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.MetaUtil

class RestoreWorker(context: Context, params: WorkerParameters) : CoreWorker(context, params) {
	override val channelId = "restore"
	override val channelName = "Restore Channel"
	override val channelDescription = "Notifications for restore tasks."
	override val notificationTitle: String = applicationContext.getString(R.string.restoringFiles)

	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val recycleIds = inputData.getLongArray(RestoreWorker.KEY_RECYCLE_IDS)
			?: return Result.failure()
		val recycleBin = RecycleBin.getInstance(applicationContext)
		val failedRestores = mutableListOf<String>()

		sendPeekNotification()

		val parentMap = repo.parents.associateByTo(mutableMapOf(), { it.documentUri }, { it.id })
		val recycledImages = repo.recycledImagesSynch(*recycleIds)

		recycledImages.forEachIndexed { index, entity ->
			val restoredUri = entity.path.toUri()
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

			sendUpdateNotification(restoredUri.lastPathSegment ?: "", index, recycleIds.size)

			// TODO: More insight into failures
//			 if (failedRestores.size > 0) {
//				 builder.setStyle(NotificationCompat.BigTextStyle()
//					 .bigText(failedRestores.joinToString(", ")))
//			 }
//			 notifications.notify(builder.build())

			val recycledFile = recycleBin.getFile(entity.id)
			if (recycledFile == null) {
				failedRestores.add(entity.path)
				return@forEachIndexed
			}
			val recycledUri = Uri.fromFile(recycledFile)
			FileUtil.copy(applicationContext, recycledUri, restoredUri)
			recycleBin.remove(entity.id)
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

	companion object {
		const val JOB_TAG = "restore_job"
		const val KEY_RECYCLE_IDS = "recycle ids"

		@JvmStatic
		fun buildRequest(imagesToRestore: Array<Long>): OneTimeWorkRequest {
			val data = workDataOf(
				RestoreWorker.KEY_RECYCLE_IDS to imagesToRestore
			)

			return OneTimeWorkRequestBuilder<RestoreWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}