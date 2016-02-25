package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.UriPermission;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.CoreActivity;
import com.anthonymandra.framework.FileUtil;
import com.anthonymandra.framework.ImageCache.ImageCacheParams;
import com.anthonymandra.framework.ImageDecoder;
import com.anthonymandra.framework.MetaService;
import com.anthonymandra.framework.MetaWakefulReceiver;
import com.anthonymandra.framework.SearchService;
import com.anthonymandra.framework.SwapProvider;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.framework.Util;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.util.DbUtil;
import com.anthonymandra.util.ImageUtils;
import com.anthonymandra.widget.GalleryRecyclerAdapter;
import com.anthonymandra.widget.ItemOffsetDecoration;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.crashlytics.android.ndk.CrashlyticsNdk;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.Target;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.inscription.WhatsNewDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.fabric.sdk.android.Fabric;

public class GalleryActivity extends CoreActivity
		implements
		GalleryRecyclerAdapter.OnItemClickListener,
		GalleryRecyclerAdapter.OnItemLongClickListener,
		OnScrollListener,
        ShareActionProvider.OnShareTargetSelectedListener,
		OnSharedPreferenceChangeListener,
		LoaderManager.LoaderCallbacks<Cursor>,
		GalleryRecyclerAdapter.OnSelectionUpdatedListener
{
	private static final String TAG = GalleryActivity.class.getSimpleName();

	private enum WriteResume
	{
		Search
	}

	private IntentFilter mResponseIntentFilter = new IntentFilter();

	public static final String KEY_STARTUP_DIR = "keyStartupDir";

	public static final String LICENSE_RESULT = "license_result";
	public static final int LICENSE_ALLOW = 1;
	public static final int LICENSE_DISALLOW = 2;
	public static final int LICENSE_ERROR = 3;

	// Preference fields
	public static final String PREFS_NAME = "RawDroidPrefs";
	public static final boolean PREFS_AUTO_INTERFACE_DEFAULT = true;
	public static final String PREFS_VERSION_NUMBER = "prefVersionNumber";
	public static final String PREFS_SHOW_FILTER_HINT = "prefShowFilterHint";
	public static final String PREFS_LAST_BETA_VERSION = "prefLastBetaVersion";
	public static final String IMAGE_CACHE_DIR = "thumbs";
	public static final String PREFS_PERMISSIBLE_USB = "prefPermissibleUsb";
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

	private static final String[] TEST_ROOTS =
			{
					"/storage/emulated",
			};

	private static final String[] GALLERY_PROJECTION =
			{
					Meta.Data._ID,
					Meta.Data.URI,
					Meta.Data.ORIENTATION,
					Meta.Data.RATING,
					Meta.Data.SUBJECT,
					Meta.Data.TIMESTAMP,
					Meta.Data.LABEL
			};

	// Request codes
	private static final int REQUEST_COPY_DIR = 12;
	private static final int REQUEST_SAVE_JPG_DIR = 15;
    private static final int REQUEST_UPDATE_PHOTO = 16;
	private static final int REQUEST_ACCESS_USB = 17;

	public static final String GALLERY_INDEX_EXTRA = "gallery_index";

	public static final File START_PATH = new File("/mnt");
	private static final File ROOT = new File("/");
	private Parcelable gridState;

    private static boolean inTutorial = false;
	private static boolean inActionMode = false;

	// Widget handles
	private RecyclerView mImageGrid;
	private GridLayoutManager mGridLayout;

	// Image processing
	private ImageDecoder mImageDecoder;
	private GalleryRecyclerAdapter mGalleryAdapter;
	/**
	 * Stores uris when lifecycle is interrupted (ie: requesting a destination folder)
	 */
	protected List<Uri> mItemsForIntent = new ArrayList<>();

	// Selection support
	private boolean multiSelectMode;

	private int mDisplayWidth;
	private int mDisplayHeight;

	private ActionMode mContextMode;

	// private int tutorialStage;
	private ShowcaseView tutorial;
    private Toolbar mToolbar;
	private XmpFilterFragment mXmpFilterFragment;
	private ProgressBar mProgressBar;
	private ActionBarDrawerToggle mDrawerToggle;
	private DrawerLayout mDrawerLayout;
//	private SwipeRefreshLayout mSwipeRefresh;
	private static int mParsedImages;

	@Override
	public int getContentView()
	{
		return R.layout.gallery;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Crashlytics crashlyticsKit = new Crashlytics.Builder()
				.core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
				.build();

		Fabric.with(this, crashlyticsKit, new CrashlyticsNdk());
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		toggleEditXmpFragment(); // Keep fragment visible in designer, but hide initially

        mToolbar = (Toolbar) findViewById(R.id.galleryToolbar);
		mToolbar.setNavigationIcon(R.drawable.ic_action_filter);
		mProgressBar = (ProgressBar) findViewById(R.id.toolbarSpinner);
        setSupportActionBar(mToolbar);
//        getSupportActionBar().setLogo(R.mipmap.ic_launcher);

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
//		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//		getSupportActionBar().setHomeButtonEnabled(true);

//		doFirstRun();

		AppRater.app_launched(this);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		inTutorial = false;

		mDisplayWidth = metrics.widthPixels;
		mDisplayHeight = metrics.heightPixels;

		ImageCacheParams cacheParams = new ImageCacheParams(this, IMAGE_CACHE_DIR);

		// Set memory cache to 25% of mem class
		cacheParams.setMemCacheSizePercent(this, 0.15f);

		// The ImageFetcher takes care of loading images into our ImageView children asynchronously
		mImageDecoder = new ImageDecoder(this, getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size));
		mImageDecoder.setFolderImage(R.drawable.android_folder);
		mImageDecoder.setUnknownImage(R.drawable.ic_unknown_file);
		mImageDecoder.addImageCache(getFragmentManager(), cacheParams);

//		mGalleryAdapter = new GalleryAdapter(this, null, mImageDecoder);
		mGalleryAdapter = new GalleryRecyclerAdapter(this, null, mImageDecoder);
		mGalleryAdapter.setOnSelectionListener(this);
		mGalleryAdapter.setOnItemClickListener(this);
		mGalleryAdapter.setOnItemLongClickListener(this);

//		mImageGrid = ((GridView) findViewById(R.id.gridview));
//		mImageGrid.setOnScrollListener(this);
//		mImageGrid.setOnItemClickListener(this);
//		mImageGrid.setOnItemLongClickListener(this);

		mImageGrid = ((RecyclerView) findViewById(R.id.gridview));
		final int displayWidth = getResources().getDisplayMetrics().widthPixels;
		final int thumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
		final int thumbSpacing = 2 * getResources().getDimensionPixelSize(R.dimen.image_thumbnail_margin);
		final int numColumns = (int) Math.floor(displayWidth / (thumbSize + thumbSpacing));

		mGridLayout = new GridLayoutManager(this, numColumns);
		mGridLayout.setSmoothScrollbarEnabled(true);
//		mImageGrid.setOnScrollListener(this);
//		mImageGrid.setOnItemClickListener(this);
//		mImageGrid.setOnItemLongClickListener(this);

		PreferenceManager.setDefaultValues(this, R.xml.preferences_metadata, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_storage, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_view, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_license, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_watermark, false);

		ItemOffsetDecoration spacing = new ItemOffsetDecoration(this, R.dimen.image_thumbnail_margin);
		mImageGrid.setLayoutManager(mGridLayout);
		mImageGrid.addItemDecoration(spacing);
		mImageGrid.setHasFixedSize(true);
		mImageGrid.setAdapter(mGalleryAdapter);

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
								.append(intent.getIntExtra(MetaService.EXTRA_COMPLETED_JOBS, -1))
								.append(" of ")
								.append(intent.getIntExtra(MetaService.EXTRA_TOTAL_JOBS, -1)));//mGalleryAdapter.getCount()));
						break;
					case MetaService.BROADCAST_PROCESSING_COMPLETE:
						mToolbar.setSubtitle("Updating...");
						break;
					case MetaService.BROADCAST_PARSE_COMPLETE:
						mProgressBar.setVisibility(View.GONE);
						mToolbar.setSubtitle("");
						break;
					case SearchService.BROADCAST_FOUND:
//						mSwipeRefresh.setRefreshing(false);
						String[] images = intent.getStringArrayExtra(SearchService.EXTRA_IMAGE_URIS);
						mParsedImages = 0;
						if (images.length == 0)
						{
							offerRequestPermission();
						}
						else
						{
							for (String image : images)
							{
								Uri uri = Uri.parse(image);
								MetaWakefulReceiver.startMetaService(GalleryActivity.this, uri);
							}
						}
						break;
				}
			}
		}, mResponseIntentFilter);
		setImageCountTitle();

		if (getIntent().getData() != null)
		{
			ImageUtils.importKeywords(this, getIntent().getData());
		}

//		checkWriteAccess();
		getLoaderManager().initLoader(META_LOADER_ID, getIntent().getBundleExtra(EXTRA_META_BUNDLE), this);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()))
		{
			/* There is absolutely no way to uniquely identify a usb device across connections
			 So what we'll do instead is rely on the funky SAF host as a unique ID
			 The flaw here is the severe edge case that in a multi-device situation we will not
			 request permission for additional devices and jump out at the first recognized device.
			 Well that and the fact Google will break all this in 6.1 */

			Set<String> permissibleUsb = PreferenceManager.getDefaultSharedPreferences(this)
					.getStringSet(PREFS_PERMISSIBLE_USB, new HashSet<String>());
			for (String uriString : permissibleUsb)
			{
				Uri permission = Uri.parse(uriString);
				try
				{
					ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(permission, "r");
					// If pfd exists then this is a reconnected device, avoid hassling user
					if (pfd != null)
					{
						Utils.closeSilently(pfd);
						return;
					}
					Utils.closeSilently(pfd);
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}

			// Since this appears to be a new device gather uri and request write permission
			Intent request = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			request.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.selectUsb));
			startActivityForResult(request, REQUEST_ACCESS_USB);
		}
	}

	protected void updateMetaLoaderXmp(XmpFilter filter)
	{
		StringBuilder selection = new StringBuilder();
		List<String> selectionArgs = new ArrayList<>();
		boolean requiresJoiner= false;

		final String AND = " AND ";
		final String OR = " OR ";
		String joiner = filter.andTrueOrFalse ? AND : OR;

		if (filter.xmp != null)
		{
			if (filter.xmp.label != null && filter.xmp.label.length > 0)
			{
				requiresJoiner = true;

				selection.append(DbUtil.createMultipleIN(Meta.Data.LABEL, filter.xmp.label.length));
				for (String label : filter.xmp.label)
				{
					selectionArgs.add(label);
				}
			}
			if (filter.xmp.subject != null && filter.xmp.subject.length > 0)
			{
				if (requiresJoiner)
					selection.append(joiner);
				requiresJoiner = true;

				selection.append(DbUtil.createMultipleLike(Meta.Data.SUBJECT, filter.xmp.subject, selectionArgs, joiner, false));
			}
			if (filter.xmp.rating != null && filter.xmp.rating.length > 0)
			{
				if (requiresJoiner)
					selection.append(joiner);
				requiresJoiner = true;

				selection.append(DbUtil.createMultipleIN(Meta.Data.RATING, filter.xmp.rating.length));
				for (int rating : filter.xmp.rating)
				{
					selectionArgs.add(Double.toString((double)rating));
				}
			}
		}
		if (filter.hiddenFolders != null && filter.hiddenFolders.size() > 0)
		{
			if (requiresJoiner)
				selection.append(AND);  // Always exclude the folders, don't OR
			requiresJoiner = true;

			selection.append(DbUtil.createMultipleLike(Meta.Data.PARENT,
					filter.hiddenFolders.toArray(new String[filter.hiddenFolders.size()]),
					selectionArgs,
					AND,    // Requires AND so multiple hides don't negate each other
					true));
		}

		String order = filter.sortAscending ? " ASC" : " DESC";
		StringBuilder sort = new StringBuilder();

		if (filter.segregateByType)
		{
			sort.append(Meta.Data.TYPE).append(" ASC, ");
		}
		switch (filter.sortColumn)
		{
			case Date: sort.append(Meta.Data.TIMESTAMP).append(order); break;
			case Name: sort.append(Meta.Data.NAME).append(order); break;
			default: sort.append(Meta.Data.NAME).append(" ASC");
		}

		updateMetaLoader(null, selection.toString(), selectionArgs.toArray(new String[selectionArgs.size()]), sort.toString());
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
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (settings.getBoolean("isFirstRun", true))
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("isFirstRun", false);
            editor.apply();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.welcomeTitle);
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
		mXmpFilterFragment = (XmpFilterFragment) getSupportFragmentManager().findFragmentById(R.id.filterFragment);
		mXmpFilterFragment.registerXmpFilterChangedListener(new XmpFilterFragment.MetaFilterChangedListener()
		{
			@Override
			public void onMetaFilterChanged(XmpFilter filter)
			{
				updateMetaLoaderXmp(filter);
			}
		});
		mXmpFilterFragment.registerSearchRootRequestedListener(new XmpFilterFragment.SearchRootRequestedListener()
		{
			@Override
			public void onSearchRootRequested()
			{
				setWriteResume(WriteResume.Search, null);
				requestWritePermission();
			}
		});

		// load filter data initially (must be done here due to
		updateMetaLoaderXmp(mXmpFilterFragment.getXmpFilter());
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

				String[] projection = null;//GALLERY_PROJECTION;
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
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		mGalleryAdapter.swapCursor(cursor);
		setImageCountTitle();

		if (mGalleryAdapter.getCount() == 0)
			offerRequestPermission();
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

		mImageDecoder.setExitTasksEarly(false);
		mGalleryAdapter.notifyDataSetChanged();
	}

	@Override
	protected LicenseHandler getLicenseHandler()
	{
		return new LicenseHandler(this);
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

	private void offerRequestPermission()
	{
		if (!mActivityVisible)
			return; // User might switch apps waiting for search to complete

		AlertDialog.Builder builder =
				new AlertDialog.Builder(this).
						setTitle(R.string.offerSearchTitle).
						setMessage(R.string.offerPermissionMessage).
						setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								setWriteResume(WriteResume.Search, null);
								requestWritePermission();
							}
						}).
						setNegativeButton(R.string.neutral, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								//do nothing
							}
						});
		builder.create().show();
	}

	protected void setImageCountTitle()
	{
		getSupportActionBar().setTitle(mGalleryAdapter.getCount() + " Images");
	}

	protected void scanRawFiles()
	{
		mProgressBar.setVisibility(View.VISIBLE);
		runCleanDatabase();

		final Set<String> excludedFolders = mXmpFilterFragment.getExcludedFolders();

//		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//		SharedPreferences.Editor editor = prefs.edit();
//		final String key = getString(R.string.KEY_EXCLUDED_FOLDERS);
//		// You must make a copy of the returned preference set or changes will not be recognized
//		Set<String> excludedFolders = new HashSet<>(prefs.getStringSet(key, new HashSet<String>()));
		List<UriPermission> rootPermissions = getRootPermissions();
		int size = rootPermissions.size();
		String[] permissions = new String[size];
		for (int i = 0; i < size; i++)
		{
			permissions[i] = rootPermissions.get(i).getUri().toString();
		}

		SearchService.startActionSearch(
				this,
				/*TEST_ROOTS,MOUNT_ROOTS*/ null,    // Files unsupported on 4.4+
				permissions,
				excludedFolders.toArray(new String[excludedFolders.size()]));
	}

	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_COPY_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
					handleCopyDestinationResult(data.getData());
				}
				break;
			case REQUEST_SAVE_JPG_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
                    handleExportThumbResult(data.getData());
				}
				break;
            case REQUEST_UPDATE_PHOTO:
                if (resultCode == RESULT_OK && data != null)
                {
                    handlePhotoUpdate(data.getIntExtra(GALLERY_INDEX_EXTRA, 0));
                }
	            break;
			case REQUEST_ACCESS_USB:
				if (resultCode == RESULT_OK && data != null)
				{
					handleUsbAccessRequest(data.getData());
				}
				break;
		}
	}

    private void handlePhotoUpdate(int index)
    {
		mImageGrid.smoothScrollToPosition(index);
    }

	private void handleCopyDestinationResult(final Uri destination)
	{
		// TODO: Might want to figure out a way to get free space to intriduce this check again
//		long importSize = getSelectedImageSize();
//		if (destination.getFreeSpace() < importSize)
//		{
//			Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
//			return;
//		}

		new CopyTask().execute(mItemsForIntent, destination);
	}

	private long getSelectedImageSize()
	{
		long selectionSize = 0;
		for (Uri selected : mGalleryAdapter.getSelectedItems())
		{
			UsefulDocumentFile df = UsefulDocumentFile.fromUri(this, selected);
			selectionSize += df.length();
		}
		return selectionSize;
	}

    @SuppressWarnings("unchecked")
    private void handleExportThumbResult(final Uri destination)
    {
		// TODO: Might want to figure out a way to get free space to introduce this check again
//        long importSize = getSelectedImageSize();
//        if (destination.getFreeSpace() < importSize)
//        {
//            Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
//            return;
//        }

        CopyThumbTask ct = new CopyThumbTask();
        ct.execute(mItemsForIntent, destination);
    }

	private void handleUsbAccessRequest(Uri treeUri)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		// You must make a copy of the returned preference set or changes will not be recognized
		Set<String> permissibleUsbDevices = new HashSet<>(prefs.getStringSet(PREFS_PERMISSIBLE_USB, new HashSet<String>()));

		// The following oddity is because permission uris are not valid without SAF
		UsefulDocumentFile makeUriUseful = UsefulDocumentFile.fromUri(this, treeUri);
		permissibleUsbDevices.add(makeUriUseful.getUri().toString());

		SharedPreferences.Editor editor = prefs.edit();
		editor.putStringSet(PREFS_PERMISSIBLE_USB, permissibleUsbDevices);
		editor.apply();

		getContentResolver().takePersistableUriPermission(treeUri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION |
						Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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
				requestRename();
				return true;
			case R.id.galleryClearCache:
				mImageDecoder.clearCache();
				getContentResolver().delete(Meta.Data.CONTENT_URI, null, null);
				Toast.makeText(this, R.string.cacheCleared, Toast.LENGTH_SHORT).show();
				return true;
			case R.id.contextDelete:
				deleteImages(getSelectedImages());
				return true;
			case R.id.contextSaveJpg:
				requestSaveJpgDestination();
				return true;
			case R.id.contextCopy:
				requestCopyDestination();
				return true;
			case R.id.gallery_recycle:
            case R.id.context_recycle:
				showRecycleBin();
				return true;
			case R.id.gallerySelectAll:
            case R.id.contextSelectAll:
				selectAll();
				return false;
			case R.id.galleryRefresh:
				if (getRootPermissions().size() == 0)
				{
					offerRequestPermission();
				}
				else
				{
					scanRawFiles();
				}
				return true;
			case R.id.galleryTutorial:
				runTutorial();
				return true;
			case R.id.gallerySd:
				requestWritePermission();
				return true;
			case R.id.contextBlockFolder:
				addExcludedFolder();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void addExcludedFolder()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
		final String key = getString(R.string.KEY_EXCLUDED_FOLDERS);
		// You must make a copy of the returned preference set or changes will not be recognized
		Set<String> excludedFolders = new HashSet<>(prefs.getStringSet(key, new HashSet<String>()));

		for (Uri uri : mGalleryAdapter.getSelectedItems())
		{
			File file = new File(uri.getPath());

			/**
			 * We need canonical here, otherwise we could exclude one copy of an image only to allow the other(s)
			 * ie: /storage/extSdCard and /mnt/extSdCard point to the same physical memory
			 */
			File canon = new File(FileUtil.getCanonicalPathSilently(file));
			excludedFolders.add(canon.getParent());
		}

		mXmpFilterFragment.addExcludedFolders(excludedFolders);
//		getContentResolver().delete(Meta.Data.CONTENT_URI,
//				createMultipleIN(Meta.Data.PARENT, excludedFolders.size()),
//				excludedFolders.toArray(new String[excludedFolders.size()]));
//		scanRawFiles();
	}

	@Override
	public boolean onShareTargetSelected(ShareActionProvider source, Intent intent)
	{
		if (mContextMode != null)
			mContextMode.finish(); // end the contextual action bar and multi-select mode
		return false;
	}

	private void requestRename()
	{
		if (mGalleryAdapter.getSelectedItemCount() == 0)
			mGalleryAdapter.selectAll();

		showRenameDialog(getSelectedImages());
	}

	private void requestCopyDestination()
	{
		storeSelectionForIntent();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_COPY_DIR);
	}

    private void requestSaveJpgDestination()
    {
	    storeSelectionForIntent();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_SAVE_JPG_DIR);
    }

	@Override
	protected void onImageSetChanged()
	{
		// Not needed with a cursorloader
		//TODO: This could be used to batch adds/removes
	}

	@Override
	protected void onImageAdded(Uri item)
	{
		addDatabaseReference(item);
	}

	@Override
	protected List<Uri> getSelectedImages()
	{
		return mGalleryAdapter.getSelectedItems();
	}

	@Override
	protected void onImageRemoved(Uri item)
	{
		removeDatabaseReference(item);
	}

	protected boolean removeDatabaseReference(Uri toRemove)
	{
		int rowsDeleted = getContentResolver().delete(
				Meta.Data.CONTENT_URI,
				ImageUtils.getWhere(),
				new String[] {toRemove.toString()});
		return rowsDeleted > 0;
	}

	protected Uri addDatabaseReference(Uri toAdd)
	{
		return getContentResolver().insert(
				Meta.Data.CONTENT_URI,
				ImageUtils.getContentValues(this, toAdd));
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

	@Override
	public void onSelectionUpdated(final Set<Uri> selectedUris)
	{
		if (mContextMode != null)
		{
			mContextMode.setTitle("Select Items");
			mContextMode.setSubtitle(selectedUris.size() + " selected");
		}

		// DocumentFile has some overhead in creation so run a thread here.
		// For ex., select all with 100s of images can take a few seconds.
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				ArrayList<Uri> arrayUri = new ArrayList<>();
				for (Uri selection : selectedUris)
				{
					UsefulDocumentFile f = UsefulDocumentFile.fromUri(GalleryActivity.this, selection);
					arrayUri.add(SwapProvider.createSwapUri(f.getName(), selection));
				}

				if (selectedUris.size() == 1)
				{
					setShareUri(arrayUri.get(0));
				}
				else if (selectedUris.size() > 1)
				{
					setShareUri(arrayUri);
				}
			}
		}).start();
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
			if (handled && (item.getItemId() != R.id.toggleXmp)) //We don't exit context for xmp bar
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

	public void selectAll()
	{
		startContextualActionBar();
		mGalleryAdapter.selectAll();
	}

	public void startMultiSelectMode()
	{
		mGalleryAdapter.clearSelection(); // Ensure we don't have any stragglers
		multiSelectMode = true;
	}

	public void endMultiSelectMode()
	{
		multiSelectMode = false;
		mGalleryAdapter.clearSelection();
	}

	private void startContextualActionBar()
	{
		mContextMode = startSupportActionMode(new GalleryActionMode());
		mContextMode.setTitle("Select Items");
		startMultiSelectMode();
	}

	@Override
	public boolean onItemLongClick(RecyclerView.Adapter<?> parent, View view, int position, long id)
	{
		MediaItem media = mGalleryAdapter.getImage(position);

		if (media == null)
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

	@Override
	protected void onResumeWriteAction(Enum callingMethod, Object[] callingParameters)
	{
		super.onResumeWriteAction(callingMethod, callingParameters);
		if (callingMethod == null)
			return;

		switch ((WriteResume)callingMethod)
		{
			case Search:
				scanRawFiles();
				break;
		}
	}

	@TargetApi(VERSION_CODES.JELLY_BEAN)
	@Override
	public void onItemClick(RecyclerView.Adapter<?> parent, View v, int position, long id)
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

    int tutorialStage;
	public void runTutorial()
	{
        tutorialStage = 0;
        File tutorialDirectory = FileUtil.getDiskCacheDir(this, "tutorial");
        if (!tutorialDirectory.exists())
        {
            tutorialDirectory.mkdir();
        }

        //generate some example images
        try
        {
	        File f1 = new File(tutorialDirectory, "Image1.png");
	        File f2 = new File(tutorialDirectory, "Image2.png");
	        File f3 = new File(tutorialDirectory, "Image3.png");
	        File f4 = new File(tutorialDirectory, "Image4.png");
	        File f5 = new File(tutorialDirectory, "Image5.png");

            FileOutputStream one = new FileOutputStream(f1);
            FileOutputStream two = new FileOutputStream(f2);
            FileOutputStream three = new FileOutputStream(f3);
            FileOutputStream four = new FileOutputStream(f4);
            FileOutputStream five = new FileOutputStream(f5);

            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial1).compress(Bitmap.CompressFormat.PNG, 100, one);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial2).compress(Bitmap.CompressFormat.PNG, 100, two);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial3).compress(Bitmap.CompressFormat.PNG, 100, three);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial4).compress(Bitmap.CompressFormat.PNG, 100, four);
            BitmapFactory.decodeResource(getResources(), R.drawable.tutorial5).compress(Bitmap.CompressFormat.PNG, 100, five);

	        addDatabaseReference(Uri.fromFile(f1));
	        addDatabaseReference(Uri.fromFile(f2));
	        addDatabaseReference(Uri.fromFile(f3));
	        addDatabaseReference(Uri.fromFile(f4));
	        addDatabaseReference(Uri.fromFile(f5));
	        updateMetaLoader(null,
			        Meta.Data.URI + " LIKE ?",
			        new String[] {"%"+tutorialDirectory.getName()+"%"}, null);}

        catch (FileNotFoundException e)
        {
            Toast.makeText(this, "Unable to open tutorial examples.  Please skip file selection.", Toast.LENGTH_LONG).show();
        }

        inTutorial = true;

        tutorial = new ShowcaseView.Builder(this)//, true)
		        .withMaterialShowcase()
                .setContentTitle(R.string.tutorialWelcomeTitle)
                .setContentText(R.string.tutorialWelcomeText)
                .doNotBlockTouches()
		        .setStyle(R.style.CustomShowcaseTheme2)
		        .replaceEndButton(R.layout.tutorial_button)
		        .setOnClickListener(new TutorialClickListener())
                .build();

        tutorial.setButtonText(getString(R.string.next));
        tutorial.setButtonPosition(getRightParam(getResources()));
		tutorial.setDetailTextAlignment(Layout.Alignment.ALIGN_OPPOSITE);
        setTutorialNoShowcase();
	}

	/**
	 * Represents an Action item to showcase (e.g., one of the buttons on an ActionBar).
	 * To showcase specific action views such as the home button, use {@link ToolbarActionItemTarget}
	 *
	 * @see ToolbarActionItemTarget
	 */
	private class ToolbarActionItemTarget implements Target
	{
		private final Toolbar toolbar;
		private final int menuItemId;

		public ToolbarActionItemTarget(Toolbar toolbar, @IdRes int itemId) {
			this.toolbar = toolbar;
			this.menuItemId = itemId;
		}

		@Override
		public Point getPoint() {
			return new ViewTarget(toolbar.findViewById(menuItemId)).getPoint();
		}

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
				case 5: // Search
                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialFindImagesText));
                    setTutorialActionView(R.id.galleryRefresh, false);
					break;
				case 6: // Long Select
                    tutorial.setScaleMultiplier(20f);
                    tutorial.setContentTitle(getString(R.string.tutorialSelectTitle));
                    tutorial.setContentText(getString(R.string.tutorialSingleSelectText));
                    view = mImageGrid.getChildAt(0);
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
					break;
				case 7: // Add select
					// If the user is lazy select for them
					if (!inActionMode)
						onItemLongClick(mGalleryAdapter, mImageGrid.getChildAt(0), 0, 0);

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText));
                    view = mImageGrid.getChildAt(2);
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
					break;
				case 8: // Select feedback
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 2)
					{
						mContextMode.finish();
						onItemLongClick(mGalleryAdapter, mImageGrid.getChildAt(0), 0, 0);
						onItemClick(mGalleryAdapter, mImageGrid.getChildAt(2), 2, 2);
					}

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText2));
                    // This is ghetto, I know the spinner lies UNDER the selection view
					//FIXME: Need something to point at
					setTutorialTitleView(true);
//					tutorial.setShowcase(new ToolbarActionItemTarget(mToolbar, mToolbar.getId()), true);
					break;
				case 9: // Select All
					if (inActionMode)
						mContextMode.finish();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectAll));
                    setTutorialActionView(R.id.gallerySelectAll, true);
					break;
				case 10: // Exit Selection
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 1)
					{
						mGalleryAdapter.selectAll();
					}

                    tutorial.setScaleMultiplier(1f);
                    tutorial.setContentText(getString(R.string.tutorialExitSelectionText));
                    setTutorialHomeView(true);
					break;
                case 11: // Select between beginning
                    if (mContextMode != null)
                        mContextMode.finish();

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText1));
                    view = mImageGrid.getChildAt(1);		//WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
                    break;
                case 12: // Select between end
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 1)
						onItemLongClick(mGalleryAdapter, mImageGrid.getChildAt(1), 1, 1);

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText2));

					setTutorialHomeView(true);
                    view = mImageGrid.getChildAt(3);	//WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(new ViewTarget(view), true);
                    else
                        setTutorialNoShowcase();    //TODO: User set an empty folder, somehow???
                    break;
                case 13: // Select between feedback
					// If the user is lazy select for them
					if (mGalleryAdapter.getSelectedItemCount() < 2)
					{
						onItemLongClick(mGalleryAdapter, mImageGrid.getChildAt(1), 1, 1);
						onItemLongClick(mGalleryAdapter, mImageGrid.getChildAt(3), 3, 3);
					}

                    tutorial.setScaleMultiplier(1.5f);
                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText3));
                    // This is ghetto, I know the spinner lies UNDER the selection view
					//FIXME: need something to point at
	                setTutorialTitleView(true);
	                break;
                case 14: // Rename
					if (!inActionMode)
						startContextualActionBar();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialRenameTitle));
                    tutorial.setContentText(getString(R.string.tutorialRenameText));
                    setTutorialActionView(R.id.contextRename, true);
                    break;
                case 15: // Move
					if (!inActionMode)
						startContextualActionBar();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialMoveTitle));
                    tutorial.setContentText(getString(R.string.tutorialMoveText));
                    setTutorialActionView(R.id.contextCopy, true);
                    break;
                case 16: // Export
					if (!inActionMode)
						startContextualActionBar();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialExportTitle));
                    tutorial.setContentText(getString(R.string.tutorialExportText));
                    setTutorialActionView(R.id.contextSaveJpg, true);
                    break;
                case 17: // Share (can't figure out how to address the share button
//					if (!inActionMode)
//						startContextualActionBar();
//
//                    tutorial.setContentTitle(getString(R.string.tutorialShareTitle));
//                    tutorial.setContentText(getString(R.string.tutorialShareText));
//                    setTutorialShareView(true);
//					setTutorialActionView(R.id.contextShare, true);
//                    break;
					tutorialStage++;
                case 18: // Recycle
                    if (mContextMode != null)
                        mContextMode.finish();

                    tutorial.setScaleMultiplier(0.5f);
                    tutorial.setContentTitle(getString(R.string.tutorialRecycleTitle));
                    tutorial.setContentText(getString(R.string.tutorialRecycleText));
                    setTutorialActionView(R.id.gallery_recycle, true);
                    break;
                case 19: // Actionbar help
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

	    File tutorialDirectory = FileUtil.getDiskCacheDir(this, "tutorial");
	    File f1 = new File(tutorialDirectory, "Image1.png");
	    File f2 = new File(tutorialDirectory, "Image2.png");
	    File f3 = new File(tutorialDirectory, "Image3.png");
	    File f4 = new File(tutorialDirectory, "Image4.png");
	    File f5 = new File(tutorialDirectory, "Image5.png");

	    removeDatabaseReference(Uri.fromFile(f1));
	    removeDatabaseReference(Uri.fromFile(f2));
	    removeDatabaseReference(Uri.fromFile(f3));
	    removeDatabaseReference(Uri.fromFile(f4));
	    removeDatabaseReference(Uri.fromFile(f5));

	    f1.delete();
	    f2.delete();
	    f3.delete();
	    f4.delete();
	    f5.delete();
	    tutorialDirectory.delete();
		updateMetaLoaderXmp(mXmpFilterFragment.getXmpFilter());
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

	/**
	 * ballpark home (filter) location
	 * @param animate
	 */
    private void setTutorialHomeView(boolean animate)
    {
        PointTarget home = new PointTarget(16,16);  //Design guideline is 32x32
        tutorial.setShowcase(home, animate);
    }

	/**
	 * ballpark title location
	 * @param animate
	 */
	private void setTutorialTitleView(boolean animate)
	{
		PointTarget title = new PointTarget(48,16);  //Design guideline is 32x32
		tutorial.setShowcase(title, animate);
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
