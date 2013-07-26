package com.anthonymandra.framework;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.RawDroid;

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
	protected List<MetaMedia> mRawImages = new ArrayList<MetaMedia>();
	protected List<MetaMedia> mNativeImages = new ArrayList<MetaMedia>();
	protected List<MetaMedia> mXmpFiles = new ArrayList<MetaMedia>();
	protected List<MetaMedia> mUnknownFiles = new ArrayList<MetaMedia>();
	protected List<MetaMedia> mVisibleItems = new ArrayList<MetaMedia>();

	protected File mCurrentPath;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		// mProgressDialog.setCanceledOnTouchOutside(true);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		createSwapDir();
		createRecycleBin();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (recycleBin != null)
			recycleBin.flushCache();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (recycleBin != null)
			recycleBin.closeCache();
		recycleBin = null;
		clearSwapDir();
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

	protected void updateViewerItems()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean showNative = prefs.getBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);

		mVisibleItems.clear();
		mVisibleItems.addAll(mRawImages);
		if (showNative)
			mVisibleItems.addAll(mNativeImages);
	}
	
	protected int findMediaByFilename(String filename)
	{
		for (MediaObject raw : mRawImages)
		{
			if (raw.getPath().equals(filename))
			{
				return mRawImages.indexOf(raw);
			}
		}
		
		for (MediaObject generic : mNativeImages)
		{
			if (generic.getPath().equals(filename))
			{
				return mRawImages.size() + mNativeImages.indexOf(generic);
			}
		}
		
		return -1;
	}

	protected int getImageId(MediaObject media)
	{
		if (mRawImages.contains(media))
		{
			return mRawImages.indexOf(media);
		}
		if (mNativeImages.contains(media))
		{
			return mRawImages.size() + mNativeImages.indexOf(media);
		}
		return -1;
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
	
	protected void setSingeImage(File image)
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
		mSwapDir = Utils.getDiskCacheDir(this, SWAP_BIN_DIR);
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
				for (File toDelete : swapFiles)
				{
					toDelete.delete();
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
			recycleBin = new RecycleBin(this, RawDroid.RECYCLE_BIN_DIR, binSizeMb * 1024 * 1024);
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
					write(toRestore, restore);
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

	protected void write(File destination, InputStream is)
	{
		BufferedOutputStream bos = null;
		byte[] data = null;
		try
		{
			bos = new BufferedOutputStream(new FileOutputStream(destination));
			data = new byte[is.available()];
			is.read(data);
			bos.write(data);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (bos != null)
				{
					bos.close();
				}
				if (is != null)
				{
					is.close();
				}
			}
			catch (IOException e)
			{
			}
		}
	}

	protected boolean removeImage(MediaObject toRemove)
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
	protected void deleteImage(final MediaObject toDelete)
	{
		List<MediaObject> itemsToDelete = new ArrayList<MediaObject>();
		itemsToDelete.add(toDelete);
		deleteImages(itemsToDelete);
	}

	@SuppressWarnings("unchecked")
	protected void deleteImages(final List<MediaObject> itemsToDelete)
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
		for (MediaObject toDelete : itemsToDelete)
		{
			spaceRequired += toDelete.getFileSize();
		}

		// Go straight to delete if
		// 1. MTP (unsupported)
		// 2. Recycle is set to off
		// 3. For some reason the bin is null
/*		if (itemsToDelete.get(0) instanceof MtpImage)
		{
			justDelete = true;
			message = getString(R.string.warningRecycleMtp);
		}
		else */if (!useRecycle)
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

	protected class RecycleTask extends AsyncTask<List<MediaObject>, Integer, Boolean> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog.setTitle(R.string.recyclingFiles);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(final List<MediaObject>... params)
		{
			List<MediaObject> itemsToDelete = params[0];
			mProgressDialog.setMax(itemsToDelete.size());
			final List<MediaObject> removed = new ArrayList<MediaObject>();

			for (MediaObject image : itemsToDelete)
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

	protected class DeleteTask extends AsyncTask<List<MediaObject>, Integer, Boolean> implements OnCancelListener
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
		protected Boolean doInBackground(final List<MediaObject>... params)
		{
			List<MediaObject> itemsToDelete = params[0];
			mProgressDialog.setMax(itemsToDelete.size());
			final List<MediaObject> removed = new ArrayList<MediaObject>();

			for (MediaObject image : itemsToDelete)
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

	protected class ShareTask extends AsyncTask<Object, Integer, Intent> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog.setTitle(R.string.convertingFiles);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@SuppressWarnings("unchecked")
		@Override
		protected Intent doInBackground(final Object... params)
		{
			Intent shareIntent = (Intent) params[0];
			List<MediaObject> toShare = (List<MediaObject>) params[1];
			mProgressDialog.setMax(toShare.size());
			ArrayList<Uri> arrayUri = new ArrayList<Uri>();
			int completed = 0;
			for (MediaObject image : toShare)
			{
				BufferedInputStream imageData = image.getThumbInputStream();
				if (imageData == null)
					return null;
				File swapFile = getSwapFile(Utils.swapExtention(image.getName(), ".jpg"));
				write(swapFile, imageData);
				try
				{
					imageData.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				publishProgress(++completed);
				arrayUri.add(Uri.fromFile(swapFile));
			}

			if (arrayUri.size() == 0)
			{
				return null;
			}
			else if (arrayUri.size() > 1)
			{
				shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayUri);
			}
			else
			{
				shareIntent.putExtra(Intent.EXTRA_STREAM, arrayUri.get(0));
			}

			return shareIntent;
		}

		@Override
		protected void onPostExecute(Intent result)
		{
			mProgressDialog.dismiss();
			if (result == null)
			{
				Toast.makeText(GalleryActivity.this, R.string.warningFailedToGetStream, Toast.LENGTH_LONG).show();
				return;
			}
			startActivityForResult(result, REQUEST_CODE_SHARE);
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
			clearSwapDir();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		switch (requestCode)
		{
			case REQUEST_CODE_SHARE:
				clearSwapDir();
				break;
		}
	}

	/**
	 * Converts images to jpeg format for sharing and executes given share intent.
	 * 
	 * @param shareIntent
	 *            Share intent to populate and execute
	 * @param itemsToShare
	 *            Media to convert and share
	 */
	public void share(Intent shareIntent, List<MediaObject> itemsToShare)
	{
		if (itemsToShare.size() == 0)
		{
			Toast.makeText(this, R.string.warningNoItemsSelected, Toast.LENGTH_SHORT).show();
			return;
		}
		new ShareTask().execute(shareIntent, itemsToShare);
	}

	/**
	 * Single item convenience method (see {@link #share(Intent, List)}
	 */
	public void share(Intent shareIntent, MediaObject toShare)
	{
		List<MediaObject> share = new ArrayList<MediaObject>();
		share.add(toShare);
		share(shareIntent, share);
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
		return (filename.toLowerCase(Locale.US).endsWith("jpg") || 
				filename.toLowerCase(Locale.US).endsWith("jpeg")|| 
				filename.toLowerCase(Locale.US).endsWith("png") || 
				filename.toLowerCase(Locale.US).endsWith("bmp") || 
				filename.toLowerCase(Locale.US).endsWith("gif"));
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

	class MetaAlphaCompare implements Comparator<MediaObject>
	{
		// Comparator interface requires defining compare method.
		@Override
		public int compare(MediaObject filea, MediaObject fileb)
		{
			return filea.getName().compareToIgnoreCase(fileb.getName());
		}
	}
}
