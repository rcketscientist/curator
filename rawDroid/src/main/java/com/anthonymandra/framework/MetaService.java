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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;

import java.util.ArrayList;
import java.util.List;
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
     * Intent ID to request parsing of image meta data
     */
    public static final String ACTION_UPDATE = "com.anthonymandra.framework.action.ACTION_UPDATE";

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

    private static final AtomicInteger sJobsTotal = new AtomicInteger(0);
    private static final AtomicInteger sJobsComplete = new AtomicInteger(0);
    private static final int sMinBatchSize = 20;

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

        public Thread newThread(@NonNull Runnable r) {
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

    @Override
    public void onCreate()
    {
        super.onCreate();
//        setThreadPool(Executors.newFixedThreadPool(2));

        //TODO: For some reason this is ending up single threaded
//        setThreadPool(new ThreadPoolExecutor(
//                0, Runtime.getRuntime().availableProcessors(),
//                60L, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<Runnable>(),
//                new MetaThreadFactory()));
    }

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
            else if (ACTION_UPDATE.equals(action))
            {
                handleActionUpdate(intent);
            }
        }
    }

    public static boolean isProcessed(Cursor c)
    {
        final int processedColumn = c.getColumnIndex(Meta.PROCESSED);
        return c.getInt(processedColumn) != 0;
    }

	/**
     * Increment counter and if all jobs are complete reset the counters
     */
    private static void jobComplete()
    {
        int completed = sJobsComplete.incrementAndGet();
        if (completed == sJobsTotal.get())
        {
            sJobsComplete.set(0);
            sJobsTotal.set(0);
        }
    }

    private static class UpdateInfo
    {
        private static final String[] PROJECTION = {Meta.URI, Meta.NAME};
        private String Uri;
        private String Name;

        UpdateInfo(String uri, String name)
        {
            Uri = uri;
            Name = name;
        }
    }

    private static @Nullable Cursor getUnprocessedMetaCursor(Context c)
    {
        return c.getContentResolver().query(Meta.CONTENT_URI,
                UpdateInfo.PROJECTION,
                Meta.PROCESSED + " is null or " + Meta.PROCESSED + " = ?",
                new String[] {""},
                null);
    }

    private static List<UpdateInfo> getUpdateArray(Context context)
    {
        try( Cursor c = getUnprocessedMetaCursor(context) )
        {
            if (c == null)
                return null;

            int uriIndex = c.getColumnIndex(Meta.URI);
            int nameIndex = c.getColumnIndex(Meta.NAME);

            List<UpdateInfo> updates = new ArrayList<>();

            while (c.moveToNext())
            {
                updates.add(new UpdateInfo(c.getString(uriIndex), c.getString(nameIndex)));
            }
            return updates;
        }
    }

    private void handleActionUpdate(Intent intent)
    {
        List<UpdateInfo> updates = getUpdateArray(this);
        if (updates == null)
            return;

        sJobsTotal.addAndGet(updates.size());

        try
        {
            for (UpdateInfo update : updates)
            {
                ContentValues values = ImageUtils.getContentValues(this, Uri.parse(update.Uri));
                jobComplete();

                if (values == null)
                    continue;

                addUpdate(update.Uri, values);

                Intent broadcast = new Intent(BROADCAST_IMAGE_PARSED)
                        .putExtra(EXTRA_URI, update.Uri)
                        .putExtra(EXTRA_COMPLETED_JOBS, sJobsComplete.get())
                        .putExtra(EXTRA_TOTAL_JOBS, sJobsTotal.get());
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

                try
                {
                    processUpdates();
                } catch (RemoteException | OperationApplicationException e)
                {
                    //TODO: Notify user
                    e.printStackTrace();
                }
            }
        }
        finally
        {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

    /**
     * Parse given uris and add to database in a batch
     */
    private void handleActionParse(Intent intent)
    {
        String[] uris;
        if (intent.hasExtra(EXTRA_URIS))
            uris = intent.getStringArrayExtra(EXTRA_URIS);
        else
            uris = new String[] { intent.getData().toString() };

        try(Cursor c = ImageUtils.getMetaCursor(this, uris))
        {
            if (c == null)
                return;

	        sJobsTotal.addAndGet(c.getCount());
            while (c.moveToNext())
            {
                Uri uri = Uri.parse(c.getString(c.getColumnIndex(Meta.URI)));

                ContentValues values = new ContentValues();
                boolean isProcessed = isProcessed(c);
                if (isProcessed)
                {
                    DatabaseUtils.cursorRowToContentValues(c, values);
                }
                else
                {
                    values = ImageUtils.getContentValues(this, uri);
                }
	            jobComplete();

                if (values == null)
                    continue;

                // If this is a high priority request then add to db immediately
                if (isHigherThanDefault(intent))
                {
                    if (!isProcessed)
                    {
                        getContentResolver().update(Meta.CONTENT_URI,
                                values,
                                ImageUtils.getWhereUri(),
                                new String[]{ uri.toString() });
                    }

                    int nameColumn = c.getColumnIndex(Meta.NAME);
                    if (nameColumn == -1)
                        continue;

                    values.put(Meta.NAME, c.getString(nameColumn));  // add name to broadcast

                    Intent broadcast = new Intent(BROADCAST_REQUESTED_META)
                            .putExtra(EXTRA_URI, uri.toString())
                            .putExtra(EXTRA_METADATA, values);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
                }
                else if (!isProcessed)
                {
                    addUpdate(uri.toString(), values);
                }

                Intent broadcast = new Intent(BROADCAST_IMAGE_PARSED)
                        .putExtra(EXTRA_URI, uri.toString())
                        .putExtra(EXTRA_COMPLETED_JOBS, sJobsComplete.get())
                        .putExtra(EXTRA_TOTAL_JOBS, sJobsTotal.get());
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

                try
                {
                    processUpdates();
                }
                catch (RemoteException | OperationApplicationException e)
                {
                    //TODO: Notify user
                    e.printStackTrace();
                }
            }
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
            processUpdates(true);
        }
        catch (RemoteException | OperationApplicationException e)
        {
            e.printStackTrace();
        }

        Intent broadcast = new Intent(BROADCAST_PARSE_COMPLETE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private synchronized void processUpdates() throws RemoteException, OperationApplicationException
    {
        processUpdates(false);
    }

    private synchronized void processUpdates(boolean processNow) throws RemoteException, OperationApplicationException
    {
        // Update the database periodically
        if (mOperations.size() > sMinBatchSize || processNow)
        {
            getContentResolver().applyBatch(Meta.AUTHORITY, mOperations);
            mOperations.clear();
            Intent broadcast = new Intent(BROADCAST_BULK_UPDATE);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
    }

    private synchronized void addUpdate(String uriString, ContentValues values)
    {
        mOperations.add(ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
                .withSelection(ImageUtils.getWhereUri(), new String[] {uriString})
                .withValues(values)
                .build());
    }
}
