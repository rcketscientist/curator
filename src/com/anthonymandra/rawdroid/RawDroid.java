package com.anthonymandra.rawdroid;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;

import org.openintents.intents.FileManagerIntents;

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
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.ImageCache.ImageCacheParams;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.MediaObject;
import com.anthonymandra.framework.Utils;
import com.anthonymandra.widget.LoadingImageView;
import com.github.espiandev.showcaseview.ShowcaseView;
import com.inscription.ChangeLogDialog;
import com.inscription.WhatsNewDialog;

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
	public static final String RECYCLE_BIN_DIR = "recycle";
	public static final String[] USB_LOCATIONS = new String[]
	{ "/mnt/usb_storage", "/Removable", "/mnt/UsbDriveA", "/mnt/UsbDriveB", "/mnt/UsbDriveC", "/mnt/UsbDriveD", "/mnt/UsbDriveE", "/mnt/UsbDriveF",
			"/mnt/sda1", "/mnt/sdcard2", "/udisk", "/mnt/extSdCard", Environment.getExternalStorageDirectory().getPath() + "/usbStorage/sda1",
			Environment.getExternalStorageDirectory().getPath() + "/usbStorage" };

	// Request codes
	private static final int REQUEST_MOUNTED_IMPORT_DIR = 2;
	private static final int REQUEST_EXPORT_THUMB_DIR = 5;

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
	protected ArrayList<MediaObject> mSelectedImages = new ArrayList<MediaObject>();
	protected List<MediaObject> mItemsForIntent = new ArrayList<MediaObject>();
	
	Dialog formatDialog;

	// Selection support
	private boolean multiSelectMode;

	public static File keywords;

	private int displayWidth;
	private int displayHeight;

	private ActionMode mMode;

	private ShareActionProvider mShareProvider;
	private Intent mShareIntent;

	// private int tutorialStage;
	private ShowcaseView tutorial;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.gallery);
		
		//TODO: Clean up bogus cache
		Utils.debugClearCache(this);

		// checkLicense();
		checkExpiration(10, 1, 2013);

		AppRater.app_launched(this);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		displayWidth = metrics.widthPixels;
		displayHeight = metrics.heightPixels;

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

		getKeywords();

		imageAdapter = new ImageAdapter(this, mImageDecoder, displayWidth, mImageThumbSize, mImageThumbSpacing);
		imageGrid.setAdapter(imageAdapter);

		// getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowTitleEnabled(false);
		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		Context context = getSupportActionBar().getThemedContext();

		navAdapter = new ArrayAdapter<SpinnerFile>(context, R.layout.sherlock_spinner_item, new ArrayList<SpinnerFile>());
		navAdapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		getSupportActionBar().setListNavigationCallbacks(navAdapter, this);

		mShareIntent = new Intent(Intent.ACTION_SEND);
		mShareIntent.setType("image/jpeg");

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

	private void checkExpiration(int month, int day, int year)
	{
		// Convert to zero-base
		month--;
		day--;

		// get the current date
		GregorianCalendar currentDate = new GregorianCalendar();
		GregorianCalendar expiration = new GregorianCalendar(year, month, day); // Month is zero-based!

		// Set expiration for full featured demo
		if (currentDate.after(expiration))
		{
			new AlertDialog.Builder(this).setIcon(R.drawable.icon).setTitle("This demo is out of date, please update!")
					.setPositiveButton("OK", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							finish();
						}
					}).show();
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
				// TODO Auto-generated catch block
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
		whatsNewDialog.show();

		if (mCurrentPath != null && mCurrentPath.exists())
		{
			updatePath(mCurrentPath);
			resetScrollLocation();
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
		}
	}

	@SuppressWarnings("unchecked")
	private void handleImportDirResult(final String destinationPath)
	{
		File destination = new File(destinationPath);

		if (destination.equals(mCurrentPath))
		{
			Toast.makeText(RawDroid.this, R.string.warningSourceEqualsDestination, Toast.LENGTH_LONG).show();
			return;
		}

		long importSize = getSelectedImageSize();
		if (destination.getFreeSpace() < importSize)
		{
			Toast.makeText(RawDroid.this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
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
		for (MediaObject toImport : mSelectedImages)
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
			Toast.makeText(RawDroid.this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
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
	private List<MediaObject> storeSelectionForIntent()
	{
		return mItemsForIntent = (ArrayList<MediaObject>) mSelectedImages.clone();
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
				changeLogDialog.show();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onShareTargetSelected(ShareActionProvider source, Intent intent)
	{
		List<MediaObject> filesToShare = new ArrayList<MediaObject>();
		filesToShare.addAll(mSelectedImages);
		share(intent, filesToShare);
		mMode.finish();
		return true;
	}

	private void selectAll()
	{
		startContextualActionBar();
		mSelectedImages.addAll(mVisibleItems);
		updateSelectedItemsCount();
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
//		if (mRawImages.size() > 0 && mRawImages.get(0) instanceof MtpImage)
//		{
//			Toast.makeText(this, "Feature not supported for usb host.", Toast.LENGTH_LONG).show();
//		}

		List<MediaObject> filesToRename = new ArrayList<MediaObject>();
		String title;
		if (mSelectedImages.size() > 0)
		{
			title = String.format(getString(R.string.renameImages), getString(R.string.selected));
			filesToRename = mSelectedImages;
		}
		else
		{
			title = String.format(getString(R.string.renameImages), getString(R.string.all));
			filesToRename.addAll(mRawImages);
		}
		// TODO: This should manage raw and jpg
		new FormatDialog(this, title, filesToRename, new OnResponseListener()).show();
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
			Toast.makeText(RawDroid.this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
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
		// setPath(path.getPath());
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

	private void resetScrollLocation()
	{
		if (imageGrid != null)
			imageGrid.onRestoreInstanceState(gridState);
	}

	// private void setPath(String path)
	// {
	// File p = new File(path);
	// if (!p.exists())
	// {
	// Toast.makeText(getBaseContext(), R.string.warningInvalidPath, Toast.LENGTH_SHORT).show();
	// path = startPoint;
	// }
	//
	// this.path = path;
	// updatePath();
	// }

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
				mShareIntent = new Intent(Intent.ACTION_SEND);
				mShareIntent.setType("image/jpeg");
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

	public void updateSelectedItemsCount()
	{
		if (mMode != null)
		{
			mMode.setTitle(mSelectedImages.size() + " selected");
		}
		if (mSelectedImages.size() > 1)
		{
			mShareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
			if (mShareProvider != null)
				mShareProvider.setShareIntent(mShareIntent);
		}
		else
		{
			mShareIntent.setAction(Intent.ACTION_SEND);
			if (mShareProvider != null)
				mShareProvider.setShareIntent(mShareIntent);
		}
	}

	public class ImageAdapter extends BaseAdapter
	{
		private Context mContext;
		protected int mItemHeight = 0;
		protected int mNumColumns = 0;
		protected GridView.LayoutParams mImageViewLayoutParams;
		protected ImageDecoder mImageDecoder;

		public ImageAdapter(Context c)
		{
			mContext = c;
		}

		public ImageAdapter(Context c, ImageDecoder cache, int viewWidth, int thumbSize, int thumbSpacing)
		{
			super();
			mContext = c;
			mImageDecoder = cache;
			mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

			if (getNumColumns() == 0)
			{
				final int numColumns = (int) Math.floor(viewWidth / (thumbSize + thumbSpacing));
				if (numColumns > 0)
				{
					final int columnWidth = (viewWidth / numColumns) - thumbSpacing;
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
				LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				// view = inflater.inflate(R.layout.fileview, root)
				view = inflater.inflate(R.layout.fileview, parent, false);
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
				MediaObject media = getImage(position);
				if (mRawImages.contains(media) || mNativeImages.contains(media))
				{
					viewHolder.filename.setText(media.getName());
					mImageDecoder.loadImage(media, viewHolder.image);
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
			if (view.getLayoutParams().height != mItemHeight)
			{
				view.setLayoutParams(mImageViewLayoutParams);
			}

			return view;
		}

		public MediaObject getImage(int index)
		{
			int location = index - mFolders.size();
			if (location < 0)
				return null;
			return mVisibleItems.get(location);
		}
		
		private void addUniqueSelection(MediaObject media)
		{
			if (!mSelectedImages.contains(media))
				mSelectedImages.add(media);
		}

		public void addSelection(MediaObject media)
		{
			addUniqueSelection(media);
			updateSelectedItemsCount();
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
				MediaObject lastSelected = mSelectedImages.get(mSelectedImages.size() - 1);
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

			updateSelectedItemsCount();
			notifyDataSetChanged();
		}

		public void removeSelection(MediaObject media)
		{
			mSelectedImages.remove(media);
			updateSelectedItemsCount();
			notifyDataSetChanged();
		}

		public void clearSelection()
		{
			mSelectedImages.clear();
			updateSelectedItemsCount();
			notifyDataSetChanged();
		}

		public List<MediaObject> getSelectedItems()
		{
			return mSelectedImages;
		}

		public void toggleSelection(MediaObject media)
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
		MediaObject media = imageAdapter.getImage(position);

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

		MediaObject media = imageAdapter.getImage(position);
		if (multiSelectMode)
		{
			imageAdapter.toggleSelection(media);
			return;
		}

		int imageIndex = getImageId(media);
		if (imageIndex != -1)
		{
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(RawDroid.this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(PREFS_MOST_RECENT_FOLDER, mCurrentPath.getPath());
			editor.commit();

			Intent data = new Intent(RawDroid.this, ViewImage.class);
			data.setData(Uri.fromFile(new File(media.getPath())));

			startActivity(data);
		}
		else
		{
			Log.e(TAG, "Unhandled index: " + imageIndex);
			// alertRestart();
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		gridState = imageGrid.onSaveInstanceState();
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

	private class CopyImageTask extends AsyncTask<List<MediaObject>, String, Boolean> implements OnCancelListener
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
		protected Boolean doInBackground(List<MediaObject>... params)
		{
			boolean totalSuccess = true;
			List<MediaObject> copyList = params[0];

			importProgress.setMax(copyList.size());

			for (MediaObject toCopy : copyList)
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

	private class CopyThumbTask extends AsyncTask<List<MediaObject>, String, Boolean> implements OnCancelListener
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
		protected Boolean doInBackground(List<MediaObject>... params)
		{
			boolean totalSuccess = true;
			List<MediaObject> copyList = params[0];
			importProgress.setMax(copyList.size());
			for (MediaObject toExport : copyList)
			{
				publishProgress(toExport.getName());
				boolean result = toExport.copyThumb(mDestination);
				if (!result)
				{
					Log.e(TAG, "Error copying " + toExport.getFileSize());
					failed.add(toExport.getName());
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
		tutorial = ShowcaseView.insertShowcaseView(displayWidth - 50, displayHeight - 30, this, R.string.tutorial, R.string.tutorialStart, co);
		tutorial.overrideButtonClick(new TutorialClickListener());
	}

	private static enum Tutorials
	{
		Connection1, Connection2, Connection3, Connection4, Connection5, Memory, Directory, /*RecentFolder,*/ Usb, LongPressHelp, RecycleBin, LongPressSelect, SelectAll, SelectMode, SelectBetween, Import, Export, Rename, Delete, Share
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
					tutorial.setShowcasePosition(displayWidth - 49, displayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect1);
					break;
				case Connection2: // Connection
					tutorial.setShowcasePosition(displayWidth - 50, displayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect2);
					break;
				case Connection3: // Connection
					tutorial.setShowcasePosition(displayWidth - 49, displayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect3);
					break;
				case Connection4: // Connection
					tutorial.setShowcasePosition(displayWidth - 50, displayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect4);
					break;
				case Connection5: // Connection
					tutorial.setShowcasePosition(displayWidth - 49, displayHeight - 30);
					tutorial.setText(R.string.tutorialConnectTitle, R.string.tutorialConnect5);
					break;
				case Memory: // Memory
					tutorial.setShowcasePosition(displayWidth - 50, displayHeight - 30);
					tutorial.setText(R.string.tutorialMemoryTitle, R.string.tutorialMemory);
					break;
				case Directory: // Directory
					tutorial.setShowcaseItem(ShowcaseView.ITEM_TITLE_OR_SPINNER, 1, RawDroid.this);
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
					tutorial.setShowcaseContextItem(R.id.galleryImportImages, RawDroid.this);
					tutorial.setText(R.string.importImages, R.string.tutorialImport);
					break;
				case Export: // Export
					tutorial.setShowcaseContextItem(R.id.galleryExportThumbs, RawDroid.this);
					tutorial.setText(R.string.exportThumbnails, R.string.tutorialExport);
					break;
				case Rename: // Rename
					tutorial.setShowcaseContextItem(R.id.galleryRename, RawDroid.this);
					tutorial.setText(R.string.rename, R.string.tutorialRename);
					break;
				case Delete: // Delete
					tutorial.setShowcaseContextItem(R.id.gallery_delete, RawDroid.this);
					tutorial.setText(R.string.delete, R.string.tutorialDelete);
					break;
				case Share: // Share
					tutorial.setShowcaseContextItem(R.id.galleryShare, RawDroid.this);
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
			File[] matches = dir.listFiles(imageFilter);
			if (matches != null && matches.length > 0)
				imageFolders.add(dir.getPath());

			// Recursion pass
			for (File f : dir.listFiles())
			{
				if (f == null)
					continue;

				if (f.isDirectory() && f.canRead())
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

	private void checkLicense()
	{
		Log.i(TAG, "Request License");
		Intent i = new Intent();
		i.setAction(LICENSE_REQUEST);
		sendBroadcast(i, PERMISSION);
	}

	private void closeTutorial()
	{
		mMode.finish();
		tutorial.hide();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key == FullSettingsActivity.KEY_ShowXmpFiles || 
			key == FullSettingsActivity.KEY_ShowNativeFiles || 
			key == FullSettingsActivity.KEY_ShowUnknownFiles)
		{
			updateLocalFiles();
		}
	}
}
