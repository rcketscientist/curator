package com.anthonymandra.rawdroid.workers

import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthonymandra.framework.RecycleBin
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.FullSettingsActivity
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.Util

class RecycleWorker(context: Context, params: WorkerParameters) : CoreWorker(context, params) {
	override val channelId = "recycle"
	override val channelName = "Recycle Channel"
	override val channelDescription = "Notifications for recycle tasks."
	override val notificationTitle: String = applicationContext.getString(R.string.recyclingFiles)

	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val imagesIds = inputData.getLongArray(RecycleWorker.KEY_RECYCLE_IDS)
			?: return Result.failure()

		val binSizeMb: Int = try {
			PreferenceManager.getDefaultSharedPreferences(applicationContext).getInt(
				FullSettingsActivity.KEY_RecycleBinSize,
				FullSettingsActivity.defRecycleBin)
		} catch (e: NumberFormatException) {
			FullSettingsActivity.defRecycleBin
		}
		val recycleBin = RecycleBin.getInstance(applicationContext, binSizeMb * 1024 * 1024L)

		sendPeekNotification()

		val images = repo.synchImages(imagesIds)

		images.forEachIndexed { index, value ->
			if (isStopped) {
				sendCancelledNotification()
				return Result.success()
			}

			sendUpdateNotification(value.name, index, images.size)

			recycleBin.addFileSynch(applicationContext, value.uri.toUri())
			deleteAssociatedFiles(value)
			repo.deleteImage(value)
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
	private fun deleteFile(file: Uri): Boolean {
		val document = UsefulDocumentFile.fromUri(applicationContext, file)
		return document.delete()
	}

	companion object {
		const val JOB_TAG = "recycle_job"
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