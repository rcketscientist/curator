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
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentTransaction;
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
import com.anthonymandra.util.DbUtil;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtils;
import com.crashlytics.android.Crashlytics;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpWriter;
import com.inscription.ChangeLogDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class CoreActivity extends DocumentActivity
{
	private enum WriteActions
	{
		COPY,
		COPY_THUMB,
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

	protected DocumentRecycleBin recycleBin;
	protected File mSwapDir;

	protected ProgressDialog mProgressDialog;

	protected ShareActionProvider mShareProvider;
	protected Intent mShareIntent;

	protected XmpEditFragment mXmpFragment;

	protected boolean mActivityVisible;

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
		setContentView(getContentView());
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
		mActivityVisible = true;
		LicenseManager.getLicense(this, getLicenseHandler());
		createSwapDir();
		createRecycleBin();
	}

	protected abstract LicenseHandler getLicenseHandler();

	/**
	 * Subclasses must define the layout id here.  It will be loaded in {@link #onCreate}.
	 * The layout should conform to viewer template (xmp, meta, histogram, etc).
	 * @return The resource id of the layout to load
	 */
	public abstract int getContentView();

	@Override
	protected void onPause()
	{
		super.onPause();
		mActivityVisible = false;
		if (recycleBin != null)
			recycleBin.flushCache();
	}

	@Override
	public void onBackPressed()
	{
		if (!mXmpFragment.isHidden())
		{
			toggleEditXmpFragment();
			return;
		}
		super.onBackPressed();
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
			case R.id.toggleXmp:
				toggleEditXmpFragment();
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

	@Override
	protected void onPostCreate(@Nullable Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		mXmpFragment = (XmpEditFragment) getSupportFragmentManager().findFragmentById(R.id.editFragment);
		mXmpFragment.setListener(new XmpEditFragment.MetaChangedListener()
		{
			@Override
			public void onMetaChanged(Integer rating, String label, String[] subject)
			{
				XmpEditFragment.XmpEditValues values = new XmpEditFragment.XmpEditValues();
				values.Label = label;
				values.Subject = subject;
				values.Rating = rating;
				writeXmpModifications(values);
			}
		});
		mXmpFragment.setLabelListener(new XmpEditFragment.LabelChangedListener()
		{
			@Override
			public void onLabelChanged(String label)
			{
				XmpEditFragment.XmpEditValues values = new XmpEditFragment.XmpEditValues();
				values.Label = label;

				new Thread(new PrepareXmpRunnable(values, XmpUpdateField.Label)).start();
			}
		});
		mXmpFragment.setRatingListener(new XmpEditFragment.RatingChangedListener()
		{
			@Override
			public void onRatingChanged(Integer rating)
			{
				XmpEditFragment.XmpEditValues values = new XmpEditFragment.XmpEditValues();
				values.Rating = rating;

				new Thread(new PrepareXmpRunnable(values, XmpUpdateField.Rating)).start();
			}
		});
		mXmpFragment.setSubjectListener(new XmpEditFragment.SubjectChangedListener()
		{

			@Override
			public void onSubjectChanged(String[] subject)
			{
				XmpEditFragment.XmpEditValues values = new XmpEditFragment.XmpEditValues();
				values.Subject = subject;

				new Thread(new PrepareXmpRunnable(values, XmpUpdateField.Subject)).start();
			}
		});
		toggleEditXmpFragment(); // Keep fragment visible in designer, but hide initially
	}

	protected void toggleEditXmpFragment()
	{
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		if (mXmpFragment.isHidden())
		{
			ft.show(mXmpFragment);
			ft.setCustomAnimations(android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left);
		}
		else
		{
			ft.hide(mXmpFragment);
			ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
		}
		ft.commit();
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

	protected void writeXmpModifications(XmpEditFragment.XmpEditValues values)
	{
		List<Uri> selection = getSelectedImages();
		if (selection != null)
		{
			ContentValues cv = new ContentValues();
			cv.put(Meta.Data.LABEL, values.Label);
			cv.put(Meta.Data.RATING, values.Rating);
			cv.put(Meta.Data.SUBJECT, ImageUtils.convertArrayToString(values.Subject));

			Map<Uri, ContentValues> xmpPairing = new HashMap<>();
			for (Uri uri : selection)
			{
				xmpPairing.put(uri, cv);
			}

			writeXmp(xmpPairing);
		}
	}

	protected void writeXmp(Map<Uri, ContentValues> xmpPairing)
	{
		new WriteXmpTask().execute(xmpPairing);
	}

	/**
	 * Create swap directory or clear the contents
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
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
			@SuppressWarnings("ResultOfMethodCallIgnored")
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

		String body =
				"Variant:   " + BuildConfig.FLAVOR + "\n" +
				"Version:   " + BuildConfig.VERSION_NAME + "\n" +
				"Make:      " + Build.MANUFACTURER + "\n" +
				"Model:     " + Build.MODEL + "\n" +
				"ABI:       " + Arrays.toString(Build.SUPPORTED_ABIS) + "\n" +
				"Android:   " + Build.DISPLAY + "\n" +
				"SDK:       " + Build.VERSION.SDK_INT + "\n\n" +
				"---Please don't remove this data---" + "\n\n";

		emailIntent.putExtra(Intent.EXTRA_TEXT, body);
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

	/**
	 * Returns any selected images
	 */
	protected abstract List<Uri> getSelectedImages();

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
	@SuppressWarnings("UnusedReturnValue")
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

			ArrayList<ContentProviderOperation> dbInserts = new ArrayList<>();
			for (Uri toCopy : totalImages)
			{
				try
				{
					setWriteResume(WriteActions.COPY, new Object[]{remainingImages});

					UsefulDocumentFile source = UsefulDocumentFile.fromUri(CoreActivity.this, toCopy);
					Uri destinationFile = FileUtil.getChildUri(destinationFolder, source.getName());
					copyAssociatedFiles(toCopy, destinationFile);
					dbInserts.add(ImageUtils.newInsert(CoreActivity.this, destinationFile));
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
			ImageUtils.updateMetaDatabase(CoreActivity.this, dbInserts);
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

	protected boolean writeThumb(ParcelFileDescriptor source, ParcelFileDescriptor destination)
	{
		return LibRaw.writeThumbFd(source.getFd(), 100, Bitmap.Config.ARGB_8888, Bitmap.CompressFormat.JPEG, destination.getFd());
	}

	protected boolean writeThumbWatermark(ParcelFileDescriptor source, ParcelFileDescriptor destination, byte[] waterMap,
	                                      int waterWidth, int waterHeight, LibRaw.Margins waterMargins) {
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

			boolean success = true;
			ArrayList<ContentProviderOperation> dbInserts = new ArrayList<>();
			for (Uri toThumb : totalImages)
			{
				setWriteResume(WriteActions.COPY_THUMB, new Object[] { remainingImages } );
				UsefulDocumentFile source = UsefulDocumentFile.fromUri(CoreActivity.this, toThumb);
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
					dbInserts.add(ImageUtils.newInsert(CoreActivity.this, destinationFile.getUri()));
					remainingImages.remove(toThumb);
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
			ImageUtils.updateMetaDatabase(CoreActivity.this, dbInserts);
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

			ArrayList<ContentProviderOperation> dbDeletes = new ArrayList<>();
			boolean totalSuccess = true;
			for (Uri toDelete : totalDeletes)
			{
				setWriteResume(WriteActions.DELETE, new Object[]{remainingDeletes});
				try
				{
					if (deleteAssociatedFiles(toDelete))
					{
						onImageRemoved(toDelete);
						dbDeletes.add(ImageUtils.newDelete(toDelete));
					}
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					totalSuccess = false;
				}
				remainingDeletes.remove(toDelete);
			}

			ImageUtils.updateMetaDatabase(CoreActivity.this, dbDeletes);
			return totalSuccess;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
			else
				Snackbar.make(findViewById(android.R.id.content), R.string.deleteFail, Snackbar.LENGTH_LONG).show();

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

			ArrayList<ContentProviderOperation> dbDeletes = new ArrayList<>();
			for (Uri toRecycle : totalImages)
			{
				setWriteResume(WriteActions.RECYCLE, new Object[]{remainingImages});
				try
				{
					//TODO: Handle related files
					recycleBin.addFile(toRecycle);
					dbDeletes.add(ImageUtils.newDelete(toRecycle));
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

			ImageUtils.updateMetaDatabase(CoreActivity.this, dbDeletes);
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

			boolean totalSuccess = true;
			ArrayList<ContentProviderOperation> dbInserts = new ArrayList<>();
			for (String image : totalImages)
			{
				Uri toRestore = Uri.parse(image);
				try
				{
					setWriteResume(WriteActions.RESTORE, new Object[]{remainingImages});
					Uri uri = Uri.fromFile(recycleBin.getFile(image));
					moveFile(uri, toRestore);
					onImageAdded(toRestore);
					dbInserts.add(ImageUtils.newInsert(CoreActivity.this, toRestore));
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					totalSuccess = false;
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}

			ImageUtils.updateMetaDatabase(CoreActivity.this, dbInserts);
			return totalSuccess;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
			else
				Snackbar.make(findViewById(android.R.id.content), R.string.restoreFail, Snackbar.LENGTH_LONG).show();
			onImageSetChanged();
		}
	}

	public boolean renameImage(Uri source, String baseName, ArrayList<ContentProviderOperation> updates) throws IOException
	{
		Boolean xmpSuccess = true;
		Boolean jpgSuccess = true;

		UsefulDocumentFile sourceFile = UsefulDocumentFile.fromUri(this, source);
		UsefulDocumentFile renameFile = getRenamedFile(sourceFile, baseName);

		if (!renameAssociatedFile(sourceFile, baseName))
			return false;

		ContentValues imageValues = new ContentValues();
		imageValues.put(Meta.Data.NAME, renameFile.getName());
		imageValues.put(Meta.Data.URI, renameFile.getUri().toString());

		updates.add(
				ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
						.withSelection(ImageUtils.getWhere(), new String[]{source.toString()})
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
								.withSelection(ImageUtils.getWhere(), new String[]{jpgDoc.getUri().toString()})
								.withValues(jpgValues)
								.build());
			}
		}

		return xmpSuccess && jpgSuccess;
	}

	public boolean renameAssociatedFile(UsefulDocumentFile original, String baseName)
			throws IOException
	{
		UsefulDocumentFile renameFile = getRenamedFile(original, baseName);
		return moveFile(original.getUri(), renameFile.getUri());
	}

	/**
	 * This will only work with hierarchical tree uris
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

			ImageUtils.updateMetaDatabase(CoreActivity.this, operations);
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
			final Map<Uri, ContentValues> xmpPairing = (Map<Uri, ContentValues>) params[0];
			final ArrayList<ContentProviderOperation> databaseUpdates = new ArrayList<>();

			Iterator uris = xmpPairing.entrySet().iterator();
			while(uris.hasNext())
			{
				setWriteResume(WriteActions.WRITE_XMP, new Object[]{xmpPairing});

				Map.Entry<Uri, ContentValues> pair = (Map.Entry) uris.next();
				ContentValues values = pair.getValue();
				databaseUpdates.add(ImageUtils.newUpdate(pair.getKey(), values));

				final Metadata meta = new Metadata();
				meta.addDirectory(new XmpDirectory());
				ImageUtils.updateSubject(meta, ImageUtils.convertStringToArray(values.getAsString(Meta.Data.SUBJECT)));
				ImageUtils.updateRating(meta, values.getAsInteger(Meta.Data.RATING));
				ImageUtils.updateLabel(meta, values.getAsString(Meta.Data.LABEL));

				final Uri xmpUri = ImageUtils.getXmpFile(CoreActivity.this, pair.getKey()).getUri();
				final UsefulDocumentFile xmpDoc;
				try
				{
					xmpDoc = getDocumentFile(xmpUri, false, false); // For now use getDocumentFile to leverage write testing
					//TODO: need DocumentActivity.openOutputStream
				}
				catch (WritePermissionException e)
				{
					// Write pending updates, method will resume with remaining images
					ImageUtils.updateMetaDatabase(CoreActivity.this, databaseUpdates);
					return false;
				}

				try(OutputStream os = getContentResolver().openOutputStream(xmpDoc.getUri()))
				{
					if (meta.containsDirectoryOfType(XmpDirectory.class))
						XmpWriter.write(os, meta);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}

				uris.remove();  //concurrency-safe remove
			}

			ImageUtils.updateMetaDatabase(CoreActivity.this, databaseUpdates);
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
		}
	}

	enum XmpUpdateField
	{
		Rating,
		Label,
		Subject
	}

	protected class PrepareXmpRunnable implements Runnable
	{
		private final List<Uri> selectedImages;
		private final XmpEditFragment.XmpEditValues update;
		private final XmpUpdateField updateType;
		private final String[] projection = new String[] { Meta.Data.URI, Meta.Data.RATING, Meta.Data.LABEL, Meta.Data.SUBJECT };

		public PrepareXmpRunnable(XmpEditFragment.XmpEditValues update, XmpUpdateField updateType)
		{
			this.selectedImages = getSelectedImages();
			this.update = update;
			this.updateType = updateType;
		}

		@Override
		public void run()
		{
			String[] selectionArgs = new String[selectedImages.size()];
			for (int i = 0; i < selectedImages.size(); i++)
			{
				selectionArgs[i] = selectedImages.get(i).toString();
			}

			// Grab existing metadata
			Cursor c = getContentResolver().query(Meta.Data.CONTENT_URI,
					projection,
					DbUtil.createMultipleIN(Meta.Data.URI, selectedImages.size()),
					selectionArgs,
					null);

			if (c == null)
				return;

			// Create mappings with existing values
			Map<Uri, ContentValues> xmpPairs = new HashMap<>();
			while (c.moveToNext())
			{
				ContentValues cv = new ContentValues(projection.length);
				DatabaseUtils.cursorRowToContentValues(c, cv);
				xmpPairs.put(Uri.parse(cv.getAsString(Meta.Data.URI)), cv);
			}

			// Update singular fields in the existing values
			for (Map.Entry<Uri, ContentValues> xmpPair : xmpPairs.entrySet())
			{
				switch(updateType)
				{
					case Label:
						xmpPair.getValue().put(Meta.Data.LABEL, update.Label);
						break;
					case Rating:
						xmpPair.getValue().put(Meta.Data.RATING, update.Rating);
						break;
					case Subject:
						xmpPair.getValue().put(Meta.Data.SUBJECT, ImageUtils.convertArrayToString(update.Subject));
						break;
				}
			}
			writeXmp(xmpPairs);
		}
	}
}
