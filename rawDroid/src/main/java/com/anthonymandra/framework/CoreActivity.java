package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v7.widget.ShareActionProvider;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.rawdroid.Constants;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.ImageViewActivity;
import com.anthonymandra.rawdroid.LegacyViewerActivity;
import com.anthonymandra.rawdroid.LicenseManager;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.XmpEditFragment;
import com.anthonymandra.rawprocessor.LibRaw;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtils;
import com.crashlytics.android.Crashlytics;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpWriter;
import com.inscription.ChangeLogDialog;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
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

	protected DocumentRecycleBin recycleBin;
	protected File mSwapDir;

	protected ProgressDialog mProgressDialog;

	protected ShareActionProvider mShareProvider;
	protected Intent mShareIntent;

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
		setStoragePermissionRequestEnabled(true);
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
		LicenseManager.getLicense(this, getLicenseHandler());
		// Request storage permission here:
		// http://developer.android.com/samples/RuntimePermissions/src/com.example.android.system.runtimepermissions/MainActivity.html#l153
		createSwapDir();
		createRecycleBin();
	}

	protected abstract LicenseHandler getLicenseHandler();

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
		if (callingMethod == null)
		{
			Crashlytics.logException(new Exception("Null Write Method"));
			return;
		}

		if (callingMethod instanceof  WriteActions)
		{
			switch ((WriteActions) callingMethod)
			{
				case COPY:
					new CopyTask().execute(callingParameters);
					break;
				case DELETE:
					new DeleteTask().execute(callingParameters);
					break;
				case RECYCLE:
					new RecycleTask().execute(callingParameters);
					break;
				case RENAME:
					new RenameTask().execute(callingParameters);
					break;
				case RESTORE:
					new RestoreTask().execute(callingParameters);
					break;
				case WRITE_XMP:
					new WriteXmpTask().execute(callingParameters);
					break;
			}
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
				} catch (RemoteException | OperationApplicationException e)
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
			ContentProviderResult[] results = getContentResolver().applyBatch(Meta.AUTHORITY, databaseUpdates);
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
		mSwapDir = FileUtil.getDiskCacheDir(this, SWAP_BIN_DIR);
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
		final List<String> filesToRestore = new ArrayList<>();
		final List<String> shortNames = new ArrayList<>(keys.size());
		for (String key : keys)
		{
			shortNames.add(Uri.parse(key).getLastPathSegment());
		}
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
			.setMultiChoiceItems(shortNames.toArray(new String[shortNames.size()]), null, new DialogInterface.OnMultiChoiceClickListener()
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
	 * Deletes a file and determines if a recycle is necessary.
	 *
	 * @param toDelete file to delete.
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

	protected void showRenameDialog(final List<Uri> itemsToRename)
	{
		final View dialogView = LayoutInflater.from(this).inflate(R.layout.format_name, null);
		final Spinner format = (Spinner) dialogView.findViewById(R.id.spinner1);
		final EditText nameText = (EditText) dialogView.findViewById(R.id.editTextFormat);
		final TextView exampleText = (TextView) dialogView.findViewById(R.id.textViewExample);

		nameText.addTextChangedListener(new TextWatcher()
		{
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s)
			{
				exampleText.setText(
						"Ex: " + formatRename(format.getSelectedItemPosition(),
								s.toString(),
								itemsToRename.size() - 1,
								itemsToRename.size()));
			}
		});

		format.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				exampleText.setText(
						"Ex: " + formatRename(format.getSelectedItemPosition(),
								nameText.getText().toString(),
								itemsToRename.size() - 1,
								itemsToRename.size()));
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		final AlertDialog renameDialog = new AlertDialog.Builder(this)
				.setTitle(getString(R.string.renameImages))
				.setView(dialogView)
				.setPositiveButton(R.string.rename, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						String customName = nameText.getText().toString();
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
			return ImageUtils.isJpeg(file);
		}
	}

	class RawFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return ImageUtils.isRaw(file);
		}
	}

	class NativeFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return ImageUtils.isNative(file);
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
			if (state == null || state.toString().startsWith("modified"))
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
	private boolean copyAssociatedFiles(Uri fromImage, Uri toImage) throws IOException
	{
		if (ImageUtils.hasXmpFile(this, fromImage))
		{
			copyFile(ImageUtils.getXmpFile(this, fromImage).getUri(),
					ImageUtils.getXmpFile(this, toImage).getUri());
		}
		if (ImageUtils.hasJpgFile(this, fromImage))
		{
			copyFile(ImageUtils.getJpgFile(this, fromImage).getUri(),
					ImageUtils.getJpgFile(this, toImage).getUri());
		}
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
		@SuppressWarnings("unchecked")
		protected Boolean doInBackground(Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof Uri))
				throw new IllegalArgumentException();

			List<Uri> totalImages = (List<Uri>) params[0];
			List<Uri> remainingImages = new ArrayList<>(totalImages);

			Uri destinationFolder = (Uri) params[1];

			mProgressDialog.setMax(totalImages.size());

			for (Uri toCopy : totalImages)
			{
				try
				{
					setWriteResume(WriteActions.COPY, new Object[]{remainingImages});

					UsefulDocumentFile source = UsefulDocumentFile.fromUri(CoreActivity.this, toCopy);
					Uri destinationFile = FileUtil.getChildUri(destinationFolder, source.getName());
					copyAssociatedFiles(toCopy, destinationFile);
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return false;
				} catch (IOException e)
				{
					e.printStackTrace();
				}
				remainingImages.remove(toCopy);
				onImageAdded(toCopy);
				publishProgress();
			}
			return true;
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

	protected boolean writeThumb(Uri source, ParcelFileDescriptor destination)
	{
		return LibRaw.writeThumbFile(source.getPath(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, destination.getFd());
	}

	protected boolean writeThumb(ParcelFileDescriptor source, ParcelFileDescriptor destination)
	{
		return LibRaw.writeThumbFd(source.getFd(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, destination.getFd());
	}

	protected boolean writeThumbWatermark(Uri source, ParcelFileDescriptor destination, byte[] waterMap,
	                                      int waterWidth, int waterHeight, LibRaw.Margins waterMargins) {
		// TODO: This must work solely on file descriptors (source also)
		return LibRaw.writeThumbFileWatermark(source.getPath(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, destination.getFd(), waterMap, waterMargins.getArray(), waterWidth, waterHeight);
	}

	protected boolean writeThumbWatermark(ParcelFileDescriptor source, ParcelFileDescriptor destination, byte[] waterMap,
	                                      int waterWidth, int waterHeight, LibRaw.Margins waterMargins) {
		// TODO: This must work solely on file descriptors (source also)
		return LibRaw.writeThumbFdWatermark(source.getFd(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, destination.getFd(), waterMap, waterMargins.getArray(), waterWidth, waterHeight);
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
		@SuppressWarnings("unchecked")
		protected Boolean doInBackground(Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof Uri))
				throw new IllegalArgumentException();

			boolean totalSuccess = true;
			List<Uri> totalImages = (List<Uri>) params[0];
			List<Uri> remainingImages = new ArrayList<>(totalImages);
			Uri destinationFolder = (Uri) params[1];

			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(CoreActivity.this);
			boolean showWatermark = pref.getBoolean(FullSettingsActivity.KEY_EnableWatermark, false);
			String watermarkText = pref.getString(FullSettingsActivity.KEY_WatermarkText, "");
			int watermarkAlpha = pref.getInt(FullSettingsActivity.KEY_WatermarkAlpha, 75);
			int watermarkSize = pref.getInt(FullSettingsActivity.KEY_WatermarkSize, 150);
			String watermarkLocation = pref.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");

			int top = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkTopMargin, "-1"));
			int bottom = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkBottomMargin, "-1"));
			int right = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkRightMargin, "-1"));
			int left = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkLeftMargin, "-1"));
			LibRaw.Margins margins = new LibRaw.Margins(top, left, bottom, right);

			Bitmap watermark;
			byte[] waterData = null;
			boolean processWatermark = false;
			int waterWidth = 0, waterHeight = 0;

			if (Constants.VariantCode < 11 || LicenseManager.getLastResponse() != License.LicenseState.pro)
			{
				processWatermark = true;
				// Just grab the first width and assume that will be sufficient for all images
				final int width = getContentResolver().query(Meta.Data.CONTENT_URI, null, Meta.Data.URI + "?", new String[] {totalImages.get(0).toString()}, null).getInt(Meta.WIDTH_COLUMN);
				watermark = ImageUtils.getDemoWatermark(CoreActivity.this, width);
				waterData = ImageUtils.getBitmapBytes(watermark);
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
					watermark = ImageUtils.getWatermarkText(watermarkText, watermarkAlpha, watermarkSize, watermarkLocation);
					waterData = ImageUtils.getBitmapBytes(watermark);
					waterWidth = watermark.getWidth();
					waterHeight = watermark.getHeight();
				}
			}

			//content://com.android.externalstorage.documents/tree/0000-0000%3A_WriteTest%2Ftest1%2Ftest2
			boolean success = true;
			for (Uri toExport : totalImages)
			{
				UsefulDocumentFile source = UsefulDocumentFile.fromUri(CoreActivity.this, toExport);
				UsefulDocumentFile destinationTree = null;
				try
				{
					destinationTree = getDocumentFile(destinationFolder, true, true);
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
				}

				if (destinationTree == null)
				{
					success = false;
					continue;
				}

				UsefulDocumentFile destinationFile = destinationTree.createFile(null, FileUtil.swapExtention(source.getName(), "jpg" ));

				publishProgress(source.getName());

				ParcelFileDescriptor outputPfd = null;
				ParcelFileDescriptor inputPfd = null;
				try
				{
					inputPfd = FileUtil.getParcelFileDescriptor(CoreActivity.this, source.getUri(), "r");
					outputPfd = FileUtil.getParcelFileDescriptor(CoreActivity.this, destinationFile.getUri(), "w");
					if (outputPfd == null)
					{
						success = false;
						continue;
					}

					if (processWatermark)
					{
						success = writeThumbWatermark(inputPfd, outputPfd, waterData, waterWidth, waterHeight, margins) && success;
					}
					else
					{
						success = writeThumb(inputPfd, outputPfd) && success;
					}
					onImageAdded(destinationFile.getUri());
				}
				catch(Exception e)
				{
					success = false;
				}
				finally
				{
					Utils.closeSilently(inputPfd);
					Utils.closeSilently(outputPfd);
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

	private boolean deleteAssociatedFiles(Uri image) throws WritePermissionException
	{
		Uri[] associatedFiles = ImageUtils.getAssociatedFiles(this, image);
		for (Uri file : associatedFiles)
			deleteFile(file);
		return deleteFile(image);
	}

	protected class DeleteTask extends AsyncTask<Object, Integer, Boolean>
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
		@SuppressWarnings("unchecked")
		protected Boolean doInBackground(final Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof Uri))
				throw new IllegalArgumentException();

			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission
			final List<Uri> totalDeletes = (List<Uri>) params[0];
			List<Uri> remainingDeletes = new ArrayList<>(totalDeletes);

			mProgressDialog.setMax(totalDeletes.size());
			final List<Uri> removed = new ArrayList<>();

			for (Uri toDelete : totalDeletes)
			{
				setWriteResume(WriteActions.DELETE, new Object[]{remainingDeletes});
				try
				{
					if (deleteAssociatedFiles(toDelete))
					{
						onImageRemoved(toDelete);
						removed.add(toDelete);
					}
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return false;
				}
				remainingDeletes.remove(toDelete);
			}

			return true;
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

	protected class RecycleTask extends AsyncTask<Object, Integer, Void> implements OnCancelListener
	{
		@Override
		protected void onPreExecute()
		{
			mProgressDialog.setTitle(R.string.recyclingFiles);
			mProgressDialog.show();
		}

		@Override
		@SuppressWarnings("unchecked")
		protected Void doInBackground(final Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof Uri))
				throw new IllegalArgumentException();

			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission
			final List<Uri> totalImages = (List<Uri>) params[0];
			List<Uri> remainingImages = new ArrayList<>(totalImages);

			mProgressDialog.setMax(remainingImages.size());

			for (Uri toRecycle : totalImages)
			{
				setWriteResume(WriteActions.RECYCLE, new Object[]{remainingImages});
				try
				{
					//TODO: Handle related files
					// Simply delete any related files
//					Uri[] associates = ImageUtils.getAssociatedFiles(CoreActivity.this, toRecycle);
//					for (Uri associate : associates)
//						deleteFile(associate);

					recycleBin.addFile(toRecycle);
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return null;
				}
				catch (IOException e)
				{
					Log.e(TAG, "Failed to add image to recycle bin: " + toRecycle.toString() + "/n" + e);
				}
				remainingImages.remove(toRecycle);
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

	protected class RestoreTask extends AsyncTask<Object, Integer, Boolean>
	{
		@Override
		@SuppressWarnings("unchecked")
		protected Boolean doInBackground(final Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof String))
				throw new IllegalArgumentException();

			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission
			final List<String> totalImages = (List<String>) params[0];
			List<String> remainingImages = new ArrayList<>(totalImages);

			for (String image : totalImages)
			{
				Uri toRestore = Uri.parse(image);
				try
				{
					setWriteResume(WriteActions.RESTORE, new Object[]{remainingImages});
					Uri uri = Uri.fromFile(recycleBin.getFile(image));
					moveFile(uri, toRestore);
					onImageAdded(toRestore);
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					return false;
				} catch (IOException e)
				{
					e.printStackTrace();
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

	public boolean renameImage(Uri source, String baseName, ArrayList<ContentProviderOperation> updates) throws IOException
	{
		Boolean imageSuccess = false;
		Boolean xmpSuccess = true;
		Boolean jpgSuccess = true;

		UsefulDocumentFile sourceFile = UsefulDocumentFile.fromUri(this, source);
		UsefulDocumentFile renameFile = getRenamedFile(sourceFile, baseName);
		imageSuccess = renameAssociatedFile(sourceFile, baseName);

		if (!imageSuccess)
			return imageSuccess;

		ContentValues imageValues = new ContentValues();
		imageValues.put(Meta.Data.NAME, renameFile.getName());
		imageValues.put(Meta.Data.URI, renameFile.getUri().toString());

		updates.add(
				ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
						.withSelection(Meta.Data.URI + "=?", new String[]{source.toString()})
						.withValues(imageValues)
						.build());

		if (ImageUtils.hasXmpFile(this, source))
		{
			UsefulDocumentFile xmpDoc = ImageUtils.getXmpFile(this, source);
			xmpSuccess = renameAssociatedFile(xmpDoc, baseName);
		}
		if (ImageUtils.hasJpgFile(this, source))
		{
			UsefulDocumentFile jpgDoc = ImageUtils.getJpgFile(this, source);
			UsefulDocumentFile renamedJpeg = getRenamedFile(jpgDoc, baseName);
			jpgSuccess = renameAssociatedFile(jpgDoc, baseName);

			if (jpgSuccess)
			{
				ContentValues jpgValues = new ContentValues();
				jpgValues.put(Meta.Data.NAME, renamedJpeg.getName());
				jpgValues.put(Meta.Data.URI, renamedJpeg.getUri().toString());

				updates.add(
						ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
								.withSelection(Meta.Data.URI + "=?", new String[]{jpgDoc.getUri().toString()})
								.withValues(jpgValues)
								.build());
			}
		}

		return imageSuccess && xmpSuccess && jpgSuccess;
	}

	public boolean renameAssociatedFile(UsefulDocumentFile original, String baseName)
			throws IOException
	{
		UsefulDocumentFile renameFile = getRenamedFile(original, baseName);
		return moveFile(original.getUri(), renameFile.getUri());
	}

	/**
	 * This will only work with hierarchical tree uris
	 * @param original
	 * @param baseName
     * @return
     */
	public UsefulDocumentFile getRenamedFile(UsefulDocumentFile original, String baseName)
	{
		String filename = original.getName();
		String ext = filename.substring(filename.lastIndexOf("."),
				filename.length());

		String rename = baseName + ext;
		String parent = original.getParentFile().getUri().toString();
		String renameUriString = parent + "/" + rename;
		return UsefulDocumentFile.fromUri(this, Uri.parse(renameUriString));
	}

	private static int numDigits(int x)
	{
		return (x < 10 ? 1 : (x < 100 ? 2 : (x < 1000 ? 3 : (x < 10000 ? 4 : (x < 100000 ? 5 : (x < 1000000 ? 6 : (x < 10000000 ? 7 : (x < 100000000 ? 8
				: (x < 1000000000 ? 9 : 10)))))))));
	}

	private static String formatRename(int format, String baseName, int index, int total)
	{
		final String sequencer = "%0" + numDigits(total) + "d";

		String rename = null;
		switch (format)
		{
			case 0:
				rename = baseName + "-" + String.format(sequencer, index);
				break;
			case 1:
				rename = baseName + " (" + String.format(sequencer, index) + " of " + total + ")";
				break;
		}

		return rename;
	}

	protected class RenameTask extends AsyncTask<Object, Integer, Boolean>
	{
		@Override
		@SuppressWarnings("unchecked")
		protected Boolean doInBackground(final Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof Uri))
				throw new IllegalArgumentException();

			final List<Uri> totalImages = (List<Uri>) params[0];
			List<Uri> remainingImages = new ArrayList<>(totalImages);

			final int format = (int) params[1];
			final String customName = (String) params[2];

			int counter = 0;
			final int total = totalImages.size();
			final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

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

					String rename = formatRename(format, customName, counter, total);

					renameImage(image, rename, operations);

					if (renameImage(image, rename, operations))
					{
						remainingImages.remove(image);
					}
				}
			} catch (WritePermissionException e)
			{
				e.printStackTrace();
				return false;
			} catch (IOException e)
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
		@SuppressWarnings("unchecked")
		protected Boolean doInBackground(final Object... params)
		{
			if (!(params[0] instanceof  List<?>) || !(((List<?>) params[0]).get(0) instanceof Uri))
				throw new IllegalArgumentException();

			final List<Uri> totalImages = (List<Uri>) params[0];
			List<Uri> remainingImages = new ArrayList<>(totalImages);

			final XmpEditFragment.XmpEditValues values = (XmpEditFragment.XmpEditValues) params[1];
			final ArrayList<ContentProviderOperation> databaseUpdates = new ArrayList<>();

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

				final Uri xmpUri = ImageUtils.getXmpFile(CoreActivity.this, image).getUri();
				final UsefulDocumentFile xmpDoc;
				try
				{
					xmpDoc = getDocumentFile(xmpUri, false, false); // For now use getDocumentFile to leverage write testing
					setWriteResume(WriteActions.WRITE_XMP, new Object[]{remainingImages});
					//TODO: need DocumentActivity.openOutputStream
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
