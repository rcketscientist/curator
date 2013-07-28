package com.anthonymandra.rawdroid;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.openintents.intents.FileManagerIntents;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.actionbarsherlock.widget.ShareActionProvider.OnShareTargetSelectedListener;
import com.android.gallery3d.app.OrientationManager;
import com.android.gallery3d.app.PhotoDataAdapter;
import com.android.gallery3d.app.PhotoDataAdapter.DataListener;
import com.android.gallery3d.app.TransitionStore;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.anthonymandra.framework.AsyncTask;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.Histogram;
import com.anthonymandra.framework.MediaObject;
import com.anthonymandra.framework.MetaMedia;
import com.anthonymandra.framework.Util;
import com.anthonymandra.widget.HistogramView;

public class ImageViewActivity extends GalleryActivity implements /* ScaleChangedListener, */OnShareTargetSelectedListener, OnSharedPreferenceChangeListener,
		PhotoView.Listener, OrientationManager.Listener, com.android.gallery3d.app.GalleryActivity, DataListener
{
	private static final String TAG = ImageViewActivity.class.getSimpleName();

	protected static final int REQUEST_CODE_PICK_FILE_OR_DIRECTORY = 1;
	protected static final int REQUEST_CODE_KEYWORDS = 2;
	protected static final int REQUEST_CODE_EDIT = 3;

	private static final int MSG_LOCK_ORIENTATION = 2;
	private static final int MSG_UNLOCK_ORIENTATION = 3;
	private static final int MSG_UNFREEZE_GLROOT = 6;

	private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

	private ImageModel mModel;
	private Handler mHandler;
	private ThreadPool mThreadPool;
	private Object mLock = new Object();
	private boolean isInterfaceHidden;

	private TransitionStore mTransitionStore = new TransitionStore();
	private OrientationManager mOrientationManager;
	private ImageCacheService mImageCacheService;
	private PhotoView mPhotoView;
	private GLRootView mGLRootView;
	private GLView mRootPane = new GLView()
	{

		@Override
		protected void renderBackground(GLCanvas view)
		{
			view.clearBuffer();
		}

		@Override
		protected void onLayout(boolean changed, int left, int top, int right, int bottom)
		{
			mPhotoView.layout(0, 0, right - left, bottom - top);
		}
	};

	private HistogramView histView;
	private View imagePanels;
	private View metaPanel;
	private ImageButton buttonRotate;
	private View navigationPanel;
	private TextView zoomLevel;
	private TextView metaDate;
	private TextView metaModel;
	private TextView metaIso;
	private TextView metaExposure;
	private TextView metaAperture;
	private TextView metaFocal;
	private TextView metaDimensions;
	private TextView metaAlt;
	private TextView metaFlash;
	private TextView metaLat;
	private TextView metaLon;
	private TextView metaName;
	private TextView metaWb;
	private TextView metaLens;
	private TextView metaDriveMode;
	private TextView metaExposureMode;
	private TextView metaExposureProgram;

	private Timer autoHide;

	// private DecodeRawTask decodeTask;
	// private FrameLayout decodeProgress;

	public static int displayWidth;
	public static int displayHeight;

	ShareActionProvider mShareProvider;

	XmpFragment xmpFrag;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.viewer_layout);
		GalleryUtils.initialize(this);
		lookupViews();

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayWidth = metrics.widthPixels;
		displayHeight = metrics.heightPixels;

		mOrientationManager = new OrientationManager(this);
		mOrientationManager.addListener(this);

		mPhotoView = new PhotoView(this);
		mPhotoView.setListener(this);
		mRootPane.addComponent(mPhotoView);

		mGLRootView.setOrientationSource(mOrientationManager);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		Uri data = getIntent().getData();
		if (data == null)
		{
			finish();
		}

		File input = new File(data.getPath());
		if (!input.exists())
		{
			Toast.makeText(this, "Path could not be found, please email me if this continues", Toast.LENGTH_LONG).show();
			finish();
		}

		int indexHint = 0;
		if (input.isDirectory())
		{
			setPath(input);
			// mCurrentIndex = 0;
			updateViewerItems();
		}
		else
		{
			File parent = input.getParentFile();
			if (parent.exists())
				setPath(input.getParentFile());
			else
				setSingleImage(input);

			indexHint = findMediaByFilename(input.getPath());
			if (indexHint == FILE_NOT_FOUND)
			{
				if (parent.exists() && parent.listFiles().length > 0)
				{
					indexHint = 0;
				}
				else
				{
					Toast.makeText(this, "Path could not be found, please email me if this continues", Toast.LENGTH_LONG).show();
					finish();
				}
			}
			updateViewerItems();

			// If a native image is sent make sure the settings are set to view it
			if (isNative(input) && !settings.getBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true))
			{
				Editor editor = settings.edit();
				editor.putBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);
				editor.commit();
			}
		}

		if (getResources().getBoolean(R.bool.hasTwoPanes))
		{
			xmpFrag = (XmpFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentSideBar);
			hideSidebar();
		}

		// decodeProgress = (FrameLayout) findViewById(R.id.frameRawProgress);

		PhotoDataAdapter pda = new PhotoDataAdapter(this, mPhotoView, mVisibleItems, indexHint);
		pda.setDataListener(this);
		mModel = pda;
		mPhotoView.setModel(mModel);

		// mImageViewer = new ImageViewer(this);
		// mImageViewer.setModel(mModel);
		// mRootPane.addComponent(mImageViewer);
		// mModel.requestNextImageWithMeta();

		attachButtons();

		setActionBar();

		mHandler = new SynchronizedHandler(getGLRoot())
		{
			@Override
			public void handleMessage(Message message)
			{
				switch (message.what)
				{
				// case MSG_HIDE_BARS: {
				// hideBars();
				// break;
				// }
					case MSG_LOCK_ORIENTATION:
					{
						mOrientationManager.lockOrientation();
						break;
					}
					case MSG_UNLOCK_ORIENTATION:
					{
						mOrientationManager.unlockOrientation();
						break;
					}
					// case MSG_ON_FULL_SCREEN_CHANGED: {
					// mAppBridge.onFullScreenChanged(message.arg1 == 1);
					// break;
					// }
					// case MSG_UPDATE_ACTION_BAR: {
					// updateBars();
					// break;
					// }
					// case MSG_WANT_BARS: {
					// wantBars();
					// break;
					// }
					case MSG_UNFREEZE_GLROOT:
					{
						getGLRoot().unfreeze();
						break;
					}
					default:
						throw new AssertionError(message.what);
				}
			}
		};
	}

	private void lookupViews()
	{
		mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
		imagePanels = findViewById(R.id.imagePanels);
		histView = (HistogramView) findViewById(R.id.histogramView1);
		metaPanel = findViewById(R.id.tableLayoutMeta);
		buttonRotate = (ImageButton) findViewById(R.id.imageButtonRotate);
		navigationPanel = findViewById(R.id.leftNavigation);
		zoomLevel = (TextView) findViewById(R.id.textViewScale);

		// Initially set the interface to GONE to allow settings to implement
		metaPanel.setVisibility(View.GONE);
		navigationPanel.setVisibility(View.GONE);
		histView.setVisibility(View.GONE);

		metaDate = (TextView) findViewById(R.id.textViewDate);
		metaModel = (TextView) findViewById(R.id.textViewModel);
		metaIso = (TextView) findViewById(R.id.textViewIso);
		metaExposure = (TextView) findViewById(R.id.textViewExposure);
		metaAperture = (TextView) findViewById(R.id.textViewAperture);
		metaFocal = (TextView) findViewById(R.id.textViewFocal);
		metaDimensions = (TextView) findViewById(R.id.textViewDimensions);
		metaAlt = (TextView) findViewById(R.id.textViewAlt);
		metaFlash = (TextView) findViewById(R.id.textViewFlash);
		metaLat = (TextView) findViewById(R.id.textViewLat);
		metaLon = (TextView) findViewById(R.id.textViewLon);
		metaName = (TextView) findViewById(R.id.textViewName);
		metaWb = (TextView) findViewById(R.id.textViewWhite);
		metaLens = (TextView) findViewById(R.id.textViewLens);
		metaDriveMode = (TextView) findViewById(R.id.textViewDriveMode);
		metaExposureMode = (TextView) findViewById(R.id.textViewExposureMode);
		metaExposureProgram = (TextView) findViewById(R.id.textViewExposureProgram);
	}

	private void setActionBar()
	{
		final ActionBar actionBar = getSupportActionBar();

		int actionBarHeight = 0;
		// Calculate ActionBar height
		TypedValue tv = new TypedValue();
		if (getTheme().resolveAttribute(R.attr.actionBarSize, tv, true))
		{
			actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
		}

		final View sideBarWrapper = findViewById(R.id.frameLayoutSidebar);
		if (sideBarWrapper != null) // Portait doens't require the wrapper
		{
			((MarginLayoutParams) sideBarWrapper.getLayoutParams()).setMargins(0, actionBarHeight, 0, 0);
			sideBarWrapper.requestLayout();
		}

		((MarginLayoutParams) imagePanels.getLayoutParams()).setMargins(0, actionBarHeight, 0, 0);
		imagePanels.requestLayout();

		// Hide title text and set home as up
		actionBar.setDisplayShowTitleEnabled(false);
		// actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.translucent_black_rect));
	}

	private void attachButtons()
	{
		((ImageButton) findViewById(R.id.imageButtonLeftBack)).setOnClickListener(new PreviousImageClickListener());
		((ImageButton) findViewById(R.id.imageButtonLeftFwd)).setOnClickListener(new NextImageClickListener());
		// Rotate
		buttonRotate.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				int orientation = getRequestedOrientation();
				orientation = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
						: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
				setRequestedOrientation(orientation);
			}
		});
	}

	@Override
	protected void onDestroy()
	{
		// if (mAppBridge != null) {
		// mAppBridge.setServer(null);
		// mScreenNailItem.setScreenNail(null);
		// mAppBridge.detachScreenNail();
		// mAppBridge = null;
		// mScreenNailSet = null;
		// mScreenNailItem = null;
		// }
		mOrientationManager.removeListener(this);
		mGLRootView.setOrientationSource(null);

		// Remove all pending messages.
		mHandler.removeCallbacksAndMessages(null);
		super.onDestroy();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		// mGLRootView.onResume();
		mGLRootView.freeze();
		mGLRootView.setContentPane(mRootPane);
		mModel.resume();
		mPhotoView.resume();
		mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);
		mOrientationManager.resume();

		// if (mModel.isRecycled())
		// {
		// mModel.resetBitmaps();
		// mModel.requestNextImageWithMeta();
		// }
		// mImageViewer.prepareTextures();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mOrientationManager.pause();
		// mGLRootView.onPause();
		mGLRootView.unfreeze();
		mHandler.removeMessages(MSG_UNFREEZE_GLROOT);
		// This just does an animation back to the gallery...it crashes.
//		 if (isFinishing())
//		 preparePhotoFallbackView();

		mPhotoView.pause();
		mModel.pause();

		MetaMedia.getMicroThumbPool().clear();
		MetaMedia.getThumbPool().clear();
		MetaMedia.getBytesBufferPool().clear();

		// mGLRootView.lockRenderThread();
		// try
		// {
		// mImageViewer.freeTextures();
		// }
		// finally
		// {
		// mGLRootView.unlockRenderThread();
		// }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getSupportMenuInflater().inflate(R.menu.full_options, menu);
		MenuItem actionItem = menu.findItem(R.id.viewShare);
		if (actionItem != null)
		{
			mShareProvider = (ShareActionProvider) actionItem.getActionProvider();
			mShareProvider.setOnShareTargetSelectedListener(this);
			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			shareIntent.setType("image/jpeg");
			mShareProvider.setShareIntent(shareIntent);
		}
		return true;
	}

	@Override
	public boolean onShareTargetSelected(ShareActionProvider source, Intent intent)
	{
		share(intent, mModel.getCurrentItem());
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.view_save:
				saveImage();
				return true;
			case R.id.view_edit:
				editImage();
				return true;
			case R.id.view_wallpaper:
				setWallpaper();
				return true;
			case R.id.view_tag:
				tagImage();
				return true;
			case R.id.view_delete:
				deleteImage(mModel.getCurrentItem());
				return true;
			case R.id.view_recycle:
				showRecycleBin();
				return true;
			case R.id.viewSettings:
				startSettings();
				return true;
			case R.id.viewHelp:
				Toast.makeText(this, R.string.prefTitleComingSoon, Toast.LENGTH_SHORT).show();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void startSettings()
	{
		Intent settings = new Intent(ImageViewActivity.this, FullSettingsActivity.class);
		startActivity(settings);
	}

	private void editImage()
	{
		MediaObject media = mModel.getCurrentItem();
		BufferedInputStream imageData = media.getThumbInputStream();
		if (imageData == null)
		{
			Toast.makeText(this, R.string.warningFailedToGetStream, Toast.LENGTH_LONG).show();
			return;
		}
		File swapFile = getSwapFile(Util.swapExtention(media.getName(), "jpg"));
		write(swapFile, imageData);
		try
		{
			imageData.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		Intent action = new Intent(Intent.ACTION_EDIT);
		action.setDataAndType(Uri.fromFile(swapFile), "image/*");
		Intent chooser = Intent.createChooser(action, getResources().getString(R.string.edit));
		startActivityForResult(chooser, REQUEST_CODE_EDIT);
	}

	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_CODE_PICK_FILE_OR_DIRECTORY:
				if (resultCode == RESULT_OK && data != null)
				{
					File dest = new File(data.getData().getPath());

					SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
					SharedPreferences.Editor editor = settings.edit();
					editor.putString(RawDroid.PREFS_MOST_RECENT_SAVE, dest.getParent());
					editor.commit();

					MediaObject source = mModel.getCurrentItem();
					BufferedOutputStream bos = null;
					try
					{
						bos = new BufferedOutputStream(new FileOutputStream(dest));
						byte[] thumb = source.getThumb();
						if (thumb == null)
						{
							Toast.makeText(this, R.string.warningFailedToGetStream, Toast.LENGTH_LONG).show();
							return;
						}
						bos.write(thumb); // Assumes thumb is already in an image format (jpg at time of coding)
					}
					catch (IOException e)
					{
						Toast.makeText(this, R.string.save_fail, Toast.LENGTH_SHORT).show();
						return;
					}
					finally
					{
						if (bos != null)
							try
							{
								bos.close();
							}
							catch (IOException e)
							{
							}
					}

					Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
				}
				break;
			case REQUEST_CODE_EDIT:
				// This doesn't seem to return a result.
				// if (resultCode == RESULT_OK)
				// {
				// String test = "";
				// }
		}
	}

	private void showPanels()
	{
		isInterfaceHidden = false;
		imagePanels.post(new Runnable()
		{
			@TargetApi(11)
			public void run()
			{
				// imagePanels.setVisibility(View.VISIBLE);
				if (!PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getString(FullSettingsActivity.KEY_ShowNav, "Automatic")
						.equals("Never"))
					navigationPanel.setVisibility(View.VISIBLE);
				if (!PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getString(FullSettingsActivity.KEY_ShowMeta, "Automatic")
						.equals("Never"))
					metaPanel.setVisibility(View.VISIBLE);
				if (!PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getString(FullSettingsActivity.KEY_ShowHist, "Automatic")
						.equals("Never"))
					histView.setVisibility(View.VISIBLE);
				// navigationPanel.setVisibility(View.VISIBLE);
				buttonRotate.setVisibility(View.VISIBLE);
				// metaPanel.setVisibility(View.VISIBLE);
				// histView.setVisibility(View.VISIBLE);
				getSupportActionBar().show();
			}
		});
	}

	private void hidePanels()
	{
		isInterfaceHidden = true;
		imagePanels.post(new Runnable()
		{
			public void run()
			{

				if (!PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getString(FullSettingsActivity.KEY_ShowNav, "Automatic")
						.equals("Always"))
					navigationPanel.setVisibility(View.GONE);
				if (!PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getString(FullSettingsActivity.KEY_ShowMeta, "Automatic")
						.equals("Always"))
					metaPanel.setVisibility(View.GONE);
				if (!PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getString(FullSettingsActivity.KEY_ShowHist, "Automatic")
						.equals("Always"))
					histView.setVisibility(View.GONE);
				buttonRotate.setVisibility(View.GONE);
				// metaPanel.setVisibility(View.GONE);
				// histView.setVisibility(View.GONE);
				// imagePanels.setVisibility(View.GONE);
				getSupportActionBar().hide();
			}
		});
	}

	public void togglePanels()
	{
		if (autoHide != null)
			autoHide.cancel();
		if (isInterfaceHidden)
			showPanels();
		else
			hidePanels();
	}

	private void populateExif()
	{
		if (autoHide != null)
			autoHide.cancel();

		MetaMedia meta = mModel.getCurrentItem();

		if (metaDate == null || meta == null)
		{
			Toast.makeText(this, "Could not access metadata views, please email rawdroid@anthonymandra.com!", Toast.LENGTH_LONG).show();
			return;
		}
		
		metaDate.setText(meta.getDateTime());
		metaModel.setText(meta.getModel());
		metaIso.setText(meta.getIso());
		metaExposure.setText(meta.getExposure());
		metaAperture.setText(meta.getFNumber());
		metaFocal.setText(meta.getFocalLength());
		metaDimensions.setText(meta.getWidth() + " x " + meta.getHeight());//meta.getImageWidth() + " x " + meta.getImageHeight());
		metaAlt.setText(meta.getAltitude());
		metaFlash.setText(meta.getFlash());
		metaLat.setText(meta.getLatitude());
		metaLon.setText(meta.getLongitude());
		metaName.setText(meta.getName());
		metaWb.setText(meta.getWhiteBalance());
		metaLens.setText(meta.getLensModel());
		metaDriveMode.setText(meta.getDriveMode());
		metaExposureMode.setText(meta.getExposureMode());
		metaExposureProgram.setText(meta.getExposureProgram());

		autoHide = new Timer();
		autoHide.schedule(new AutoHideMetaTask(), 3000);
	}

	private void saveImage()
	{
		Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String startPath = settings.getString(RawDroid.PREFS_MOST_RECENT_SAVE, null);
		MediaObject media = mModel.getCurrentItem();

		if (startPath == null)
		{
			startPath = "";
		}

		File saveFile = new File(startPath + File.separator + media.getName());

		// Construct URI from file name.
		intent.setData(Uri.fromFile(saveFile));

		// Set fancy title and button (optional)
		intent.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.save_as));
		intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.save));

		try
		{
			startActivityForResult(intent, REQUEST_CODE_PICK_FILE_OR_DIRECTORY);
		}
		catch (ActivityNotFoundException e)
		{
			// No compatible file manager was found.
			Toast.makeText(ImageViewActivity.this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void updateAfterDelete()
	{
		updateImageSource();
		// updateViewerItems();
		// if (mVisibleItems.size() == 0)
		// {
		// onBackPressed();
		// }
		//
		// if (mCurrentIndex >= mVisibleItems.size())
		// {
		// swap out the deleted image
		// previousImage();
		// previous();
		// mCurrentIndex = mVisibleItems.size() -1;
		// mModel.setCurrentPhoto(getCurrentImage().getUri(), mCurrentIndex);
		// }
		// else
		// {
		// swap out the deleted image
		// com.android.gallery3d.util.Utils.swap(screenNails, INDEX_CURRENT, INDEX_PREVIOUS);
		// then tap the existing next functionality by stepping back index
		// decrementImageIndex();
		// next();
		// TODO: this will fail!
		// mModel.setCurrentPhoto(getCurrentImage().getUri(), mCurrentIndex);
		// }
		// deleteToNext();
	}

	@Override
	protected void updateAfterRestore()
	{
		updateImageSource();
		// updateViewerItems();
		// // mModel.refresh();
	}

	private void updateImageSource()
	{
		updateViewerItems();
		mModel.refresh();
	}

	private void setWallpaper()
	{
		try
		{
			byte[] imageData = mModel.getCurrentItem().getThumb();
			WallpaperManager.getInstance(this).setBitmap(Util.createBitmapToSize(imageData, displayWidth, displayHeight));
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString());
			Toast.makeText(ImageViewActivity.this, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show();
		}
	}

	private void tagImage()
	{
		if (getResources().getBoolean(R.bool.hasTwoPanes))
		{
			if (xmpFrag.isVisible())
			{
				hideSidebar();
			}
			else
			{
				if (autoHide != null)
					autoHide.cancel();
				hidePanels();
				showSidebar();
			}
		}
		else
		{
			if (xmpFrag != null && xmpFrag.isAdded())
			{
				onBackPressed();
			}
			else
			{
				if (autoHide != null)
					autoHide.cancel();
				hidePanels();

				FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
				xmpFrag = XmpFragment.newInstance(mModel.getCurrentItem());
				transaction.add(R.id.frameLayoutViewer, xmpFrag);
				transaction.addToBackStack(null);
				transaction.commit();
			}
		}
	}

	private void hideSidebar()
	{
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.hide(xmpFrag);
		transaction.commit();
	}

	private void showSidebar()
	{
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.show(xmpFrag);
		transaction.addToBackStack(null);
		transaction.commit();
	}

	class AutoHideMetaTask extends TimerTask
	{
		@Override
		public void run()
		{
			hidePanels();
		}
	}

	/**
	 * Process the current image for meta data.
	 * 
	 * @author amand_000
	 * 
	 */
	class LoadMetadataTask extends AsyncTask<Void, Void, Void>
	{
		@Override
		protected Void doInBackground(Void... params)
		{
			mModel.getCurrentItem().readMetadata();
			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			populateExif();

			if (PreferenceManager.getDefaultSharedPreferences(ImageViewActivity.this).getBoolean(FullSettingsActivity.KEY_ShowImageInterface,
					RawDroid.PREFS_AUTO_INTERFACE_DEFAULT))
			{
				showPanels();
			}

			if (xmpFrag != null && xmpFrag.isAdded())
				xmpFrag.setMediaObject(mModel.getCurrentItem());
		}
	}

	class PreviousImageClickListener implements View.OnClickListener
	{

		@Override
		public void onClick(View v)
		{
			mModel.switchToPrevImage();
		}
	}

	class NextImageClickListener implements View.OnClickListener
	{

		@Override
		public void onClick(View v)
		{
			mModel.switchToNextImage();
		}
	}

	// private class MyImageViewerModel implements ImageViewer.ImageModel
	// {
	// private BitmapRegionDecoder mLargeBitmap;
	// private Bitmap mScreenNails[] = new Bitmap[3]; // prev, curr, next
	//
	// public BitmapRegionDecoder getLargeBitmap()
	// {
	// return mLargeBitmap;
	// }
	//
	// public boolean isRecycled()
	// {
	// for (Bitmap screennail : mScreenNails)
	// {
	// if (screennail != null && screennail.isRecycled())
	// return true;
	// }
	// return false;
	// }
	//
	// public void resetCurrent()
	// {
	// if (mScreenNails[INDEX_CURRENT] != null)
	// {
	// mScreenNails[INDEX_CURRENT].recycle();
	// mScreenNails[INDEX_CURRENT] = null;
	// }
	// }
	//
	// public void resetNext()
	// {
	// if (mScreenNails[INDEX_NEXT] != null)
	// {
	// mScreenNails[INDEX_NEXT].recycle();
	// mScreenNails[INDEX_NEXT] = null;
	// }
	// }
	//
	// public void resetPrevious()
	// {
	// if (mScreenNails[INDEX_PREVIOUS] != null)
	// {
	// mScreenNails[INDEX_PREVIOUS].recycle();
	// mScreenNails[INDEX_PREVIOUS] = null;
	// }
	// }
	//
	// public void resetBitmaps()
	// {
	// resetPrevious();
	// resetCurrent();
	// resetNext();
	// }
	//
	// public void refresh()
	// {
	// resetBitmaps();
	// requestNextImageWithMeta();
	// }
	//
	// public ImageData getImageData(int which)
	// {
	// Bitmap screennail = mScreenNails[which];
	// if (screennail == null)
	// return null;
	//
	// int width = 0;
	// int height = 0;
	//
	// if (which == INDEX_CURRENT && mLargeBitmap != null)
	// {
	// width = mLargeBitmap.getWidth();
	// height = mLargeBitmap.getHeight();
	// }
	// else
	// {
	// // We cannot get the size of image before getting the
	// // full-size image. In the future, we should add the data to
	// // database or get it from the header in runtime. Now, we
	// // just use the thumb-nail image to estimate the size
	// float scaleW = (float) displayWidth / screennail.getWidth();
	// float scaleH = (float) displayHeight / screennail.getHeight();
	// float scale = Math.min(scaleW, scaleH);
	// // float scale = (float) TARGET_LENGTH / Math.max(screennail.getWidth(), screennail.getHeight());
	// width = Math.round(screennail.getWidth() * scale);
	// height = Math.round(screennail.getHeight() * scale);
	// }
	// return new ImageData(width, height, screennail);
	// }
	//
	// public void next()
	// {
	// if (mCurrentIndex >= mVisibleItems.size() - 1)
	// {
	// runOnUiThread(new Runnable()
	// {
	// @Override
	// public void run()
	// {
	// Toast.makeText(ImageViewActivity.this, R.string.lastImage, Toast.LENGTH_SHORT).show();
	// }
	//
	// });
	// return;
	// }
	// incrementImageIndex();
	// Bitmap[] screenNails = mScreenNails;
	//
	// if (screenNails[INDEX_PREVIOUS] != null)
	// {
	// screenNails[INDEX_PREVIOUS].recycle();
	// screenNails[INDEX_PREVIOUS] = null;
	// }
	// screenNails[INDEX_PREVIOUS] = screenNails[INDEX_CURRENT];
	// screenNails[INDEX_CURRENT] = screenNails[INDEX_NEXT];
	// screenNails[INDEX_NEXT] = null;
	//
	// if (mLargeBitmap != null)
	// {
	// mLargeBitmap.recycle();
	// mLargeBitmap = null;
	// }
	//
	// requestNextImageWithMeta();
	// }
	//
	// public void previous()
	// {
	// if (mCurrentIndex == 0)
	// {
	// runOnUiThread(new Runnable()
	// {
	// @Override
	// public void run()
	// {
	// Toast.makeText(ImageViewActivity.this, R.string.firstImage, Toast.LENGTH_SHORT).show();
	// }
	// });
	// return;
	// }
	// decrementImageIndex();
	// Bitmap[] screenNails = mScreenNails;
	//
	// if (screenNails[INDEX_NEXT] != null)
	// {
	// screenNails[INDEX_NEXT].recycle();
	// screenNails[INDEX_NEXT] = null;
	// }
	// screenNails[INDEX_NEXT] = screenNails[INDEX_CURRENT];
	// screenNails[INDEX_CURRENT] = screenNails[INDEX_PREVIOUS];
	// screenNails[INDEX_PREVIOUS] = null;
	//
	// if (mLargeBitmap != null)
	// {
	// mLargeBitmap.recycle();
	// mLargeBitmap = null;
	// }
	//
	// requestNextImageWithMeta();
	// }
	//
	// public void deleteToPrevious()
	// {
	// Bitmap[] screenNails = mScreenNails;
	//
	// if (screenNails[INDEX_CURRENT] != null)
	// {
	// screenNails[INDEX_CURRENT].recycle();
	// screenNails[INDEX_CURRENT] = null;
	// }
	// screenNails[INDEX_CURRENT] = screenNails[INDEX_PREVIOUS];
	// screenNails[INDEX_PREVIOUS] = null;
	//
	// if (mLargeBitmap != null)
	// {
	// mLargeBitmap.recycle();
	// mLargeBitmap = null;
	// }
	//
	// mImageViewer.initiateDeleteTransition();
	// }
	//
	// public void deleteToNext()
	// {
	// Bitmap[] screenNails = mScreenNails;
	//
	// if (mCurrentIndex >= mVisibleItems.size())
	// {
	// // swap out the deleted image
	// previous();
	// }
	// else
	// {
	// // swap out the deleted image
	// com.android.gallery3d.util.Utils.swap(screenNails, INDEX_CURRENT, INDEX_PREVIOUS);
	// // then tap the existing next functionality by stepping back index
	// decrementImageIndex();
	// next();
	// }
	// }
	//
	// public void updateScreenNail(int index, Bitmap screenNail)
	// {
	// int offset = (index - mCurrentIndex) + 1; // Zero-based -1,0,1 (0,1,2)
	//
	// if (screenNail != null)
	// {
	// if (offset < 0 || offset > 2)
	// {
	// screenNail.recycle();
	// return;
	// }
	// mScreenNails[offset] = screenNail;
	// mImageViewer.notifyScreenNailInvalidated(offset);
	// }
	// // requestNextImage();
	// }
	//
	// public void updateLargeImage(int index, BitmapRegionDecoder largeBitmap)
	// {
	// int offset = (index - mCurrentIndex) + 1;
	//
	// if (largeBitmap != null)
	// {
	// if (offset != INDEX_CURRENT)
	// {
	// largeBitmap.recycle();
	// return;
	// }
	//
	// mLargeBitmap = largeBitmap;
	// mImageViewer.notifyLargeBitmapInvalidated();
	// // We need to update the estimated width and height
	// mImageViewer.notifyScreenNailInvalidated(INDEX_CURRENT);
	// }
	// // requestNextImage();
	// }
	//
	// public void requestNextImageWithMeta()
	// {
	// // mImageViewer.setRotation(0);
	// loadExif();
	// requestNextImage();
	// }
	//
	// public void requestNextImage()
	// {
	// // First request the current screen nail
	// if (mScreenNails[INDEX_CURRENT] == null)
	// {
	// MediaObject current = getCurrentImage();
	// if (current != null)
	// {
	// CurrentImageLoader cml = new CurrentImageLoader();
	// cml.executeOnExecutor(LibRaw.EXECUTOR, mCurrentIndex, current);
	// // return;
	// }
	// }
	// else
	// {
	// HistogramTask ht = new HistogramTask();
	// ht.execute(mScreenNails[INDEX_CURRENT]);
	// }
	//
	// // Next, the next screen nail if not last image
	// if (mScreenNails[INDEX_NEXT] == null && !(mCurrentIndex >= mVisibleItems.size() - 1))
	// {
	// MediaObject next = mVisibleItems.get(mCurrentIndex + 1);
	// if (next != null)
	// {
	// SmallImageLoader sml = new SmallImageLoader();
	// sml.executeOnExecutor(LibRaw.EXECUTOR, mCurrentIndex + 1, next);
	// // return;
	// }
	// }
	//
	// // Next, the previous screen nail if not the first image
	// if (mScreenNails[INDEX_PREVIOUS] == null && mCurrentIndex > 0)
	// {
	// MediaObject previous = mVisibleItems.get(mCurrentIndex - 1);
	// if (previous != null)
	// {
	// SmallImageLoader sml = new SmallImageLoader();
	// sml.executeOnExecutor(LibRaw.EXECUTOR, mCurrentIndex - 1, previous);
	// // return;
	// }
	// }
	//
	// // Next, the full size image
	// if (mLargeBitmap == null)
	// {
	// MediaObject current = getCurrentImage();
	// if (current != null)
	// {
	// LargeImageLoader lml = new LargeImageLoader();
	// lml.executeOnExecutor(LibRaw.EXECUTOR, mCurrentIndex, current);
	// // return;
	// }
	// }
	// }
	// }

	// public void deleteToNext()
	// {
	// mModel.deleteToNext();
	// }
	//
	// public void deleteToPrevious()
	// {
	// mModel.deleteToPrevious();
	// }

	// private class CurrentImageLoader extends SmallImageLoader
	// {
	// @Override
	// protected void onPostExecute(Bitmap result)
	// {
	// super.onPostExecute(result);
	// HistogramTask ht = new HistogramTask();
	// ht.execute(result);
	// }
	// }
	//
	// private class SmallImageLoader extends AsyncTask<Object, Void, Bitmap>
	// {
	// int mIndex;
	//
	// @Override
	// protected Bitmap doInBackground(Object... params)
	// {
	// mIndex = (Integer) params[0];
	// MediaObject mMedia = (MediaObject) params[1];
	// byte[] imageData = mMedia.getThumb();
	// if (imageData == null)
	// return null;
	// return Utils.createBitmapLarge(imageData, ImageViewActivity.displayWidth, ImageViewActivity.displayHeight, true);
	// }
	//
	// @Override
	// protected void onPostExecute(Bitmap result)
	// {
	// mGLRootView.lockRenderThread();
	// mModel.updateScreenNail(mIndex, result);
	// mGLRootView.unlockRenderThread();
	// }
	// }
	//
	// private class LargeImageLoader extends AsyncTask<Object, Void, BitmapRegionDecoder>
	// {
	// int mIndex;
	//
	// @Override
	// protected BitmapRegionDecoder doInBackground(Object... params)
	// {
	// mIndex = (Integer) params[0];
	// MediaObject mMedia = (MediaObject) params[1];
	// byte[] imageData = mMedia.getThumb();
	// if (imageData == null)
	// return null;
	//
	// try
	// {
	// return BitmapRegionDecoder.newInstance(imageData, 0, imageData.length, false);
	// }
	// catch (IOException e)
	// {
	// return null;
	// }
	// }
	//
	// @Override
	// protected void onPostExecute(BitmapRegionDecoder result)
	// {
	// mGLRootView.lockRenderThread();
	// mModel.updateLargeImage(mIndex, result);
	// mGLRootView.unlockRenderThread();
	// }
	// }

	public class HistogramTask extends AsyncTask<Bitmap, Void, Histogram>
	{

		// http://zerocool.is-a-geek.net/?p=269
		@Override
		protected Histogram doInBackground(Bitmap... params)
		{
			Bitmap input = params[0];
			Histogram result = new Histogram();

			if (input == null || input.isRecycled())
				return null;

			int width = input.getWidth();
			int height = input.getHeight();

			// Kind of arbitrary, reduce the number of pixels processed.
			int pixelStride = 1;
			int lineStride = 1;
			if (width >= 256 && height >= 256)
			{
				pixelStride = width / 256;
				lineStride = height / 256;
			}

			for (int i = 0; i < input.getWidth(); i += pixelStride)// i++)
			{
				for (int j = 0; j < input.getHeight(); j += lineStride)// j++)
				{
					try
					{
						int pixel = input.getPixel(i, j);
						result.processPixel(pixel);
					}
					catch (Exception e)
					{
						Log.d(TAG, "Histogram: getPixel failed: ", e);
						return null;
					}
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(Histogram result)
		{
			if (result != null)
				histView.updateHistogram(result);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key == FullSettingsActivity.KEY_ShowNativeFiles)
		{
			updateViewerItems();

			MediaObject media = mModel.getCurrentItem();

			// If current images are native and viewing is turned off finish activity
			if (media == null || !sharedPreferences.getBoolean(key, true) && isNative(new File(media.getPath())))
			{
				if (mVisibleItems.size() > 0)
				{
					mModel.switchToFirstImage();
				}
				else
				{
					Toast.makeText(this, "All images were native, returning to gallery.", Toast.LENGTH_SHORT).show();
					finish();
				}
			}
		}
	}

	@Override
	public Context getAndroidContext()
	{
		return this;
	}

	@Override
	public ThreadPool getThreadPool()
	{
		if (mThreadPool == null)
		{
			mThreadPool = new ThreadPool();
		}
		return mThreadPool;
	}

	@Override
	public ImageCacheService getImageCacheService()
	{
		// This method may block on file I/O so a dedicated lock is needed here.
		synchronized (mLock)
		{
			if (mImageCacheService == null)
			{
				mImageCacheService = new ImageCacheService(getAndroidContext());
			}
			return mImageCacheService;
		}
	}

	@Override
	public GLRoot getGLRoot()
	{
		return mGLRootView;
	}

	@Override
	public OrientationManager getOrientationManager()
	{
		return mOrientationManager;
	}

	@Override
	public TransitionStore getTransitionStore()
	{
		return mTransitionStore;
	}

	@Override
	public void onOrientationCompensationChanged()
	{
		mGLRootView.requestLayoutContentPane();
	}

	@Override
	public void onSingleTapUp(int x, int y)
	{
		// TODO Auto-generated method stub
	}

	@Override
	public void onSingleTapConfirmed()
	{
		togglePanels();
	}

	@Override
	public void lockOrientation()
	{
		mHandler.sendEmptyMessage(MSG_LOCK_ORIENTATION);
	}

	@Override
	public void unlockOrientation()
	{
		mHandler.sendEmptyMessage(MSG_UNLOCK_ORIENTATION);
	}

	@Override
	public void onFullScreenChanged(boolean full)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onActionBarAllowed(boolean allowed)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onActionBarWanted()
	{
		// TODO Auto-generated method stub

	}

	@Override
	/**
	 * This seems to occur when the first image is loaded.
	 */
	public void onCurrentImageUpdated()
	{
		// getGLRoot().unfreeze();

		updateImageDetails();
	}

	@Override
	/**
	 * This occurs whenever the current image changes.
	 */
	public void onPhotoChanged(int index, Uri path)
	{

		updateImageDetails();
	}

	private void updateImageDetails()
	{
		if (mModel.getCurrentItem() != null)
		{
			new LoadMetadataTask().execute();

			new HistogramTask().execute(mModel.getCurrentBitmap());
		}
	}

	// @Override
	// public void onDeleteImage(Path path, int offset)
	// {
	// // TODO Auto-generated method stub
	//
	// }

	@Override
	public void onUndoDeleteImage()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onCommitDeleteImage()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onDeleteImage(String path, int offset)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onLoadingStarted()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onLoadingFinished()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onScaleChanged(float scale)
	{
		final String zoom = String.valueOf((int) (scale * 100) + "%");
		zoomLevel.post(new Runnable()
		{
			@Override
			public void run()
			{
				zoomLevel.setText(zoom);
			}
		});
	}

	// @Override
	// public void onLoadingFinished()
	// {
	// //TODO Do nothing for now
	// // if (!mModel.isEmpty())
	// // {
	// // MetaMedia photo = mModel.getMediaItem(0);
	// // if (photo != null)
	// // updateCurrentPhoto(photo);
	// // }
	// // else if (mIsActive)
	// // {
	// // // We only want to finish the PhotoPage if there is no
	// // // deletion that the user can undo.
	// // if (mMediaSet.getNumberOfDeletions() == 0)
	// // {
	// // mActivity.getStateManager().finishState(PhotoPage.this);
	// // }
	// // }
	// }
	//
	// @Override
	// public void onLoadingStarted()
	// {
	// }
	//
	// @Override
	// public void onPhotoChanged(int index)
	// {
	// mCurrentIndex = index;
	// //TODO Do nothing for now
	// // if (item != null)
	// // {
	// // MediaItem photo = mModel.getMediaItem(0);
	// // if (photo != null)
	// // updateCurrentPhoto(photo);
	// // }
	// // updateBars();
	// }

}