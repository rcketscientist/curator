package com.anthonymandra.framework;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.anthonymandra.content.Meta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchService extends IntentService
{
	private static final String TAG = SearchService.class.getSimpleName();
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

	/**
	 * Since 'external storage' exists in triplicate on 4.2+,
	 * what we do is skip these roots UNLESS they are contained in
	 * Environment.getExternalStorageDirectory().  For that case we
	 * scan ONLY that directly.  This ensures unambiguous paths and
	 * adherence to the "official" mount point...ugh
	 */
	private static final String[] SKIP_ROOTS =
			{
					"/storage/emulated",
					"/mnt/sdcard"
			};

    private static final Set<String> images = new HashSet<>();
	private File mExternalStorageDir;

    public static void startActionSearch(Context context, String[] roots, String[] skip)
    {
        Intent intent = new Intent(context, SearchService.class);
        intent.setAction(ACTION_SEARCH);
        intent.putExtra(EXTRA_ROOTS, roots);
        intent.putExtra(EXTRA_SKIP, skip);
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
                handleActionSearch(root, skip);//, ext);
            }
        }
    }

    private void handleActionSearch(String[] roots, String[] alwaysExcludeDir)
    {
	    // Ensure 'official' storage is part of our search list
	    mExternalStorageDir = Environment.getExternalStorageDirectory();
	    Set<String> rootDirs = new HashSet<>(Arrays.asList(roots));
	    rootDirs.add(mExternalStorageDir.getPath());

        images.clear();

        for (String root : rootDirs)
            search(new File(root), alwaysExcludeDir);

	    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        if (images.size() > 0)
        {
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
        }

	    Intent broadcast = new Intent(BROADCAST_FOUND)
			    .putExtra(EXTRA_IMAGES, images.toArray(new String[operations.size()]));
	    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

	/**
	 *
	 * @param dir Directory to recursively search
	 * @param alwaysExcludeDir These roots are skipped absolutely even if they are the official storage
	 */
    public void search(File dir, String[] alwaysExcludeDir)
    {
        if (dir == null)
            return;
        if (dir.listFiles() == null)
            return;

	    boolean isExternalStorage;
	    isExternalStorage = dir.getPath().startsWith(mExternalStorageDir.getPath());

	    // We must always deal with canonical to avoid the redundant mount points
	    String canonPath = FileUtil.getCanonicalPathSilently(dir);

	    /**
	     * We obey these skip locations unless they are the official storage
	     * This should avoid all the emulated mount points in 4.2+
	     */
	    if (!isExternalStorage)
	    {
		    for (String skip : SKIP_ROOTS)
		    {
			    if (canonPath.startsWith(skip))
				    return;
		    }
	    }
	    else
	    {
		    Log.d(TAG, "ExternalStorage: dir= " + dir.getPath() + ", canon= " + canonPath);
	    }

	    /**
	     * We always obey these exclusions even if they are the official storage
	     * The user can choose to ignore the internal storage in favor of external sd for example
	     */
        for (String skip : alwaysExcludeDir)
        {
            if (canonPath.startsWith(skip))
	            return;
        }

	    Log.d(TAG, "Processed: dir= " + dir.getPath() + ", canon= " + canonPath);

	    Util.ImageFilter imageFilter = new Util.ImageFilter();
        File[] imageFiles = dir.listFiles(imageFilter);
        if (imageFiles != null && imageFiles.length > 0)
        {
            for (File raw: imageFiles)
            {
                if (ImageUtils.isProcessed(this, Uri.fromFile(raw)))
                {
                    continue;
                }

	            String path = raw.getPath();

	            /**
	             * If it's not the external storage we use the canonical path
	             * The reason for this is that the official storage doesn't have canonical paths,
	             * so we process getPath only for Environment.getExternalStorageDirectory()
	             * while skipping alternate mount points
	             *
	             * However, the external sd card has canonical paths, but arbitrary
	             * manufacturer mount points, so for everything else we use canonical
	             *
	             * All this ensures we get one and only one copy
	             */
	            if (!isExternalStorage)
		            path = FileUtil.getCanonicalPathSilently(raw);

                images.add(path);
            }
        }

        // Recursion pass
        for (File f : dir.listFiles())
        {
            if (f == null)
                continue;

            if (f.isDirectory() && f.canRead() && !f.isHidden())
                search(f, alwaysExcludeDir);
        }
    }

}