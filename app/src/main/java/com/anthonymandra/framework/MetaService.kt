package com.anthonymandra.framework

import android.app.IntentService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.content.WakefulBroadcastReceiver
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.data.AppDatabase
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.util.MetaUtil
import java.util.*
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

    internal class UpdateInfo internal constructor(internal val Uri: String, internal val Name: String) {
        companion object {
            private val PROJECTION = arrayOf(Meta.URI, Meta.NAME)
        }
    }

    private fun handleActionUpdate(intent: Intent) {
        val updates = getUpdateArray(this) ?: return

        sJobsTotal.addAndGet(updates.size)

        val repo = DataRepository.getInstance(AppDatabase.getInstance(this.applicationContext))

        try {
            for (update in updates) {
                val metadata = MetaUtil.readMetadata(
                    this,
                    repo,
                    Uri.parse(update.Uri))
                jobComplete()

                if (metadata == null)   // TODO: Check processed?
                    continue

                updateProvider(update.Uri, values)

                val broadcast = Intent(BROADCAST_IMAGE_PARSED)
                    .putExtra(EXTRA_URI, update.Uri)
                    .putExtra(EXTRA_COMPLETED_JOBS, sJobsComplete.get())
                    .putExtra(EXTRA_TOTAL_JOBS, sJobsTotal.get())
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
            }
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }
    }

    private fun getParseArray(uris: Array<String>): List<ContentValues>? {
        MetaUtil.getMetaCursor(this, uris)!!.use { c ->
            if (c == null)
                return null

            val rows = ArrayList<ContentValues>()

            while (c.moveToNext()) {
                val values = ContentValues()
                DatabaseUtils.cursorRowToContentValues(c, values)
                rows.add(values)
            }
            return rows
        }
    }

    /**
     * Parse given uris and add to database in a batch
     */
    private fun handleActionParse(intent: Intent) {
        val uris: Array<String>
        if (intent.hasExtra(EXTRA_URIS))
            uris = intent.getStringArrayExtra(EXTRA_URIS)
        else
            uris = arrayOf(intent.data!!.toString())

        val rows = getParseArray(uris) ?: return

        sJobsTotal.addAndGet(rows.size)

        try {
            for (values in rows) {
                val uri = Uri.parse(values.getAsString(Meta.URI))
                val isProcessed = MetaUtil.isProcessed(values)

                // If the image is unprocessed process it now
                if (!isProcessed)
                    values = MetaUtil.getContentValues(this, uri, values)

                jobComplete()

                if (values == null)
                    continue

                if (isHighPriority(intent)) {
                    val broadcast = Intent(BROADCAST_REQUESTED_META)
                        .putExtra(EXTRA_URI, uri.toString())
                        .putExtra(EXTRA_METADATA, values)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
                }

                if (!isProcessed) {
                    updateProvider(uri.toString(), values)
                }

                val broadcast = Intent(BROADCAST_IMAGE_PARSED)
                    .putExtra(EXTRA_URI, uri.toString())
                    .putExtra(EXTRA_COMPLETED_JOBS, sJobsComplete.get())
                    .putExtra(EXTRA_TOTAL_JOBS, sJobsTotal.get())
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
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

    private fun updateProvider(uri: String, values: ContentValues) {
        contentResolver.update(Meta.CONTENT_URI,
            values,
            Meta.URI_SELECTION,
            arrayOf(uri))
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
         * Starts this service to perform action Foo with the given parameters. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        fun startActionParse(context: Context, uris: Array<String>) {
            val intent = Intent(context, MetaService::class.java)
            intent.action = ACTION_PARSE
            intent.putExtra(EXTRA_URIS, uris)
            context.startService(intent)
        }

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

        private fun getUnprocessedMetaCursor(c: Context): Cursor? {
            return c.contentResolver.query(Meta.CONTENT_URI,
                UpdateInfo.PROJECTION,
                Meta.PROCESSED + " is null or " + Meta.PROCESSED + " = ?",
                arrayOf(""), null)
        }

        private fun getUpdateArray(context: Context): List<UpdateInfo>? {
            getUnprocessedMetaCursor(context)!!.use { c ->
                if (c == null)
                    return null

                val uriIndex = c.getColumnIndex(Meta.URI)
                val nameIndex = c.getColumnIndex(Meta.NAME)

                val updates = ArrayList<UpdateInfo>()

                while (c.moveToNext()) {
                    updates.add(UpdateInfo(c.getString(uriIndex), c.getString(nameIndex)))
                }
                return updates
            }
        }
    }
}
