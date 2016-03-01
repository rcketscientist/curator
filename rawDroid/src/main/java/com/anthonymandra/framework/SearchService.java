package com.anthonymandra.framework;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;
import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SearchService extends IntentService
{
	private static final String TAG = SearchService.class.getSimpleName();
    /**
     * Broadcast ID when parsing is complete
     */
    public static final String BROADCAST_FOUND = "com.anthonymandra.framework.action.BROADCAST_FOUND";

    /**
     * Broadcast extra containing uris for the discovered images
     */
    public static final String EXTRA_IMAGE_URIS = "com.anthonymandra.framework.action.EXTRA_IMAGE_URIS";

    private static final String ACTION_SEARCH = "com.anthonymandra.framework.action.ACTION_SEARCH";
    private static final String EXTRA_FILEPATH_ROOTS = "com.anthonymandra.framework.extra.EXTRA_FILEPATH_ROOTS";
	private static final String EXTRA_DOCUMENT_TREE_URI_ROOTS = "com.anthonymandra.framework.extra.EXTRA_DOCUMENT_TREE_URI_ROOTS";
    private static final String EXTRA_SKIP = "com.anthonymandra.framework.extra.EXTRA_SKIP";

    public static void startActionSearch(Context context, String[] filePathRoots, String[] documentTreeUris, String[] skip)
    {
        Intent intent = new Intent(context, SearchService.class);
        intent.setAction(ACTION_SEARCH);
        intent.putExtra(EXTRA_FILEPATH_ROOTS, filePathRoots);
	    intent.putExtra(EXTRA_DOCUMENT_TREE_URI_ROOTS, documentTreeUris);
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
                final String[] root = intent.getStringArrayExtra(EXTRA_FILEPATH_ROOTS);
	            final String[] uris = intent.getStringArrayExtra(EXTRA_DOCUMENT_TREE_URI_ROOTS);
                final String[] skip = intent.getStringArrayExtra(EXTRA_SKIP);
                handleActionSearch(root, uris, skip);//, ext);
            }
        }
    }

    private void handleActionSearch(@Nullable String[] filePathRoots, @Nullable String[] uriRoots, @Nullable String[] alwaysExcludeDir)
    {
	    // If filePathRoots is null we won't even search ExternalStorageDirectory.
	    // This allows a strictly SAF search
//	    if (filePathRoots != null)
//	    {
//		    // Ensure 'official' storage is part of our search list
//		    mExternalStorageDir = Environment.getExternalStorageDirectory();
//		    Set<String> rootDirs = new HashSet<>(Arrays.asList(filePathRoots));
//		    rootDirs.add(mExternalStorageDir.getPath());
//
//		    for (String root : rootDirs)
//			    search(new File(root), alwaysExcludeDir);
//	    }

	    ForkJoinPool pool = new ForkJoinPool(4);
	    Set<Uri> foundImages = new HashSet<>();
	    if (uriRoots != null)
	    {
		    for (String uri : uriRoots)
		    {
			    SearchTask task = new SearchTask(this, UsefulDocumentFile.fromUri(this, Uri.parse(uri)), alwaysExcludeDir);
			    foundImages.addAll(pool.invoke(task));
		    }
	    }

	    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
	    ArrayList<String> uriStrings = new ArrayList<>();
        if (foundImages.size() > 0)
        {
            for (Uri image : foundImages)
            {
	            operations.add(ImageUtils.newInsert(this, image));
	            uriStrings.add(image.toString());
            }

            try
            {
                // TODO: If I implement bulkInsert it's faster
                getContentResolver().applyBatch(Meta.AUTHORITY, operations);
            } catch (RemoteException | OperationApplicationException e)
            {
	            Crashlytics.logException(
			            new Exception("SearchService: handleActionSearch applyBatch error", e));
            }
        }

	    Intent broadcast = new Intent(BROADCAST_FOUND)
			    .putExtra(EXTRA_IMAGE_URIS, uriStrings.toArray(new String[operations.size()]));
	    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

	class SearchTask extends RecursiveTask<Set<Uri>>
	{
		Set<Uri> foundImages = new HashSet<>();
		final UsefulDocumentFile mRoot;
		final String[] mExcludeDir;
		final Context mContext;
		SearchTask(Context c, UsefulDocumentFile root, String[] excludeDir)
		{
			mRoot = root;
			mExcludeDir = excludeDir;
			mContext = c;
		}

		@Override
		protected Set<Uri> compute()
		{
			if (mRoot == null)
				return null;
			String name = mRoot.getName();
			if (name == null || name.startsWith("."))
				return null;

			UsefulDocumentFile[] contents = mRoot.listFiles();
			if (contents == null)
				return null;

			if (mExcludeDir != null)
			{
				for (String skip : mExcludeDir)
				{
					if (skip == null)
						continue;
					if (mRoot.getUri().toString().startsWith(skip)) //TODO: This likely needs tuning...
						return null;
				}
			}

			// Recursion pass
			List<SearchTask> forks = new LinkedList<>();
			for (UsefulDocumentFile f : contents)
			{
				if (f == null)
					continue;

				if (f.isDirectory() && f.canRead())
				{
					SearchTask child = new SearchTask(mContext, f, mExcludeDir);
					forks.add(child);
					child.fork();

				}
			}

			for (SearchTask task : forks)
			{
				Set<Uri> childImages = task.join();
				if (childImages != null)
					foundImages.addAll(childImages);
			}

			UsefulDocumentFile[] imageFiles = listImages(contents);

			if (imageFiles.length > 0)
			{
				for (UsefulDocumentFile image: imageFiles)
				{
					if (ImageUtils.isProcessed(mContext, image.getUri()))   //TODO: Might just want to check if it exists in db
					{
						continue;
					}

					foundImages.add(image.getUri());
				}
			}

			return foundImages;
		}
	}

	/**
	 * emulate File.listFiles(ImageFilter)
	 */
	public static UsefulDocumentFile[] listImages(UsefulDocumentFile[] files)
	{
		List<UsefulDocumentFile> result = new ArrayList<>(files.length);
		for (UsefulDocumentFile file : files) {
			String name = file.getName();
			if (name == null)
				continue;
			if(ImageUtils.isImage(file.getName()))
				result.add(file);
		}
		return result.toArray(new UsefulDocumentFile[result.size()]);
	}
}
