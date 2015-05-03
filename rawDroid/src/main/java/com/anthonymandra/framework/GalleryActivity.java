package com.anthonymandra.framework;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.dcraw.LibRaw;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.ImageViewActivity;
import com.anthonymandra.rawdroid.LegacyViewerActivity;
import com.anthonymandra.rawdroid.LicenseManager;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.XmpBaseFragment;
import com.anthonymandra.rawdroid.XmpFilterFragment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class GalleryActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor>
{
	@SuppressWarnings("unused")
	private static final String TAG = GalleryActivity.class.getSimpleName();

	public static final String SWAP_BIN_DIR = "swap";
    public static final String RECYCLE_BIN_DIR = "recycle";
	public static final int FILE_NOT_FOUND = -1;

	private static final int REQUEST_CODE_SHARE = 00;
	private static final int REQUEST_CODE_WRITE_PERMISSION = 01;

	protected RecycleBin recycleBin;
	protected File mSwapDir;

	protected ProgressDialog mProgressDialog;

	private static final String[] RAW_EXT = new String[]
	{ ".3fr", ".ari", ".arw", ".bay", ".crw", ".cr2", ".cap", ".dcs", ".dcr", ".dng", ".drf", ".eip", ".erf", ".fff", ".iiq", ".k25", ".kdc", ".mdc", ".mef",
			".mos", ".mrw", ".nef", ".nrw", ".obm", ".orf", ".pef", ".ptx", ".pxn", ".r3d", ".raf", ".raw", ".rwl", ".rw2", ".rwz", ".sr2", ".srf", ".srw",
			".tif", ".tiff", ".x3f" };

	private static final File[] MOUNT_ROOTS =
	{
		new File("/mnt"),
		new File("/Removable"),
		new File("/udisk"),
		new File("/usbStorage"),
		new File("/storage"),
		new File("/dev/bus/usb"),
	};

	/**
	 * Android multi-user environment is fucked up.  Multiple mount points
	 * under /storage/emulated that point to same filesystem
	 * /storage/emulated/legacy, /storage/emulated/[integer user value]
	 * Then add dozens of symlinks, making searching an absolute nightmare
	 * For now assume symlink and skip anything under /storage/emulated
	 */
	private static final File[] SKIP_ROOTS =
	{
		new File("/storage/emulated")
	};

	protected RawFilter rawFilter = new RawFilter();
	private XmpFilter xmpFilter = new XmpFilter();
	private NativeFilter nativeFilter = new NativeFilter();
	private FileAlphaCompare alphaCompare = new FileAlphaCompare();
	private MetaAlphaCompare metaCompare = new MetaAlphaCompare();

    protected ShareActionProvider mShareProvider;
    protected Intent mShareIntent;
    protected Handler licenseHandler;

	// Identifies a particular Loader being used in this component
	public static final int META_LOADER_ID = 0;
	public static final String META_BUNDLE_KEY = "meta_bundle";
	public static final String META_PROJECTION_KEY = "projection";
	public static final String META_SELECTION_KEY = "selection";
	public static final String META_SELECTION_ARGS_KEY = "selection_args";
	public static final String META_SORT_ORDER_KEY = "sort_order";
	public static final String META_DEFAULT_SORT = Meta.Data.NAME + " ASC";

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mShareIntent = new Intent(Intent.ACTION_SEND);
        mShareIntent.setType("image/*");
        mShareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mShareIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

		getLoaderManager().initLoader(META_LOADER_ID, getIntent().getBundleExtra(META_BUNDLE_KEY), this);
	}

	protected void updateMetaLoaderXmp(XmpBaseFragment.XmpValues xmp, boolean andTrueOrFalse, boolean sortAscending, XmpFilterFragment.SortColumns sortColumn)
	{
		StringBuilder selection = new StringBuilder();
		List<String> selectionArgs = new ArrayList<>();
		boolean requiresJoiner= false;
		String joiner = andTrueOrFalse ? " AND " : " OR ";

		if (xmp != null)
		{
			if (xmp.label != null && xmp.label.length > 0)
			{
				requiresJoiner = true;
				selection.append(createMultipleIN(Meta.Data.LABEL, xmp.label.length));
				for (String label : xmp.label)
				{
					selectionArgs.add(label);
				}
			}
			if (xmp.subject != null && xmp.subject.length > 0)
			{
				if (requiresJoiner)
					selection.append(joiner);
				requiresJoiner = true;
				selection.append(createMultipleLike(Meta.Data.SUBJECT, xmp.subject, selectionArgs, joiner));
			}
			if (xmp.rating != null && xmp.rating.length > 0)
			{
				if (requiresJoiner)
					selection.append(joiner);

				selection.append(createMultipleIN(Meta.Data.RATING, xmp.rating.length));
				for (int rating : xmp.rating)
				{
					selectionArgs.add(Double.toString((double)rating));
				}
			}
		}

		String order = sortAscending ? " ASC" : " DESC";
		String sort;
		switch (sortColumn)
		{
			case Date: sort = Meta.Data.TIMESTAMP + order; break;
			case Name: sort = Meta.Data.NAME + order; break;
			default: sort = Meta.Data.NAME + " ASC";
		}

		updateMetaLoader(null, selection.toString(), selectionArgs.toArray(new String[selectionArgs.size()]), sort);
	}

	String createMultipleLike(String column, String[] likes, List<String> selectionArgs, String joiner)
	{
		StringBuilder selection = new StringBuilder();
		for (int i = 0; i < likes.length; i++)
		{
			if (i > 0) selection.append(joiner);
			selection.append(column)
					.append(" LIKE ?");
			selectionArgs.add("%" + likes[i] + "%");
		}

		return selection.toString();
	}

	String createMultipleIN(String column, int arguments)
	{
		StringBuilder selection = new StringBuilder();
		selection.append(column)
				.append(" IN (")
				.append(makePlaceholders(arguments))
				.append(")");
		return selection.toString();
	}

	String makePlaceholders(int len) {
		StringBuilder sb = new StringBuilder(len * 2 - 1);
		sb.append("?");
		for (int i = 1; i < len; i++)
			sb.append(",?");
		return sb.toString();
	}

	/**
	 * Updates any filled parameters.  Retains existing parameters if null.
	 * @param projection
	 * @param selection
	 * @param selectionArgs
	 * @param sortOrder
	 */
	public void updateMetaLoader(@Nullable String[] projection,@Nullable  String selection, @Nullable  String[] selectionArgs,@Nullable  String sortOrder)
	{
		Bundle metaLoader = getCurrentMetaLoaderBundle();
		if (projection != null)
			metaLoader.putStringArray(META_PROJECTION_KEY, projection);
		if (selection != null)
			metaLoader.putString(META_SELECTION_KEY, selection);
		if (selectionArgs != null)
			metaLoader.putStringArray(META_SELECTION_ARGS_KEY, selectionArgs);
		if (sortOrder != null)
			metaLoader.putString(META_SORT_ORDER_KEY, sortOrder);
		getLoaderManager().restartLoader(GalleryActivity.META_LOADER_ID, metaLoader, this);
	}

	/**
	 * Gets a bundle of the existing MetaLoader parameters
	 * @return
	 */
	public Bundle getCurrentMetaLoaderBundle()
	{
		Loader<Cursor> c = getLoaderManager().getLoader(META_LOADER_ID);
		CursorLoader cl = (CursorLoader) c;
		Bundle metaLoader = createMetaLoaderBundle(
				cl.getProjection(),
				cl.getSelection(),
				cl.getSelectionArgs(),
				cl.getSortOrder()
		);
		return metaLoader;
	}

	public static Bundle createMetaLoaderBundle(String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		Bundle metaLoader = new Bundle();
		metaLoader.putString(META_SELECTION_KEY, selection);
		metaLoader.putStringArray(META_SELECTION_ARGS_KEY, selectionArgs);
		metaLoader.putString(META_SORT_ORDER_KEY, sortOrder);
		metaLoader.putStringArray(META_PROJECTION_KEY, projection);
		return metaLoader;
	}

	/*
	* Callback that's invoked when the system has initialized the Loader and
	* is ready to start the query. This usually happens when initLoader() is
	* called. The loaderID argument contains the ID value passed to the
	* initLoader() call.
	*/
	@Override
	public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle)
	{
		/*
		 * Takes action based on the ID of the Loader that's being created
		 */
		switch (loaderID) {
			case META_LOADER_ID:

				String[] projection = null;
				String selection = null;
				String[] selectionArgs = null;
				String sort = META_DEFAULT_SORT;

				// Populate the database with filter (selection) from the previous app
				if (bundle != null)
				{
					projection = bundle.getStringArray(META_PROJECTION_KEY);
					selection = bundle.getString(META_SELECTION_KEY);
					selectionArgs = bundle.getStringArray(META_SELECTION_ARGS_KEY);
					sort = bundle.getString(META_SORT_ORDER_KEY);
				}

				// Returns a new CursorLoader
				return new CursorLoader(
						this,   				// Parent activity context
						Meta.Data.CONTENT_URI,  // Table to query
						projection,				// Projection to return
						selection,       		// No selection clause
						selectionArgs, 			// No selection arguments
						sort         			// Default sort order
				);
			default:
				// An invalid id was passed in
				return null;
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
        LicenseManager.getLicense(getBaseContext(), licenseHandler);
		createSwapDir();
		createRecycleBin();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (recycleBin != null)
			recycleBin.flushCache();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		clearSwapDir();
		if (recycleBin != null)
			recycleBin.closeCache();
		recycleBin = null;
	}

	/**
	 * Create swap directory or clear the contents
	 */
	protected void createSwapDir()
	{
		mSwapDir = Util.getDiskCacheDir(this, SWAP_BIN_DIR);
		if (!mSwapDir.exists())
		{
			mSwapDir.mkdirs();
		}
	}

	protected void clearSwapDir()
	{
		if (mSwapDir == null)
			return;

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				final File[] swapFiles = mSwapDir.listFiles();
                if (swapFiles != null)
                {
                    for (File toDelete : swapFiles)
                    {
                        toDelete.delete();
                    }
                }
			}
		}).start();
	}

	protected void createRecycleBin()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		Boolean useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true);
        int binSizeMb;
        try
        {
		    binSizeMb = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(FullSettingsActivity.KEY_RecycleBinSize,
				Integer.toString(FullSettingsActivity.defRecycleBin)));
        }
        catch (NumberFormatException e)
        {
            binSizeMb = 0;
        }
		if (useRecycle)
		{
			recycleBin = new RecycleBin(this, RECYCLE_BIN_DIR, binSizeMb * 1024 * 1024);
		}
	}

	protected void showRecycleBin()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Boolean useRecycle = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true);
		if (!useRecycle || recycleBin == null)
		{
			return;
		}
		final List<String> keys = recycleBin.getKeys();
		final List<String> filesToRestore = new ArrayList<>(keys.size());
		new AlertDialog.Builder(this).setTitle(R.string.recycleBin).setNegativeButton(R.string.emptyRecycleBin, new Dialog.OnClickListener()
		{

			public void onClick(DialogInterface dialog, int which)
			{
				recycleBin.clearCache();
			}

		}).setNeutralButton(R.string.neutral, new Dialog.OnClickListener()
		{

			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				// cancel, do nothing
			}
		}).setPositiveButton(R.string.restoreFile, new Dialog.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				for (String filename : filesToRestore)
				{
					File toRestore = new File(filename);
					BufferedInputStream restore = new BufferedInputStream(recycleBin.getFile(filename));
					Util.write(toRestore, restore);
//					addFile(toRestore, true);
					addDatabaseReference(new LocalImage(GalleryActivity.this, toRestore));
				}
				// Ideally just update the sub lists on each restore.
				updateAfterRestore();
			}
		}).setMultiChoiceItems(keys.toArray(new String[keys.size()]), null, new DialogInterface.OnMultiChoiceClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked)
			{
				if (isChecked)
					filesToRestore.add(keys.get(which));
				else
					filesToRestore.remove(keys.get(which));
			}
		}).show();
	}

	/**
	 * Gets the swap directory and clears existing files in the process
	 * 
	 * @param filename
	 *            name of the file to add to swap
	 * @return file link to new swap file
	 */
	protected File getSwapFile(String filename)
	{
		return new File(mSwapDir, filename);
	}

	protected Intent getViewerIntent()
	{
		Intent viewer = new Intent();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (!settings.contains(FullSettingsActivity.KEY_UseLegacyViewer))
		{
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(FullSettingsActivity.KEY_UseLegacyViewer, !Util.hasHoneycomb());
			editor.apply();
		}

		if(settings.getBoolean(FullSettingsActivity.KEY_UseLegacyViewer, false))
		{
			viewer.setClass(this, LegacyViewerActivity.class);
		}
		else
		{
			viewer.setClass(this, ImageViewActivity.class);
		}
		return viewer;
	}

	protected boolean removeDatabaseReference(RawObject toRemove)
	{
		int rowsDeleted = getContentResolver().delete(
				Meta.Data.CONTENT_URI,
				Meta.Data.URI + " = ?",
				new String[] {toRemove.getUri().toString()});
		return rowsDeleted > 0;
	}

	protected boolean addDatabaseReference(RawObject toAdd)
	{
		Uri created = getContentResolver().insert(Meta.Data.CONTENT_URI, ImageUtils.getContentValues(this, toAdd.getUri()));
		return created != null;
	}


	/**
	 * Placeholder class TODO: Remove all references
	 */
	private List<MediaItem> getImageListFromUriList(List<Uri> uris)
	{
		List<MediaItem> images = new ArrayList<>();
		for (Uri u: uris)
			images.add(getImageFromUri(u));
		return images;
	}

	/**
	 * Placeholder class TODO: Remove all references
	 */
	private MediaItem getImageFromUri(Uri uri)
	{
		File f = new File(uri.getPath());
		return  new LocalImage(this, f);
	}

	/**
	 * Deletes a file and determines if a recycle is necessary.
	 * 
	 * @param toDelete
	 *            file to delete.
	 * @return true currently (possibly return success later on)
	 */
	protected void deleteImage(final Uri toDelete)
	{
		List<Uri> itemsToDelete = new ArrayList<>();
		itemsToDelete.add(toDelete);
		deleteImages(itemsToDelete);
	}

	@SuppressWarnings("unchecked")
	protected void deleteImages(final List<Uri> itemsToDelete)
	{
		if (itemsToDelete.size() == 0)
		{
			Toast.makeText(getBaseContext(), R.string.warningNoItemsSelected, Toast.LENGTH_SHORT).show();
			return;
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Boolean deleteConfirm = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true);
		Boolean useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true);
		Boolean justDelete;
		String message;
		long spaceRequired = 0;
		for (Uri toDelete : itemsToDelete)
		{
			File f = new File(toDelete.getPath());
			if (f.exists())
				spaceRequired += f.length();
		}

		// Go straight to delete if
		// 1. MTP (unsupported)
		// 2. Recycle is set to off
		// 3. For some reason the bin is null
//		if (itemsToDelete.get(0) instanceof MtpImage)
//		{
//			justDelete = true;
//			message = getString(R.string.warningRecycleMtp);
//		}
		/* else */
        if (!useRecycle)
		{
			justDelete = true;
			message = getString(R.string.warningDeleteDirect);
		}
		else if (recycleBin == null)
		{
			justDelete = true;
			message = getString(R.string.warningNoRecycleBin);
		}
		else
		{
			justDelete = false;
			message = getString(R.string.warningDeleteExceedsRecycle); // This message applies to deletes exceeding bin size
		}

		if (justDelete || recycleBin.getBinSize() < spaceRequired)
		{
			if (deleteConfirm)
			{
				new AlertDialog.Builder(this).setTitle(R.string.prefTitleDeleteConfirmation).setMessage(message)
						.setNeutralButton(R.string.neutral, new Dialog.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								// cancel, do nothing
							}
						}).setPositiveButton(R.string.delete, new Dialog.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								new DeleteTask().execute(itemsToDelete);
							}
						}).show();
			}
			else
			{
				new DeleteTask().execute(itemsToDelete);
			}
		}
		else
		{
			new RecycleTask().execute(itemsToDelete);
		}
	}

    @TargetApi(21)
    protected void requestEmailIntent()
    {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto","rawdroid@anthonymandra.com", null));

        StringBuilder body = new StringBuilder();
        body.append("Variant:   ").append(BuildConfig.FLAVOR).append("\n");
        body.append("Version:   ").append(BuildConfig.VERSION_NAME).append("\n");
        body.append("Make:      ").append(Build.MANUFACTURER).append("\n");
        body.append("Model:     ").append(Build.MODEL).append("\n");
        if (Util.hasLollipop())
            body.append("ABI:       ").append(Build.SUPPORTED_ABIS).append("\n");
        else
            body.append("ABI:       ").append(Build.CPU_ABI).append("\n");
        body.append("Android:   ").append(Build.DISPLAY).append("\n");
        body.append("SDK:       ").append(Build.VERSION.SDK_INT).append("\n\n");
        body.append("---Please don't remove this data---").append("\n\n");

        emailIntent.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(emailIntent, "Send email..."));
    }

	/**
	 * Updates the UI after a delete.
	 */
	protected abstract void updateAfterDelete();

	/**
	 * Updates the UI after a restore.
	 */
	protected abstract void updateAfterRestore();

	protected class RecycleTask extends AsyncTask<List<Uri>, Integer, Boolean> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog.setTitle(R.string.recyclingFiles);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(final List<Uri>... params)
		{
			List<MediaItem> itemsToDelete = getImageListFromUriList(params[0]);
			mProgressDialog.setMax(itemsToDelete.size());
			final List<RawObject> removed = new ArrayList<>();

			for (RawObject image : itemsToDelete)
			{
				recycleBin.addFile(image);
				removeDatabaseReference(image);
			}

			return removed.size() == itemsToDelete.size();
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			mProgressDialog.dismiss();
			updateAfterDelete();
		}

		@Override
		protected void onProgressUpdate(Integer... values)
		{
			mProgressDialog.setProgress(values[0]);
			// setSupportProgress(values[0]);
		}

		@Override
		protected void onCancelled()
		{
			updateAfterDelete();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	protected class DeleteTask extends AsyncTask<List<Uri>, Integer, Boolean> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(GalleryActivity.this);
			mProgressDialog.setTitle(R.string.deletingFiles);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(final List<Uri>... params)
		{
			List<MediaItem> itemsToDelete = getImageListFromUriList(params[0]);
			mProgressDialog.setMax(itemsToDelete.size());
			final List<RawObject> removed = new ArrayList<>();

			for (RawObject image : itemsToDelete)
			{
				if (image.delete())
				{
					removeDatabaseReference(image);
					removed.add(image);
				}
			}
			return removed.size() == itemsToDelete.size();
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			mProgressDialog.dismiss();
			updateAfterDelete();
		}

		@Override
		protected void onProgressUpdate(Integer... values)
		{
			mProgressDialog.setProgress(values[0]);
			// setSupportProgress(values[0]);
		}

		@Override
		protected void onCancelled()
		{
			updateAfterDelete();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	protected void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode)
		{
			case REQUEST_CODE_WRITE_PERMISSION:
				if (resultCode == RESULT_OK && data != null)
				{
					Uri treeUri = data.getData();
					getContentResolver().takePersistableUriPermission(treeUri,
							Intent.FLAG_GRANT_READ_URI_PERMISSION |
							Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
				}
				break;
		}
	}

	protected void requestWritePermission()
	{
		getContentResolver().getPersistedUriPermissions();
		if (Util.hasLollipop())
        {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_WRITE_PERMISSION);
        }
		else if (Util.hasKitkat())
		{
			//TODO: Warn User
		}
		// Prior versions will have permission
	}

    protected void setShareUri(Uri share)
    {
        mShareIntent.setAction(Intent.ACTION_SEND);
        mShareIntent.putExtra(Intent.EXTRA_STREAM, share);
        if (mShareProvider != null)
            mShareProvider.setShareIntent(mShareIntent);
    }

    protected void setShareUri(ArrayList<Uri> shares)
    {
        mShareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        mShareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, shares);
        if (mShareProvider != null)
            mShareProvider.setShareIntent(mShareIntent);
    }

	static ArrayList<ContentProviderOperation> mContentProviderOperations;
	protected void scanRawFiles(File[] roots)
	{
		searchActive = true;
		mContentProviderOperations = new ArrayList<>();
		SearchTask st = new SearchTask();
		st.execute(roots);
	}

	protected void scanRawFiles()
	{
		scanRawFiles(MOUNT_ROOTS);
	}

	//TODO: Could be used as a fallback deep search
	protected void scanRawFiles(File searchRoot)
	{
		File[] root = {searchRoot};
		scanRawFiles(root);
	}

	protected void onSearchResults()
	{
		getSupportActionBar().setSubtitle("Found " +  totalImages + " raw images...");
	}

	static int parsedImages;
	static int totalImages;
	protected void onImageParsed()
	{
		getSupportActionBar().setSubtitle("Processed " + parsedImages + " of " + totalImages);
	}

	public static boolean searchActive;
	public class ParseMetaTask extends android.os.AsyncTask<File, Void, Void>
	{
		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					getSupportActionBar().setTitle("Processing...");
				}
			});
		}

		@Override
		protected Void doInBackground(File... params)
		{
			mContentProviderOperations.add(ContentProviderOperation.newInsert(Meta.Data.CONTENT_URI)
					.withValues(ImageUtils.getContentValues(GalleryActivity.this, Uri.fromFile(params[0])))
					.build());
			++parsedImages;
			publishProgress();
			return null;
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
			super.onProgressUpdate();
			onImageParsed();
		}

		@Override
		protected void onPostExecute(Void aVoid)
		{
			super.onPostExecute(aVoid);
			if (parsedImages >= totalImages)
			{
				ApplyBatchTask abt = new ApplyBatchTask();
				abt.execute();
				searchActive = false;
			}
		}
	}

	public class ApplyBatchTask extends android.os.AsyncTask<Void, Void, Void>
	{
		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				// TODO: If I implement bulkInsert it's faster
				getContentResolver().applyBatch(Meta.AUTHORITY, mContentProviderOperations);
			} catch (RemoteException e)
			{
				//TODO: Notify user
				e.printStackTrace();
			} catch (OperationApplicationException e)
			{
				//TODO: Notify user
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid)
		{
			getSupportActionBar().setSubtitle("");
			getLoaderManager().restartLoader(META_LOADER_ID, null, GalleryActivity.this);
		}
	}

	public class SearchTask extends android.os.AsyncTask<File, Void, Void> implements OnCancelListener
	{
		boolean cancelled;

		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			getSupportActionBar().setTitle("Searching...");
			totalImages = 0;
			parsedImages = 0;
		}

		@Override
		protected Void doInBackground(File... params)
		{
			Set<String> rawImages = new HashSet<>();
			ArrayList<ContentProviderOperation> ops = new ArrayList<>();
			for (File root : params)
			{
				rawImages.addAll(search(root));
				totalImages = rawImages.size();
				publishProgress();
			}

			for (String raw : rawImages)
			{
				ParseMetaTask pmt = new ParseMetaTask();
//				pmt.execute(new File(raw));
				pmt.executeOnExecutor(LibRaw.EXECUTOR, new File(raw));
//				pmt.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new File(raw));
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
			GalleryActivity.this.onSearchResults();
		}

		public Collection<String> search(File dir)
		{
			Set<String> matches = new HashSet<>();
			if (cancelled || dir == null)
				return matches;
			if (dir.listFiles() == null)
				return matches;
			// This is a hack to handle the jacked up filesystem
			for (File skip : SKIP_ROOTS)
			{
				if (dir.equals(skip))
					return matches;
			}

			// We must use a canonical path due to the fucked up multi-user/symlink setup
			File[] rawFiles = dir.listFiles(rawFilter);
			if (rawFiles != null && rawFiles.length > 0)
			{
				for (File raw: rawFiles)
				{
					try
					{
						matches.add(raw.getCanonicalPath());
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
					matches.addAll(search(f));
			}
			return matches;
		}

		@Override
		protected void onCancelled()
		{
			cancelled = true;
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	class XmpFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			String filename = file.getName();
			return filename.toLowerCase(Locale.US).endsWith(".xmp");
		}
	}
	
	class RawFilter implements FileFilter
	{
		@SuppressLint("DefaultLocale")
		@Override
		public boolean accept(File file)
		{
			for (String ext : RAW_EXT)
			{
				if (file.getName().toLowerCase().endsWith(ext.toLowerCase()))
					return true;
			}
			return false;
		}
	}

	class NativeFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return Util.isNative(file);
		}
	}

	class FileAlphaCompare implements Comparator<File>
	{
		// Comparator interface requires defining compare method.
		@Override
		public int compare(File filea, File fileb)
		{
			return filea.getName().compareToIgnoreCase(fileb.getName());
		}
	}

	class MetaAlphaCompare implements Comparator<RawObject>
	{
		// Comparator interface requires defining compare method.
		@Override
		public int compare(RawObject filea, RawObject fileb)
		{
			return filea.getName().compareToIgnoreCase(fileb.getName());
		}
	}

    public static class LicenseHandler extends Handler{
        private final WeakReference<Context> mContext;
        public LicenseHandler(Context context)
        {
            mContext = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            License.LicenseState state = (License.LicenseState) msg.getData().getSerializable(License.KEY_LICENSE_RESPONSE);
            if (state.toString().startsWith("modified"))
            {
                for (int i=0; i < 3; i++)
                    Toast.makeText(mContext.get(), "An app on your device has attempted to modify Rawdroid.  Check Settings > License for more information.", Toast.LENGTH_LONG).show();
            }
            else if (state == License.LicenseState.error)
            {
                Toast.makeText(mContext.get(), "There was an error communicating with Google Play.  Check Settings > License for more information.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
