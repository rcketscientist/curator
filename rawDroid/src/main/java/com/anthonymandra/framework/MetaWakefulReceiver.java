package com.anthonymandra.framework;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.WakefulBroadcastReceiver;

public class MetaWakefulReceiver extends WakefulBroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        ComponentName comp = new ComponentName(context.getPackageName(), MetaService.class.getName());
        startWakefulService(context, intent.setComponent(comp));
    }

    @SuppressWarnings("unused")
    public static void startMetaService(Context context, Uri data)
    {
        Intent intent = getService(context, data);
        context.sendBroadcast(intent);
    }

	/**
     * Starts processing metadata of any previously unprocessed entries
     * @param c context
     */
    public static void startMetaService(Context c)
    {
        Intent intent = new Intent(c, MetaWakefulReceiver.class);
        intent.setAction(MetaService.ACTION_UPDATE);
        c.sendBroadcast(intent);
    }

    public static void startMetaService(Context context, Uri data, int priority)
    {
        Intent intent = getService(context, data);
        intent.putExtra(ThreadedPriorityIntentService.EXTRA_PRIORITY, priority);
        context.sendBroadcast(intent);
    }

    public static void startPriorityMetaService(Context context, Uri data)
    {
        startMetaService(context, data, 5);
    }

    private static Intent getService(Context context, Uri data)
    {
        Intent intent = new Intent(context, MetaWakefulReceiver.class);
        intent.setAction(MetaService.ACTION_PARSE);
        intent.setData(data);
        return intent;
    }
}
