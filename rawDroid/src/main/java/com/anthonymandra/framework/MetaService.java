package com.anthonymandra.framework;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;

import java.util.ArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class MetaService extends ThreadedPriorityIntentService
{
    //TODO: May need to handle Android 6.0 Doze issues.
    /**
     * Broadcast ID when all parsing is complete
     */
    public static final String BROADCAST_PARSE_COMPLETE = "com.anthonymandra.framework.action.BROADCAST_PARSE_COMPLETE";

    /**
     * Broadcast ID when database is bulk updated with queue of parsed images
     */
    public static final String BROADCAST_BULK_UPDATE = "com.anthonymandra.framework.action.BROADCAST_BULK_UPDATE";

    /**
     * Broadcast ID when image parse is complete
     */
    public static final String BROADCAST_IMAGE_PARSED = "com.anthonymandra.framework.action.BROADCAST_IMAGE_PARSED";

    /**
     * Broadcast ID when priority image has updated database
     */
    public static final String BROADCAST_REQUESTED_META = "com.anthonymandra.framework.action.BROADCAST_REQUESTED_META";

    /**
     * Broadcast ID after processing, before database is updated
     */
    public static final String BROADCAST_PROCESSING_COMPLETE = "com.anthonymandra.framework.action.BROADCAST_PROCESSING_COMPLETE";

    /**
     * Intent ID to request parsing of image meta data
     */
    public static final String ACTION_PARSE = "com.anthonymandra.framework.action.ACTION_PARSE";

    /**
     * Intent extra containing URI of image(s) to parse for meta data
     */
    public static final String EXTRA_URIS = "com.anthonymandra.framework.extra.EXTRA_URIS";

    /**
     * Intent extra containing URI of image(s) to parse for meta data
     */
    public static final String EXTRA_URI = "com.anthonymandra.framework.extra.EXTRA_URI";

    /**
     * Intent extra containing number of completed jobs in current parse
     */
    public static final String EXTRA_COMPLETED_JOBS = "com.anthonymandra.framework.extra.EXTRA_COMPLETED_JOBS";

    /**
     * Intent extra containing number of completed jobs in current parse
     */
    public static final String EXTRA_TOTAL_JOBS = "com.anthonymandra.framework.extra.EXTRA_TOTAL_JOBS";

    /**
     * Intent extra containing the processed meta data.
     */
    public static final String EXTRA_METADATA = "com.anthonymandra.framework.extra.EXTRA_METADATA";

    private final ArrayList<ContentProviderOperation> mOperations = new ArrayList<>();

    /**
     * The default thread factory
     */
    static class MetaThreadFactory implements ThreadFactory
    {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        MetaThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "MetaService-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.MIN_PRIORITY)
                t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    }

    //TODO: For some reason this is ending up single threaded
//    @Override
//    public void onCreate()
//    {
//        super.onCreate();
//        setThreadPool(new ThreadPoolExecutor(
//                0, Runtime.getRuntime().availableProcessors(),
//                60L, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<Runnable>(),
//                new MetaThreadFactory()));
//    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionParse(Context context, String[] uris)
    {
        Intent intent = new Intent(context, MetaService.class);
        intent.setAction(ACTION_PARSE);
        intent.putExtra(EXTRA_URIS, uris);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        if (intent != null)
        {
            final String action = intent.getAction();
            if (ACTION_PARSE.equals(action))
            {
                handleActionParse(intent);
            }
        }
    }

    /**
     * Parse given uris and add to database in a batch
     */
    private void handleActionParse(Intent intent)
    {
        Uri uri = intent.getData();

        // Check if meta is already processed
        Cursor c = ImageUtils.getMetaCursor(this, uri);
        if (c.moveToFirst() && c.getInt(Meta.PROCESSED_COLUMN) != 0)
        {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(c, values);

            Intent broadcast = new Intent(BROADCAST_REQUESTED_META)
                    .putExtra(EXTRA_URI, uri.toString())
                    .putExtra(EXTRA_METADATA, values);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

            WakefulBroadcastReceiver.completeWakefulIntent(intent);
            return;
        }

        ContentValues values = ImageUtils.getContentValues(this, uri);

        // If this is a high priority request then add to db immediately
        if (isHigherThanDefault(intent))
        {
            getContentResolver().update(Meta.Data.CONTENT_URI,
                    values,
                    ImageUtils.getWhere(),
                    new String[]{uri.toString()});

            values.put(Meta.Data.NAME, c.getString(Meta.NAME_COLUMN));  // add name to broadcast
            Intent broadcast = new Intent(BROADCAST_REQUESTED_META)
                    .putExtra(EXTRA_URI, uri.toString())
                    .putExtra(EXTRA_METADATA, values);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
        else
        {
            addUpdate(uri, values);
            Intent broadcast = new Intent(BROADCAST_IMAGE_PARSED)
                    .putExtra(EXTRA_URI, uri.toString())
                    .putExtra(EXTRA_COMPLETED_JOBS, getCompletedJobs())
                    .putExtra(EXTRA_TOTAL_JOBS, getTotalJobs());
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }

        try
        {
            processUpdates(20);
        }
        catch (RemoteException | OperationApplicationException e)
        {
            //TODO: Notify user
            e.printStackTrace();
        }
        finally
        {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

    @Override
    public void onStop()
    {
        super.onStop();
        try
        {
            Intent broadcast = new Intent(BROADCAST_PROCESSING_COMPLETE);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
            //TODO: Is it possible that the thread this originated from could be reclaimed losing the update?
            processUpdates(0);
        }
        catch (RemoteException | OperationApplicationException e)
        {
            e.printStackTrace();
        }

        // TODO: Should change the extra if we want the number processed
        Intent broadcast = new Intent(BROADCAST_PARSE_COMPLETE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private synchronized void processUpdates(int minBatchSize) throws RemoteException, OperationApplicationException
    {
        // Update the database periodically
        if (mOperations.size() > minBatchSize)
        {
            // TODO: If I implement bulkInsert it's faster
            getContentResolver().applyBatch(Meta.AUTHORITY, mOperations);
            mOperations.clear();
            Intent broadcast = new Intent(BROADCAST_BULK_UPDATE);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
    }

    private synchronized void addUpdate(Uri uri, ContentValues values)
    {
        mOperations.add(ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
                .withSelection(ImageUtils.getWhere(), new String[] {uri.toString()})
                .withValues(values)
                .build());
    }
}
