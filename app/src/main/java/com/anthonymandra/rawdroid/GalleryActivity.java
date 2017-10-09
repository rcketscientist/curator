package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Toast;

import com.afollestad.materialcab.MaterialCab;
import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.CoreActivity;
import com.anthonymandra.framework.MetaDataCleaner;
import com.anthonymandra.framework.MetaService;
import com.anthonymandra.framework.MetaWakefulReceiver;
import com.anthonymandra.framework.SearchService;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.util.DbUtil;
import com.anthonymandra.util.ImageUtil;
import com.anthonymandra.widget.GalleryRecyclerAdapter;
import com.anthonymandra.widget.ItemOffsetDecoration;
import com.bumptech.glide.Glide;
import com.inscription.WhatsNewDialog;
import com.pluscubed.recyclerfastscroll.RecyclerFastScroller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.anthonymandra.rawdroid.TutorialActivity.RESULT_ERROR;

public class GalleryActivity extends CoreActivity implements
		GalleryRecyclerAdapter.OnItemClickListener,
		GalleryRecyclerAdapter.OnItemLongClickListener,
		LoaderManager.LoaderCallbacks<Cursor>,
		GalleryRecyclerAdapter.OnSelectionUpdatedListener
{
	@SuppressWarnings("unused")
	private static final String TAG = GalleryActivity.class.getSimpleName();

	private enum WriteResume
	{
		Search
	}

	private final IntentFilter mResponseIntentFilter = new IntentFilter();

	public static final String LICENSE_RESULT = "license_result";
	public static final int LICENSE_ALLOW = 1;
	public static final int LICENSE_DISALLOW = 2;
	public static final int LICENSE_ERROR = 3;

	// Preference fields
	public static final String PREFS_NAME = "RawDroidPrefs";
	public static final boolean PREFS_AUTO_INTERFACE_DEFAULT = true;
	public static final String PREFS_SHOW_FILTER_HINT = "prefShowFilterHint";
	public static final String PREFS_PERMISSIBLE_USB = "prefPermissibleUsb";

	// Request codes
	private static final int REQUEST_COPY_DIR = 12;
    private static final int REQUEST_UPDATE_PHOTO = 16;
	private static final int REQUEST_ACCESS_USB = 17;
	private static final int REQUEST_TUTORIAL = 18;

	public static final String GALLERY_INDEX_EXTRA = "gallery_index";

	// Widget handles
	private RecyclerView mImageGrid;
	private GalleryRecyclerAdapter mGalleryAdapter;
	/**
	 * Stores uris when lifecycle is interrupted (ie: requesting a destination folder)
	 */
	protected List<Uri> mItemsForIntent = new ArrayList<>();

	// Selection support
	private boolean multiSelectMode;

	private Toolbar mToolbar;
	private MaterialCab mMaterialCab;
	private XmpFilterFragment mXmpFilterFragment;
	private MaterialProgressBar mProgressBar;
	private DrawerLayout mDrawerLayout;

	private static final String[] REQUIRED_COLUMNS = {
			Meta.LABEL, Meta.NAME, Meta.PARENT, Meta.RATING, Meta.SUBJECT, Meta.TIMESTAMP,
			Meta.TYPE, Meta.URI };

	// Generate all needed columns for the projection
	private static final String[] PROJECTION;
	static
	{
		Set<String> ALL_COLUMNS = new HashSet<>();
		ALL_COLUMNS.addAll(Arrays.asList(REQUIRED_COLUMNS));
		ALL_COLUMNS.addAll(Arrays.asList(GalleryRecyclerAdapter.REQUIRED_COLUMNS));
		PROJECTION = ALL_COLUMNS.toArray(new String[ALL_COLUMNS.size()]);
	}

	@Override
	public int getContentView()
	{
		return R.layout.gallery;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mToolbar = (Toolbar) findViewById(R.id.galleryToolbar);
		mProgressBar = (MaterialProgressBar) findViewById(R.id.toolbarProgress);
        setSupportActionBar(mToolbar);

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		findViewById(R.id.filterSidebarButton).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mDrawerLayout.openDrawer(GravityCompat.START);
			}
		});


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
				mDrawerLayout.closeDrawer(GravityCompat.START);
			}
		});

		doFirstRun();

		AppRater.app_launched(this);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		int mDisplayWidth = metrics.widthPixels;
		int shortSide = Math.min(mDisplayWidth, metrics.heightPixels);

		// we want three divisions on short side, convert that to a column value
		// This will always be 3 in portrait, x in landscape (with 3 rows)
		final float thumbSize = shortSide / 3 ;
		final int numColumns = Math.round(mDisplayWidth / thumbSize);
		//TODO: 16:9 => 5 x 2.x or 3 x 5.3, which means rotation will call up slightly different sized thumbs, we need to ensure glide is initially creating the slightly larger variant

		GridLayoutManager mGridLayout = new GridLayoutManager(this, numColumns);
		mGridLayout.setSmoothScrollbarEnabled(true);

		mGalleryAdapter = new GalleryRecyclerAdapter(this, null);
		mGalleryAdapter.setOnSelectionListener(this);
		mGalleryAdapter.setOnItemClickListener(this);
		mGalleryAdapter.setOnItemLongClickListener(this);

		mImageGrid = findViewById(R.id.gridview);

		ItemOffsetDecoration spacing = new ItemOffsetDecoration(this, R.dimen.image_thumbnail_margin);
		mImageGrid.setLayoutManager(mGridLayout);
		mImageGrid.addItemDecoration(spacing);
		mImageGrid.setHasFixedSize(true);
		mImageGrid.setAdapter(mGalleryAdapter);

		mResponseIntentFilter.addAction(MetaService.BROADCAST_IMAGE_PARSED);
		mResponseIntentFilter.addAction(MetaService.BROADCAST_PARSE_COMPLETE);
		mResponseIntentFilter.addAction(SearchService.BROADCAST_SEARCH_STARTED);
		mResponseIntentFilter.addAction(SearchService.BROADCAST_SEARCH_COMPLETE);
		mResponseIntentFilter.addAction(SearchService.BROADCAST_FOUND_IMAGES);
		LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				switch(intent.getAction())
				{
					case MetaService.BROADCAST_IMAGE_PARSED:
						mToolbar.setSubtitle(new StringBuilder()
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
					case SearchService.BROADCAST_SEARCH_STARTED:
						mProgressBar.setVisibility(View.VISIBLE);
						mProgressBar.setIndeterminate(true);
						mToolbar.setSubtitle("Searching...");
						break;
					case SearchService.BROADCAST_FOUND_IMAGES:
						mToolbar.setTitle(intent.getIntExtra(SearchService.EXTRA_NUM_IMAGES, 0) + " Images");
						break;
					case SearchService.BROADCAST_SEARCH_COMPLETE:
						String[] images = intent.getStringArrayExtra(SearchService.EXTRA_IMAGE_URIS);
						if (images.length == 0)
						{
							if (mActivityVisible)
								offerRequestPermission();
						}
						else
						{
							MetaWakefulReceiver.startMetaService(GalleryActivity.this);
						}
						break;
				}
			}
		}, mResponseIntentFilter);
		setImageCountTitle();

		if (getIntent().getData() != null)
		{
			ImageUtil.importKeywords(this, getIntent().getData());
		}

//		checkWriteAccess();
		getLoaderManager().initLoader(META_LOADER_ID, getIntent().getBundleExtra(EXTRA_META_BUNDLE), this);
	}

	@TargetApi(Build.VERSION_CODES.M)
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
		else if (getIntent().getData() != null)
		{
			ImageUtil.importKeywords(this, getIntent().getData());
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

				selection.append(DbUtil.createIN(Meta.LABEL, filter.xmp.label.length));
				Collections.addAll(selectionArgs, filter.xmp.label);
			}
			if (filter.xmp.subject != null && filter.xmp.subject.length > 0)
			{
				if (requiresJoiner)
					selection.append(joiner);
				requiresJoiner = true;

				selection.append(DbUtil.createLike(Meta.SUBJECT, filter.xmp.subject,
						selectionArgs, joiner, false,
						"%", "%",   // openended wildcards, match subject anywhere
						null));
			}
			if (filter.xmp.rating != null && filter.xmp.rating.length > 0)
			{
				if (requiresJoiner)
					selection.append(joiner);
				requiresJoiner = true;

				selection.append(DbUtil.createIN(Meta.RATING, filter.xmp.rating.length));
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

			selection.append(DbUtil.createLike(Meta.PARENT,
					filter.hiddenFolders.toArray(new String[filter.hiddenFolders.size()]),
					selectionArgs,
					AND,    // Requires AND so multiple hides don't negate each other
					true,   // NOT
					null,   // No wild to start, matches path exactly
					"%",    // Wildcard end to match all children
					"%"));  // Uri contain '%' which means match any so escape them
		}

		String order = filter.sortAscending ? " ASC" : " DESC";
		StringBuilder sort = new StringBuilder();

		if (filter.segregateByType)
		{
			sort.append(Meta.TYPE).append(" COLLATE NOCASE").append(" ASC, ");
		}
		switch (filter.sortColumn)
		{
			case Date: sort.append(Meta.TIMESTAMP).append(order); break;
			case Name: sort.append(Meta.NAME).append(" COLLATE NOCASE").append(order); break;
			default: sort.append(Meta.NAME).append(" COLLATE NOCASE").append(" ASC");
		}

		updateMetaLoader(null, selection.toString(), selectionArgs.toArray(new String[selectionArgs.size()]), sort.toString());
	}

	/**
	 * Updates any filled parameters.  Retains existing parameters if null.
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
	 */
	public Bundle getCurrentMetaLoaderBundle()
	{
		Loader<Cursor> c = getLoaderManager().getLoader(META_LOADER_ID);
		CursorLoader cl = (CursorLoader) c;
		return createMetaLoaderBundle(
				cl.getProjection(),
				cl.getSelection(),
				cl.getSelectionArgs(),
				cl.getSortOrder()
		);
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

		// This must be here due to the lifecycle
		updateMetaLoaderXmp(mXmpFilterFragment.getXmpFilter());

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
		            offerRequestPermission();
	            }
            });
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
            {
	            @Override
	            public void onClick(DialogInterface dialog, int which)
	            {
		            startActivity(new Intent(GalleryActivity.this, TutorialActivity.class));
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

				String[] projection = PROJECTION;
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
						Meta.CONTENT_URI,       // Table to query
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
		super.onResume();

		// Launch what's new dialog (will only be shown once)
		final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
		whatsNewDialog.show(Constants.VariantCode == 8);

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
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	private void offerRequestPermission()
	{
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
		if (getSupportActionBar() != null)
			getSupportActionBar().setTitle(mGalleryAdapter.getItemCount() + " Images");
	}

	protected void scanRawFiles()
	{
		mProgressBar.setVisibility(View.VISIBLE);
		mProgressBar.setIndeterminate(true);        //TODO: Determinate?
		mToolbar.setSubtitle("Cleaning...");

		MetaDataCleaner.cleanDatabase(this, new Handler(new Handler.Callback()
		{
			@Override
			public boolean handleMessage(Message message)
			{
				// Upon clean initiate search
				final Set<String> excludedFolders = mXmpFilterFragment.getExcludedFolders();

				List<UriPermission> rootPermissions = getRootPermissions();
				int size = rootPermissions.size();
				String[] permissions = new String[size];
				for (int i = 0; i < size; i++)
				{
					permissions[i] = rootPermissions.get(i).getUri().toString();
				}

				SearchService.startActionSearch(
						GalleryActivity.this,
						null,    // Files unsupported on 4.4+
						permissions,
						excludedFolders.toArray(new String[excludedFolders.size()]));
				return true;
			}
		}));
	}

	@Override
	public void onActivityResult(final int requestCode, int resultCode, final Intent data)
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
			case REQUEST_TUTORIAL:
				if (resultCode == RESULT_ERROR)
				{
					Snackbar.make(getGalleryView(), "Tutorial error. Please contact support if this continues.", 5000)
							.setAction(R.string.contact, new View.OnClickListener()
							{
								@Override
								public void onClick(View v)
								{
									requestEmailIntent("Tutorial Error");
								}
							})
							.show();
				}
				// We don't really care about a result, after tutorial offer to search.
				offerRequestPermission();
				break;
		}
	}

    private void handlePhotoUpdate(int index)
    {
		mImageGrid.smoothScrollToPosition(index);
    }

	private void handleCopyDestinationResult(final Uri destination)
	{
		// TODO: Might want to figure out a way to get free space to introduce this check again
//		long importSize = getSelectedImageSize();
//		if (destination.getFreeSpace() < importSize)
//		{
//			Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
//			return;
//		}

//		new CopyTask().execute(mItemsForIntent, destination);
		copyImages(mItemsForIntent, destination);
	}

	@Override
	protected void updateMessage(String message)
	{
		mToolbar.setSubtitle(message);
	}

	@Override
	protected void setMaxProgress(int max)
	{
		mProgressBar.setMax(max);
		mProgressBar.setIndeterminate(false);
		mProgressBar.setVisibility(View.VISIBLE);

	}

	@Override
	protected void incrementProgress()
	{
		mProgressBar.incrementProgressBy(1);
	}

	@Override
	protected void endProgress()
	{
		mProgressBar.setVisibility(View.GONE);
	}

	//	private long getSelectedImageSize()
//	{
//		long selectionSize = 0;
//		for (Uri selected : mGalleryAdapter.getSelectedItems())
//		{
//			UsefulDocumentFile df = UsefulDocumentFile.fromUri(this, selected);
//			selectionSize += df.length();
//		}
//		return selectionSize;
//	}

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

	private void storeSelectionForIntent()
	{
		mItemsForIntent = mGalleryAdapter.getSelectedItems();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.contextRename:
				requestRename();
				return true;
			case R.id.galleryClearCache:
				new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						Glide.get(GalleryActivity.this).clearDiskCache();
					}
				}).start();
				Glide.get(GalleryActivity.this).clearMemory();
				getContentResolver().delete(Meta.CONTENT_URI, null, null);
				Toast.makeText(this, R.string.cacheCleared, Toast.LENGTH_SHORT).show();
				return true;
			case R.id.contextCopy:
				requestCopyDestination();
				return true;
			case R.id.menu_selectAll:
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
				startActivityForResult(new Intent(GalleryActivity.this, TutorialActivity.class), REQUEST_TUTORIAL);
				return true;
			case R.id.gallerySd:
				requestWritePermission();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
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
		if (mItemsForIntent.size() < 1)
		{
			Snackbar.make(mImageGrid, R.string.warningNoItemsSelected, Snackbar.LENGTH_SHORT).show();
			return;
		}

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_COPY_DIR);
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
		//not needed with cursorloader
//		addDatabaseReference(item);
	}

	@Override
	protected ArrayList<Uri> getSelectedImages()
	{
		return mGalleryAdapter.getSelectedItems();
	}

	@Override
	protected void onImageRemoved(Uri item)
	{
		//not needed with cursorloader
//		removeDatabaseReference(item);
	}

	protected boolean removeDatabaseReference(Uri toRemove)
	{
		int rowsDeleted = getContentResolver().delete(
				Meta.CONTENT_URI,
				Meta.URI_SELECTION,
				new String[] {toRemove.toString()});
		return rowsDeleted > 0;
	}

	protected Uri addDatabaseReference(Uri toAdd)
	{
		ContentValues cv = new ContentValues();
		cv.put(Meta.URI, toAdd.toString());
//		ImageUtil.getContentValues(this, toAdd, cv);

		return getContentResolver().insert(Meta.CONTENT_URI, cv);
	}

	@Override
	public void onSelectionUpdated(final Set<Uri> selectedUris)
	{
		if (mMaterialCab != null)
		{
			mMaterialCab.setTitle(selectedUris.size() + " " + getString(R.string.selected));
		}
	}

	private final class GalleryActionMode implements MaterialCab.Callback
	{
		@Override
		public boolean onCabCreated(MaterialCab cab, Menu menu)
		{
			return true;
		}

		@Override
		public boolean onCabItemClicked(MenuItem item)
		{
			boolean handled = onOptionsItemSelected(item);
			if (handled)
				endContextMode();
			return handled;
		}

		@Override
		public boolean onCabFinished(MaterialCab cab)
		{
			endMultiSelectMode();
			mMaterialCab = null;
			return true;
		}
	}

	public void selectAll()
	{
		startContextMode();
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

	protected void startContextMode()
	{
		mMaterialCab = new MaterialCab(this, R.id.cab_stub)
				.setTitle(getString(R.string.selectItems))
				.setMenu(R.menu.gallery_contextual)
				.start(new GalleryActionMode());
		startMultiSelectMode();
	}

	protected void endContextMode()
	{
		if (mMaterialCab != null)
			mMaterialCab.finish();
	}

	@Override
	public boolean onItemLongClick(RecyclerView.Adapter<?> parent, View view, int position, long id)
	{
		// If we're in multi-select select all items between
		if (multiSelectMode)
		{
			mGalleryAdapter.addBetweenSelection(position);
		}
		// Enter multi-select
		else
		{
			startContextMode();
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

	@Override
	public void onItemClick(RecyclerView.Adapter<?> parent, View v, int position, long id)
	{
		Uri uri = mGalleryAdapter.getUri(position);
		if (multiSelectMode)
		{
			mGalleryAdapter.toggleSelection(v, position);
			mXmpFragment.reset();   // reset the panel to ensure it's clear it's not tied to existing values
			return;
		}

		Intent viewer = new Intent(this, ViewerChooser.class);
		viewer.setData(uri);

		Bundle options = new Bundle();

		v.setDrawingCacheEnabled(true);
		v.setPressed(false);
		v.refreshDrawableState();
        options.putAll(
	        ActivityOptions.makeThumbnailScaleUpAnimation(v, v.getDrawingCache(), 0, 0).toBundle());
		//TODO: If we want this to look smooth we should load the gallery thumb in viewer so there's a smooth transition

		Bundle metaLoader = getCurrentMetaLoaderBundle();
		viewer.putExtra(EXTRA_META_BUNDLE, metaLoader);
		viewer.putExtra(ViewerActivity.EXTRA_START_INDEX, position);

		startActivityForResult(viewer, REQUEST_UPDATE_PHOTO, options);
		v.setDrawingCacheEnabled(false);
	}

	protected RecyclerView getGalleryView()
	{
		return mImageGrid;
	}

	protected GalleryRecyclerAdapter getGalleryAdapter()
	{
		return mGalleryAdapter;
	}

	protected boolean isContextModeActive()
	{
		return mMaterialCab != null && mMaterialCab.isActive();
	}

	protected Toolbar getToolbar()
	{
		return mToolbar;
	}
}
