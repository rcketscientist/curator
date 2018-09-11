package com.anthonymandra.framework

import android.app.IntentService
import android.content.Intent
import androidx.legacy.content.WakefulBroadcastReceiver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.util.MetaUtil

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
        val repo = DataRepository.getInstance(this.applicationContext)

        val updates = repo._getUnprocessedImages()

        try {
            updates.forEach {
                val metadata = MetaUtil.readMetadata(this, repo, it)
                if (metadata.processed) {
                    repo.updateMeta(it).subscribe()
                }
            }
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }
    }

    /**
     * Parse given uris and add to database in a batch
     */
    private fun handleActionParse(intent: Intent) {
        val repo = DataRepository.getInstance(this.applicationContext)

        val uris = if (intent.hasExtra(EXTRA_URIS))
            intent.getStringArrayExtra(EXTRA_URIS)
        else
            arrayOf(intent.data!!.toString())

        try {
            repo._images(listOf(*uris))
                .filter { !it.processed }
                .forEach {
                    val metadata = MetaUtil.readMetadata(this, repo, it)
                    if (metadata.processed) {
                        repo.updateMeta(it).subscribe()
                    }
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
         * Intent extra containing the processed meta data.
         */
        val EXTRA_METADATA = "com.anthonymandra.framework.extra.EXTRA_METADATA"
    }
}
