package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.ImageFilter
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.util.MetaUtil
import com.anthonymandra.util.Util

class MetaReaderWorker(context: Context, params: WorkerParameters): Worker(context, params) {
    override fun doWork(): Result {
        val filter = ImageFilter(
            inputData.getIntArray(KEY_FILTER_RATING)?.asList() ?: emptyList(),
            inputData.getStringArray(KEY_FILTER_LABEL)?.asList() ?: emptyList(),
            inputData.getLongArray(KEY_FILTER_SUBJECT)?.asList() ?: emptyList(),
            inputData.getBoolean(KEY_FILTER_AND, false),
            inputData.getBoolean(KEY_FILTER_ASC, true),
            inputData.getBoolean(KEY_FILTER_SEGREGATE, false),
            ImageFilter.SortColumns.valueOf(inputData.getString(KEY_FILTER_SORT) ?: ImageFilter.SortColumns.Name.toString()),
            inputData.getLongArray(KEY_FILTER_HIDDEN)?.toSet() ?: emptySet()
        )

        val repo = DataRepository.getInstance(this.applicationContext)

        // TODO: resources for all of these strings
        Util.createNotificationChannel(
            applicationContext,
            JOB_TAG,
            "Parsing meta...",
            "Notifications for metadata tasks.")

        val builder = Util.createNotification(
            applicationContext,
            JOB_TAG,
            applicationContext.getString(R.string.processingImages),
            applicationContext.getString(R.string.preparing))
//            .addAction(0, applicationContext.getString(R.string.cancel),
//                PendingIntent.getBroadcast(applicationContext, 111, null, 0))

        val notifications = NotificationManagerCompat.from(applicationContext)
        notifications.notify(builder.build())

        val unprocessedImages = repo._getUnprocessedImages(filter)
        unprocessedImages.forEachIndexed { index, value ->
            if (isCancelled) {
                builder
                    .setContentText("Cancelled")
                    .setProgress(0,0,false)
                    .priority = NotificationCompat.PRIORITY_HIGH
                notifications.notify(builder.build())

                return Result.SUCCESS
            }

            builder
                .setProgress(unprocessedImages.size, index, false)
                .setContentText(value.name)
                .priority = NotificationCompat.PRIORITY_DEFAULT
            notifications.notify(builder.build())

            val metadata = MetaUtil.readMetadata(applicationContext, repo, value)
            if (metadata.processed) {
                repo.updateMeta(value).subscribe()
            }
        }

        builder
            .setContentText("Complete")
            .setProgress(0,0,false)
            .priority = NotificationCompat.PRIORITY_HIGH
        notifications.notify(builder.build())

        return Result.SUCCESS
    }

    private fun NotificationManagerCompat.notify(notification: Notification) {
        this.notify(CopyWorker.JOB_TAG, 0, notification)
    }

    companion object {
        const val JOB_TAG = "metareader_job"
        const val KEY_FILTER_AND = "and"
        const val KEY_FILTER_ASC = "asc"
        const val KEY_FILTER_SEGREGATE = "segregate"
        const val KEY_FILTER_SORT = "sort"
        const val KEY_FILTER_HIDDEN = "hidden"
        const val KEY_FILTER_RATING = "rating"
        const val KEY_FILTER_LABEL = "label"
        const val KEY_FILTER_SUBJECT = "subject"

        @JvmStatic
        fun buildRequest(imageFilter: ImageFilter = ImageFilter()): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_FILTER_AND to imageFilter.andTrueOrFalse,
                KEY_FILTER_ASC to imageFilter.sortAscending,
                KEY_FILTER_SEGREGATE to imageFilter.segregateByType,
                KEY_FILTER_SORT to imageFilter.sortColumn.toString(),
                KEY_FILTER_HIDDEN to imageFilter.hiddenFolderIds.toLongArray(),
                KEY_FILTER_RATING to imageFilter.ratings.toIntArray(),
                KEY_FILTER_LABEL to imageFilter.labels.toTypedArray(),
                KEY_FILTER_SUBJECT to imageFilter.subjectIds.toLongArray()
            )

            return OneTimeWorkRequestBuilder<MetaReaderWorker>()
                    .addTag(JOB_TAG)
                    .setInputData(data)
                    .build()
        }
    }
}