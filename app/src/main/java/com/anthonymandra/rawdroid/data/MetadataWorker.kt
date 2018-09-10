//package com.anthonymandra.rawdroid.data
//
//import androidx.work.*
//import com.anthonymandra.rawdroid.XmpFilter
//import com.anthonymandra.rawdroid.XmpValues
//
//class MetadataWorker: Worker() {
//    override fun doWork(): Result {
////        val filter = XmpFilter(
////                XmpValues(inputData.getIntArray(KEY_FILTER_RATING)?.asList() ?: emptyList(),
////                        inputData.getStringArray(KEY_FILTER_LABEL)?.asList() ?: emptyList(),
////                        inputData.getStringArray(KEY_FILTER_SUBJECT)?.asList() ?: emptyList())
////        )
//
//    }
//
//    companion object {
//        const val JOB_TAG = "metadata_job"
//        const val KEY_FILTER_AND = "and"
//        const val KEY_FILTER_ASC = "asc"
//        const val KEY_FILTER_SEGREGATE = "segregate"
//        const val KEY_FILTER_SORT = "sort"
//        const val KEY_FILTER_HIDDEN = "hidden"
//        const val KEY_FILTER_RATING = "rating"
//        const val KEY_FILTER_LABEL = "label"
//        const val KEY_FILTER_SUBJECT = "subject"
//        ​
//        @JvmStatic
//        fun buildRequest(xmpFilter: XmpFilter): WorkRequest? {
//            val data = workDataOf(
//                    KEY_FILTER_AND to xmpFilter.andTrueOrFalse,
//                    KEY_FILTER_ASC to xmpFilter.sortAscending,
//                    KEY_FILTER_SEGREGATE to xmpFilter.segregateByType,
//                    KEY_FILTER_SORT to xmpFilter.sortColumn,
//                    KEY_FILTER_HIDDEN to xmpFilter.hiddenFolderIds,
//                    KEY_FILTER_RATING to xmpFilter.xmp?.rating,
//                    KEY_FILTER_LABEL to xmpFilter.xmp?.label,
//                    KEY_FILTER_SUBJECT to xmpFilter.xmp?.subject
//            )
//
//            return OneTimeWorkRequestBuilder<MetadataWorker>()
//                    .addTag(JOB_TAG)
//                    .setInputData(data)
//                    .build()
//        }
//    }
//}