package com.anthonymandra.rawdroid.workers

import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.workDataOf
import com.anthonymandra.framework.CoreActivity
import com.anthonymandra.framework.DocumentUtil
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.FileUtil
import com.anthonymandra.util.ImageUtil
import com.crashlytics.android.Crashlytics
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

class CopyWorker: Worker() {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getLongArray(KEY_COPY_URIS)
		val destination = inputData.getString(KEY_DEST_URI)?.toUri()

		if (images == null) return Result.FAILURE

		// TODO: We could have an xmp field to save the xmp file check error, although that won't work if not processed
		val metadata = repo._images(images)
		metadata.forEach {
			if (isCancelled) return Result.SUCCESS
			val destinationFile = DocumentUtil.getChildUri(destination, it.name)
			copyAssociatedFiles(it, destinationFile)
		}

		return Result.SUCCESS
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

	// TODO: Move notifications
	fun copyImages(images: Collection<MetadataTest>, destinationFolder: Uri) {
		setMaxProgress(images.size)

		var progress = 0
		val builder = NotificationCompat.Builder(this, CoreActivity.NOTIFICATION_CHANNEL)
		builder.setContentTitle(getString(R.string.copyingImages))
				.setSmallIcon(R.mipmap.ic_launcher)
				.setPriority(CoreActivity.IMPORTANT_NOTIFICATION)
				.setVibrate(longArrayOf(0, 0)) // We don't ask for permission, this allows peek
				.setProgress(images.size, progress, false)
		notificationManager.notify(0, builder.build())

		Observable.fromIterable(images)
				.subscribeOn(Schedulers.from(AppExecutors.DISK))
				.map {
					val destinationFile = DocumentUtil.getChildUri(destinationFolder, it.name)
					copyAssociatedFiles(it, destinationFile)
					it.name
				}
				.observeOn(AndroidSchedulers.mainThread())
				.subscribeBy(
						onNext = {
							builder.setProgress(images.size, ++progress, false)
							builder.setContentText(it)
							notificationManager.notify(0, builder.build())
							incrementProgress()
						},
						onComplete = {
							endProgress()

							// When the loop is finished, updates the notification
							builder.setContentText("Complete")
									.setProgress(0,0,false) // Removes the progress bar
							notificationManager.notify(0, builder.build())
						},
						onError = {
							incrementProgress()
							builder.setProgress(images.size, ++progress, false)
							it.printStackTrace()
//                    Crashlytics.setString("uri", toCopy.toString())
							Crashlytics.logException(it)
						}
				)
	}

	companion object {
		const val JOB_TAG = "copy_job"
		const val KEY_COPY_URIS = "copy uris"
		const val KEY_DEST_URI = "destination"

		@JvmStatic
		fun buildRequest(imagesToCopy: List<Long>, destination: Uri): WorkRequest? {
			val data = workDataOf(
				KEY_COPY_URIS to imagesToCopy.toLongArray(),
				KEY_DEST_URI to destination.toString()
			)

			return OneTimeWorkRequestBuilder<CopyWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}