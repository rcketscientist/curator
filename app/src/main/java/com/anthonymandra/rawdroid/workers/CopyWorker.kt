package com.anthonymandra.rawdroid.workers

import android.net.Uri
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.workDataOf
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.ImageUtil

class CopyWorker: Worker() {
	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getStringArray(KEY_COPY_URIS)
		val destination = inputData.getString(KEY_DEST_URI)?.toUri()

		return Result.SUCCESS
	}

	/**
	 * Copies an image and corresponding xmp and jpeg (ex: src/a.[cr2,xmp,jpg] -> dest/a.[cr2,xmp,jpg])
	 * @param fromImage source image
	 * @param toImage target image
	 * @return success
	 */
	private fun copyAssociatedFiles(fromImage: MetadataTest, toImage: Uri): Boolean {
		val sourceUri = Uri.parse(fromImage.uri)
		if (ImageUtil.hasXmpFile(this, sourceUri)) {
			copyFile(ImageUtil.getXmpFile(this, sourceUri).uri,
				ImageUtil.getXmpFile(this, toImage).uri)
		}
		if (ImageUtil.hasJpgFile(this, sourceUri)) {
			copyFile(ImageUtil.getJpgFile(this, sourceUri).uri,
				ImageUtil.getJpgFile(this, toImage).uri)
		}

		fromImage.uri = toImage.toString()  // update copied uri

		return copyFile(sourceUri, toImage)
	}

	companion object {
		const val JOB_TAG = "copy_job"
		const val KEY_COPY_URIS = "copy uris"
		const val KEY_DEST_URI = "destination"

		@JvmStatic
		fun buildRequest(imagesToCopy: List<Uri>, destination: Uri): WorkRequest? {
			val data = workDataOf(
				KEY_COPY_URIS to imagesToCopy.map { it.toString() }.toTypedArray(),
				KEY_DEST_URI to destination.toString()
			)

			return OneTimeWorkRequestBuilder<CopyWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}