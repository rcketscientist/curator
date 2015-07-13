package com.anthonymandra.framework;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;

import com.anthonymandra.content.Meta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchService extends IntentService
{
    /**
     * Broadcast ID when parsing is complete
     */
    public static final String BROADCAST_FOUND = "com.anthonymandra.framework.action.BROADCAST_FOUND";

    /**
     * Broadcast extra containing the number of images that were parsed
     */
    public static final String EXTRA_IMAGES = "com.anthonymandra.framework.action.EXTRA_IMAGES";

    private static final String ACTION_SEARCH = "com.anthonymandra.framework.action.ACTION_SEARCH";
    private static final String EXTRA_ROOTS = "com.anthonymandra.framework.extra.EXTRA_ROOTS";
    private static final String EXTRA_SKIP = "com.anthonymandra.framework.extra.EXTRA_SKIP";
    // This could make it generic, but skip for now
//    private static final String EXTRA_EXT = "com.anthonymandra.framework.extra.EXTRA_EXT";

    private static final Set<String> images = new HashSet<>();

    public static void startActionSearch(Context context, String[] roots, String[] skip)//, String[] ext)
    {
        Intent intent = new Intent(context, SearchService.class);
        intent.setAction(ACTION_SEARCH);
        intent.putExtra(EXTRA_ROOTS, roots);
        intent.putExtra(EXTRA_SKIP, skip);
//        intent.putExtra(EXTRA_EXT, ext);
        context.startService(intent);
    }

    public SearchService()
    {
        super("SearchService");
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        if (intent != null)
        {
            final String action = intent.getAction();
            if (ACTION_SEARCH.equals(action))
            {
                final String[] root = intent.getStringArrayExtra(EXTRA_ROOTS);
                final String[] skip = intent.getStringArrayExtra(EXTRA_SKIP);
//                final String[] ext = intent.getStringArrayExtra(EXTRA_SKIP);
                handleActionSearch(root, skip);//, ext);
            }
        }
    }

    private void handleActionSearch(String[] roots, String[] skip)//, String[] ext)
    {
        List<File> skipFiles = new ArrayList<>();
        images.clear();

        for (String toSkip : skip)
            skipFiles.add(new File(toSkip));
        for (String root : roots)
            search(new File(root), skipFiles.toArray(new File[skipFiles.size()]));

        if (images.size() > 0)
        {
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            for (String image : images)
            {
                File file = new File(image);
                ContentValues cv = new ContentValues();
                cv.put(Meta.Data.NAME, file.getName());
                cv.put(Meta.Data.URI, Uri.fromFile(file).toString());
                operations.add(ContentProviderOperation.newInsert(Meta.Data.CONTENT_URI)
                        .withValues(cv)
                        .build());
            }

            try
            {
                // TODO: If I implement bulkInsert it's faster
                getContentResolver().applyBatch(Meta.AUTHORITY, operations);
            } catch (RemoteException | OperationApplicationException e)
            {
                //TODO: Notify user
                e.printStackTrace();
            }

            Intent broadcast = new Intent(BROADCAST_FOUND)
                    .putExtra(EXTRA_IMAGES, images.toArray(new String[images.size()]));
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
    }

    public static void search(File dir, File[] skipFiles)//, String[] ext)
    {
        if (dir == null)
            return;
        if (dir.listFiles() == null)
            return;

        Util.ImageFilter imageFilter = new Util.ImageFilter();

        // This is a hack to handle the jacked up filesystem
        for (File skip : skipFiles)
        {
            if (dir.equals(skip))
                return;
        }

        // We must use a canonical path due to the fucked up multi-user/symlink setup
        File[] imageFiles = dir.listFiles(imageFilter);
        if (imageFiles != null && imageFiles.length > 0)
        {
            for (File raw: imageFiles)
            {
                try
                {
                    images.add(raw.getCanonicalPath());
                } catch (IOException e)
                {
                    // God this is ugly, just do nothing with an error.
                    e.printStackTrace();
                }
            }
        }

        // Recursion pass
        for (File f : dir.listFiles())
        {
            if (f == null)
                continue;

            if (f.isDirectory() && f.canRead() && !f.isHidden())
                search(f, skipFiles);
        }
    }

}
