package com.anthonymandra.rawdroid.workers

import android.content.Context
import androidx.core.net.toUri
import androidx.work.*
import com.anthonymandra.rawdroid.XmpUpdateField
import com.anthonymandra.rawdroid.XmpValues
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.MetaUtil

class MetaWriterWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

	override fun doWork(): Result {
		val repo = DataRepository.getInstance(this.applicationContext)
		val images = inputData.getLongArray(KEY_IMAGE_IDS)
		val subjectIds = inputData.getLongArray(KEY_SUBJECT)
		val label = inputData.getString(KEY_LABEL)
		val rating = inputData.getInt(KEY_RATING, 0)
		val field = inputData.getString(KEY_UPDATE_FIELD)?.let {
			XmpUpdateField.valueOf(it)
		}

		if (images == null) return Result.failure()
		val subjects = if (subjectIds != null) repo.synchSubjects(subjectIds) else emptyList()

		// TODO: We could have an xmp field to save the xmp file check error, although that won't work if not processed
		val metadata = repo.synchImages(images)
		metadata.forEachIndexed { index, image ->
			if (isStopped) {
				return Result.success()
			}

			val xmp = ImageUtil.getXmpFile(applicationContext, image.uri.toUri())
				?: return@forEachIndexed
			val meta = MetaUtil.readXmp(applicationContext, xmp)

			when (field) {
				XmpUpdateField.Rating -> {
					image.rating = rating.toFloat()
					repo.updateMeta(image).subscribe()
					MetaUtil.updateXmpInteger(meta, MetaUtil.RATING, rating)
				}
				XmpUpdateField.Label -> {
					image.label = label
					repo.updateMeta(image).subscribe()
					MetaUtil.updateXmpString(meta, MetaUtil.LABEL, label)
				}
				XmpUpdateField.Subject -> {
					image.subjectIds = subjectIds?.toList() ?: emptyList()
					repo.updateMeta(image).subscribe()
					MetaUtil.updateXmpStringArray(meta, MetaUtil.SUBJECT, subjects.map { it.name }.toTypedArray())
				}
				else -> {
					image.subjectIds = subjectIds?.toList() ?: emptyList()
					MetaUtil.updateXmpStringArray(meta, MetaUtil.SUBJECT, subjects.map { it.name }.toTypedArray())
					image.rating = rating.toFloat()
					MetaUtil.updateXmpInteger(meta, MetaUtil.RATING, rating)
					image.label = label
					MetaUtil.updateXmpString(meta, MetaUtil.LABEL, label)
					repo.updateMeta(image).subscribe()
				}
			}

			xmp.create()
			applicationContext.contentResolver.openOutputStream(xmp.uri)?.use {
				MetaUtil.writeXmp(it, meta)
			}

			repo.updateMeta(image)
		}

		return Result.success()
	}

	companion object {
		const val JOB_TAG = "metawriter_job"
		const val KEY_IMAGE_IDS = "image ids"
		const val KEY_SUBJECT = "subject"
		const val KEY_RATING = "rating"
		const val KEY_LABEL = "label"
		const val KEY_UPDATE_FIELD = "field"

		@JvmStatic
		fun buildRequest(images: LongArray, meta: XmpValues, field: XmpUpdateField): OneTimeWorkRequest {
			val data = workDataOf(
				KEY_IMAGE_IDS to images,
				KEY_SUBJECT to meta.subject.map { it.id }.toTypedArray(),
				KEY_LABEL to meta.label,
				KEY_RATING to meta.rating,
				KEY_UPDATE_FIELD to field.toString()
			)

			return OneTimeWorkRequestBuilder<MetaWriterWorker>()
				.addTag(JOB_TAG)
				.setInputData(data)
				.build()
		}
	}
}