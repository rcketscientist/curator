package com.anthonymandra.framework;

import android.annotation.SuppressLint;
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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.adobe.xmp.XMPMeta;
import com.anthonymandra.content.Meta;
import com.anthonymandra.image.ImageConfiguration;
import com.anthonymandra.image.JpegConfiguration;
import com.anthonymandra.image.TiffConfiguration;
import com.anthonymandra.imageprocessor.ImageProcessor;
import com.anthonymandra.imageprocessor.Watermark;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.rawdroid.Constants;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.GalleryActivity;
import com.anthonymandra.rawdroid.LicenseManager;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.XmpEditFragment;
import com.anthonymandra.util.DbUtil;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtil;
import com.anthonymandra.util.MetaUtil;
import com.crashlytics.android.Crashlytics;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpWriter;
import com.inscription.ChangeLogDialog;

import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public abstract class CoreActivity extends DocumentActivity
{
	private enum WriteActions
	{
		COPY,
		COPY_THUMB,
		DELETE,
		SAVE_IMAGE,
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

	protected XmpEditFragment mXmpFragment;

	protected boolean mActivityVisible;

	/**
	 * Stores uris when lifecycle is interrupted (ie: requesting a destination folder)
	 */
	protected List<Uri> mItemsForIntent = new ArrayList<>();

	private static final int REQUEST_SAVE_AS_DIR = 15;

	// Identifies a particular Loader being used in this component
	public static final int META_LOADER_ID = 0;

	public static final String EXTRA_META_BUNDLE = "meta_bundle";
	public static final String META_PROJECTION_KEY = "projection";
	public static final String META_SELECTION_KEY = "selection";
	public static final String META_SELECTION_ARGS_KEY = "selection_args";
	public static final String META_SORT_ORDER_KEY = "sort_order";
	public static final String META_DEFAULT_SORT = Meta.NAME + " ASC";

	private static final long EXPIRATION = 4629746000L; //~60 days

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(getContentView());
		setStoragePermissionRequestEnabled(true);
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

		if ("beta".equals(BuildConfig.FLAVOR_cycle) &&
			BuildConfig.BUILD_TIME + EXPIRATION < System.currentTimeMillis())
		{
			Toast.makeText(this, "Beta has expired.", Toast.LENGTH_LONG).show();
			//TODO: Add link to Curator store page
			finish();
		}

		//TODO: Temporarily convert pref from string to int
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		try
		{
			// This will throw if it's not a string.
			String binSize = prefs.getString(FullSettingsActivity.KEY_RecycleBinSize, "n/a");
			// noinspection AndroidLintApplySharedPref
			prefs.edit().putInt(FullSettingsActivity.KEY_RecycleBinSize, Integer.parseInt(binSize)).commit();
		}
		catch (Exception ignored){}

		PreferenceManager.setDefaultValues(this, R.xml.preferences_metadata, false);
		PreferenceManager.setDefaultValues(this, R.xml.preferences_storage, false);
		PreferenceManager.setDefaultValues(this, R.xml.preferences_view, false);
		PreferenceManager.setDefaultValues(this, R.xml.preferences_license, false);
		PreferenceManager.setDefaultValues(this, R.xml.preferences_watermark, false);

		findViewById(R.id.xmpSidebarButton).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				toggleEditXmpFragment();
			}
		});

		int i = GalleryActivity.TEST;
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

	/**
	 * @return The root view for this activity.
	 */
	public View getRootView()
	{
		return findViewById(android.R.id.content);
	}

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
			hideEditXmpFragment();
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
			case R.id.contextSaveAs:
				storeSelectionForIntent();
				requestSaveAsDestination();
				return true;
			case R.id.menu_delete:
				deleteImages(getSelectedImages());
				return true;
			case R.id.menu_recycle:
				showRecycleBin();
				return true;
			case R.id.settings:
				requestSettings();
				return true;
			case R.id.menu_share:
				requestShare();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void storeSelectionForIntent()
	{
		mItemsForIntent = getSelectedImages();
	}

	@Override
	public void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_SAVE_AS_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
					handleSaveDestinationResult(data.getData());
				}
				break;
		}
	}

	private void handleSaveDestinationResult(final Uri destination)
	{
//		storeSelectionForIntent();	// dialog resets CAB, so store first

		// TODO: Might want to figure out a way to get free space to introduce this check again
//        long importSize = getSelectedImageSize();
//        if (destination.getFreeSpace() < importSize)
//        {
//            Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
//            return;
//        }

		// Load default save config if it exists and automatically apply it
		ImageConfiguration config = ImageConfiguration.loadPreference(CoreActivity.this);
		if (config != null)
		{
			saveImage(mItemsForIntent, destination, config);
			return;
		}

		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.save_dialog);
		dialog.setTitle(R.string.saveAs);

		final TabHost tabs = (TabHost) dialog.findViewById(R.id.tabHost);
		tabs.setup();

		TabHost.TabSpec jpg = tabs.newTabSpec("JPG");
		TabHost.TabSpec tif = tabs.newTabSpec("TIF");

		jpg.setContent(R.id.JPG);
		jpg.setIndicator("JPG");
		tabs.addTab(jpg);

		tif.setContent(R.id.TIFF);
		tif.setIndicator("TIFF");
		tabs.addTab(tif);

		final TextView qualityText = (TextView) dialog.findViewById(R.id.valueQuality);
		final SeekBar qualityBar = (SeekBar) dialog.findViewById(R.id.seekBarQuality);
		final Switch compressSwitch = (Switch) dialog.findViewById(R.id.switchCompress);

		final CheckBox setDefault = (CheckBox) dialog.findViewById(R.id.checkBoxSetDefault);
		setDefault.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
				{
					Snackbar.make(dialog.getCurrentFocus(),
						Html.fromHtml(
							getResources().getString(R.string.saveDefaultConfirm) + "  "
							+ "<i>" + getResources().getString(R.string.settingsReset) + "</i>"),
						Snackbar.LENGTH_LONG)
						.show();
				}
			}
		});

		qualityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				qualityText.setText(String.valueOf(progress));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});

		Button save = (Button) dialog.findViewById(R.id.buttonSave);
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ImageConfiguration config = new JpegConfiguration();
				switch(tabs.getCurrentTab())
				{
					case 0: //JPG
						config = new JpegConfiguration();
						((JpegConfiguration)config).setQuality(qualityBar.getProgress());
						break;
					case 1: //TIF
						config = new TiffConfiguration();
						((TiffConfiguration)config).setCompress(compressSwitch.isChecked());
						break;
				}
				dialog.dismiss();

				if (setDefault.isChecked())
					config.savePreference(CoreActivity.this);

				saveImage(mItemsForIntent, destination, config);
			}
		});
		Button cancel = (Button) dialog.findViewById(R.id.buttonCancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		dialog.show();
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
					if (callingParameters != null)
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
		hideEditXmpFragment();
	}

	protected void toggleEditXmpFragment()
	{
		if (mXmpFragment.isHidden())
		{
			showEditXmpFragment();
		}
		else
		{
			hideEditXmpFragment();
		}
	}

	protected void hideEditXmpFragment()
	{
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.hide(mXmpFragment);
		ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
		ft.commit();
	}

	protected void showEditXmpFragment()
	{
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.show(mXmpFragment);
		ft.setCustomAnimations(android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left);
		ft.commit();
	}

	protected void writeXmpModifications(XmpEditFragment.XmpEditValues values)
	{
		List<Uri> selection = getSelectedImages();
		if (selection != null)
		{
			ContentValues cv = new ContentValues();
			cv.put(Meta.LABEL, values.Label);
			cv.put(Meta.RATING, values.Rating);
			cv.put(Meta.SUBJECT, DbUtil.convertArrayToString(values.Subject));

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
			binSizeMb = PreferenceManager.getDefaultSharedPreferences(this).getInt(
					FullSettingsActivity.KEY_RecycleBinSize,
					FullSettingsActivity.defRecycleBin);
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
					if (!filesToRestore.isEmpty())
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
	protected void deleteImages(@NonNull final List<Uri> itemsToDelete)
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
			if (toDelete == null)
				continue;

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

	private void requestSaveAsDestination()
	{
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_SAVE_AS_DIR);
	}

	protected void showRenameDialog(final List<Uri> itemsToRename)
	{
		@SuppressLint("InflateParams")
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

			@SuppressLint("SetTextI18n")
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
			@SuppressLint("SetTextI18n")
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
	protected abstract ArrayList<Uri> getSelectedImages();

	protected void requestShare()
	{
		if (getSelectedImages().size() < 1)
		{
			Snackbar.make(getRootView(), R.string.warningNoItemsSelected, Snackbar.LENGTH_SHORT).show();
			return;
		}

		String format = PreferenceManager.getDefaultSharedPreferences(this).getString(
				FullSettingsActivity.KEY_ShareFormat,
				getResources().getStringArray(R.array.shareFormats)[0]);

		Intent intent = new Intent();
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

		boolean convert = true;
		if ("JPG".equals(format))
		{
			intent.setType("image/jpeg");
			convert = true;
		}
		else if ("RAW".equals(format))
		{
			intent.setType("image/*");
			convert = false;
		}

		ArrayList<Uri> selectedItems = getSelectedImages();
		if (selectedItems.size() > 1)
		{
			intent.setAction(Intent.ACTION_SEND_MULTIPLE);
			boolean tooManyShares = selectedItems.size() > 10;

			if (tooManyShares)
			{
				Toast shareLimit = Toast.makeText(this, R.string.shareSubset, Toast.LENGTH_LONG);
				shareLimit.setGravity(Gravity.CENTER, 0, 0);
				shareLimit.show();
			}

			ArrayList<Uri> share = new ArrayList<>();
			// We need to limit the number of shares to avoid TransactionTooLargeException
			for (Uri selection : tooManyShares ? selectedItems.subList(0, 10) : selectedItems)
			{
				if (convert)
					share.add(SwapProvider.createSwapUri(this, selection));
				else
					share.add(selection);
			}
			intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, share);
		}
		else
		{
			intent.setAction(Intent.ACTION_SEND);
			if (convert)
				intent.putExtra(Intent.EXTRA_STREAM, SwapProvider.createSwapUri(this, selectedItems.get(0)));
			else
				intent.putExtra(Intent.EXTRA_STREAM, selectedItems.get(0));
		}

		startActivity(Intent.createChooser(intent, getString(R.string.share)));
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

	protected abstract void setMaxProgress(int max);
	protected abstract void incrementProgress();
	protected abstract void endProgress();
	protected abstract void updateMessage(String message);

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
		if (ImageUtil.hasXmpFile(this, fromImage))
		{
			copyFile(ImageUtil.getXmpFile(this, fromImage).getUri(),
					ImageUtil.getXmpFile(this, toImage).getUri());
		}
		if (ImageUtil.hasJpgFile(this, fromImage))
		{
			copyFile(ImageUtil.getJpgFile(this, fromImage).getUri(),
					ImageUtil.getJpgFile(this, toImage).getUri());
		}
		return copyFile(fromImage, toImage);
	}

	public void copyImages(final List<Uri> images, final Uri destination) {
		setMaxProgress(images.size());
		updateMessage("Copying...");
		Observable.fromIterable(images)
				.flatMap(image -> copyImage(image, destination)
						.subscribeOn(Schedulers.io()), 10)  // concurrency must be on inner
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<Uri>()
				{
					@Override
					public void onSubscribe(Disposable d) {}

					@Override
					public void onNext(Uri uri)
					{
						incrementProgress();
					}

					@Override
					public void onError(Throwable e)
					{
						//TODO
					}

					@Override
					public void onComplete()
					{
						endProgress();
						updateMessage(null);
					}
				});
	}

	private Observable<Uri> copyImage(final Uri uri, final Uri destinationFolder) {
		return Observable.fromCallable(() ->
		{
			UsefulDocumentFile source = UsefulDocumentFile.fromUri(CoreActivity.this, uri);
			Uri destinationFile = DocumentUtil.getChildUri(destinationFolder, source.getName());
			copyAssociatedFiles(uri, destinationFile);
			ContentValues cv = new ContentValues();
			MetaUtil.getImageFileInfo(this, uri, cv);
			getContentResolver().insert(Meta.CONTENT_URI, cv);
			onImageAdded(uri);
			return destinationFile;
		});
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
					Uri destinationFile = DocumentUtil.getChildUri(destinationFolder, source.getName());
					copyAssociatedFiles(toCopy, destinationFile);
					dbInserts.add(MetaUtil.newInsert(CoreActivity.this, destinationFile));
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return false;
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				catch (RuntimeException e)
				{
					e.printStackTrace();
					Crashlytics.setString("uri", toCopy.toString());
					Crashlytics.logException(e);
				}
				remainingImages.remove(toCopy);
				onImageAdded(toCopy);
				publishProgress();
			}
			MetaUtil.updateMetaDatabase(CoreActivity.this, dbInserts);
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (!CoreActivity.this.isDestroyed() && mProgressDialog != null)
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

	protected void saveImage(List<Uri> images, Uri destination, ImageConfiguration config)
	{
		if (images.size() < 0)
			return;

		// Just grab the first width and assume that will be sufficient for all images
		new SaveImageTask().execute(images, destination, ImageUtil.getWatermark(this, images.get(0)), config);
	}

	public class SaveImageTask extends AsyncTask<Object, String, Boolean> implements OnCancelListener
	{
		final ProgressDialog progressDialog = new ProgressDialog(CoreActivity.this);
		final ArrayList<ContentProviderOperation> dbInserts = new ArrayList<>();

		public SaveImageTask()
		{	}

		@Override
		protected void onPreExecute()
		{
			progressDialog.setTitle(getString(R.string.extractingImage));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setCanceledOnTouchOutside(true);
			progressDialog.setOnCancelListener(this);
			progressDialog.show();
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
			Watermark wm = (Watermark) params[2];
			ImageConfiguration config = (ImageConfiguration) params[3];

			progressDialog.setMax(totalImages.size());

			boolean success = true;
			for (Uri toThumb : totalImages)
			{
				setWriteResume(WriteActions.SAVE_IMAGE, new Object[] { remainingImages, destinationFolder, wm, config } );
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

				String desiredName = FileUtil.swapExtention(source.getName(), config.getExtension() );
				publishProgress(desiredName);
				Uri desiredUri = DocumentUtil.getChildUri(destinationFolder, desiredName);
				UsefulDocumentFile destinationFile = UsefulDocumentFile.fromUri(CoreActivity.this, desiredUri);

				if (!destinationFile.exists())
					destinationFile = destinationTree.createFile(null, desiredName);

				try(	ParcelFileDescriptor inputPfd = FileUtil.getParcelFileDescriptor(CoreActivity.this, source.getUri(), "r");
						ParcelFileDescriptor outputPfd = FileUtil.getParcelFileDescriptor(CoreActivity.this, destinationFile.getUri(), "w"))
				{
					if (outputPfd == null)
					{
						success = false;
						continue;
					}

					switch(config.getType())
					{
						case jpeg:
							int quality = ((JpegConfiguration)config).getQuality();
							if (wm != null)
							{
								success = ImageProcessor.writeThumb(inputPfd.getFd(), quality,
										outputPfd.getFd(), wm.getWatermark(), wm.getMargins().getArray(),
										wm.getWaterWidth(), wm.getWaterHeight()) && success;
							}
							else
							{
								success = ImageProcessor.writeThumb(inputPfd.getFd(), quality, outputPfd.getFd()) && success;
							}
							break;
						case tiff:
							boolean compress = ((TiffConfiguration)config).getCompress();
							if (wm != null)
							{
								success = ImageProcessor.writeTiff(desiredName, inputPfd.getFd(),
										outputPfd.getFd(), compress, wm.getWatermark(), wm.getMargins().getArray(),
										wm.getWaterWidth(), wm.getWaterHeight()) && success;
							}
							else
							{
								success = ImageProcessor.writeTiff(desiredName, inputPfd.getFd(),
										outputPfd.getFd(), compress);
							}
							break;
						default: throw new UnsupportedOperationException("unimplemented save type.");
					}

					onImageAdded(destinationFile.getUri());
					dbInserts.add(MetaUtil.newInsert(CoreActivity.this, destinationFile.getUri()));
					remainingImages.remove(toThumb);
				}
				catch(Exception e)
				{
					success = false;
				}

				publishProgress();
			}

			return success; //not used.
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			new AlertDialog.Builder(CoreActivity.this)
					.setMessage("Add converted images to the library?")
					.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							MetaUtil.updateMetaDatabase(CoreActivity.this, dbInserts);
						}
					})
					.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{ /*dismiss*/ }
					}).show();

			onImageSetChanged();

			if (!CoreActivity.this.isDestroyed() && progressDialog != null)
				progressDialog.dismiss();
		}

		@Override
		protected void onProgressUpdate(String... values)
		{
			if (values.length > 0)
			{
				progressDialog.setMessage(values[0]);
			}
			else
			{
				progressDialog.incrementProgressBy(1);
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
		Uri[] associatedFiles = ImageUtil.getAssociatedFiles(this, image);
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
						dbDeletes.add(MetaUtil.newDelete(toDelete));
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

			MetaUtil.updateMetaDatabase(CoreActivity.this, dbDeletes);
			return totalSuccess;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
			else
				Snackbar.make(getRootView(), R.string.deleteFail, Snackbar.LENGTH_LONG).show();

			if (!CoreActivity.this.isDestroyed() && mProgressDialog != null)
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
					dbDeletes.add(MetaUtil.newDelete(toRecycle));
				}
				catch (WritePermissionException e)
				{
					e.printStackTrace();
					// We'll be automatically requesting write permission so kill this process
					return null;
				}
				catch (Exception e)
				{
					Crashlytics.setString("uri", toRecycle.toString());
					Crashlytics.log(e.toString());
				}
				remainingImages.remove(toRecycle);
				onImageRemoved(toRecycle);
			}

			MetaUtil.updateMetaDatabase(CoreActivity.this, dbDeletes);
			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			if (!CoreActivity.this.isDestroyed() && mProgressDialog != null)
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
			if (params.length == 0)
				return false;
			if (!(params[0] instanceof List<?>))
				throw new IllegalArgumentException();

			final List<String> totalImages = (List<String>) params[0];
			if (totalImages.isEmpty())
				return false;

			// Create a copy to keep track of completed deletions in case this needs to be restarted
			// to request write permission

			List<String> remainingImages = new ArrayList<>(totalImages);

			boolean totalSuccess = true;
			ArrayList<ContentProviderOperation> dbInserts = new ArrayList<>();
			for (String image : totalImages)
			{
				Uri toRestore = Uri.parse(image);
				try
				{
					setWriteResume(WriteActions.RESTORE, new Object[]{remainingImages});
					File recycledFile = recycleBin.getFile(image);
					if (recycledFile == null)
						continue;

					Uri uri = Uri.fromFile(recycledFile);
					moveFile(uri, toRestore);
					onImageAdded(toRestore);
					dbInserts.add(MetaUtil.newInsert(CoreActivity.this, toRestore));
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

			MetaUtil.updateMetaDatabase(CoreActivity.this, dbInserts);
			return totalSuccess;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (result)
				clearWriteResume();
			else
				Snackbar.make(getRootView(), R.string.restoreFail, Snackbar.LENGTH_LONG).show();
			onImageSetChanged();
		}
	}

	public boolean renameImage(@NonNull Uri source, String baseName, ArrayList<ContentProviderOperation> updates) throws IOException
	{
		UsefulDocumentFile srcFile = UsefulDocumentFile.fromUri(this, source);
		UsefulDocumentFile xmpFile = ImageUtil.getXmpFile(this, source);
		UsefulDocumentFile jpgFile = ImageUtil.getJpgFile(this, source);

		final String filename = srcFile.getName();
		final String sourceExt = filename.substring(filename.lastIndexOf("."), filename.length());

		final String srcRename = baseName + sourceExt;
		final String xmpRename = baseName + ".xmp";
		final String jpgRename = baseName + ".jpg";

		// Do src first in case it's a jpg
		if (srcFile.renameTo(srcRename))
		{
			Uri rename = DocumentUtil.getNeighborUri(source, srcRename);
			if (rename == null)
			{
				UsefulDocumentFile parent = srcFile.getParentFile();
				if (parent == null)
					return false;
				UsefulDocumentFile file = parent.findFile(srcRename);
				if (file == null)
					return false;
				rename = file.getUri();
			}
			ContentValues imageValues = new ContentValues();
			imageValues.put(Meta.NAME, srcRename);
			imageValues.put(Meta.URI, rename.toString());

			updates.add(
					ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
							.withSelection(Meta.URI_SELECTION, new String[]{source.toString()})
							.withValues(imageValues)
							.build());
		}
		else
			return false;

		xmpFile.renameTo(xmpRename);

		if (jpgFile.renameTo(jpgRename))
		{
			Uri rename = DocumentUtil.getNeighborUri(source, srcRename);
			if (rename == null)
			{
				UsefulDocumentFile parent = srcFile.getParentFile();
				if (parent == null)
					return false;
				UsefulDocumentFile file = parent.findFile(jpgRename);
				if (file == null)
					return false;
				rename = file.getUri();
			}

			ContentValues jpgValues = new ContentValues();
			jpgValues.put(Meta.NAME, jpgRename);
			jpgValues.put(Meta.URI, rename.toString());

			updates.add(
					ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
							.withSelection(Meta.URI_SELECTION, new String[]{jpgFile.getUri().toString()})
							.withValues(jpgValues)
							.build());
		}

		return true;
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

			MetaUtil.updateMetaDatabase(CoreActivity.this, operations);
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
				if (pair.getKey() == null)  //AJM: Not sure how this can be null, #167
					continue;

				ContentValues values = pair.getValue();
				databaseUpdates.add(MetaUtil.newUpdate(pair.getKey(), values));

//				final Metadata meta = new Metadata(); // TODO: need to pull the existing metadata, this wipes everything
//				meta.addDirectory(new XmpDirectory());
//				MetaUtil.updateSubject(meta, DbUtil.convertStringToArray(values.getAsString(Meta.SUBJECT)));
//				MetaUtil.updateRating(meta, values.getAsInteger(Meta.RATING));
//				MetaUtil.updateLabel(meta, values.getAsString(Meta.LABEL));

				//TODO: This logic needs cleanup
				final UsefulDocumentFile xmp = ImageUtil.getXmpFile(CoreActivity.this, pair.getKey());
				if (xmp == null)
					continue;

				final XMPMeta meta = MetaUtil.readXmp(CoreActivity.this, xmp);
				MetaUtil.updateXmpStringArray(meta, MetaUtil.SUBJECT, DbUtil.convertStringToArray(values.getAsString(Meta.SUBJECT)));
				MetaUtil.updateXmpInteger(meta, MetaUtil.RATING, values.getAsInteger(Meta.RATING));
				MetaUtil.updateXmpString(meta, MetaUtil.LABEL, values.getAsString(Meta.LABEL));
//				MetaUtil.updateXmpString(meta, MetaUtil.CREATOR, "test");

				final Uri xmpUri = xmp.getUri();
				final UsefulDocumentFile xmpDoc;
				try
				{
					xmpDoc = getDocumentFile(xmpUri, false, false); // For now use getDocumentFile to leverage write testing
					//TODO: need DocumentActivity.openOutputStream
				}
				catch (WritePermissionException e)
				{
					// Write pending updates, method will resume with remaining images
					MetaUtil.updateMetaDatabase(CoreActivity.this, databaseUpdates);
					return false;
				}

				try(OutputStream os = getContentResolver().openOutputStream(xmpDoc.getUri()))
				{
					MetaUtil.writeXmp(os, meta);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}

				uris.remove();  //concurrency-safe remove
			}

			MetaUtil.updateMetaDatabase(CoreActivity.this, databaseUpdates);
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
		private final String[] projection = new String[] { Meta.URI, Meta.RATING, Meta.LABEL, Meta.SUBJECT };

		public PrepareXmpRunnable(XmpEditFragment.XmpEditValues update, XmpUpdateField updateType)
		{
			this.selectedImages = getSelectedImages();
			this.update = update;
			this.updateType = updateType;
		}

		@Override
		public void run()
		{
			if (selectedImages.size() == 0)
				return;

			// TODO: This can be done without cursor lookups, use position and lookup from cursor
			String[] selectionArgs = new String[selectedImages.size()];
			for (int i = 0; i < selectedImages.size(); i++)
			{
				selectionArgs[i] = selectedImages.get(i).toString();
			}


			Map<Uri, ContentValues> xmpPairs = new HashMap<>();
			// Grab existing metadata
			try(Cursor c = getContentResolver().query(Meta.CONTENT_URI,
					projection,
					DbUtil.createIN(Meta.URI, selectedImages.size()),
					selectionArgs,
					null))
			{

				if (c == null)
					return;

				// Create mappings with existing values
				while (c.moveToNext()) {
					ContentValues cv = new ContentValues(projection.length);
					DatabaseUtils.cursorRowToContentValues(c, cv);
					xmpPairs.put(Uri.parse(cv.getAsString(Meta.URI)), cv);
				}
			}

			// Update singular fields in the existing values
			for (Map.Entry<Uri, ContentValues> xmpPair : xmpPairs.entrySet())
			{
				switch(updateType)
				{
					case Label:
						xmpPair.getValue().put(Meta.LABEL, update.Label);
						break;
					case Rating:
						xmpPair.getValue().put(Meta.RATING, update.Rating);
						break;
					case Subject:
						xmpPair.getValue().put(Meta.SUBJECT, DbUtil.convertArrayToString(update.Subject));
						break;
				}
			}
			writeXmp(xmpPairs);
		}
	}
}
