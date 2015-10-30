package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.provider.DocumentFile;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.dcraw.LibRaw;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.rawdroid.Constants;
import com.anthonymandra.rawdroid.FormatDialog;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.ImageViewActivity;
import com.anthonymandra.rawdroid.LegacyViewerActivity;
import com.anthonymandra.rawdroid.LicenseManager;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.XmpEditFragment;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpWriter;
import com.inscription.ChangeLogDialog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public abstract class CoreActivity extends DocumentActivity
{
	private enum WriteActions
	{
		COPY,
		DELETE,
		RECYCLE,
		RESTORE,
		RENAME,
		WRITE_XMP
	}

	@SuppressWarnings("unused")
	private static final String TAG = CoreActivity.class.getSimpleName();

	public static final String SWAP_BIN_DIR = "swap";
	public static final String RECYCLE_BIN_DIR = "recycle";
	public static final int FILE_NOT_FOUND = -1;

	private static final int REQUEST_CODE_SHARE = 00;
	private static final int REQUEST_PREFIX = 2000;

	private static final String PREFERENCE_SKIP_WRITE_WARNING = "skip_write_warning";

	protected DocumentRecycleBin recycleBin;
	protected File mSwapDir;

	protected ProgressDialog mProgressDialog;

	protected RawFilter rawFilter = new RawFilter();
	protected JpegFilter jpegFilter = new JpegFilter();
	private XmpFilter xmpFilter = new XmpFilter();
	private NativeFilter nativeFilter = new NativeFilter();
	private FileAlphaCompare alphaCompare = new FileAlphaCompare();
	private MetaAlphaCompare metaCompare = new MetaAlphaCompare();

	protected ShareActionProvider mShareProvider;
	protected Intent mShareIntent;
	protected Handler licenseHandler;

	// Identifies a particular Loader being used in this component
	public static final int META_LOADER_ID = 0;
	public static final String EXTRA_META_BUNDLE = "meta_bundle";
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
		mShareIntent.setType("image/jpeg");
		mShareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		mShareIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.contact:
				requestEmailIntent();
				return true;
			case R.id.about:
				final ChangeLogDialog changeLogDialog = new ChangeLogDialog(this);
				changeLogDialog.show(Constants.VariantCode == 8);
				return true;
			case R.id.settings:
				requestSettings();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResumeWriteAction(Enum callingMethod, Object[] callingParameters)
	{
		switch ((WriteActions)callingMethod)
		{
			case DELETE:
				new DeleteTask().execute((List<Uri>) callingParameters[0]);
				break;
			case RECYCLE:
				new RecycleTask().execute((List<Uri>) callingParameters[0]);
				break;
			case RENAME:
				new RenameTask().execute(callingParameters);
				break;
			case WRITE_XMP:
				new WriteXmpTask().execute(callingParameters);
				break;
			default:
				throw new NoSuchMethodError("Write Action:" + callingMethod + " is undefined.");
		}
		clearWriteResume();
	}

	protected void runCleanDatabase()
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					ImageUtils.cleanDatabase(CoreActivity.this);
				} catch (RemoteException e)
				{
					e.printStackTrace();
				} catch (OperationApplicationException e)
				{
					e.printStackTrace();
				}
			}
		}).start();
	}

	protected void writeXmp(Uri toWrite, XmpEditFragment.XmpEditValues values)
	{
		List<Uri> placeholder = new ArrayList<>();
		placeholder.add(toWrite);
		writeXmp(placeholder, values);
	}

	protected void writeXmp(List<Uri> images, XmpEditFragment.XmpEditValues values)
	{
		new WriteXmpTask().execute(images, values);
	}

	/**
	 * Updates the metadata database.  This should be run in the background.
	 * @param databaseUpdates database updates
	 */
	protected void updateMetaDatabase(ArrayList<ContentProviderOperation> databaseUpdates)
	{
		try
		{
			// TODO: If I implement bulkInsert it's faster
			getContentResolver().applyBatch(Meta.AUTHORITY, databaseUpdates);
		} catch (RemoteException | OperationApplicationException e)
		{
			//TODO: Notify user
			e.printStackTrace();
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
		} catch (NumberFormatException e)
		{
			binSizeMb = 0;
		}
		if (useRecycle)
		{
			recycleBin = new DocumentRecycleBin(this, RECYCLE_BIN_DIR, binSizeMb * 1024 * 1024);
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
		new AlertDialog.Builder(this).setTitle(R.string.recycleBin)
			.setNegativeButton(R.string.emptyRecycleBin, new Dialog.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					recycleBin.clearCache();
				}
			})
			.setNeutralButton(R.string.neutral, new Dialog.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// cancel, do nothing
				}
			})
			.setPositiveButton(R.string.restoreFile, new Dialog.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					restoreFiles(filesToRestore);
				}
			})
			.setMultiChoiceItems(keys.toArray(new String[keys.size()]), null, new DialogInterface.OnMultiChoiceClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked)
				{
					if (isChecked)
						filesToRestore.add(keys.get(which));
					else
						filesToRestore.remove(keys.get(which));
				}
			})
			.show();
	}

	/**
	 * Gets the swap directory and clears existing files in the process
	 *
	 * @param filename name of the file to add to swap
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

		if (settings.getBoolean(FullSettingsActivity.KEY_UseLegacyViewer, false))
		{
			viewer.setClass(this, LegacyViewerActivity.class);
		}
		else
		{
			viewer.setClass(this, ImageViewActivity.class);
		}
		return viewer;
	}

	/**
	 * Placeholder class TODO: Remove all references
	 */
	private List<MediaItem> getImageListFromUriList(List<Uri> uris)
	{
		List<MediaItem> images = new ArrayList<>();
		for (Uri u : uris)
			images.add(getImageFromUri(u));
		return images;
	}

	/**
	 * Placeholder class TODO: Remove all references
	 */
	private MediaItem getImageFromUri(Uri uri)
	{
		File f = new File(uri.getPath());
		return new LocalImage(this, f);
	}

	/**
	 * Deletes a file and determines if a recycle is necessary.
	 *
	 * @param toDelete file to delete.
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

	protected void requestEmailIntent()
	{
		requestEmailIntent(null);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	protected void requestEmailIntent(String subject)
	{
		Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
				"mailto", "rawdroid@anthonymandra.com", null));

		if (subject != null)
		{
			emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		}

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

	private void requestSettings()
	{
		Intent settings = new Intent(this, FullSettingsActivity.class);
		startActivity(settings);
	}

	int numDigits(int x)
	{
		return (x < 10 ? 1 : (x < 100 ? 2 : (x < 1000 ? 3 : (x < 10000 ? 4 : (x < 100000 ? 5 : (x < 1000000 ? 6 : (x < 10000000 ? 7 : (x < 100000000 ? 8
				: (x < 1000000000 ? 9 : 10)))))))));
	}

	private void showRenameDialog(final List<Uri> itemsToRename)
	{
		final View dialogView = LayoutInflater.from(this).inflate(R.layout.format_name, null);
		final Spinner format = (Spinner) dialogView.findViewById(R.id.spinner1);
		final EditText nameText = (EditText) dialogView.findViewById(R.id.editTextFormat);

		final AlertDialog renameDialog = new AlertDialog.Builder(this)
				.setTitle(getString(R.string.renameImages))
				.setView(dialogView)
				.setPositiveButton(R.string.rename, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						String customName = ((EditText) findViewById(R.id.editTextFormat)).getText().toString();
						int selected = format.getSelectedItemPosition();
						new RenameTask().execute(itemsToRename, selected, customName);
					}
				})
				.setNegativeButton(R.string.neutral, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which) {}
				}).create();

//		final Spinner format = (Spinner) dialogView.findViewById(R.id.spinner1);
//		final EditText nameText = (EditText) dialogView.findViewById(R.id.editTextFormat);

		renameDialog.setCanceledOnTouchOutside(true);
		renameDialog.show();
	}

	/**
	 * Fires after individual items are successfully added.  This will fire multiple times in a batch.
	 * @param item added item
	 */
	protected abstract void onImageAdded(Uri item);

	/**
	 * Fires after individual items are successfully deleted.  This will fire multiple times in a batch.
	 * @param item deleted item
	 */
	protected abstract void onImageRemoved(Uri item);

	/**
	 * Fires after all actions of a batch (or single) change to the image set are complete.
	 */
	protected abstract void onImageSetChanged();

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	protected void checkWriteAccess()
	{
		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean skipWarning = settings.getBoolean(PREFERENCE_SKIP_WRITE_WARNING, false);
		if (skipWarning)
			return;

		if (Util.hasLollipop())
		{
			List<UriPermission> permissions = getContentResolver().getPersistedUriPermissions();

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.writeAccessTitle);
			builder.setMessage(R.string.requestWriteAccess);
			builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// Do nothing
				}
			});
			builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
//					requestWritePermission();
				}
			});
			builder.show();

		}
		else if (Util.hasKitkat())
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.writeAccessTitle);
			builder.setMessage(R.string.kitkatWriteIssue);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// Do nothing, just a warning
				}
			});
			builder.show();
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFERENCE_SKIP_WRITE_WARNING, true);
		editor.apply();
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

	class JpegFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return Util.isJpeg(file);
		}
	}

	class RawFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return Util.isRaw(file);
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

	public static class LicenseHandler extends Handler
	{
		private final WeakReference<Context> mContext;

		public LicenseHandler(Context context)
		{
			mContext = new WeakReference<>(context);
		}

		@Override
		public void handleMessage(Message msg)
		{
			License.LicenseState state = (License.LicenseState) msg.getData().getSerializable(License.KEY_LICENSE_RESPONSE);
			if (state.toString().startsWith("modified"))
			{
				for (int i = 0; i < 3; i++)
					Toast.makeText(mContext.get(), "An app on your device has attempted to modify Rawdroid.  Check Settings > License for more information.", Toast.LENGTH_LONG).show();
			}
			else if (state == License.LicenseState.error)
			{
				Toast.makeText(mContext.get(), "There was an error communicating with Google Play.  Check Settings > License for more information.", Toast.LENGTH_LONG).show();
			}
		}
	}

	/**
	 * File operation tasks
	 */

	/**
	 * Copies an image and corresponding xmp and jpeg (ex: src/a.[cr2,xmp,jpg] -> dest/a.[cr2,xmp,jpg])
	 * @param fromImage source image
	 * @param toImage target image
	 * @return success
	 * @throws WritePermissionException
	 */
	private boolean copy(File fromImage, File toImage) throws WritePermissionException
	{
		if (ImageUtils.hasXmpFile(fromImage))
			copyFile(ImageUtils.getXmpFile(fromImage), ImageUtils.getXmpFile(toImage));
		if (ImageUtils.hasJpgFile(fromImage))
			copyFile(ImageUtils.getJpgFile(fromImage), ImageUtils.getJpgFile(toImage));
		return copyFile(fromImage, toImage);
	}


	//TODO: Double check if we want custom AsyncTask here

	public class CopyTask extends AsyncTask<Object, String, Boolean> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(CoreActivity.this);
			mProgressDialog.setTitle(R.string.importingImages);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setCanceledOnTouchOutside(true);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Object... params)
		{
			boolean totalSuccess = true;
			List<Uri> totalImages = (List<Uri>) params[0];
			File destinationFolder = (File) params[1];

			List<Uri> remainingImages = new ArrayList<>();
			Collections.copy(remainingImages, totalImages);

			mProgressDialog.setMax(totalImages.size());

			for (Uri toCopy : totalImages)
			{
				File from = new File(toCopy.getPath());
				String filename = from.getName();
				File to = new File(destinationFolder, filename);

				publishProgress(filename);

				// Skip a file if it's the same location
				if (to.exists()) continue;

				try
				{
					setWriteResume(WriteActions.WRITE_XMP, new Object[]{remainingImages});

					totalSuccess &= copy(from, to);
					remainingImages.remove(toCopy);
					onImageAdded(toCopy);
				} catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return false;
				}
				publishProgress();
			}
			return totalSuccess;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			mProgressDialog.dismiss();

			if (result)
				clearWriteResume();

			onImageSetChanged();
		}

		@Override
		protected void onProgressUpdate(String... values)
		{
			if (values.length > 0)
			{
				mProgressDialog.setMessage(values[0]);
			}
			else
			{
				mProgressDialog.incrementProgressBy(1);
			}
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	protected boolean writeThumb(Uri source, File destination) {
		ParcelFileDescriptor pfd = null;
		try
		{
			DocumentFile dest = FileUtil.getDocumentFile(this, destination, false, true);
			pfd = getContentResolver().openFileDescriptor(dest.getUri(), "w");
			return LibRaw.writeThumbFile(source.getPath(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, pfd.getFd());
		}
		catch(Exception e)
		{
			return false;
		}
		finally
		{
			Utils.closeSilently(pfd);
		}
	}

	protected boolean writeThumbWatermark(Uri source, File destination, byte[] waterMap,
	                                   int waterWidth, int waterHeight, LibRaw.Margins waterMargins) {
		ParcelFileDescriptor pfd = null;
		try
		{
			DocumentFile dest = FileUtil.getDocumentFile(this, destination, false, true);
			pfd = getContentResolver().openFileDescriptor(dest.getUri(), "w");
			return LibRaw.writeThumbFileWatermark(source.getPath(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, pfd.getFd(), waterMap, waterMargins.getArray(), waterWidth, waterHeight);
		}
		catch(Exception e)
		{
			return false;
		}
		finally
		{
			Utils.closeSilently(pfd);
		}
	}

	public class CopyThumbTask extends AsyncTask<Object, String, Boolean> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(CoreActivity.this);
			mProgressDialog.setTitle(R.string.exportingThumb);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setCanceledOnTouchOutside(true);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Object... params)
		{
			boolean totalSuccess = true;
			List<Uri> totalImages = (List<Uri>) params[0];
			File destinationFolder = (File) params[1];

			List<Uri> remainingImages = new ArrayList<>();
			Collections.copy(remainingImages, totalImages);

			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(CoreActivity.this);
			boolean showWatermark = pref.getBoolean(FullSettingsActivity.KEY_EnableWatermark, false);
			String watermarkText = pref.getString(FullSettingsActivity.KEY_WatermarkText, "");
			int watermarkAlpha = pref.getInt(FullSettingsActivity.KEY_WatermarkAlpha, 75);
			int watermarkSize = pref.getInt(FullSettingsActivity.KEY_WatermarkSize, 150);
			String watermarkLocation = pref.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");
			LibRaw.Margins margins = new LibRaw.Margins(pref);

			Bitmap watermark;
			byte[] waterData = null;
			boolean processWatermark = false;
			int waterWidth = 0, waterHeight = 0;

			if (Constants.VariantCode < 11 || LicenseManager.getLastResponse() != License.LicenseState.pro)
			{
				processWatermark = true;
				// Just grab the first width and assume that will be sufficient for all images
				final int width = getContentResolver().query(Meta.Data.CONTENT_URI, null, Meta.Data.URI + "?", new String[] {totalImages.get(0).toString()}, null).getInt(Meta.WIDTH_COLUMN);
				watermark = Util.getDemoWatermark(CoreActivity.this, width);
				waterData = Util.getBitmapBytes(watermark);
				waterWidth = watermark.getWidth();
				waterHeight = watermark.getHeight();
				margins = LibRaw.Margins.LowerRight;
			}
			else if (showWatermark)
			{
				processWatermark = true;
				if (watermarkText.isEmpty())
				{
					Toast.makeText(CoreActivity.this, R.string.warningBlankWatermark, Toast.LENGTH_LONG).show();
					processWatermark = false;
				}
				else
				{
					watermark = Util.getWatermarkText(watermarkText, watermarkAlpha, watermarkSize, watermarkLocation);
					waterData = Util.getBitmapBytes(watermark);
					waterWidth = watermark.getWidth();
					waterHeight = watermark.getHeight();
				}
			}

			boolean success = true;
			for (Uri toExport : totalImages)
			{
				File from = new File(toExport.getPath());
				File thumbDest = new File(destinationFolder, Util.swapExtention(from.getName(), ".jpg"));
				publishProgress(from.getName());

				if (processWatermark)
				{
					success = writeThumbWatermark(toExport, thumbDest, waterData, waterWidth, waterHeight, margins) && success;
					onImageAdded(Uri.fromFile(thumbDest));
				}
				else
				{
					success = writeThumb(toExport, thumbDest) && success;
					onImageAdded(Uri.fromFile(thumbDest));
				}

				publishProgress();
			}
			return success; //not used.
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			onImageSetChanged();
			mProgressDialog.dismiss();
		}

		@Override
		protected void onProgressUpdate(String... values)
		{
			if (values.length > 0)
			{
				mProgressDialog.setMessage(values[0]);
			}
			else
			{
				mProgressDialog.incrementProgressBy(1);
			}
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	private boolean delete(File image) throws WritePermissionException
	{
		if (ImageUtils.hasXmpFile(image))
			deleteFile(ImageUtils.getXmpFile(image));
		if (ImageUtils.hasJpgFile(image))
			deleteFile(ImageUtils.getJpgFile(image));
		return deleteFile(image);
	}

	protected class DeleteTask extends AsyncTask<List<Uri>, Integer, Boolean>
			implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(CoreActivity.this);
			mProgressDialog.setTitle(R.string.deletingFiles);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(final List<Uri>... params)
		{
			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission
			final List<Uri> totalDeletes = params[0];
			List<Uri> remainingDeletes = new ArrayList<>();
			Collections.copy(remainingDeletes, totalDeletes);

			mProgressDialog.setMax(totalDeletes.size());
			final List<Uri> removed = new ArrayList<>();

			for (Uri toDelete : totalDeletes)
			{
				try
				{
					setWriteResume(WriteActions.DELETE, new Object[]{remainingDeletes});
					if (delete(new File(toDelete.getPath())))
					{
						onImageRemoved(toDelete);
						remainingDeletes.remove(toDelete);
						removed.add(toDelete);
					}
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return false;
				}
			}
			return removed.size() == totalDeletes.size();
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();

			mProgressDialog.dismiss();
			onImageSetChanged();
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
			onImageSetChanged();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	protected class RecycleTask extends AsyncTask<List<Uri>, Integer, Void> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog.setTitle(R.string.recyclingFiles);
			mProgressDialog.show();
		}

		@Override
		protected Void doInBackground(final List<Uri>... params)
		{
			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission
			final List<Uri> totalRecycles = params[0];
			List<Uri> remainingRecycles = new ArrayList<>();
			Collections.copy(remainingRecycles, totalRecycles);

			mProgressDialog.setMax(remainingRecycles.size());

			for (Uri toRecycle : totalRecycles)
			{
				setWriteResume(WriteActions.RECYCLE, new Object[]{remainingRecycles});
				recycleBin.addFile(toRecycle);
				remainingRecycles.remove(toRecycle);
				onImageRemoved(toRecycle);
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			mProgressDialog.dismiss();
			onImageSetChanged();
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
			onImageSetChanged();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	protected void restoreFiles(List<String> toRestore)
	{
		new RestoreTask().execute(toRestore);
	}

	protected class RestoreTask extends AsyncTask<List<String>, Integer, Boolean>
	{
		@Override
		protected Boolean doInBackground(final List<String>... params)
		{
			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission
			final List<String> totalRestores = params[0];
			List<String> remainingRestores = new ArrayList<>();
			Collections.copy(remainingRestores, totalRestores);

			//TODO: This must be it's own task
			List<MediaItem> success = new ArrayList<>();
			for (String filename : totalRestores)
			{
				File toRestore = new File(filename);
				try
				{
					setWriteResume(WriteActions.RESTORE, new Object[]{remainingRestores});
					moveFile(recycleBin.getFile(filename), toRestore);
					onImageAdded(Uri.fromFile(toRestore));
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					return false;
				}
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();

			onImageSetChanged();
		}
	}

	public boolean renameImage(File source, String baseName) throws WritePermissionException
	{
		Boolean imageSuccess = true;
		Boolean xmpSuccess = true;
		Boolean jpgSuccess = true;

		imageSuccess = renameAssociatedFile(source, baseName);
		if (ImageUtils.hasXmpFile(source))
		{
			xmpSuccess = renameAssociatedFile(ImageUtils.getXmpFile(source), baseName);
		}
		if (ImageUtils.hasJpgFile(source))
		{
			jpgSuccess = renameAssociatedFile(ImageUtils.getJpgFile(source), baseName);
		}

		return imageSuccess && xmpSuccess && jpgSuccess;
	}

	public boolean renameAssociatedFile(File original, String baseName)
			throws WritePermissionException
	{
		File renameFile = getRenamedFile(original, baseName);
		return moveFile(original, renameFile);
	}

	public File getRenamedFile(File original, String baseName)
	{
		String filename = original.getName();
		String ext = filename.substring(filename.lastIndexOf("."),
				filename.length());

		String rename = baseName + ext;
		return new File(original.getParent(), rename);
	}

	protected class RenameTask extends AsyncTask<Object, Integer, Boolean>
	{
		@Override
		protected Boolean doInBackground(final Object... params)
		{
			final List<Uri> totalImages = (List<Uri>) params[0];
			final int format = (int) params[1];
			final String customName = (String) params[2];

			int counter = 0;
			final int total = totalImages.size();
			final String sequencer = "%0" + numDigits(total) + "d";
			final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

			List<Uri> remainingImages = new ArrayList<>();
			Collections.copy(remainingImages, totalImages);

			try
			{
				for (Uri image : totalImages)
				{
					++counter;
					setWriteResume(WriteActions.RENAME, new Object[]{
							remainingImages,
							format,
							customName
					});

					String rename = null;
					switch (format)
					{
						case 0:
							rename = customName + "-" + String.format(sequencer, counter);
							break;
						case 1:
							rename = customName + " (" + String.format(sequencer, counter) + " of " + total + ")";
							break;
					}

					File imageFile = new File(image.getPath());

					renameImage(imageFile, rename);
					remainingImages.remove(image);
					File renameFile = getRenamedFile(imageFile, rename);

					ContentValues c = new ContentValues();
					c.put(Meta.Data.NAME, renameFile.getName());
					c.put(Meta.Data.URI, Uri.fromFile(renameFile).toString());

					operations.add(
							ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
									.withSelection(Meta.URI_COLUMN + "=?", new String[]{image.toString()})
									.withValues(c)
									.build());
				}
			} catch (WritePermissionException e)
			{
				e.printStackTrace();
			}

			updateMetaDatabase(operations);
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
		}
	}

	protected class WriteXmpTask extends AsyncTask<Object, Integer, Boolean>
	{
		@Override
		protected Boolean doInBackground(final Object... params)
		{
			final List<Uri> totalImages = (List<Uri>) params[0];
			final XmpEditFragment.XmpEditValues values = (XmpEditFragment.XmpEditValues) params[1];
			final ArrayList<ContentProviderOperation> databaseUpdates = new ArrayList<>();

			List<Uri> remainingImages = new ArrayList<>();
			Collections.copy(remainingImages, totalImages);

			for (Uri image : totalImages)
			{
				final Metadata meta = new Metadata();
				meta.addDirectory(new XmpDirectory());
				ImageUtils.updateSubject(meta, values.Subject);
				ImageUtils.updateRating(meta, values.Rating);
				ImageUtils.updateLabel(meta, values.Label);

				ContentValues cv = new ContentValues();
				cv.put(Meta.Data.LABEL, values.Label);
				cv.put(Meta.Data.RATING, values.Rating);
				cv.put(Meta.Data.SUBJECT, ImageUtils.convertArrayToString(values.Subject));

				databaseUpdates.add(ContentProviderOperation.newInsert(Meta.Data.CONTENT_URI)
						.withValues(ImageUtils.getContentValues(CoreActivity.this, image))
						.build());

				final File xmp = ImageUtils.getXmpFile(new File(image.getPath()));
				final DocumentFile xmpDoc;
				try
				{
					setWriteResume(WriteActions.WRITE_XMP, new Object[]{remainingImages});
					xmpDoc = getDocumentFile(xmp, false, true);
					remainingImages.remove(image);
				}
				catch (WritePermissionException e)
				{
					// Write pending updates, method will resume with remainingImages
					updateMetaDatabase(databaseUpdates);
					return false;
				}

				OutputStream os = null;
				try
				{
					os = getContentResolver().openOutputStream(xmpDoc.getUri());
					if (meta.containsDirectoryOfType(XmpDirectory.class))
						XmpWriter.write(os, meta);
				} catch (FileNotFoundException e)
				{
					e.printStackTrace();
				}
				finally
				{
					Utils.closeSilently(os);
				}
			}

			updateMetaDatabase(databaseUpdates);
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
		}
	}
}
