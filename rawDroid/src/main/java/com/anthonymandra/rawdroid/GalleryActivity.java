package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.text.Html;
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
import android.widget.Checkable;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.KeywordProvider;
import com.anthonymandra.content.Meta;
import com.anthonymandra.dcraw.LibRaw.Margins;
import com.anthonymandra.framework.CoreActivity;
import com.anthonymandra.framework.ImageCache.ImageCacheParams;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.ImageUtils;
import com.anthonymandra.framework.License;
import com.anthonymandra.framework.LocalImage;
import com.anthonymandra.framework.MetaService;
import com.anthonymandra.framework.MetaWakefulReceiver;
import com.anthonymandra.framework.RawObject;
import com.anthonymandra.framework.SearchService;
import com.anthonymandra.framework.SwapProvider;
import com.anthonymandra.framework.Util;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.widget.LoadingImageView;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.inscription.WhatsNewDialog;

import org.openintents.filemanager.FileManagerActivity;
import org.openintents.intents.FileManagerIntents;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GalleryActivity extends CoreActivity implements OnItemClickListener, OnItemLongClickListener, OnScrollListener,
        ShareActionProvider.OnShareTargetSelectedListener, OnSharedPreferenceChangeListener, LoaderManager.LoaderCallbacks<Cursor>
{
	private static final String TAG = GalleryActivity.class.getSimpleName();

	private IntentFilter mResponseIntentFilter = new IntentFilter();

	public static final String KEY_STARTUP_DIR = "keyStartupDir";

	public static final String LICENSE_RESULT = "license_result";
	public static final int LICENSE_ALLOW = 1;
	public static final int LICENSE_DISALLOW = 2;
	public static final int LICENSE_ERROR = 3;

	// Preference fields
	public static final String PREFS_NAME = "RawDroidPrefs";
	public static final boolean PREFS_AUTO_INTERFACE_DEFAULT = true;
	public static final String PREFS_MOST_RECENT_SAVE = "mostRecentSave";
	public static final String PREFS_MOST_RECENT_IMPORT = "mostRecentImport";
	public static final String PREFS_VERSION_NUMBER = "prefVersionNumber";
	public static final String PREFS_SHOW_FILTER_HINT = "prefShowFilterHint";
	public static final String PREFS_LAST_BETA_VERSION = "prefLastBetaVersion";
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

	private static final String[] MOUNT_ROOTS =
	{
			"/mnt",
			"/Removable",
			"/udisk",
			"/usbStorage",
			"/storage",
			"/dev/bus/usb",
	};

	/**
	 * Android multi-user environment is fucked up.  Multiple mount points
	 * under /storage/emulated that point to same filesystem
	 * /storage/emulated/legacy, /storage/emulated/[integer user value]
	 * Then add dozens of symlinks, making searching an absolute nightmare
	 * For now assume symlink and skip anything under /storage/emulated
	 */
	private static final String[] SKIP_ROOTS =
	{
			//TODO: Create an additional regex string list to skip folders like */Android/, */data/
			"/storage/emulated"
	};

	// Request codes
	private static final int REQUEST_MOUNTED_IMPORT_DIR = 12;
	private static final int REQUEST_EXPORT_THUMB_DIR = 15;
    private static final int REQUEST_UPDATE_PHOTO = 16;

	public static final String GALLERY_INDEX_EXTRA = "gallery_index";

	public static final File START_PATH = new File("/mnt");
	private static final File ROOT = new File("/");
	private Parcelable gridState;

    private static boolean inTutorial = false;
	private static boolean inActionMode = false;

	// Widget handles
	private GridView mImageGrid;

	// Image processing
	private int mImageThumbSize;
	private int mImageThumbSpacing;
	private ImageDecoder mImageDecoder;
	private GalleryAdapter mGalleryAdapter;
	protected List<Uri> mItemsForIntent = new ArrayList<>();

	// Selection support
	private boolean multiSelectMode;

	private int mDisplayWidth;
	private int mDisplayHeight;

	private ActionMode mContextMode;

	// private int tutorialStage;
	private ShowcaseView tutorial;
    private Toolbar mToolbar;
	private ProgressBar mProgressBar;
	private ActionBarDrawerToggle mDrawerToggle;
	private DrawerLayout mDrawerLayout;
//	private SwipeRefreshLayout mSwipeRefresh;
	private static int mParsedImages;

	private boolean isFirstDatabaseLoad = true;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.gallery);
        mToolbar = (Toolbar) findViewById(R.id.galleryToolbar);
		mToolbar.setNavigationIcon(R.drawable.ic_action_filter);
		mProgressBar = (ProgressBar) findViewById(R.id.toolbarSpinner);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setLogo(R.mipmap.ic_launcher);

//		mSwipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
//		mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
//		{
//			@Override
//			public void onRefresh()
//			{
//				scanRawFiles();
//			}
//		});

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerToggle = new ActionBarDrawerToggle(
				this,
				mDrawerLayout,
				mToolbar,
				R.string.drawer_open,  /* "open drawer" description */
				R.string.drawer_close  /* "close drawer" description */);

		// Set the drawer toggle as the DrawerListener
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

//		doFirstRun();

		AppRater.app_launched(this);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		mDisplayWidth = metrics.widthPixels;
		mDisplayHeight = metrics.heightPixels;

        inTutorial = false;

		mImageGrid = ((GridView) findViewById(R.id.gridview));
		mImageGrid.setOnScrollListener(this);
		mImageGrid.setOnItemClickListener(this);
		mImageGrid.setOnItemLongClickListener(this);

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

		mGalleryAdapter = new GalleryAdapter(this, null);
		mImageGrid.setAdapter(mGalleryAdapter);

        licenseHandler = new LicenseHandler(this);

		mResponseIntentFilter.addAction(MetaService.BROADCAST_IMAGE_PARSED);
		mResponseIntentFilter.addAction(MetaService.BROADCAST_PARSE_COMPLETE);
		mResponseIntentFilter.addAction(SearchService.BROADCAST_FOUND);
		LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				switch(intent.getAction())
				{
					case MetaService.BROADCAST_IMAGE_PARSED:
						mParsedImages++;
						mToolbar.setSubtitle(new StringBuilder()
								.append("Processed ")
								.append(mParsedImages)
								.append(" of ")
								.append(mGalleryAdapter.getCount()));
						break;
					case MetaService.BROADCAST_PARSE_COMPLETE:
						mProgressBar.setVisibility(View.GONE);
						mToolbar.setSubtitle("");
						break;
					case SearchService.BROADCAST_FOUND:
//						mSwipeRefresh.setRefreshing(false);
						String[] images = intent.getStringArrayExtra(SearchService.EXTRA_IMAGES);
						mParsedImages = 0;
						for (String image : images)
						{
							MetaWakefulReceiver.startMetaService(GalleryActivity.this, Uri.fromFile(new File(image)));
						}
						break;
				}
			}
		}, mResponseIntentFilter);
		setImageCountTitle();

		if (getIntent().getData() != null)
		{
			try
			{
				InputStream is = getContentResolver().openInputStream(getIntent().getData());

				// Attempt to import keywords
				// TODO: We should check the format first
				boolean success = KeywordProvider.importKeywords(this, new InputStreamReader(is));
				int message;
				if (success)
				{
					message = R.string.resultImportSuccessful;
				}
				else
				{
					message = R.string.resultImportFailed;
				}
				Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
			}
			catch (FileNotFoundException e)
			{
				e.printStackTrace();
			}
		}

//		checkWriteAccess();
		getLoaderManager().initLoader(META_LOADER_ID, getIntent().getBundleExtra(EXTRA_META_BUNDLE), this);
	}

	protected void updateMetaLoaderXmp(XmpBaseFragment.XmpValues xmp, boolean andTrueOrFalse, boolean sortAscending, boolean segregateByType, XmpFilterFragment.SortColumns sortColumn)
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
		StringBuilder sort = new StringBuilder();

		if (segregateByType)
		{
			sort.append(Meta.Data.TYPE).append(" ASC, ");
		}
		switch (sortColumn)
		{
			case Date: sort.append(Meta.Data.TIMESTAMP).append(order); break;
			case Name: sort.append(Meta.Data.NAME).append(order); break;
			default: sort.append(Meta.Data.NAME).append(" ASC");
		}

		updateMetaLoader(null, selection.toString(), selectionArgs.toArray(new String[selectionArgs.size()]), sort.toString());
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
		getLoaderManager().restartLoader(CoreActivity.META_LOADER_ID, metaLoader, this);
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
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		mDrawerToggle.syncState();
		mToolbar.setNavigationIcon(R.drawable.ic_action_filter);

		loadXmpFilter();	//must be done here due to fragment/activity lifecycle

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		if (settings.getBoolean(PREFS_SHOW_FILTER_HINT, true))
		{
			mDrawerLayout.openDrawer(GravityCompat.START);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(PREFS_SHOW_FILTER_HINT, false);
			editor.apply();
		}
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
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // Do nothing
                }
            });
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
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

	private void loadXmpFilter()
	{
		XmpFilterFragment xmpFilter = (XmpFilterFragment) getSupportFragmentManager().findFragmentById(R.id.filterFragment);
		xmpFilter.registerXmpFilterChangedListener(new XmpFilterFragment.MetaFilterChangedListener()
		{
			@Override
			public void onMetaFilterChanged(XmpBaseFragment.XmpValues xmp, boolean andTrueOrFalse, boolean sortAscending, boolean segregateByType, XmpFilterFragment.SortColumns sortColumn)
			{
				updateMetaLoaderXmp(xmp, andTrueOrFalse, sortAscending, segregateByType,  sortColumn);
			}
		});

		// load filter data initially (must be done here due to
		updateMetaLoaderXmp(
				xmpFilter.getXmpValues(),
				xmpFilter.getAndOr(),
				xmpFilter.getAscending(),
				xmpFilter.getSegregate(),
				xmpFilter.getSortCoumn());
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		mGalleryAdapter.swapCursor(cursor);
		setImageCountTitle();

		if (isFirstDatabaseLoad && mGalleryAdapter.getCount() == 0)
			offerInitDatabase();
		isFirstDatabaseLoad = false;
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		mGalleryAdapter.swapCursor(null);
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
		//TODO: Check if galleryadapter selection ids should be stored here (savestate)
		super.onResume();

		// Launch what's new dialog (will only be shown once)
		final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
		whatsNewDialog.show(Constants.VariantCode == 8);

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final int versionShown = prefs.getInt(PREFS_LAST_BETA_VERSION, 0);
		if (versionShown != getAppVersionCode())
		{
			AlertDialog.Builder builder =
					new AlertDialog.Builder(this).
							setTitle(R.string.betaWelcomeTitle).
							setMessage(Html.fromHtml(getString(R.string.betaWelcomeMessage))).
							setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
								{
									//Do nothing
								}
							});
			builder.create().show();

			//Update last shown version
			final SharedPreferences.Editor edit = prefs.edit();
			edit.putInt(PREFS_LAST_BETA_VERSION, getAppVersionCode());
			edit.apply();
		}

		mImageDecoder.setExitTasksEarly(false);
		mGalleryAdapter.notifyDataSetChanged();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mImageDecoder.setPauseWork(false);
		mImageDecoder.setExitTasksEarly(true);
		mImageDecoder.flushCache();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mImageDecoder.closeCache();
	}

	//Get the current app version
	private int getAppVersionCode() {
		try {
			final PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (PackageManager.NameNotFoundException ignored) {
			return 0;
		}
	}

	private void offerInitDatabase()
	{
		ImageView image = new ImageView(this);
		image.setImageResource(R.drawable.ic_action_refresh);

		AlertDialog.Builder builder =
				new AlertDialog.Builder(this).
						setTitle(R.string.offerSearchTitle).
						setMessage(R.string.offerSearchMessage).
						setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								scanRawFiles();
							}
						}).
						setNegativeButton(R.string.neutral, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								//do nothing
							}
						}).
								setView(image);
		builder.create().show();
	}

	protected void setImageCountTitle()
	{
		getSupportActionBar().setTitle(mGalleryAdapter.getCount() + " Images");
	}

	protected void scanRawFiles()
	{
		mProgressBar.setVisibility(View.VISIBLE);
		SearchService.startActionSearch(GalleryActivity.this, MOUNT_ROOTS, SKIP_ROOTS);
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
                    handlePhotoUpdate(data.getIntExtra(GALLERY_INDEX_EXTRA, 0));
                }
		}
	}

    private void handlePhotoUpdate(int index)
    {
		mImageGrid.smoothScrollToPosition(index);
    }

	private void handleImportDirResult(final String destinationPath)
	{
		File destination = new File(destinationPath);
        if (!destination.canWrite())
        {
            showWriteAccessError();
        }

		//TODO: Confirm this still works (code that should handle is in the copy task)
//		if (destination.equals(mCurrentPath))
//		{
//			Toast.makeText(this, R.string.warningSourceEqualsDestination, Toast.LENGTH_LONG).show();
//			return;
//		}

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
		ct.execute(getImageListFromUriList(mItemsForIntent));
	}

	private long getSelectedImageSize()
	{
		long selectionSize = 0;
		for (Uri selected : mGalleryAdapter.getSelectedItems())
		{
			File toImport = new File(selected.getPath());
			selectionSize += toImport.length();
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
        editor.putString(GalleryActivity.PREFS_MOST_RECENT_SAVE, destination.getPath());
        editor.apply();

        CopyThumbTask ct = new CopyThumbTask(destination);
        ct.execute(getImageListFromUriList(mItemsForIntent));
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

	private void storeSelectionForIntent()
	{
		mItemsForIntent = mGalleryAdapter.getSelectedItems();
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
				getContentResolver().delete(Meta.Data.CONTENT_URI, null, null);
				Toast.makeText(this, R.string.cacheCleared, Toast.LENGTH_SHORT).show();
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
//			case R.id.gallerySettings:
//				requestSettings();
//				return true;
			case R.id.gallery_recycle:
            case R.id.context_recycle:
				showRecycleBin();
				return true;
			case R.id.gallerySelectAll:
            case R.id.contextSelectAll:
				selectAll();
				return false;
			case R.id.galleryRefresh:
				//TODO: probably don't want to delete existing
				getContentResolver().delete(Meta.Data.CONTENT_URI, null, null);
				scanRawFiles();
				return true;
//			case R.id.galleryContact:
//				requestEmailIntent();
//				return true;
			case R.id.galleryTutorial:
				runTutorial();
				return true;
//			case R.id.galleryAbout:
//				final ChangeLogDialog changeLogDialog = new ChangeLogDialog(this);
//				changeLogDialog.show(Constants.VariantCode == 8);
//				return true;
			case R.id.gallerySd:
				requestWritePermission();
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

	private List<MediaItem> getImageListFromUriList(List<Uri> uris)
	{
		List<MediaItem> images = new ArrayList<>();
		for (Uri u: uris)
		{
			File f = new File(u.getPath());
			images.add(new LocalImage(this, f));
		}
		return images;
	}

	private void requestRename()
	{
		if (mGalleryAdapter.getSelectedItemCount() == 0)
			mGalleryAdapter.selectAll();

		storeSelectionForIntent();

		FormatDialog dialog = new FormatDialog(this, getImageListFromUriList(mItemsForIntent));
        dialog.setTitle(getString(R.string.renameImages));
        dialog.setDialogListener(new FormatDialog.DialogListener()
		{
			@Override //FIXME: Rename needs to update the database!
			public void onCompleted()
			{
//				updatePath(mCurrentPath);
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
        Intent intent = new Intent(this, FileManagerActivity.class);
		intent.setAction(FileManagerIntents.ACTION_PICK_DIRECTORY);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String recentImport = settings.getString(GalleryActivity.PREFS_MOST_RECENT_IMPORT, null);

		//TODO: Better start location?
		File importLocation = ROOT;
		if (recentImport != null)
		{
			importLocation = new File(recentImport);
			if (!importLocation.exists())
			{
				importLocation = ROOT;
			}
		}
		if (importLocation == null)
			importLocation = START_PATH;

		// Construct URI from file name.
		intent.setData(Uri.fromFile(importLocation));

		// Set fancy title and button (optional)
		intent.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.chooseDestination));
		intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.import1));

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
        Intent intent = new Intent(this, FileManagerActivity.class);
		intent.setAction(FileManagerIntents.ACTION_PICK_DIRECTORY);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String recentExport = settings.getString(GalleryActivity.PREFS_MOST_RECENT_SAVE, null);

		//TODO: Better start location?
		File exportLocation = ROOT;
		if (recentExport != null)
		{
			exportLocation = new File(recentExport);
			if (!exportLocation.exists())
			{
				exportLocation = ROOT;
			}
		}

		// Construct URI from file name.
		intent.setData(Uri.fromFile(exportLocation));

		// Set fancy title and button (optional)
		intent.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.exportThumbnails));
		intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.export));
		startActivityForResult(intent, REQUEST_EXPORT_THUMB_DIR);
    }

	@Override
	protected void updateAfterDelete()
	{
		// Not needed with a cursorloader
	}

	@Override
	protected void updateAfterRestore()
	{
		// Not needed with a cursorloader
	}

	@Override
	protected void addImage(MediaItem item)
	{
		addDatabaseReference(item);
	}

	@Override
	protected void removeImage(MediaItem item)
	{
		removeDatabaseReference(item);
	}

	protected boolean removeDatabaseReference(RawObject toRemove)
	{
		int rowsDeleted = getContentResolver().delete(
				Meta.Data.CONTENT_URI,
				Meta.Data.URI + " = ?",
				new String[] {toRemove.getUri().toString()});
		return rowsDeleted > 0;
	}

	protected Uri addDatabaseReference(RawObject toAdd)
	{
		return getContentResolver().insert(
				Meta.Data.CONTENT_URI,
				ImageUtils.getContentValues(this, toAdd.getUri()));
	}

	@Override
	public void onBackPressed()
	{
		if (inTutorial)
		{
			closeTutorial();
		}
		else
		{
			super.onBackPressed();
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
				mShareProvider.setOnShareTargetSelectedListener(GalleryActivity.this);
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
			mGalleryAdapter.endMultiSelectMode();
            mContextMode = null;
		}
	}

	public class GalleryAdapter extends CursorAdapter
	{
		//TODO: prepopulate colors
		private final Context mContext;
		private final LayoutInflater mInflater;
		protected int mItemHeight = 0;
		protected int mNumColumns = 0;
		protected GridView.LayoutParams mImageViewLayoutParams;
		private Set<Uri> mSelectedItems = new HashSet<>();
		private Set<Long> mSelectedIds = new HashSet<>();
		private TreeSet<Integer> mSelectedPositions = new TreeSet<>();

		public GalleryAdapter(Context context, Cursor c)
		{
			super(context, c, false);
			mContext = context;
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
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			final View view = mInflater.inflate(R.layout.fileview, parent, false);
			view.setLayoutParams(mImageViewLayoutParams);

			LoadingImageView image = (LoadingImageView) view.findViewById(R.id.webImageView);
			image.setScaleType(ImageView.ScaleType.CENTER_CROP);

			view.setTag(R.id.webImageView, image);
			view.setTag(R.id.filenameView, view.findViewById(R.id.filenameView));
			view.setTag(R.id.galleryRatingBar, view.findViewById(R.id.galleryRatingBar));
			view.setTag(R.id.label, view.findViewById(R.id.label));
			return view;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			LoadingImageView imageView = (LoadingImageView) view.getTag(R.id.webImageView);
			TextView fileName = (TextView) view.getTag(R.id.filenameView);
			View label = (View) view.getTag(R.id.label);
			RatingBar rating = (RatingBar) view.getTag(R.id.galleryRatingBar);
			rating.setRating(cursor.getFloat(cursor.getColumnIndex(Meta.Data.RATING)));

			Uri uri = Uri.parse(cursor.getString(Meta.URI_COLUMN));
			String labelString = cursor.getString(Meta.LABEL_COLUMN);
			int rotation = ImageUtils.getRotation(cursor.getInt(Meta.ORIENTATION_COLUMN));
			imageView.setRotation(rotation);

			if (labelString != null)
			{
				switch (labelString.toLowerCase())
				{
					case "purple":
						view.setBackgroundColor(getResources().getColor(R.color.startPurple));
						label.setVisibility(View.VISIBLE);
						label.setBackgroundColor(getResources().getColor(R.color.startPurple));
						break;
					case "blue":
						view.setBackgroundColor(getResources().getColor(R.color.startBlue));
						label.setVisibility(View.VISIBLE);
						label.setBackgroundColor(getResources().getColor(R.color.startBlue));
						break;
					case "yellow":
						view.setBackgroundColor(getResources().getColor(R.color.startYellow));
						label.setVisibility(View.VISIBLE);
						label.setBackgroundColor(getResources().getColor(R.color.startYellow));
						break;
					case "green":
						view.setBackgroundColor(getResources().getColor(R.color.startGreen));
						label.setVisibility(View.VISIBLE);
						label.setBackgroundColor(getResources().getColor(R.color.startGreen));
						break;
					case "red":
						view.setBackgroundColor(getResources().getColor(R.color.startRed));
						label.setVisibility(View.VISIBLE);
						label.setBackgroundColor(getResources().getColor(R.color.startRed));
						break;
					default:
						view.setBackgroundColor(0);
						label.setVisibility(View.GONE);
						break;
				}
			}
			else
			{
				view.setBackgroundColor(0);
				label.setVisibility(View.GONE);
			}
			File image = new File(uri.getPath());
			fileName.setText(cursor.getString(cursor.getColumnIndex(Meta.Data.NAME)));
			mImageDecoder.loadImage(new LocalImage(mContext, image), imageView);
			((Checkable) view).setChecked(mSelectedItems.contains(uri));
		}

		public MediaItem getImage(int position)
		{
			return new LocalImage(mContext, new File(getUri(position).getPath()));
		}

		private Uri getUri(int position)
		{
			Cursor c = (Cursor)getItem(position);
			return Uri.parse(c.getString((c.getColumnIndex(Meta.Data.URI))));
		}

		public List<Uri> getSelectedItems()
		{
			return new ArrayList<Uri>(mSelectedItems);
		}

		public int getSelectedItemCount()
		{
			return mSelectedItems.size();
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

		public void addBetween(int start, int end)
		{
			for (int i = start; i <= end; i++)
			{
				if (getCursor().moveToPosition(i))
				{
					addSelection(getUri(i), i);
				}
			}
			updateSelection();
			notifyDataSetChanged();
		}

		public void addBetweenSelection(int position)
		{
			if (mSelectedPositions.size() > 0)
			{
				int first = mSelectedPositions.first();
				int last = mSelectedPositions.last();
				if (position > last)
				{
					addBetween(last, position);
				}
				else if (position < first)
				{
					addBetween(position, first);
				}
			}
			else
			{
				addBetween(0, position);
			}
		}

		/**
		 * Add a selection and update the view
		 * @param uri of selection
		 * @param position of selection
		 */
		private void addSelection(View view, Uri uri, int position)
		{
			addSelection(uri, position);
			((Checkable)view).setChecked(true);
		}

		/**
		 * Add a selection without updating the view
		 * This will generally require a call to notifyDataSetChanged()
		 * @param uri of selection
		 * @param position of selection
		 */
		private void addSelection(Uri uri, int position)
		{
			mSelectedItems.add(uri);
			mSelectedPositions.add(position);
			mSelectedIds.add(getItemId(position));
		}

		private void removeSelection(View view, Uri uri, int position)
		{
			mSelectedItems.remove(uri);
			mSelectedPositions.remove(position);
			mSelectedIds.remove(getItemId(position));
			((Checkable)view).setChecked(false);
		}

		public void clearSelection()
		{
			mSelectedItems.clear();
			mSelectedPositions.clear();
			updateSelection();
			notifyDataSetChanged();
		}

		public void toggleSelection(View v, int position)
		{
			Uri uri = getUri(position);
			if (mSelectedItems.contains(uri))
			{
				removeSelection(v, uri, position);
			}
			else
			{
				addSelection(v, uri, position);
			}
			updateSelection();
		}

		public void updateSelection()
		{
			ArrayList<Uri> arrayUri = new ArrayList<>();
			for (Uri selection : mSelectedItems)
			{
				arrayUri.add(SwapProvider.getSwapUri(new File(selection.getPath())));
			}

			if (mContextMode != null)
			{
				mContextMode.setTitle("Select Items");
				mContextMode.setSubtitle(mSelectedItems.size() + " selected");
			}
			if (mSelectedItems.size() == 1)
			{
				setShareUri(arrayUri.get(0));
			}
			else if (mSelectedItems.size() > 1)
			{
				setShareUri(arrayUri);
			}
		}

		public void selectAll()
		{
			if (getCursor().moveToFirst())
			{
				do
				{
					mSelectedItems.add(getUri(getCursor().getPosition()));
				} while (getCursor().moveToNext());
			}

			updateSelection();
			notifyDataSetChanged();
		}

		public void startMultiSelectMode()
		{
			clearSelection(); // Ensure we don't have any stragglers
			multiSelectMode = true;
		}

		public void endMultiSelectMode()
		{
			multiSelectMode = false;
			clearSelection();
		}
	}

	public void selectAll()
	{
		startContextualActionBar();
		mGalleryAdapter.selectAll();
	}

	private void startContextualActionBar()
	{
		mContextMode = startSupportActionMode(new GalleryActionMode());
		mGalleryAdapter.startMultiSelectMode();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		MediaItem media = mGalleryAdapter.getImage(position);

		if (media == null || media.isDirectory())
			return false;

		// If we're in multi-select select all items between
		if (multiSelectMode)
		{
			mGalleryAdapter.addBetweenSelection(position);
		}
		// Enter multi-select
		else
		{
			startContextualActionBar();
			mGalleryAdapter.toggleSelection(view, position);
		}

		return true;
	}

    @TargetApi(VERSION_CODES.JELLY_BEAN)
	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id)
	{
		Uri uri = mGalleryAdapter.getUri(position);
		if (multiSelectMode)
		{
			mGalleryAdapter.toggleSelection(v, position);
			return;
		}

        if (inTutorial)
        {
            Toast.makeText(this, R.string.tutorialDisabled, Toast.LENGTH_SHORT).show();
            return;
        }

		Intent viewer = new Intent(this, ViewerChooser.class);//getViewerIntent();
		viewer.setData(uri);

		Bundle options = new Bundle();
        if (Util.hasJellyBean())
        {
            // makeThumbnailScaleUpAnimation() looks kind of ugly here as the loading spinner may
            // show plus the thumbnail image in GridView is cropped. so using
            // makeScaleUpAnimation() instead.
            options.putAll(
					ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle());
        }

		Bundle metaLoader = getCurrentMetaLoaderBundle();
		viewer.putExtra(EXTRA_META_BUNDLE, metaLoader);
		viewer.putExtra(ViewerActivity.EXTRA_START_INDEX, position);

		startActivityForResult(viewer, REQUEST_UPDATE_PHOTO, options);
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
		private List<String> skipped = new ArrayList<>();
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
			importProgress = new ProgressDialog(GalleryActivity.this);
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
				// Skip a file if it's the same location
				File intendedDestination = new File(mDestination, toCopy.getName());
				if (intendedDestination.exists())
				{
					skipped.add(intendedDestination.getName());
					continue;
				}

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
			mGalleryAdapter.notifyDataSetChanged();

			if (failed.size() > 0)
			{
				String failures = "Failed files: ";
				for (String fail : failed)
				{
					failures += fail + ", ";
				}
				Toast.makeText(GalleryActivity.this, failures, Toast.LENGTH_LONG).show();
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

	private class CopyThumbTask extends AsyncTask<List<MediaItem>, String, Boolean> implements OnCancelListener
	{
		private ProgressDialog importProgress;
		private File mDestination;
		List<String> failed = new ArrayList<>();

		//TODO: This could be multithreaded.
		public CopyThumbTask(File destination)
		{
			mDestination = destination;
		}

		@Override
		protected void onPreExecute()
		{
			importProgress = new ProgressDialog(GalleryActivity.this);
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
			
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(GalleryActivity.this);
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
				//TODO: Since this is calling Width it's only accurate if we use full decode.
                watermark = Util.getDemoWatermark(GalleryActivity.this, copyList.get(0).getWidth());
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
                    Toast.makeText(GalleryActivity.this, R.string.warningBlankWatermark, Toast.LENGTH_LONG).show();
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
			//FIXME
			mGalleryAdapter.notifyDataSetChanged();

			if (failed.size() > 0)
			{
				String failures = "Failed files: ";
				for (String fail : failed)
				{
					failures += fail + ", ";
				}
				failures += "\nIf you are watermarking, check settings/sizes!";
				Toast.makeText(GalleryActivity.this, failures, Toast.LENGTH_LONG).show();
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
		//FIXME
//		previousPath = mCurrentPath;
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

		//FIXME: This should just push the four files into and empty db
//        updatePath(tutorialDirectory);
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
					// FIXME: NOT NEEDED
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialFindTitle));
                    tutorial.setContentText(getString(R.string.tutorialFindUSBText));
//                    setTutorialActionView(R.id.gallery_usb, false);
					break;
				case 6: // Find Import
                    closeOptionsMenu(); //if overflow was shown
                    tutorial.setContentText(getString(R.string.tutorialFindImportText));
                    setTutorialNoShowcase();
					break;
				case 7: // Find Path
					//FIXME: NOT NEEDED
                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialFindPathText));
//					setTutorialActionView(R.id.navSpinner, true);
					break;
				case 8: // Recent Folder
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialRecentFolderText));
                    setTutorialNoShowcase();
					break;
				case 9: // Find Images
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialFindImagesText));
                    setTutorialActionView(R.id.galleryRefresh, false);
					break;
				case 10: // Long Select
                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialSelectTitle));
                    tutorial.setContentText(getString(R.string.tutorialSingleSelectText));
                    view = mImageGrid.getChildAt(0);
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
					break;
				case 11: // Add select
					// If the user is lazy select for them
					if (!inActionMode)
						onItemLongClick(mImageGrid, mImageGrid.getChildAt(0), 0, 0);

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText));
                    view = mImageGrid.getChildAt(2);
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
					break;
				case 12: // Select feedback
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 2)
					{
						mContextMode.finish();
						onItemLongClick(mImageGrid, mImageGrid.getChildAt(0), 0, 0);
						onItemClick(mImageGrid, mImageGrid.getChildAt(2), 2, 2);
					}

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText2));
                    // This is ghetto, I know the spinner lies UNDER the selection view
					//FIXME: Need something to point at
//					setTutorialActionView(R.id.navSpinner, true);
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
					if (mGalleryAdapter.getSelectedItemCount() < 1)
					{
						mGalleryAdapter.selectAll();
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
                    view = mImageGrid.getChildAt(3);		//WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
                    break;
                case 16: // Select between end
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 1)
						onItemLongClick(mImageGrid, mImageGrid.getChildAt(1), 1, 1);

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText2));

					setTutorialHomeView(true);
                    view = mImageGrid.getChildAt(1);	//WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
                    break;
                case 17: // Select between feedback
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 2)
					{
						onItemLongClick(mImageGrid, mImageGrid.getChildAt(1), 1, 1);
						onItemLongClick(mImageGrid, mImageGrid.getChildAt(3), 3, 3);
					}

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText3));
                    // This is ghetto, I know the spinner lies UNDER the selection view
					//FIXME: need something to point at
//					setTutorialActionView(R.id.navSpinner, true);
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
		//FIXME: Database backup?
//		updatePath(previousPath);
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(FullSettingsActivity.KEY_ShowXmpFiles)
                || key.equals(FullSettingsActivity.KEY_ShowNativeFiles)
                || key.equals(FullSettingsActivity.KEY_ShowUnknownFiles))
		{
			//FIXME: Setting currently useless
//			updateLocalFiles();
		}
	}
}
