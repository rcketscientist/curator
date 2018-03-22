package com.anthonymandra.framework

import android.app.IntentService
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.content.WakefulBroadcastReceiver
import com.anthonymandra.rawdroid.data.AppDatabase
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.util.MetaUtil
import java.util.concurrent.atomic.AtomicInteger

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
class MetaService : PriorityIntentService("MetaService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (ACTION_PARSE == action) {
                handleActionParse(intent)
            } else if (ACTION_UPDATE == action) {
                handleActionUpdate(intent)
            }
        }
    }

    private fun handleActionUpdate(intent: Intent) {
        val repo = DataRepository.getInstance(AppDatabase.getInstance(this.applicationContext))

        val updates = repo.unprocessedImages()
        sJobsTotal.addAndGet(updates.size)

        try {
            val metaUpdates = updates.map {
                val metadata = MetaUtil.readMetadata(this, repo, it)
                jobComplete()

                val broadcast = Intent(BROADCAST_IMAGE_PARSED)
                        .putExtra(EXTRA_URI, it.uri)    // TODO: Better to send id
                        .putExtra(EXTRA_COMPLETED_JOBS, sJobsComplete.get())
                        .putExtra(EXTRA_TOTAL_JOBS, sJobsTotal.get())
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)

                return@map metadata
            }.filter { it.processed }

            metaUpdates.let {
                repo.updateMeta(*it.toTypedArray())
            }
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }
    }

    /**
     * Parse given uris and add to database in a batch
     */
    private fun handleActionParse(intent: Intent) {
        val repo = DataRepository.getInstance(AppDatabase.getInstance(this.applicationContext))

        val uris = if (intent.hasExtra(EXTRA_URIS))
            intent.getStringArrayExtra(EXTRA_URIS)
        else
            arrayOf(intent.data!!.toString())

        val images = repo.images(listOf(*uris)).value ?: return

        sJobsTotal.addAndGet(images.size)

        try {
            val updates = images
                .filter { !it.processed }
                .map {
                    val metadata = MetaUtil.readMetadata(this, repo, it)
                    jobComplete()

                    // TODO: There was a null check here but I'm doubtful it's needed

                    if (isHighPriority(intent)) {
                        val broadcast = Intent(BROADCAST_REQUESTED_META)
                            .putExtra(EXTRA_URI, it.uri)
                        // TODO: Is it a problem to look up on the other end?  This used to broadcast the meta (not written here...)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
                        return@map null
                    }

                    val broadcast = Intent(BROADCAST_IMAGE_PARSED)
                        .putExtra(EXTRA_URI, it.uri)
                        .putExtra(EXTRA_COMPLETED_JOBS, sJobsComplete.get())
                        .putExtra(EXTRA_TOTAL_JOBS, sJobsTotal.get())
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)

                    return@map metadata
                }.mapNotNull { it } // TODO: This is some sloppy garbage...

            updates.let {
                repo.updateMeta(*it.toTypedArray())
            }
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val broadcast = Intent(BROADCAST_PARSE_COMPLETE)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    companion object {
        //TODO: May need to handle Android 6.0 Doze issues.
        /**
         * Broadcast ID when all parsing is complete
         */
        val BROADCAST_PARSE_COMPLETE = "com.anthonymandra.framework.action.BROADCAST_PARSE_COMPLETE"

        /**
         * Broadcast ID when database is bulk updated with queue of parsed images
         */
        val BROADCAST_BULK_UPDATE = "com.anthonymandra.framework.action.BROADCAST_BULK_UPDATE"

        /**
         * Broadcast ID when image parse is complete
         */
        val BROADCAST_IMAGE_PARSED = "com.anthonymandra.framework.action.BROADCAST_IMAGE_PARSED"

        /**
         * Broadcast ID when priority image has updated database
         */
        val BROADCAST_REQUESTED_META = "com.anthonymandra.framework.action.BROADCAST_REQUESTED_META"

        /**
         * Broadcast ID after processing, before database is updated
         */
        val BROADCAST_PROCESSING_COMPLETE = "com.anthonymandra.framework.action.BROADCAST_PROCESSING_COMPLETE"

        /**
         * Intent ID to request parsing of image meta data
         */
        val ACTION_PARSE = "com.anthonymandra.framework.action.ACTION_PARSE"

        /**
         * Intent ID to request parsing of image meta data
         */
        val ACTION_UPDATE = "com.anthonymandra.framework.action.ACTION_UPDATE"

        /**
         * Intent extra containing URI of image(s) to parse for meta data
         */
        val EXTRA_URIS = "com.anthonymandra.framework.extra.EXTRA_URIS"

        /**
         * Intent extra containing URI of image(s) to parse for meta data
         */
        val EXTRA_URI = "com.anthonymandra.framework.extra.EXTRA_URI"

        /**
         * Intent extra containing number of completed jobs in current parse
         */
        val EXTRA_COMPLETED_JOBS = "com.anthonymandra.framework.extra.EXTRA_COMPLETED_JOBS"

        /**
         * Intent extra containing number of completed jobs in current parse
         */
        val EXTRA_TOTAL_JOBS = "com.anthonymandra.framework.extra.EXTRA_TOTAL_JOBS"

        /**
         * Intent extra containing the processed meta data.
         */
        val EXTRA_METADATA = "com.anthonymandra.framework.extra.EXTRA_METADATA"

        private val sJobsTotal = AtomicInteger(0)
        private val sJobsComplete = AtomicInteger(0)

        /**
         * Increment counter and if all jobs are complete reset the counters
         */
        private fun jobComplete() {
            val completed = sJobsComplete.incrementAndGet()
            if (completed == sJobsTotal.get()) {
                sJobsComplete.set(0)
                sJobsTotal.set(0)
            }
        }
    }
}
