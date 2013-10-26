package com.anthonymandra.framework;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.R;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Activity that handles a recycling bin and UI updates.
 * 
 * @author amand_000
 * 
 */
public abstract class GalleryActivity extends SherlockFragmentActivity
{
	@SuppressWarnings("unused")
	private static final String TAG = GalleryActivity.class.getSimpleName();

	public static final String SWAP_BIN_DIR = "swap";
    public static final String RECYCLE_BIN_DIR = "recycle";
	public static final int FILE_NOT_FOUND = -1;

	private static final int REQUEST_CODE_SHARE = 0;

	protected RecycleBin recycleBin;
	protected File mSwapDir;

	protected ProgressDialog mProgressDialog;

	private static final String[] IMAGE_EXT = new String[]
	{ ".3fr", ".ari", ".arw", ".bay", ".crw", ".cr2", ".cap", ".dcs", ".dcr", ".dng", ".drf", ".eip", ".erf", ".fff", ".iiq", ".k25", ".kdc", ".mdc", ".mef",
			".mos", ".mrw", ".nef", ".nrw", ".obm", ".orf", ".pef", ".ptx", ".pxn", ".r3d", ".raf", ".raw", ".rwl", ".rw2", ".rwz", ".sr2", ".srf", ".srw",
			".x3f" };

	private XmpFilter xmpFilter = new XmpFilter();
	private NativeFilter nativeFilter = new NativeFilter();
	private FileAlphaCompare alphaCompare = new FileAlphaCompare();
	private MetaAlphaCompare metaCompare = new MetaAlphaCompare();

	protected List<File> mFolders = new ArrayList<File>();
	protected List<MediaItem> mRawImages = new ArrayList<MediaItem>();
	protected List<MediaItem> mNativeImages = new ArrayList<MediaItem>();
	protected List<MediaItem> mXmpFiles = new ArrayList<MediaItem>();
	protected List<MediaItem> mUnknownFiles = new ArrayList<MediaItem>();
	protected List<MediaItem> mVisibleItems = new ArrayList<MediaItem>();

	protected File mCurrentPath;

    protected LicenseManager mLicenseManager;

    protected ShareActionProvider mShareProvider;
    protected Intent mShareIntent;

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        mLicenseManager = new LicenseManager(this, new Handler());
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		// mProgressDialog.setCanceledOnTouchOutside(true);
        mShareIntent = new Intent(Intent.ACTION_SEND);
        mShareIntent.setType("image/jpeg");
	}

	@Override
	protected void onResume()
	{
		super.onResume();
        mLicenseManager.forceLicenseCheck();
        mLicenseManager.checkLicense();
        mLicenseManager.registerObserver();
		createSwapDir();
		createRecycleBin();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
        mLicenseManager.unregisterObserver();
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

	protected void updateGalleryItems()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean showXmp = prefs.getBoolean(FullSettingsActivity.KEY_ShowXmpFiles, false);
		boolean showUnknown = prefs.getBoolean(FullSettingsActivity.KEY_ShowUnknownFiles, false);
		boolean showNative = prefs.getBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);

		mVisibleItems.clear();
		mVisibleItems.addAll(mRawImages);
		if (showNative)
			mVisibleItems.addAll(mNativeImages);
		if (showXmp)
			mVisibleItems.addAll(mXmpFiles);
		if (showUnknown)
			mVisibleItems.addAll(mUnknownFiles);

		if (mVisibleItems.size() == 0 && mVisibleItems.size() < mNativeImages.size())
		{
			Toast.makeText(this, R.string.warningGenericImagesOff, Toast.LENGTH_LONG).show();
		}
	}

	protected int findMediaByFilename(String filename)
	{
		for (RawObject raw : mRawImages)
		{
			if (raw.getFilePath().equals(filename))
			{
				return mRawImages.indexOf(raw);
			}
		}

		for (RawObject generic : mNativeImages)
		{
			if (generic.getFilePath().equals(filename))
			{
				return mRawImages.size() + mNativeImages.indexOf(generic);
			}
		}

		return FILE_NOT_FOUND;
	}

    protected int findVisibleByFilename(String filename)
    {
        for (RawObject media: mVisibleItems)
        {
            if (media.getFilePath().equals(filename))
            {
                return mVisibleItems.indexOf(media);
            }
        }

        return FILE_NOT_FOUND;
    }


    protected int getImageId(RawObject media)
	{
		if (mRawImages.contains(media))
		{
			return mRawImages.indexOf(media);
		}
		if (mNativeImages.contains(media))
		{
			return mRawImages.size() + mNativeImages.indexOf(media);
		}
		return FILE_NOT_FOUND;
	}

	protected void clearSubLists()
	{
		mFolders.clear();
		mXmpFiles.clear();
		mNativeImages.clear();
		mRawImages.clear();
		mUnknownFiles.clear();
	}

	protected boolean setPath(File newPath)
	{
		mCurrentPath = newPath;
		return processLocalFolder();
	}

	protected void setSingleImage(File image)
	{
		addFile(image, false);
	}

	protected boolean processLocalFolder()
	{
		if (!mCurrentPath.exists() || !mCurrentPath.isDirectory() || mCurrentPath.listFiles() == null)
		{
			return false;
		}

		clearSubLists();

		File[] sortedFiles = mCurrentPath.listFiles();
		Arrays.sort(sortedFiles, alphaCompare);
		for (File file : sortedFiles)
		{
			addFile(file, false);
		}
		return true;
	}

	protected void addFile(File file, boolean sort)
	{
		if (file.isDirectory())
		{
			mFolders.add(file);
			return;
		}

		LocalImage media = new LocalImage(file);

		if (xmpFilter.accept(file))
		{
			mXmpFiles.add(media);
			if (sort)
				Collections.sort(mXmpFiles, metaCompare);
		}
		if (nativeFilter.accept(file))
		{
			mNativeImages.add(media);
			if (sort)
				Collections.sort(mNativeImages, metaCompare);
		}
		else
		{
			if (media.canDecode())
			{
				mRawImages.add(media);
				if (sort)
					Collections.sort(mRawImages, metaCompare);
			}
			else
			{
				mUnknownFiles.add(media);
				if (sort)
					Collections.sort(mUnknownFiles, metaCompare);
			}
		}
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

		new Runnable()
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
		}.run();
	}

	protected void createRecycleBin()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		Boolean useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true);
		int binSizeMb = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(FullSettingsActivity.KEY_RecycleBinSize,
				Integer.toString(FullSettingsActivity.defRecycleBin)));
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
		final List<String> filesToRestore = new ArrayList<String>(keys.size());
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
					addFile(toRestore, true);
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

	public static File getKeywordFile(Context context)
	{
		return new File(context.getFilesDir().getAbsolutePath(), "keywords.txt");
	}

	protected boolean removeImage(RawObject toRemove)
	{
		boolean result = false;
		result = result || mRawImages.remove(toRemove);
		result = result || mNativeImages.remove(toRemove);
		result = result || mXmpFiles.remove(toRemove);
		result = result || mUnknownFiles.remove(toRemove);
		result = result || mVisibleItems.remove(toRemove);
		return result;
	}

	/**
	 * Deletes a file and determines if a recycle is necessary.
	 * 
	 * @param toDelete
	 *            file to delete.
	 * @return true currently (possibly return success later on)
	 */
	protected void deleteImage(final MediaItem toDelete)
	{
		List<MediaItem> itemsToDelete = new ArrayList<MediaItem>();
		itemsToDelete.add(toDelete);
		deleteImages(itemsToDelete);
	}

	@SuppressWarnings("unchecked")
	protected void deleteImages(final List<MediaItem> itemsToDelete)
	{
		if (itemsToDelete.size() == 0)
		{
			Toast.makeText(getBaseContext(), R.string.warningNoItemsSelected, Toast.LENGTH_SHORT).show();
			return;
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Boolean deleteConfirm = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true);
		Boolean useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true);
		Boolean justDelete = false;
		String message;
		long spaceRequired = 0;
		for (RawObject toDelete : itemsToDelete)
		{
			spaceRequired += toDelete.getFileSize();
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
		/* else */if (!useRecycle)
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

	/**
	 * Updates the UI after a delete.
	 */
	protected abstract void updateAfterDelete();

	/**
	 * Updates the UI after a restore.
	 */
	protected abstract void updateAfterRestore();

	protected class RecycleTask extends AsyncTask<List<MediaItem>, Integer, Boolean> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog.setTitle(R.string.recyclingFiles);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(final List<MediaItem>... params)
		{
			List<MediaItem> itemsToDelete = params[0];
			mProgressDialog.setMax(itemsToDelete.size());
			final List<RawObject> removed = new ArrayList<RawObject>();

			for (RawObject image : itemsToDelete)
			{
				recycleBin.addFile(image);
				removeImage(image);
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

	protected class DeleteTask extends AsyncTask<List<MediaItem>, Integer, Boolean> implements OnCancelListener
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
		protected Boolean doInBackground(final List<MediaItem>... params)
		{
			List<MediaItem> itemsToDelete = params[0];
			mProgressDialog.setMax(itemsToDelete.size());
			final List<RawObject> removed = new ArrayList<RawObject>();

			for (RawObject image : itemsToDelete)
			{
				if (image.delete())
				{
					removeImage(image);
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

	protected FilenameFilter imageFilter = new FilenameFilter()
	{
		@SuppressLint("DefaultLocale")
		@Override
		public boolean accept(File dir, String filename)
		{
			for (String ext : IMAGE_EXT)
			{
				if (filename.toLowerCase().endsWith(ext.toLowerCase()))
					return true;
			}
			return false;
		}
	};

	@Override
	protected void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		switch (requestCode)
		{
			case REQUEST_CODE_SHARE:
//				clearSwapDir();
				break;
		}
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

	class XmpFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			String filename = file.getName();
			return filename.toLowerCase(Locale.US).endsWith(".xmp");
		}
	}

	class NativeFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return isNative(file);
		}
	}

	protected boolean isNative(File file)
	{
		String filename = file.getName();
		return (filename.toLowerCase(Locale.US).endsWith("jpg") || filename.toLowerCase(Locale.US).endsWith("jpeg")
				|| filename.toLowerCase(Locale.US).endsWith("png") || filename.toLowerCase(Locale.US).endsWith("bmp") || filename.toLowerCase(Locale.US)
				.endsWith("gif"));
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
}
