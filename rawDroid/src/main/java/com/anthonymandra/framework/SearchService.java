package com.anthonymandra.framework;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchService extends IntentService
{
	@SuppressWarnings("unused")
	private static final String TAG = SearchService.class.getSimpleName();
	/**
	 * Broadcast ID when parsing is complete
	 */
	public static final String BROADCAST_SEARCH_STARTED = "com.anthonymandra.framework.action.BROADCAST_SEARCH_STARTED";
    /**
     * Broadcast ID when parsing is complete
     */
    public static final String BROADCAST_SEARCH_COMPLETE = "com.anthonymandra.framework.action.BROADCAST_SEARCH_COMPLETE";
	/**
	 * Broadcast extra containing uris for the discovered images
	 */
	public static final String EXTRA_IMAGE_URIS = "com.anthonymandra.framework.action.EXTRA_IMAGE_URIS";

	/**
	 * Broadcast ID when images are found, sent after every folder with hits
	 */
	public static final String BROADCAST_FOUND_IMAGES = "com.anthonymandra.framework.action.BROADCAST_FOUND_IMAGES";
	/**
	 * Broadcast extra containing running total of images found in the current search
	 */
	public static final String EXTRA_NUM_IMAGES = "com.anthonymandra.framework.action.EXTRA_NUM_IMAGES";
	private static AtomicInteger mImageCount = new AtomicInteger(0);

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
	            final String[] uris = intent.getStringArrayExtra(EXTRA_DOCUMENT_TREE_URI_ROOTS);
                final String[] skip = intent.getStringArrayExtra(EXTRA_SKIP);
                handleActionSearch(uris, skip);
            }
        }
    }

    private void handleActionSearch(@Nullable String[] uriRoots, @Nullable String[] alwaysExcludeDir)
    {
	    mImageCount.set(0);
	    Intent broadcast = new Intent(BROADCAST_SEARCH_STARTED);
	    LocalBroadcastManager.getInstance(SearchService.this).sendBroadcast(broadcast);

	    ForkJoinPool pool = new ForkJoinPool();     // This should be a common pool for the app.  Currently only use
	    Set<ContentValues> foundImages = new HashSet<>();
	    if (uriRoots != null)
	    {
		    for (String uri : uriRoots)
		    {
			    SearchTask task = new SearchTask(this, UsefulDocumentFile.fromUri(this, Uri.parse(uri)), alwaysExcludeDir);
				Set<ContentValues> searchResults = pool.invoke(task);
				if (searchResults != null)
			    	foundImages.addAll(searchResults);
		    }
	    }

	    Set<String> uriStrings = new HashSet<>();
        if (foundImages.size() > 0)
        {
	        getContentResolver().bulkInsert(Meta.CONTENT_URI, foundImages.toArray(new ContentValues[foundImages.size()]));
        }

	    for (ContentValues image : foundImages)
	    {
		    uriStrings.add(image.getAsString(Meta.URI));
	    }

	    broadcast = new Intent(BROADCAST_SEARCH_COMPLETE)
			    .putExtra(EXTRA_IMAGE_URIS, uriStrings.toArray(new String[uriStrings.size()]));
	    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

	class SearchTask extends RecursiveTask<Set<ContentValues>>
	{
		Set<ContentValues> foundImages = new HashSet<>();
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
		protected Set<ContentValues> compute()
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
					if (mRoot.getUri().toString().startsWith(skip))
						return null;
				}
			}

			// Recursion pass
			List<SearchTask> forks = new LinkedList<>();
			for (UsefulDocumentFile f : contents)
			{
				if (f == null)
					continue;

				if (f.getCachedData() != null && f.getCachedData().isDirectory && f.getCachedData().canRead)
				{
					SearchTask child = new SearchTask(mContext, f, mExcludeDir);
					forks.add(child);
					child.fork();

				}
			}

			UsefulDocumentFile[] imageFiles = listImages(contents);

			if (imageFiles.length > 0)
			{
				int imageCount = mImageCount.addAndGet(imageFiles.length);
				Intent broadcast = new Intent(BROADCAST_FOUND_IMAGES)
						.putExtra(EXTRA_NUM_IMAGES, imageCount);
				LocalBroadcastManager.getInstance(SearchService.this).sendBroadcast(broadcast);

				for (UsefulDocumentFile image: imageFiles)
				{
					Uri uri = image.getUri();
					if (uri == null)    // Somehow we can get null uris in here...
						continue;       // https://bitbucket.org/rcketscientist/rawdroid/issues/230/coreactivityjava-line-677-crashlytics

					foundImages.add(getImageFileInfo(image));
				}
			}

			for (SearchTask task : forks)
			{
				Set<ContentValues> childImages = task.join();
				if (childImages != null)
					foundImages.addAll(childImages);
			}

			return foundImages;
		}
	}

	public ContentValues getImageFileInfo(@NonNull UsefulDocumentFile file)
	{
		UsefulDocumentFile.FileData fd = file.getCachedData();
		ContentValues cv = new ContentValues();

		if (fd != null)
		{
			cv.put(Meta.NAME, fd.name);
			cv.put(Meta.PARENT, fd.parent.toString());
			cv.put(Meta.TIMESTAMP, fd.lastModified);
		}
		else
		{
			UsefulDocumentFile parent = file.getParentFile();
			if (parent != null)
				cv.put(Meta.PARENT, parent.toString());
		}

		cv.put(Meta.DOCUMENT_ID, file.getDocumentId());
		cv.put(Meta.URI, file.getUri().toString());
		cv.put(Meta.TYPE, ImageUtil.getImageType(this, file.getUri()).getValue());
		return cv;
	}

	/**
	 * emulate File.listFiles(ImageFilter)
	 */
	public static UsefulDocumentFile[] listImages(UsefulDocumentFile[] files)
	{
		List<UsefulDocumentFile> result = new ArrayList<>(files.length);
		for (UsefulDocumentFile file : files) {
			if (file.getCachedData() == null)
				continue;
			String name = file.getCachedData().name;
			if (name == null)
				continue;
			if(ImageUtil.isImage(name))
				result.add(file);
		}
		return result.toArray(new UsefulDocumentFile[result.size()]);
	}
}
