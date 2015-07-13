package com.anthonymandra.framework;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.anthonymandra.content.Meta;

import java.util.ArrayList;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class MetaService extends ThreadedPriorityIntentService
{
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
    public static final String BROADCAST_IMAGE_UPDATE = "com.anthonymandra.framework.action.BROADCAST_IMAGE_UPDATE";

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

    private final ArrayList<ContentProviderOperation> mOperations = new ArrayList<>();

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
        ContentValues values = ImageUtils.getContentValues(this, uri);

        // If this is a high priority request then add to db immediately
        if (isHigherThanDefault(intent))
        {
            getContentResolver().update(Meta.Data.CONTENT_URI,
                    values,
                    Meta.Data.URI + " = ?",
                    new String[]{uri.toString()});

            Intent broadcast = new Intent(BROADCAST_IMAGE_UPDATE).putExtra(EXTRA_URI, uri.toString());
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
        else
        {
            addUpdate(values);
            Intent broadcast = new Intent(BROADCAST_IMAGE_PARSED).putExtra(EXTRA_URI, uri.toString());
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }

        WakefulBroadcastReceiver.completeWakefulIntent(intent);
//        try
//        {
//            processUpdates(49);
//        }
//        catch (RemoteException | OperationApplicationException e)
//        {
//            //TODO: Notify user
//            e.printStackTrace();
//        }
//        finally
//        {
//            WakefulBroadcastReceiver.completeWakefulIntent(intent);
//        }
    }

    @Override
    public void onStop()
    {
        super.onStop();
        try
        {
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

    private synchronized void addUpdate(ContentValues values)
    {
        mOperations.add(ContentProviderOperation.newInsert(Meta.Data.CONTENT_URI)
                .withValues(values)
                .build());
    }
}
