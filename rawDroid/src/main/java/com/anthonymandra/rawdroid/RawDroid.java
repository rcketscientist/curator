package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.dcraw.LibRaw.Margins;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.ImageCache.ImageCacheParams;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.License;
import com.anthonymandra.framework.RawObject;
import com.anthonymandra.framework.Util;
import com.anthonymandra.widget.LoadingImageView;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.inscription.ChangeLogDialog;
import com.inscription.WhatsNewDialog;

import org.openintents.filemanager.FileManagerActivity;
import org.openintents.intents.FileManagerIntents;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RawDroid extends GalleryActivity implements OnItemClickListener, OnItemLongClickListener, OnScrollListener,
        ShareActionProvider.OnShareTargetSelectedListener, OnSharedPreferenceChangeListener, AdapterView.OnItemSelectedListener
{
	private static final String TAG = RawDroid.class.getSimpleName();

	public static final String KEY_STARTUP_DIR = "keyStartupDir";

	public static final String LICENSE_RESULT = "license_result";
	public static final int LICENSE_ALLOW = 1;
	public static final int LICENSE_DISALLOW = 2;
	public static final int LICENSE_ERROR = 3;

	// Preference fields
	public static final String PREFS_NAME = "RawDroidPrefs";
	public static final boolean PREFS_AUTO_INTERFACE_DEFAULT = true;
	public static final String PREFS_MOST_RECENT_FOLDER = "mostRecentFolder";
	public static final String PREFS_MOST_RECENT_SAVE = "mostRecentSave";
	public static final String PREFS_MOST_RECENT_IMPORT = "mostRecentImport";
	public static final String PREFS_VERSION_NUMBER = "prefVersionNumber";
	public static final String IMAGE_CACHE_DIR = "thumbs";
	public static final String[] USB_LOCATIONS = new String[]
	{
            "/mnt/usb_storage",
            "/Removable",
            "/mnt/UsbDriveA",
            "/mnt/UsbDriveB",
            "/mnt/UsbDriveC",
            "/mnt/UsbDriveD",
            "/mnt/UsbDriveE",
            "/mnt/UsbDriveF",
			"/mnt/sda1",
            "/mnt/sdcard2",
            "/udisk",
            "/mnt/extSdCard",
            Environment.getExternalStorageDirectory().getPath() + "/usbStorage/sda1",
			Environment.getExternalStorageDirectory().getPath() + "/usbStorage",
            "/mnt/usb",
            "/storage/usb",
            "/dev/bus/usb/001/002",
            "/storage/USBstorage",
            "/storage/USBstorage2",
            "/storage/USBstorage3",
            "/storage/USBstorage4",
            "/storage/USBstorage5",
            "/storage/USBstorage6",
            "/storage/usbdisk",
            "/storage/usbdisk1",
            "/storage/usbdisk2"
    };

	// Request codes
	private static final int REQUEST_MOUNTED_IMPORT_DIR = 2;
	private static final int REQUEST_EXPORT_THUMB_DIR = 5;
    private static final int REQUEST_UPDATE_PHOTO = 6;

	// File system objects
	private ArrayAdapter<SpinnerFile> navAdapter;
	public static final File START_PATH = new File("/mnt");
	private static final File ROOT = new File("/");
	private Parcelable gridState;

    private static boolean inTutorial = false;
	private static boolean inActionMode = false;

	// Widget handles
	private GridView imageGrid;

	// Image processing
	private int mImageThumbSize;
	private int mImageThumbSpacing;
	private ImageDecoder mImageDecoder;
	private ImageAdapter imageAdapter;
	protected ArrayList<MediaItem> mSelectedImages = new ArrayList<>();
	protected List<MediaItem> mItemsForIntent = new ArrayList<>();

	// Selection support
	private boolean multiSelectMode;

	public static File keywords;

	private int mDisplayWidth;
	private int mDisplayHeight;

	private ActionMode mContextMode;

	// private int tutorialStage;
	private ShowcaseView tutorial;
    private Toolbar mToolbar;
    private Spinner mNavigationSpinner;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.gallery);
        mToolbar = (Toolbar) findViewById(R.id.galleryToolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(R.string.app_name);
        getSupportActionBar().setLogo(R.drawable.icon);

		doFirstRun();
//        doProCheck();

		AppRater.app_launched(this);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		mDisplayWidth = metrics.widthPixels;
		mDisplayHeight = metrics.heightPixels;

        inTutorial = false;

		imageGrid = ((GridView) findViewById(R.id.gridview));
		imageGrid.setOnScrollListener(this);
		imageGrid.setOnItemClickListener(this);
		imageGrid.setOnItemLongClickListener(this);

		ImageCacheParams cacheParams = new ImageCacheParams(this, IMAGE_CACHE_DIR);

		// Set memory cache to 25% of mem class
		cacheParams.setMemCacheSizePercent(this, 0.15f);

		// The ImageFetcher takes care of loading images into our ImageView children asynchronously
		mImageDecoder = new ImageDecoder(this, mImageThumbSize);
		mImageDecoder.setFolderImage(R.drawable.android_folder);
		mImageDecoder.setUnknownImage(R.drawable.ic_unknown_file);
		mImageDecoder.addImageCache(getFragmentManager(), cacheParams);

		mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
		mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);

		PreferenceManager.setDefaultValues(this, R.xml.preferences_metadata, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_storage, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_view, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_license, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_watermark, false);

		getKeywords();

		imageAdapter = new ImageAdapter(this);
		imageGrid.setAdapter(imageAdapter);

        Context themedContext = getSupportActionBar().getThemedContext();
		getSupportActionBar().setDisplayShowTitleEnabled(false);

		navAdapter = new ArrayAdapter<>(themedContext, android.R.layout.simple_spinner_item, new ArrayList<SpinnerFile>());
		navAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mNavigationSpinner = (Spinner) findViewById(R.id.navSpinner);
        mNavigationSpinner.setAdapter(navAdapter);
        mNavigationSpinner.setOnItemSelectedListener(this);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(this);

		String startupDir = getIntent().getStringExtra(KEY_STARTUP_DIR);

		if (startupDir != null)
		{
			File startup = new File(startupDir);
			if (startup.exists())
			{
				updatePath(startup);
			}
		}
		else
		{
			setRecentFolder();
		}

        licenseHandler = new LicenseHandler(this);
	}

	private void doFirstRun()
	{
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (settings.getBoolean("isFirstRun", true))
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("isFirstRun", false);
            editor.apply();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.welcomeTitle);
            builder.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // Do nothing
                }
            });
            builder.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    runTutorial();
                }
            });

            if (Constants.VariantCode > 9)
            {
                builder.setMessage(R.string.welcomeTutorial);
            }
            else
            {
                builder.setMessage(R.string.welcomeMessage);
            }

            builder.show();
        }
	}

    private boolean packageExists(final String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);

            if (info == null) {
                // No need really to test for null, if the package does not
                // exist it will really rise an exception. but in case Google
                // changes the API in the future lets be safe and test it
                return false;
            }

            return true;
        } catch (Exception ex) {
            // If we get here only means the Package does not exist
        }

        return false;
    }

    private void doProCheck()
    {
        final String p = "com.anthonymandra.rawdroidpro";
        if (!BuildConfig.APPLICATION_ID.equals(p) && packageExists(p))
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Please use Rawdroid Pro!");
            builder.setMessage("" +
                    "Rawdroid Pro is no longer a license and is a standalone app.\n\n" +
                    "You may uninstall Rawdroid Demo and upgrade Rawdroid Pro.\n\n" +
                    "You will use Rawdroid Pro from now on.\n\n" +
                    "Please email me with any questions.");
            builder.show();
        }
    }

	private void getKeywords()
	{
		keywords = getKeywordFile(this);
		if (!keywords.exists())
		{
			Toast.makeText(this, "Keywords file not found.  Generic created for testing.  Import in options.", Toast.LENGTH_LONG).show();
			try
			{
				BufferedWriter bw = new BufferedWriter(new FileWriter(keywords));
				bw.write("Europe");
				bw.newLine();
				bw.write("\tFrance");
				bw.newLine();
				bw.write("\tItaly");
				bw.newLine();
				bw.write("\t\tRome");
				bw.newLine();
				bw.write("\t\tVenice");
				bw.newLine();
				bw.write("\tGermany");
				bw.newLine();
				bw.write("South America");
				bw.newLine();
				bw.write("\tBrazil");
				bw.newLine();
				bw.write("\tChile");
				bw.newLine();
				bw.write("United States");
				bw.newLine();
				bw.write("\tNew Jersey");
				bw.newLine();
				bw.write("\t\tTrenton");
				bw.newLine();
				bw.write("\tVirginia");
				bw.newLine();
				bw.write("\t\tRichmond");
				bw.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private void setRecentFolder()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String recent = settings.getString(PREFS_MOST_RECENT_FOLDER, null);
		if (recent != null && new File(recent).exists())
		{
			updatePath(new File(recent));
		}
		else
		{
			updatePath(START_PATH);
		}
	}

	/**
	 * Notifies the gallery that data has changed, generally called from {@link #updateLocalFiles()}
	 */
	private void updateGallery()
	{
		if (imageAdapter != null)
		{
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					imageAdapter.notifyDataSetChanged();
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.gallery_options, menu);
		return true;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// Launch what's new dialog (will only be shown once)
		final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
		whatsNewDialog.show(Constants.VariantCode == 8);

		if (mCurrentPath != null && mCurrentPath.exists())
		{
			updatePath(mCurrentPath);
		}
		else
		{
			setRecentFolder();
		}

		// We end muli-select because .contains fails after a resume and doesn't show selections
		// if (mContextMode != null)
		// mContextMode.finish();
		mImageDecoder.setExitTasksEarly(false);
		imageAdapter.notifyDataSetChanged();
		Log.i(TAG, "onResume");
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mImageDecoder.setExitTasksEarly(true);
		mImageDecoder.flushCache();
//		closeMtpDevice();
		Log.i(TAG, "onPause");
	}

	@Override
	public void onStop()
	{
		super.onStop();
		Log.i(TAG, "onStop");
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mImageDecoder.closeCache();
	}

	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_MOUNTED_IMPORT_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
					handleImportDirResult(data.getData().getPath());
				}
				break;
			case REQUEST_EXPORT_THUMB_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
                    handleExportThumbResult(data.getData().getPath());
//					handleExportThumbResult(data);
				}
				break;
            case REQUEST_UPDATE_PHOTO:
                if (resultCode == RESULT_OK && data != null)
                {
                    handlePhotoUpdate(data.getData());
                }
		}
	}

    private void handlePhotoUpdate(Uri photo)
    {
        int index = findMediaByFilename(photo.getPath());
        if (index > 0)
            imageGrid.smoothScrollToPosition(index);
    }

//	@SuppressWarnings("unchecked")
	private void handleImportDirResult(final String destinationPath)
	{
		File destination = new File(destinationPath);
        if (!destination.canWrite())
        {
            showWriteAccessError();
        }

		if (destination.equals(mCurrentPath))
		{
			Toast.makeText(this, R.string.warningSourceEqualsDestination, Toast.LENGTH_LONG).show();
			return;
		}

		long importSize = getSelectedImageSize();
		if (destination.getFreeSpace() < importSize)
		{
			Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
			return;
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFS_MOST_RECENT_IMPORT, destination.getPath());
		editor.apply();

		CopyImageTask ct = new CopyImageTask(destination);
		ct.execute(mItemsForIntent);
	}

	private long getSelectedImageSize()
	{
		long selectionSize = 0;
		for (RawObject toImport : mSelectedImages)
		{
			selectionSize += toImport.getFileSize();
		}
		return selectionSize;
	}

    @SuppressWarnings("unchecked")
    private void handleExportThumbResult(final String destinationPath)
    {
        File destination = new File(destinationPath);
        if (!destination.canWrite())
        {
            showWriteAccessError();
        }

        long importSize = getSelectedImageSize();
        if (destination.getFreeSpace() < importSize)
        {
            Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(RawDroid.PREFS_MOST_RECENT_SAVE, destination.getPath());
        editor.apply();

        CopyThumbTask ct = new CopyThumbTask(destination);
        ct.execute(mItemsForIntent);
    }

    private void showWriteAccessError()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.warningAccessError);
        if (Util.hasKitkat())
        {
            alert.setMessage(R.string.warningWriteDisabledKitKat);
        }
        else
        {
            alert.setMessage(R.string.warningWriteDisabled);
        }
        alert.show();
    }

    // TODO: Partial attempt to implement SAF, but intent entirely unweildy.  To support 5.0 in future, just request permission for extCard root initially
	@SuppressWarnings("unchecked")
//	private void handleExportThumbResult(Intent data)
//	{
//        //TODO: DocumentFile supposedly has high overhead, but avoiding a massive filesystem rewrite for now
//        DocumentFile destination;
//        long freeSpace = 0;
//        String destPath = null;
//        if (!Util.hasLollipop())
//        {
//            destPath = data.getData().getPath();
////            File dest = new File(destPath);
////            freeSpace = dest.getFreeSpace();
//        }
//        else
//        {
//            destPath = data.getData().getPath();
////            destination = DocumentFile.fromTreeUri(this, data.getData());
//        }
//
////		File destination = new File(destinationPath);
////		long importSize = getSelectedImageSize();
////		if (freeSpace < importSize)
////		{
////			Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
////			return;
////		}
//
//		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
//		SharedPreferences.Editor editor = settings.edit();
//		editor.putString(RawDroid.PREFS_MOST_RECENT_SAVE, destPath);
//		editor.apply();
//
//		CopyThumbTask ct = new CopyThumbTask(new File(destPath));
//		ct.execute(mItemsForIntent);
//	}

//	@SuppressWarnings("unchecked")
	private List<MediaItem> storeSelectionForIntent()
	{
		return mItemsForIntent = (ArrayList<MediaItem>) mSelectedImages.clone();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (inTutorial)
			Toast.makeText(this, R.string.tutorialDisabled, Toast.LENGTH_SHORT).show();
		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.contextRename:
				storeSelectionForIntent();
				requestRename();
				return true;
			case R.id.galleryClearCache:
				mImageDecoder.clearCache();
				Toast.makeText(this, R.string.cacheCleared, Toast.LENGTH_SHORT).show();
				return true;
			case R.id.gallery_usb:
				requestUsb();
				return true;
			case R.id.context_delete:
				storeSelectionForIntent();
				deleteImages(mItemsForIntent);
				return true;
			case R.id.contextExportThumbs:
				storeSelectionForIntent();
				requestExportThumbLocation();
				return true;
			case R.id.contextMoveImages:
				storeSelectionForIntent();
				requestImportImageLocation();
				return true;
			case R.id.gallerySettings:
				requestSettings();
				return true;
			case R.id.gallery_recycle:
            case R.id.context_recycle:
				showRecycleBin();
				return true;
			case R.id.gallerySelectAll:
            case R.id.contextSelectAll:
				selectAll();
				return false;
			case R.id.gallerySearch:
				SearchTask st = new SearchTask();
				st.execute();
				return true;
			case R.id.galleryContact:
				requestEmailIntent();
				return true;
			case R.id.galleryTutorial:
				runTutorial();
				return true;
			case R.id.galleryAbout:
				final ChangeLogDialog changeLogDialog = new ChangeLogDialog(this);
				changeLogDialog.show(Constants.VariantCode == 8);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onShareTargetSelected(ShareActionProvider source, Intent intent)
	{
		mContextMode.finish(); // end the contextual action bar and multi-select mode
		return false;
	}

	private void selectAll()
	{
		startContextualActionBar();
		mSelectedImages.addAll(mVisibleItems);
		updateSelection();
		imageAdapter.notifyDataSetChanged();
	}

	private void requestUsb()
	{
		for (String location : USB_LOCATIONS)
		{
			File usb = new File(location);
			File[] contents = usb.listFiles();
			if (usb.exists() && contents != null && contents.length > 0)
			{
				updatePath(usb);
				return;
			}
		}
		Toast.makeText(this, R.string.warningNoUSB, Toast.LENGTH_LONG).show();
	}

	private void requestRename()
	{
		List<MediaItem> filesToRename = new ArrayList<>();
		if (mSelectedImages.size() > 0)
		{
			filesToRename = storeSelectionForIntent();
		}
		else
		{
			filesToRename.addAll(mRawImages);
		}
		FormatDialog dialog = new FormatDialog(this, filesToRename);
        dialog.setTitle(getString(R.string.renameImages));
        dialog.setDialogListener(new FormatDialog.DialogListener() {
            @Override
            public void onCompleted()
            {
                updatePath(mCurrentPath);
            }

            @Override
            public void onCanceled()
            {
                //Do nothing
            }
        });
        dialog.show();
	}

	private void requestImportImageLocation()
	{
        Intent intent;
//        if (Util.hasLollipop())
//        {
//            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//            startActivityForResult(intent, REQUEST_EXPORT_THUMB_DIR);
//        }
//        else
//        {
            intent = new Intent(this, FileManagerActivity.class);
            intent.setAction(FileManagerIntents.ACTION_PICK_DIRECTORY);
//            intent = new Intent(FileManagerIntents.ACTION_PICK_DIRECTORY);

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String recentImport = settings.getString(RawDroid.PREFS_MOST_RECENT_IMPORT, null);
            File importLocation = mCurrentPath;
            if (recentImport != null)
            {
                importLocation = new File(recentImport);
                if (!importLocation.exists())
                {
                    importLocation = mCurrentPath;
                }
            }
            if (importLocation == null)
                importLocation = START_PATH;

            // Construct URI from file name.
            intent.setData(Uri.fromFile(importLocation));

            // Set fancy title and button (optional)
            intent.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.chooseDestination));
            intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.import1));
//        }

		try
		{
			startActivityForResult(intent, REQUEST_MOUNTED_IMPORT_DIR);
		}
		catch (ActivityNotFoundException e)
		{
			// No compatible file manager was found.
			Toast.makeText(this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

    private void requestExportThumbLocation()
    {
        Intent intent;
//        if (Util.hasLollipop())
//        {
//            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//            startActivityForResult(intent, REQUEST_EXPORT_THUMB_DIR);
//        }
//        else
//        {
//            intent = new Intent(FileManagerIntents.ACTION_PICK_DIRECTORY);
            intent = new Intent(this, FileManagerActivity.class);
            intent.setAction(FileManagerIntents.ACTION_PICK_DIRECTORY);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String recentExport = settings.getString(RawDroid.PREFS_MOST_RECENT_SAVE, null);

            File exportLocation = mCurrentPath;
            if (recentExport != null)
            {
                exportLocation = new File(recentExport);
                if (!exportLocation.exists())
                {
                    exportLocation = mCurrentPath;
                }
            }

            // Construct URI from file name.
            intent.setData(Uri.fromFile(exportLocation));

            // Set fancy title and button (optional)
            intent.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.exportThumbnails));
            intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.export));
//        }

        try
        {
            startActivityForResult(intent, REQUEST_EXPORT_THUMB_DIR);
        }
        catch (ActivityNotFoundException e)
        {
            // No compatible file manager was found.
            Toast.makeText(this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
        }
    }

	private void requestSettings()
	{
		Intent settings = new Intent(this, FullSettingsActivity.class);
		startActivity(settings);
	}

	@Override
	protected void updateAfterDelete()
	{
		updateLocalFiles();
	}

	@Override
	protected void updateAfterRestore()
	{
		updateLocalFiles();
	}

	/**
	 * Reprocess the files and updates the gallery
	 */
	protected void updateLocalFiles()
	{
		// if (processLocalFolder())
		// {
		updateGalleryItems();
		updateGallery();
		// }
		// else
		// {
		// Toast.makeText(this, R.string.warningInvalidPath, Toast.LENGTH_LONG).show();
		// updatePath(START_PATH);
		// }
	}

	private void updatePath(File path)
	{
		setPath(path);

		setNavigation();

		updateLocalFiles();
	}

	private void setNavigation()
	{
        mNavigationSpinner.setSelection(0);

		SpinnerFile currentPath = new SpinnerFile(mCurrentPath.getPath());

		navAdapter.clear();
		navAdapter.add(currentPath);

		File parent = currentPath.getParentFile();
		while (parent != null)
		{
			navAdapter.add(new SpinnerFile(parent.getPath()));
			parent = parent.getParentFile();
		}
	}

	private void upOneLevel()
	{
		if (mCurrentPath.getParent() != null)
			updatePath(mCurrentPath.getParentFile());
	}

	@Override
	public void onBackPressed()
	{
        if (inTutorial)
		{
			closeTutorial();
		}
        // Default back operation in action mode
        else if (mContextMode != null)
        {
            super.onBackPressed();
            // Same as: mContextMode.finish();
        }
		else if (mCurrentPath.equals(ROOT))
        {
            super.onBackPressed();
        }
		else
		{
			upOneLevel();
		}
	}

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
    {
        if (inTutorial)
        {
            Toast.makeText(this, R.string.tutorialDisabled, Toast.LENGTH_SHORT).show();
            return;
        }

        File path = navAdapter.getItem(position);
        updatePath(path);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {
        //Nothing selected, well do nothing of course
    }

    class DirAlphaComparator implements Comparator<File>
	{

		// Comparator interface requires defining compare method.
		@Override
		public int compare(File filea, File fileb)
		{
			// ... Sort directories before files,
			// otherwise alphabetical ignoring case.
			if (filea.isDirectory() && !fileb.isDirectory())
			{
				return -1;

			}
			else if (!filea.isDirectory() && fileb.isDirectory())
			{
				return 1;

			}
			else
			{
				return filea.getName().compareToIgnoreCase(fileb.getName());
			}
		}
	}

	@TargetApi(12)
	static public boolean isCamera(UsbDevice device)
	{
		int count = device.getInterfaceCount();
		for (int i = 0; i < count; i++)
		{
			UsbInterface intf = device.getInterface(i);
			if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_STILL_IMAGE && intf.getInterfaceSubclass() == 1 && intf.getInterfaceProtocol() == 1)
			{
				return true;
			}
		}
		return false;
	}

	static class ViewHolder
	{
		public TextView filename;
		public LoadingImageView image;
	}

	/**
	 * Extension of {@link File} that overrides toString to return name as opposed to path.
	 * 
	 * @author amand_000
	 * 
	 */
	@SuppressWarnings("serial")
	private class SpinnerFile extends File
	{
		public SpinnerFile(String path)
		{
			super(path);
		}

		@Override
		public String toString()
		{
			if (this.getName().equals(""))
				return "root";
			else
				return this.getName();
		}
	}

	private final class GalleryActionMode implements ActionMode.Callback
	{
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu)
		{
			inActionMode = true;
			getMenuInflater().inflate(R.menu.gallery_contextual, menu);
			MenuItem actionItem = menu.findItem(R.id.contextShare);
			if (actionItem != null)
			{
				mShareProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(actionItem);
				mShareProvider.setOnShareTargetSelectedListener(RawDroid.this);
				mShareProvider.setShareIntent(mShareIntent);
			}

			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu)
		{
			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item)
		{
			boolean handled = onOptionsItemSelected(item);
			if (handled)
			{
				mode.finish();
			}
			return handled;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode)
		{
			inActionMode = false;
			endMultiSelectMode();
            mContextMode = null;
		}
	}

	public void startMultiSelectMode()
	{
		imageAdapter.clearSelection(); // Ensure we don't have any stragglers
		multiSelectMode = true;
	}

	public void endMultiSelectMode()
	{
		multiSelectMode = false;
		// Clearing the selection would force me to hold onto a copy for intents
		// Shouldn't be able to do any harm when not in ActionMode anyway.
		imageAdapter.clearSelection();
		// clearSwapDir();
	}

	public void updateSelection()
	{
        ArrayList<Uri> arrayUri = new ArrayList<>();
        for (RawObject selection : mSelectedImages)
        {
            arrayUri.add(selection.getSwapUri());
        }

		if (mContextMode != null)
		{
			mContextMode.setTitle(mSelectedImages.size() + " selected");
		}
        if (mSelectedImages.size() == 1)
        {
            setShareUri(arrayUri.get(0));
        }
		else if (mSelectedImages.size() > 1)
		{
            setShareUri(arrayUri);
		}
	}

	public class ImageAdapter extends BaseAdapter
	{
		private LayoutInflater mInflater;
		protected int mItemHeight = 0;
		protected int mNumColumns = 0;
		protected GridView.LayoutParams mImageViewLayoutParams;

		public ImageAdapter(Context context)
		{
			super();
			mInflater = LayoutInflater.from(context);
			mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

			if (getNumColumns() == 0)
			{
				final int numColumns = (int) Math.floor(mDisplayWidth / (mImageThumbSize + mImageThumbSpacing));
				if (numColumns > 0)
				{
					final int columnWidth = (mDisplayWidth / numColumns) - mImageThumbSpacing;
					setNumColumns(numColumns);
					setItemHeight(columnWidth);
				}
			}
		}

		@Override
		public int getCount()
		{
			return mVisibleItems.size() + mFolders.size();
		}

		@Override
		public Object getItem(int position)
		{
			return null;// mVisibleItems.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return position;
		}

		@SuppressWarnings("deprecation")
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View view;
			ViewHolder viewHolder;
			if (convertView == null)
			{			
				view = mInflater.inflate(R.layout.fileview, parent, false);
				viewHolder = new ViewHolder();
				viewHolder.filename = (TextView) view.findViewById(R.id.filenameView);
				viewHolder.image = (LoadingImageView) view.findViewById(R.id.webImageView);

				viewHolder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
				view.setLayoutParams(mImageViewLayoutParams);
				view.setTag(viewHolder);
			}
			else
			{
				// Otherwise re-use the converted view
				view = convertView;
				viewHolder = (ViewHolder) view.getTag();
				viewHolder.image.setBackgroundDrawable(null);
			}

			if (position < mFolders.size())
			{
				viewHolder.filename.setText(mFolders.get(position).getName());
				viewHolder.image.setImageResource(R.drawable.ic_folder);
			}
			else
			{
				final MediaItem media = getImage(position);
				if (mRawImages.contains(media) || mNativeImages.contains(media))
				{
					viewHolder.filename.setText(media.getName());
					mImageDecoder.loadImage(media, viewHolder.image);
//					new Thread(new Runnable() {						
//						@Override
//						public void run() {
//							media.readMetadata();	//TODO: This should be handled by a provider						
//						}
//					}).start();			
				}
				else if (mXmpFiles.contains(media))
				{
					viewHolder.filename.setText(media.getName());
					viewHolder.image.setImageResource(R.drawable.ic_xmpfile);
				}
				else
				{
					viewHolder.filename.setText(media.getName());
					viewHolder.image.setImageResource(R.drawable.ic_unknown_file);
				}

				if (multiSelectMode && mSelectedImages.contains(media))
				{
					viewHolder.image.setBackgroundResource(R.drawable.gallery_border);
				}
			}

			// Check the height matches our calculated column width
//			if (view.getLayoutParams().height != mItemHeight)
//			{
//				view.setLayoutParams(mImageViewLayoutParams);
//			}

			return view;
		}

		public MediaItem getImage(int index)
		{
			int location = index - mFolders.size();
			if (location < 0)
				return null;
			return mVisibleItems.get(location);
		}

		private void addUniqueSelection(MediaItem media)
		{
			if (!mSelectedImages.contains(media))
				mSelectedImages.add(media);
		}

		public void addSelection(MediaItem media)
		{
			addUniqueSelection(media);
			updateSelection();
			notifyDataSetChanged();
		}

		public void addBetweenSelection(int position)
		{
			// Default to select all items up to (if there's currently no selection)
			int visibleIndex = position - mFolders.size();
			int startIndex = mFolders.size();
			int endIndex = visibleIndex;

			if (mSelectedImages.size() > 0)
			{
				MediaItem lastSelected = mSelectedImages.get(mSelectedImages.size() - 1);
				int lastIndex = mVisibleItems.indexOf(lastSelected);

				// Later selection
				if (lastIndex < visibleIndex)
				{
					endIndex = visibleIndex;
					startIndex = lastIndex;
				}
				// Earlier selection
				else
				{
					endIndex = lastIndex;
					startIndex = visibleIndex;
				}
			}

			if (endIndex >= mVisibleItems.size())
				Toast.makeText(RawDroid.this, "Select between indexing failed, if this continues please email me!", Toast.LENGTH_LONG).show();

			for (int i = startIndex; i <= endIndex; ++i)
			{
				addUniqueSelection(mVisibleItems.get(i));
			}

			updateSelection();
			notifyDataSetChanged();
		}

		public void removeSelection(MediaItem media)
		{
			mSelectedImages.remove(media);
			updateSelection();
			notifyDataSetChanged();
		}

		public void clearSelection()
		{
			mSelectedImages.clear();
			updateSelection();
			notifyDataSetChanged();
		}

		public List<MediaItem> getSelectedItems()
		{
			return mSelectedImages;
		}

		public void toggleSelection(MediaItem media)
		{
			if (media == null)
				return;

			if (mSelectedImages.contains(media))
			{
				removeSelection(media);
			}
			else
			{
				addSelection(media);
			}
		}

		public void setItemHeight(int height)
		{
			if (height == mItemHeight)
			{
				return;
			}
			mItemHeight = height;
			mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, mItemHeight);

			if (mImageDecoder != null)
				mImageDecoder.setImageSize(height);

			notifyDataSetChanged();
		}

		public void setNumColumns(int numColumns)
		{
			mNumColumns = numColumns;
		}

		public int getNumColumns()
		{
			return mNumColumns;
		}
	}

	private void startContextualActionBar()
	{
		mContextMode = startSupportActionMode(new GalleryActionMode());
		startMultiSelectMode();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		MediaItem media = imageAdapter.getImage(position);

		if (media == null || media.isDirectory())
			return false;

		// If we're in multi-select select all items between
		if (multiSelectMode)
		{
			imageAdapter.addBetweenSelection(position);
		}
		// Enter multi-select
		else
		{
			startContextualActionBar();
			imageAdapter.toggleSelection(media);
		}

		return true;
	}

    @TargetApi(VERSION_CODES.JELLY_BEAN)
	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id)
	{
		// Directory Entry
		if (position < mFolders.size())
		{
			File dir = mFolders.get(position);
			if (dir.listFiles() == null)
			{
				Toast.makeText(this, R.string.accessDenied, Toast.LENGTH_SHORT).show();
				return;
			}

			updatePath(dir);
			return;
		}

		MediaItem media = imageAdapter.getImage(position);
		if (multiSelectMode)
		{
			imageAdapter.toggleSelection(media);
			return;
		}

        if (inTutorial)
        {
            Toast.makeText(this, R.string.tutorialDisabled, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(RawDroid.this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREFS_MOST_RECENT_FOLDER, mCurrentPath.getPath());
        editor.apply();

        Intent viewer = new Intent(this, ViewerChooser.class);
        viewer.setData(media.getUri());
        if (Util.hasJellyBean())
        {
            // makeThumbnailScaleUpAnimation() looks kind of ugly here as the loading spinner may
            // show plus the thumbnail image in GridView is cropped. so using
            // makeScaleUpAnimation() instead.
            ActivityOptions options =
                    ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight());
            startActivityForResult(viewer, REQUEST_UPDATE_PHOTO, options.toBundle());
        }
        else
        {
            startActivityForResult(viewer, REQUEST_UPDATE_PHOTO);
        }

	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
        //Do nothing
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		// Pause fetcher to ensure smoother scrolling when flinging
		if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING)
		{
			mImageDecoder.setPauseWork(true);
		}
		else
		{
			mImageDecoder.setPauseWork(false);
		}
	}

	private class CopyImageTask extends AsyncTask<List<MediaItem>, String, Boolean> implements OnCancelListener
	{
		private ProgressDialog importProgress;
		private File mDestination;
		List<String> failed = new ArrayList<>();

		public CopyImageTask(File destination)
		{
			super();
			mDestination = destination;
		}

		@Override
		protected void onPreExecute()
		{
			importProgress = new ProgressDialog(RawDroid.this);
			importProgress.setTitle(R.string.importingImages);
			importProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			importProgress.setCanceledOnTouchOutside(true);
			importProgress.setOnCancelListener(this);
			importProgress.show();
		}

		@Override
		protected Boolean doInBackground(List<MediaItem>... params)
		{
			boolean totalSuccess = true;
			List<MediaItem> copyList = params[0];

			importProgress.setMax(copyList.size());

			for (MediaItem toCopy : copyList)
			{
				publishProgress(toCopy.getName());
				boolean result = toCopy.copy(mDestination);
				if (!result)
				{
					Log.e(TAG, "Error copying " + toCopy.getName());
					failed.add(toCopy.getName());
					totalSuccess = false;
				}
				publishProgress();
			}
			return totalSuccess;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			updatePath(mDestination);
			if (failed.size() > 0)
			{
				String failures = "Failed files: ";
				for (String fail : failed)
				{
					failures += fail + ", ";
				}
				Toast.makeText(RawDroid.this, failures, Toast.LENGTH_LONG).show();
			}
			importProgress.dismiss();
			// If this was initiated by a camera host
//			closeMtpDevice();
		}

		@Override
		protected void onProgressUpdate(String... values)
		{
			if (values.length > 0)
			{
				importProgress.setMessage(values[0]);
			}
			else
			{
				importProgress.incrementProgressBy(1);
			}
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

	private class CopyThumbTask extends AsyncTask<List<MediaItem>, String, Boolean> implements OnCancelListener
	{
		private ProgressDialog importProgress;
		private File mDestination;
		List<String> failed = new ArrayList<>();

		public CopyThumbTask(File destination)
		{
			mDestination = destination;
		}

		@Override
		protected void onPreExecute()
		{
			importProgress = new ProgressDialog(RawDroid.this);
			importProgress.setTitle(R.string.exportingThumb);
			importProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			importProgress.setCanceledOnTouchOutside(true);
			importProgress.setOnCancelListener(this);
			importProgress.show();
		}

		@Override
		protected Boolean doInBackground(List<MediaItem>... params)
		{
			List<MediaItem> copyList = params[0];
			importProgress.setMax(copyList.size());
			
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(RawDroid.this);
            boolean showWatermark = pref.getBoolean(FullSettingsActivity.KEY_EnableWatermark, false);
            String watermarkText = pref.getString(FullSettingsActivity.KEY_WatermarkText, "");
            int watermarkAlpha = pref.getInt(FullSettingsActivity.KEY_WatermarkAlpha, 75);
            int watermarkSize = pref.getInt(FullSettingsActivity.KEY_WatermarkSize, 150);
            String watermarkLocation = pref.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");
            Margins margins = new Margins(pref);
            
            Bitmap watermark;
            byte[] waterData = null;
            boolean processWatermark = false;
            int waterWidth = 0, waterHeight = 0;
            		
            if (Constants.VariantCode < 11 || LicenseManager.getLastResponse() != License.LicenseState.pro)
            {
            	processWatermark = true;
                watermark = Util.getDemoWatermark(RawDroid.this, copyList.get(0).getThumbWidth());
                waterData = Util.getBitmapBytes(watermark);
                waterWidth = watermark.getWidth();
                waterHeight = watermark.getHeight();
                margins = Margins.LowerRight;
            }
            else if (showWatermark)
            {
                processWatermark = true;
                if (watermarkText.isEmpty())
                {
                    Toast.makeText(RawDroid.this, R.string.warningBlankWatermark, Toast.LENGTH_LONG).show();
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
          
			for (RawObject toExport : copyList)
			{
				publishProgress(toExport.getName());
				File thumbDest = new File(mDestination, Util.swapExtention(toExport.getName(), ".jpg"));

				boolean success;
				if (processWatermark)
				{
					success = toExport.writeThumbWatermark(thumbDest, waterData, waterWidth, waterHeight, margins);
				}
                else
                {
                	success = toExport.writeThumb(thumbDest);          
                }				

				if (!success)
					failed.add(toExport.getName());
					
				publishProgress();
			}
			return true; //not used.
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			updatePath(mDestination);
			if (failed.size() > 0)
			{
				String failures = "Failed files: ";
				for (String fail : failed)
				{
					failures += fail + ", ";
				}
				failures += "\nIf you are watermarking, check settings/sizes!";
				Toast.makeText(RawDroid.this, failures, Toast.LENGTH_LONG).show();
			}
			importProgress.dismiss();
		}

		@Override
		protected void onProgressUpdate(String... values)
		{
			if (values.length > 0)
			{
				importProgress.setMessage(values[0]);
			}
			else
			{
				importProgress.incrementProgressBy(1);
			}
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			this.cancel(true);
		}
	}

    int tutorialStage;
	File previousPath;
	public void runTutorial()
	{
        tutorialStage = 0;
		previousPath = mCurrentPath;
        File tutorialDirectory = Util.getDiskCacheDir(this, "tutorial");
        if (!tutorialDirectory.exists())
        {
            tutorialDirectory.mkdir();
        }

        //generate some example images
        try
        {
            FileOutputStream one = new FileOutputStream(new File(tutorialDirectory, "Image1.png"));
            FileOutputStream two = new FileOutputStream(new File(tutorialDirectory, "/Image2.png"));
            FileOutputStream three = new FileOutputStream(new File(tutorialDirectory, "/Image3.png"));
            FileOutputStream four = new FileOutputStream(new File(tutorialDirectory, "/Image4.png"));
            FileOutputStream five = new FileOutputStream(new File(tutorialDirectory, "/Image5.png"));

            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial1).compress(Bitmap.CompressFormat.PNG, 100, one);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial2).compress(Bitmap.CompressFormat.PNG, 100, two);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial3).compress(Bitmap.CompressFormat.PNG, 100, three);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial4).compress(Bitmap.CompressFormat.PNG, 100, four);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial5).compress(Bitmap.CompressFormat.PNG, 100, five);

        }

        catch (FileNotFoundException e)
        {
            Toast.makeText(this, "Unable to open tutorial examples.  Please skip file selection.", Toast.LENGTH_LONG).show();
        }

        updatePath(tutorialDirectory);
        inTutorial = true;

        tutorial = new ShowcaseView.Builder(this)//, true)
                .setContentTitle(R.string.tutorialWelcomeTitle)
                .setContentText(R.string.tutorialWelcomeText)
                .doNotBlockTouches()
                .setOnClickListener(new TutorialClickListener())
                .build();

        tutorial.setButtonText(getString(R.string.next));
        tutorial.setButtonPosition(getRightParam(getResources()));
        setTutorialNoShowcase();
	}

	private class TutorialClickListener implements OnClickListener
	{
        //Note: Don't animate coming from "NoShowcase" it flies in from off screen which is silly.
		View view;
		@Override
		public void onClick(View v)
		{
			switch (tutorialStage)
			{
				case 0: // Connection
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialConnectTitle));
                    tutorial.setContentText(getString(R.string.tutorialConnectText1));
                    setTutorialNoShowcase();
					break;
				case 1: // Connection
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialConnectText2));
                    setTutorialNoShowcase();
                    break;
				case 2: // Connection
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialConnectText3));
                    setTutorialNoShowcase();
					break;
				case 3: // Connection
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialConnectText4));
                    setTutorialNoShowcase();
					break;
				case 4: // Connection
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialConnectText5));
                    setTutorialNoShowcase();
					break;
				case 5: // Find Images
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialFindTitle));
                    tutorial.setContentText(getString(R.string.tutorialFindUSBText));
                    setTutorialActionView(R.id.gallery_usb, false);
					break;
				case 6: // Find Import
                    closeOptionsMenu(); //if overflow was shown
                    tutorial.setContentText(getString(R.string.tutorialFindImportText));
                    setTutorialNoShowcase();
					break;
				case 7: // Find Path
                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialFindPathText));
					setTutorialActionView(R.id.navSpinner, true);
					break;
				case 8: // Recent Folder
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialRecentFolderText));
                    setTutorialNoShowcase();
					break;
				case 9: // Find Images
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialFindImagesText));
                    setTutorialActionView(R.id.gallerySearch, false);
					break;
				case 10: // Long Select
                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialSelectTitle));
                    tutorial.setContentText(getString(R.string.tutorialSingleSelectText));
                    view = imageGrid.getChildAt(0);
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
					break;
				case 11: // Add select
					// If the user is lazy select for them
					if (!inActionMode)
						onItemLongClick(imageGrid, imageGrid.getChildAt(0), 0, 0);

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText));
                    view = imageGrid.getChildAt(2);
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
					break;
				case 12: // Select feedback
					// If the user is lazy select for them
					if (mSelectedImages.size() < 2)
					{
						mContextMode.finish();
						onItemLongClick(imageGrid, imageGrid.getChildAt(0), 0, 0);
						onItemClick(imageGrid, imageGrid.getChildAt(2), 2, 2);
					}

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText2));
                    // This is ghetto, I know the spinner lies UNDER the selection view
					setTutorialActionView(R.id.navSpinner, true);
					break;
				case 13: // Select All
					if (inActionMode)
						mContextMode.finish();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectAll));
                    setTutorialActionView(R.id.gallerySelectAll, true);
					break;
				case 14: // Exit Selection
					// If the user is lazy select for them
					if (mSelectedImages.size() < 1)
					{
						selectAll();
					}

                    tutorial.setScaleMultiplier(1f);
                    tutorial.setContentText(getString(R.string.tutorialExitSelectionText));
                    setTutorialHomeView(true);
					break;
                case 15: // Select between beginning
                    if (mContextMode != null)
                        mContextMode.finish();

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText1));
                    view = imageGrid.getChildAt(3);		//WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
                    break;
                case 16: // Select between end
					// If the user is lazy select for them
					if (mSelectedImages.size() < 1)
						onItemLongClick(imageGrid, imageGrid.getChildAt(1), 1, 1);

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText2));

					setTutorialHomeView(true);
                    view = imageGrid.getChildAt(1);	//WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
                    break;
                case 17: // Select between feedback
					// If the user is lazy select for them
					if (mSelectedImages.size() < 2)
					{
						onItemLongClick(imageGrid, imageGrid.getChildAt(1), 1, 1);
						onItemLongClick(imageGrid, imageGrid.getChildAt(3), 3, 3);
					}

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText3));
                    // This is ghetto, I know the spinner lies UNDER the selection view
					setTutorialActionView(R.id.navSpinner, true);
                    break;
                case 18: // Rename
					if (!inActionMode)
						startContextualActionBar();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialRenameTitle));
                    tutorial.setContentText(getString(R.string.tutorialRenameText));
                    setTutorialActionView(R.id.contextRename, true);
                    break;
                case 19: // Move
					if (!inActionMode)
						startContextualActionBar();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialMoveTitle));
                    tutorial.setContentText(getString(R.string.tutorialMoveText));
                    setTutorialActionView(R.id.contextMoveImages, true);
                    break;
                case 20: // Export
					if (!inActionMode)
						startContextualActionBar();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialExportTitle));
                    tutorial.setContentText(getString(R.string.tutorialExportText));
                    setTutorialActionView(R.id.contextExportThumbs, true);
                    break;
                case 21: // Share (can't figure out how to address the share button
//					if (!inActionMode)
//						startContextualActionBar();
//
//                    tutorial.setContentTitle(getString(R.string.tutorialShareTitle));
//                    tutorial.setContentText(getString(R.string.tutorialShareText));
//                    setTutorialShareView(true);
//					setTutorialActionView(R.id.contextShare, true);
//                    break;
					tutorialStage++;
                case 22: // Recycle
                    if (mContextMode != null)
                        mContextMode.finish();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialRecycleTitle));
                    tutorial.setContentText(getString(R.string.tutorialRecycleText));
                    setTutorialActionView(R.id.gallery_recycle, true);
                    break;
                case 23: // Actionbar help
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialActionbarHelpTitle));
                    tutorial.setContentText(getString(R.string.tutorialActionbarHelpText));
                    setTutorialActionView(R.id.gallerySelectAll, true);
                    break;
				default: // We're done
                    closeTutorial();
                    break;
			}
            tutorialStage++;
		}
	}

    public void closeTutorial()
    {
        inTutorial = false;
        if (mContextMode != null)
            mContextMode.finish();
        closeOptionsMenu();
        tutorial.hide();
		updatePath(previousPath);
    }

    //TODO: Hack for showcase appearing behind nav bar
    public int getNavigationBarHeight(int orientation) {
        try {
            Resources resources = getResources();
            int id = resources.getIdentifier(
                    orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape",
                    "dimen", "android");
            if (id > 0) {
                return resources.getDimensionPixelSize(id);
            }
        } catch (NullPointerException | IllegalArgumentException | Resources.NotFoundException e) {
            return 0;
        }
        return 0;
    }

    //TODO: Hack for showcase appearing behind nav bar
    public RelativeLayout.LayoutParams getRightParam(Resources res) {
        RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lps.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        int margin = ((Number) (res.getDisplayMetrics().density * 12)).intValue();
        lps.setMargins(margin, margin, margin, getNavigationBarHeight(res.getConfiguration().orientation) + 5);
        return lps;
    }

    static int bias = 1;
    private void setTutorialNoShowcase()
    {
        //Using the same target multiple times doesn't update the showcase
//        tutorial.setTarget(ViewTarget.NONE);

        // So this funkiness places the showcase off screen flittering back and forth one pixel
        // This doesn't screw up word wrap.
//        tutorial.setScaleMultiplier(0.1f);

        bias *= -1;
        tutorial.setShowcaseX(mDisplayWidth + 1000 + bias);

    }

    private void setTutorialHomeView(boolean animate)
    {
        PointTarget home = new PointTarget(16,16);  //Design guideline is 32x32
        tutorial.setShowcase(home, animate);
    }

    /**
     * Showcase item or overflow if it doesn't exist
     * @param itemId menu id
     * @param animate Animate the showcase from the previous spot.  Recommend FALSE if previous showcase was NONE
     */
    private void setTutorialActionView(int itemId, boolean animate)
    {
		ViewTarget target;
		View itemView = findViewById(itemId);
		if (itemView == null)
		{
			//List of all mToolbar items, assuming last is overflow
			List<View> views = mToolbar.getTouchables();
			target = new ViewTarget(views.get(views.size()-1)); //overflow
		}
		else
		{
			target = new ViewTarget(itemView);
		}

		tutorial.setShowcase(target, animate);
    }

	private void setTutorialShareView(boolean animate)
	{
		ViewTarget target;
		View itemView = mShareProvider.onCreateActionView();
		if (itemView == null)
		{
			//List of all mToolbar items, assuming last is overflow
			List<View> views = mToolbar.getTouchables();
			target = new ViewTarget(views.get(views.size()-1)); //overflow
			openOptionsMenu();   // Change size of showcase?
		}
		else
		{
			target = new ViewTarget(itemView);
		}

		tutorial.setShowcase(target, animate);
	}

	protected class SearchTask extends AsyncTask<Void, Void, Void> implements OnCancelListener
	{
		boolean cancelled;
		List<String> imageFolders = new ArrayList<>();

		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(RawDroid.this);
			mProgressDialog.setTitle(getString(R.string.search) + " " + getString(R.string.experimental) + "...");
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			File mount = new File("/mnt");
			search(mount);
			return null;
		}

		public void search(File dir)
		{
			if (cancelled || dir == null)
				return;
			if (dir.listFiles() == null)
				return;

			// Results in root pass
			File[] matches = dir.listFiles(rawFilter);
			if (matches != null && matches.length > 0)
				imageFolders.add(dir.getPath());

			// Recursion pass
			for (File f : dir.listFiles())
			{
				if (f == null)
					continue;

				if (f.isDirectory() && f.canRead() && !f.isHidden())
					search(f);
			}
		}

		@Override
		protected void onPostExecute(Void result)
		{
			mProgressDialog.dismiss();
			AlertDialog.Builder builder = new AlertDialog.Builder(RawDroid.this);
			builder.setTitle(R.string.imageFolders);
			builder.setSingleChoiceItems(imageFolders.toArray(new String[imageFolders.size()]), -1, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					updatePath(new File(imageFolders.get(which)));
					dialog.dismiss();
				}
			});
			builder.show();
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(FullSettingsActivity.KEY_ShowXmpFiles)
                || key.equals(FullSettingsActivity.KEY_ShowNativeFiles)
                || key.equals(FullSettingsActivity.KEY_ShowUnknownFiles))
		{
			updateLocalFiles();
		}
	}
}
