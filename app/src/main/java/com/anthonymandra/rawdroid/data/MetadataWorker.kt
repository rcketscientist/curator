package com.anthonymandra.rawdroid.data

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.workDataOf
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.util.MetaUtil

class MetadataWorker: Worker() {
    override fun doWork(): Result {
        val filter = XmpFilter(
            inputData.getIntArray(KEY_FILTER_RATING)?.asList() ?: emptyList(),
            inputData.getStringArray(KEY_FILTER_LABEL)?.asList() ?: emptyList(),
            inputData.getLongArray(KEY_FILTER_SUBJECT)?.asList() ?: emptyList(),
            inputData.getBoolean(KEY_FILTER_AND, false),
            inputData.getBoolean(KEY_FILTER_ASC, true),
            inputData.getBoolean(KEY_FILTER_SEGREGATE, false),
            XmpFilter.SortColumns.valueOf(inputData.getString(KEY_FILTER_SORT) ?: XmpFilter.SortColumns.Name.toString()),
            inputData.getLongArray(KEY_FILTER_HIDDEN)?.toSet() ?: emptySet()
        )

        val repo = DataRepository.getInstance(this.applicationContext)

        val unprocessedImages = repo._getUnprocessedImages(filter)
        unprocessedImages.forEach {
            
            val metadata = MetaUtil.readMetadata(applicationContext, repo, it)
            if (metadata.processed) {
                repo.updateMeta(it).subscribe()
            }
        }
        return Result.SUCCESS
    }

    companion object {
        const val JOB_TAG = "metadata_job"
        const val KEY_FILTER_AND = "and"
        const val KEY_FILTER_ASC = "asc"
        const val KEY_FILTER_SEGREGATE = "segregate"
        const val KEY_FILTER_SORT = "sort"
        const val KEY_FILTER_HIDDEN = "hidden"
        const val KEY_FILTER_RATING = "rating"
        const val KEY_FILTER_LABEL = "label"
        const val KEY_FILTER_SUBJECT = "subject"

        @JvmStatic
        fun buildRequest(xmpFilter: XmpFilter = XmpFilter()): WorkRequest? {
            val data = workDataOf(
                KEY_FILTER_AND to xmpFilter.andTrueOrFalse,
                KEY_FILTER_ASC to xmpFilter.sortAscending,
                KEY_FILTER_SEGREGATE to xmpFilter.segregateByType,
                KEY_FILTER_SORT to xmpFilter.sortColumn.toString(),
                KEY_FILTER_HIDDEN to xmpFilter.hiddenFolderIds.toLongArray(),
                KEY_FILTER_RATING to xmpFilter.ratings.toIntArray(),
                KEY_FILTER_LABEL to xmpFilter.labels.toTypedArray(),
                KEY_FILTER_SUBJECT to xmpFilter.subjectIds.toLongArray()
            )

            return OneTimeWorkRequestBuilder<MetadataWorker>()
                    .addTag(JOB_TAG)
                    .setInputData(data)
                    .build()
        }
    }
}