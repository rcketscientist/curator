package com.anthonymandra.rawdroid.data

import androidx.work.Worker

const val KEY_FILTER = "filter"

class MetadataWorker: Worker() {
    override fun doWork(): Result {
        val filter = inputData.keyValueMap
    }
}