package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
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
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.OnNavigationListener;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.actionbarsherlock.widget.ShareActionProvider.OnShareTargetSelectedListener;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.dcraw.LibRaw.Margins;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.ImageCache.ImageCacheParams;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.RawObject;
import com.anthonymandra.framework.Util;
import com.anthonymandra.widget.LoadingImageView;
import com.github.espiandev.showcaseview.ShowcaseView;
import com.inscription.ChangeLogDialog;
import com.inscription.WhatsNewDialog;

import org.openintents.intents.FileManagerIntents;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;

public class RawDroid extends GalleryActivity implements OnNavigationListener, OnItemClickListener, OnItemLongClickListener, OnScrollListener,
		OnShareTargetSelectedListener, OnSharedPreferenceChangeListener
{
	private static final String TAG = RawDroid.class.getSimpleName();

	public static final String KEY_STARTUP_DIR = "keyStartupDir";

	public static final String LICENSE_RECEIVER = "com.anthonymandra.rawdroid.LicenseResponse";
	public static final String LICENSE_REQUEST = "com.anthonymandra.rawdroid.LicenseRequest";
	public static final String PERMISSION = "com.anthonymandra.rawdroid.permission";
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
            "/dev/bus/usb/001/002" };

	// Request codes
	private static final int REQUEST_MOUNTED_IMPORT_DIR = 2;
	private static final int REQUEST_EXPORT_THUMB_DIR = 5;
    private static final int REQUEST_UPDATE_PHOTO = 6;

	// File system objects
	private ArrayAdapter<SpinnerFile> navAdapter;
	public static final File START_PATH = new File("/mnt");
	private static final File ROOT = new File("/");
	private Parcelable gridState;

	// Widget handles
	private GridView imageGrid;

	// Image processing
	private int mImageThumbSize;
	private int mImageThumbSpacing;
	private ImageDecoder mImageDecoder;
	private ImageAdapter imageAdapter;
	protected ArrayList<MediaItem> mSelectedImages = new ArrayList<MediaItem>();
	protected List<MediaItem> mItemsForIntent = new ArrayList<MediaItem>();

	Dialog formatDialog;

	// Selection support
	private boolean multiSelectMode;

	public static File keywords;

	private int mDisplayWidth;
	private int mDisplayHeight;

	private ActionMode mMode;

	// private int tutorialStage;
	private ShowcaseView tutorial;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.gallery);

		doFirstRun();

		AppRater.app_launched(this);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		mDisplayWidth = metrics.widthPixels;
		mDisplayHeight = metrics.heightPixels;

		imageGrid = ((GridView) findViewById(R.id.gridview));
		imageGrid.setOnScrollListener(this);
		imageGrid.setOnItemClickListener(this);
		imageGrid.setOnItemLongClickListener(this);

		ImageCacheParams cacheParams = new ImageCacheParams(this, IMAGE_CACHE_DIR);

		// Set memory cache to 25% of mem class
		cacheParams.setMemCacheSizePercent(this, 0.10f);

		// The ImageFetcher takes care of loading images into our ImageView children asynchronously
		mImageDecoder = new ImageDecoder(this, mImageThumbSize);
		mImageDecoder.setFolderImage(R.drawable.android_folder);
		mImageDecoder.setUnknownImage(R.drawable.unkown_file);
		mImageDecoder.addImageCache(getSupportFragmentManager(), cacheParams);

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

		// getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowTitleEnabled(false);
		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		Context context = getSupportActionBar().getThemedContext();

		navAdapter = new ArrayAdapter<SpinnerFile>(context, R.layout.sherlock_spinner_item, new ArrayList<SpinnerFile>());
		navAdapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		getSupportActionBar().setListNavigationCallbacks(navAdapter, this);

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
	}

	private void doFirstRun()
	{
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (settings.getBoolean("isFirstRun", true)) 
        {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.welcomeTitle);
			builder.setMessage(R.string.welcomeMessage);
			builder.show();
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("isFirstRun", false);
            editor.commit();
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
		getSupportMenuInflater().inflate(R.menu.gallery_options, menu);
		return true;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// Launch what's new dialog (will only be shown once)
		final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
		whatsNewDialog.show(!mLicenseManager.isLicensed());

		if (mCurrentPath != null && mCurrentPath.exists())
		{
			updatePath(mCurrentPath);
		}
		else
		{
			setRecentFolder();
		}

		// We end muli-select because .contains fails after a resume and doesn't show selections
		// if (mMode != null)
		// mMode.finish();
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

	@SuppressWarnings("unchecked")
	private void handleImportDirResult(final String destinationPath)
	{
		File destination = new File(destinationPath);

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
		editor.commit();

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
		long importSize = getSelectedImageSize();
		if (destination.getFreeSpace() < importSize)
		{
			Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
			return;
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(RawDroid.PREFS_MOST_RECENT_SAVE, destination.getPath());
		editor.commit();

		CopyThumbTask ct = new CopyThumbTask(destination);
		ct.execute(mItemsForIntent);
	}

	@SuppressWarnings("unchecked")
	private List<MediaItem> storeSelectionForIntent()
	{
		return mItemsForIntent = (ArrayList<MediaItem>) mSelectedImages.clone();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.galleryRename:
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
			case R.id.gallery_delete:
				storeSelectionForIntent();
				deleteImages(mItemsForIntent);
				return true;
			case R.id.galleryExportThumbs:
				storeSelectionForIntent();
				requestExportThumbLocation();
				return true;
			case R.id.galleryImportImages:
				storeSelectionForIntent();
				requestImportImageLocation();
				return true;
			case R.id.gallerySettings:
				requestSettings();
				return true;
			case R.id.gallery_recycle:
				showRecycleBin();
				return true;
			case R.id.gallerySelectAll:
				selectAll();
				return false;
			case R.id.gallerySearch:
				SearchTask st = new SearchTask();
				st.execute();
				return true;
//			case R.id.galleryRecentFolder:
//				goToLastUsedFolder();
//				return true;
			case R.id.galleryHelp:
				runTutorial();
				return true;
			case R.id.galleryAbout:
				final ChangeLogDialog changeLogDialog = new ChangeLogDialog(this);
				changeLogDialog.show(!mLicenseManager.isLicensed());
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onShareTargetSelected(ShareActionProvider source, Intent intent)
	{
		mMode.finish(); // end the contextual action bar and multi-select mode
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
		List<MediaItem> filesToRename = new ArrayList<MediaItem>();
		if (mSelectedImages.size() > 0)
		{
			filesToRename = storeSelectionForIntent();
		}
		else
		{
			filesToRename.addAll(mRawImages);
		}
		// TODO: This should manage raw and jpg
		new FormatDialog(this, getString(R.string.renameImages), filesToRename, new OnResponseListener()).show();
	}

	private void requestImportImageLocation()
	{
		Intent images = new Intent(FileManagerIntents.ACTION_PICK_DIRECTORY);

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
		images.setData(Uri.fromFile(importLocation));

		// Set fancy title and button (optional)
		images.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.chooseDestination));
		images.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.import1));

		try
		{
			startActivityForResult(images, REQUEST_MOUNTED_IMPORT_DIR);
		}
		catch (ActivityNotFoundException e)
		{
			// No compatible file manager was found.
			Toast.makeText(this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

	private void requestExportThumbLocation()
	{
		Intent intent = new Intent(FileManagerIntents.ACTION_PICK_DIRECTORY);
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

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId)
	{
		File path = navAdapter.getItem(itemPosition);
		updatePath(path);
		// setPath(path.getFilePath());
		return true;
	}

	private void setNavigation()
	{
		getSupportActionBar().setSelectedNavigationItem(0);
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
		if (mCurrentPath.equals(ROOT))
			super.onBackPressed();
		else
		{
			upOneLevel();
		}
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

	private class OnResponseListener implements FormatDialog.ResponseListener
	{
		@Override
		public void Response(Boolean accept)
		{
			if (accept)
			{
				updateGallery();
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
			getSupportMenuInflater().inflate(R.menu.gallery_contextual, menu);
			getSupportMenuInflater().inflate(R.menu.gallery_options, menu);
			MenuItem actionItem = menu.findItem(R.id.galleryShare);
			if (actionItem != null)
			{
				mShareProvider = (ShareActionProvider) actionItem.getActionProvider();
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
			endMultiSelectMode();
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
        ArrayList<Uri> arrayUri = new ArrayList<Uri>();
        for (RawObject selection : mSelectedImages)
        {
            arrayUri.add(selection.getSwapUri());
        }

		if (mMode != null)
		{
			mMode.setTitle(mSelectedImages.size() + " selected");
		}
        if (mSelectedImages.size() == 0)
        {
            return;
        }
		else if (mSelectedImages.size() > 1)
		{
            setShareUri(arrayUri);
		}
		else
		{
            setShareUri(arrayUri.get(0));
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
				viewHolder.image.setImageResource(R.drawable.android_folder);
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
				else
				{
					viewHolder.filename.setText(media.getName());
					viewHolder.image.setImageResource(R.drawable.unkown_file);
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
				RawObject lastSelected = mSelectedImages.get(mSelectedImages.size() - 1);
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

		public void removeSelection(RawObject media)
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
		mMode = startActionMode(new GalleryActionMode());
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

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
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

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(RawDroid.this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREFS_MOST_RECENT_FOLDER, mCurrentPath.getPath());
        editor.commit();

        Intent viewer = new Intent(this, ViewerChooser.class);
        viewer.setData(media.getUri());
        startActivityForResult(viewer, REQUEST_UPDATE_PHOTO);

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
		List<String> failed = new ArrayList<String>();

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

			for (RawObject toCopy : copyList)
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
		List<String> failed = new ArrayList<String>();

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
            int watermarkSize = pref.getInt(FullSettingsActivity.KEY_WatermarkSize, 12);
            String watermarkLocation = pref.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");
            Margins margins = new Margins(pref);
            
            Bitmap watermark;
            byte[] waterData = null;
            boolean processWatermark = false;
            int waterWidth = 0, waterHeight = 0;
            		
            if (!mLicenseManager.isLicensed())
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
                watermark = Util.getWatermarkText(watermarkText, watermarkAlpha, watermarkSize, watermarkLocation);
                waterData = Util.getBitmapBytes(watermark);
                waterWidth = watermark.getWidth();
                waterHeight = watermark.getHeight();
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

	public void runTutorial()
	{
		ShowcaseView.ConfigOptions co = new ShowcaseView.ConfigOptions();
		tutorial = ShowcaseView.insertShowcaseView(mDisplayWidth - 50, mDisplayHeight - 30, this, R.string.tutorial, R.string.tutorialStart, co);
		tutorial.overrideButtonClick(new TutorialClickListener());
	}

	private static enum Tutorials
	{
		Connection1, Connection2, Connection3, Connection4, Connection5, Memory, Directory, /* RecentFolder, */Usb, LongPressHelp, RecycleBin, LongPressSelect, SelectAll, SelectMode, SelectBetween, Import, Export, Rename, Delete, Share
	};

	private static final EnumSet<Tutorials> tutorialOrder = EnumSet.allOf(Tutorials.class);

	private class TutorialClickListener implements OnClickListener
	{
		int tutorialStage = -1;
		Tutorials[] order = tutorialOrder.toArray(new Tutorials[0]);

		@Override
		public void onClick(View v)
		{
			tutorialStage++;
			if (tutorialStage >= order.length)
			{
				closeTutorial();
				return;
			}

			switch (order[tutorialStage])
			{
			// Even if I'm just changing the text it won't work unless I change the view
				case Connection1: // Connection
					tutorial.setShowcasePosition(mDisplayWidth - 49, mDisplayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect1);
					break;
				case Connection2: // Connection
					tutorial.setShowcasePosition(mDisplayWidth - 50, mDisplayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect2);
					break;
				case Connection3: // Connection
					tutorial.setShowcasePosition(mDisplayWidth - 49, mDisplayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect3);
					break;
				case Connection4: // Connection
					tutorial.setShowcasePosition(mDisplayWidth - 50, mDisplayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect4);
					break;
				case Connection5: // Connection
					tutorial.setShowcasePosition(mDisplayWidth - 49, mDisplayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect5);
					break;
				case Memory: // Memory
					tutorial.setShowcasePosition(mDisplayWidth - 50, mDisplayHeight - 30);
					tutorial.setText(R.string.tutorialMemoryTitle, R.string.tutorialMemory);
					break;
				case Directory: // Directory
					tutorial.setShowcaseItem(ShowcaseView.ITEM_SPINNER, 1, RawDroid.this);
					tutorial.setText(R.string.directory, R.string.tutorialPath);
					break;
//				case RecentFolder: // Recent Folder
//					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.galleryRecentFolder, RawDroid.this);
//					tutorial.setText(R.string.lastUsedFolder, R.string.tutorialRecentFolder);
//					break;
				case Usb: // Usb
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.gallery_usb, RawDroid.this);
					tutorial.setText(R.string.tutorialUsbTitle, R.string.tutorialUsb);
					break;
				case LongPressHelp: // Long press action
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_OVERFLOW, 0, RawDroid.this);
					tutorial.setText(R.string.tutorialActionLongPressTitle, R.string.tutorialActionLongPress);
					break;
				case RecycleBin: // Recycling Bin
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.gallery_recycle, RawDroid.this);
					tutorial.setText(R.string.accessRecycle, R.string.tutorialRecycle);
					break;
				case LongPressSelect: // Long Select
					tutorial.setShowcaseView(((GridView) findViewById(R.id.gridview)).getChildAt(0));
					tutorial.setText(R.string.select, R.string.tutorialSelect);
					break;
				case SelectAll: // Select All
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.gallerySelectAll, RawDroid.this);
					tutorial.setText(R.string.selectAll, R.string.tutorialSelectAll);
					break;
				case SelectMode: // Select Mode
					startContextualActionBar();
					tutorial.setShowcasePosition(0, 0);
					tutorial.setText(R.string.tutorialSelectModeTitle, R.string.tutorialSelectMode);
					break;
				case SelectBetween: // Select Between
					GridView gv = (GridView) findViewById(R.id.gridview);
					int count = gv.getCount() > 3 ? 3 : gv.getCount();
					tutorial.setShowcaseView(gv.getChildAt(count));
					tutorial.setText(R.string.tutorialSelectBetweenTitle, R.string.tutorialSelectBetween);
					break;
				case Import: // Import
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.galleryImportImages, RawDroid.this);
					tutorial.setText(R.string.importImages, R.string.tutorialImport);
					break;
				case Export: // Export
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.galleryExportThumbs, RawDroid.this);
					tutorial.setText(R.string.exportThumbnails, R.string.tutorialExport);
					break;
				case Rename: // Rename
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.galleryRename, RawDroid.this);
					tutorial.setText(R.string.rename, R.string.tutorialRename);
					break;
				case Delete: // Delete
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.gallery_delete, RawDroid.this);
					tutorial.setText(R.string.delete, R.string.tutorialDelete);
					break;
				case Share: // Share
					tutorial.setShowcaseItem(ShowcaseView.ITEM_ACTION_ITEM, R.id.galleryShare, RawDroid.this);
					tutorial.setText(R.string.shareWith, R.string.tutorialShare);
					break;
				default: // We're done
					closeTutorial();
			}
		}
	}

	protected class SearchTask extends AsyncTask<Void, Void, Void> implements OnCancelListener
	{
		boolean cancelled;
		List<String> imageFolders = new ArrayList<String>();

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
			builder.setSingleChoiceItems(imageFolders.toArray(new String[0]), -1, new DialogInterface.OnClickListener()
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

	private void closeTutorial()
	{
		mMode.finish();
		tutorial.hide();
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
