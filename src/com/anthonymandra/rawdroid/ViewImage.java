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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Bundle;
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
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.ImageViewer;
import com.android.gallery3d.ui.ImageViewer.ImageData;
import com.android.gallery3d.ui.ImageViewer.ScaleChangedListener;
import com.anthonymandra.dcraw.LibRaw;
import com.anthonymandra.framework.AsyncTask;
import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.Histogram;
import com.anthonymandra.framework.MediaObject;
import com.anthonymandra.framework.MetaMedia;
import com.anthonymandra.framework.Utils;
import com.anthonymandra.widget.HistogramView;

public class ViewImage extends GalleryActivity implements ScaleChangedListener, OnShareTargetSelectedListener, OnSharedPreferenceChangeListener
{
	private static final String TAG = "ViewImage";

	protected static final int REQUEST_CODE_PICK_FILE_OR_DIRECTORY = 1;
	protected static final int REQUEST_CODE_KEYWORDS = 2;
	protected static final int REQUEST_CODE_EDIT = 3;

	private ImageViewer mImageViewer;
	private final MyImageViewerModel mModel = new MyImageViewerModel();
	private boolean isInterfaceHidden;

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
			if (mImageViewer != null)
			{
				mImageViewer.layout(0, 0, right - left, bottom - top);
			}
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

	private int mImageIndex;
	ShareActionProvider mShareProvider;

	XmpFragment xmpFrag;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayWidth = metrics.widthPixels;
		displayHeight = metrics.heightPixels;

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(this);

		Uri data = getIntent().getData();
		if (data == null)
		{
			finish();
		}
		
		File input = new File(data.getPath());
		if (!input.exists())
		{
			finish();
		}
		
		if (input.isDirectory())
		{
			setPath(input);
			mImageIndex = 0;
			updateViewerItems();
		}
		else
		{
			File parent = input.getParentFile();
			if (parent.exists())
				setPath(input.getParentFile());
			else
				setSingeImage(input);
			
			mImageIndex = findMediaByFilename(input.getPath());		//Could probably just use this method and remove KEY_PHOTO_INDEX
			updateViewerItems();
			
			// If a native image is sent make sure the settings are set to view it
			if (isNative(input) && !settings.getBoolean(FullSettingsActivity.KEY_ShowNativeFiles, false))
			{
				Editor editor = settings.edit();
				editor.putBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);
				editor.commit();
			}
		}
		
		setContentView(R.layout.viewer_layout);
		lookupViews();

		if (getResources().getBoolean(R.bool.hasTwoPanes))
		{
			xmpFrag = (XmpFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentSideBar);
			hideSidebar();
		}

		// decodeProgress = (FrameLayout) findViewById(R.id.frameRawProgress);

		mImageViewer = new ImageViewer(this);
		mImageViewer.setModel(mModel);
		mRootPane.addComponent(mImageViewer);
		mModel.requestNextImageWithMeta();

		attachButtons();

		setActionBar();
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
	public void onResume()
	{
		super.onResume();
		mGLRootView.onResume();
		mGLRootView.setContentPane(mRootPane);

		if (mModel.isRecycled())
		{
			mModel.resetBitmaps();
			mModel.requestNextImageWithMeta();
		}
		mImageViewer.prepareTextures();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mGLRootView.onPause();
		mGLRootView.lockRenderThread();
		try
		{
			mImageViewer.freeTextures();
		}
		finally
		{
			mGLRootView.unlockRenderThread();
		}
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
		share(intent, getCurrentImage());
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
				deleteImage(getCurrentImage());
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
		Intent settings = new Intent(ViewImage.this, FullSettingsActivity.class);
		startActivity(settings);
	}

	private void editImage()
	{
		MediaObject media = getCurrentImage();
		BufferedInputStream imageData = media.getThumbInputStream();
		if (imageData == null)
		{
			Toast.makeText(this, R.string.warningFailedToGetStream, Toast.LENGTH_LONG).show();
			return;
		}
		File swapFile = getSwapFile(Utils.swapExtention(media.getName(), "jpg"));
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

					MediaObject source = getCurrentImage();
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
				if (!PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getString(FullSettingsActivity.KEY_ShowNav, "Automatic").equals("Never"))
					navigationPanel.setVisibility(View.VISIBLE);
				if (!PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getString(FullSettingsActivity.KEY_ShowMeta, "Automatic").equals("Never"))
					metaPanel.setVisibility(View.VISIBLE);
				if (!PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getString(FullSettingsActivity.KEY_ShowHist, "Automatic").equals("Never"))
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

				if (!PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getString(FullSettingsActivity.KEY_ShowNav, "Automatic").equals("Always"))
					navigationPanel.setVisibility(View.GONE);
				if (!PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getString(FullSettingsActivity.KEY_ShowMeta, "Automatic").equals("Always"))
					metaPanel.setVisibility(View.GONE);
				if (!PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getString(FullSettingsActivity.KEY_ShowHist, "Automatic").equals("Always"))
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

	public void loadExif()
	{
		if (mImageIndex < 0 || mImageIndex >= mVisibleItems.size())
			return;
		MetaMedia raw = getCurrentImage();
		if (raw == null)
			return;

		new LoadMetadataTask().execute();
	}

	private void populateExif()
	{
		if (autoHide != null)
			autoHide.cancel();

		MetaMedia meta = getCurrentImage();
		int rotation = meta.getOrientation();
		if (rotation != 0)
		{
			mImageViewer.setRotation(rotation);
		}

		metaDate.setText(meta.getDateTime());
		metaModel.setText(meta.getModel());
		metaIso.setText(meta.getIso());
		metaExposure.setText(meta.getExposure());
		metaAperture.setText(meta.getFNumber());
		metaFocal.setText(meta.getFocalLength());
		metaDimensions.setText(meta.getImageWidth() + " x " + meta.getImageHeight());
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

	private MetaMedia getCurrentImage()
	{
		if (mImageIndex < 0 || mImageIndex >= mVisibleItems.size())
		{
			Toast.makeText(this, "Error 2x01: Failed to load requested image.  If this continues please email details leading to this bug!", Toast.LENGTH_LONG).show();
			if (mVisibleItems.size() == 0)
			{
				finish();
			}
			else
			{
				mImageIndex = 0;				
			}
		}
//		MediaObject current = 
//		this.getIntent().setData(data);
		return mVisibleItems.get(mImageIndex);
	}

	private void saveImage()
	{
		Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String startPath = settings.getString(RawDroid.PREFS_MOST_RECENT_SAVE, null);
		MediaObject media = getCurrentImage();

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
			Toast.makeText(ViewImage.this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void updateAfterDelete()
	{
		updateViewerItems();
		if (mVisibleItems.size() == 0)
		{
			onBackPressed();
		}

		deleteToNext();
	}

	@Override
	protected void updateAfterRestore()
	{
		updateViewerItems();
		mModel.refresh();
	}

	private void setWallpaper()
	{
		try
		{
			byte[] imageData = getCurrentImage().getThumb();
			WallpaperManager.getInstance(this).setBitmap(Utils.createBitmapToSize(imageData, displayWidth, displayHeight));
		}
		catch (Exception e)
		{
			Log.e(TAG, e.toString());
			Toast.makeText(ViewImage.this, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show();
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
				xmpFrag = XmpFragment.newInstance(getCurrentImage());
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

	public void decrementImageIndex()
	{
		--mImageIndex;
	}

	public void incrementImageIndex()
	{
		++mImageIndex;
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
			getCurrentImage().readMetadata();
			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			populateExif();

			if (PreferenceManager.getDefaultSharedPreferences(ViewImage.this).getBoolean(FullSettingsActivity.KEY_ShowImageInterface,
					RawDroid.PREFS_AUTO_INTERFACE_DEFAULT))
			{
				showPanels();
			}

			if (xmpFrag != null && xmpFrag.isAdded())
				xmpFrag.setMediaObject(getCurrentImage());
		}
	}

	class PreviousImageClickListener implements View.OnClickListener
	{

		@Override
		public void onClick(View v)
		{
			previousImage();
		}
	}

	class NextImageClickListener implements View.OnClickListener
	{

		@Override
		public void onClick(View v)
		{
			nextImage();
		}
	}

	public void resetCurrentImage()
	{
		mModel.resetCurrent();
	}

	private class MyImageViewerModel implements ImageViewer.Model
	{
		private BitmapRegionDecoder mLargeBitmap;
		private Bitmap mScreenNails[] = new Bitmap[3]; // prev, curr, next

		public BitmapRegionDecoder getLargeBitmap()
		{
			return mLargeBitmap;
		}

		public boolean isRecycled()
		{
			for (Bitmap screennail : mScreenNails)
			{
				if (screennail != null && screennail.isRecycled())
					return true;
			}
			return false;
		}

		public void resetCurrent()
		{
			if (mScreenNails[INDEX_CURRENT] != null)
			{
				mScreenNails[INDEX_CURRENT].recycle();
				mScreenNails[INDEX_CURRENT] = null;
			}
		}

		public void resetNext()
		{
			if (mScreenNails[INDEX_NEXT] != null)
			{
				mScreenNails[INDEX_NEXT].recycle();
				mScreenNails[INDEX_NEXT] = null;
			}
		}

		public void resetPrevious()
		{
			if (mScreenNails[INDEX_PREVIOUS] != null)
			{
				mScreenNails[INDEX_PREVIOUS].recycle();
				mScreenNails[INDEX_PREVIOUS] = null;
			}
		}

		public void resetBitmaps()
		{
			resetPrevious();
			resetCurrent();
			resetNext();
		}

		public void refresh()
		{
			resetBitmaps();
			requestNextImageWithMeta();
		}

		public ImageData getImageData(int which)
		{
			Bitmap screennail = mScreenNails[which];
			if (screennail == null)
				return null;

			int width = 0;
			int height = 0;

			if (which == INDEX_CURRENT && mLargeBitmap != null)
			{
				width = mLargeBitmap.getWidth();
				height = mLargeBitmap.getHeight();
			}
			else
			{
				// We cannot get the size of image before getting the
				// full-size image. In the future, we should add the data to
				// database or get it from the header in runtime. Now, we
				// just use the thumb-nail image to estimate the size
				float scaleW = (float) displayWidth / screennail.getWidth();
				float scaleH = (float) displayHeight / screennail.getHeight();
				float scale = Math.min(scaleW, scaleH);
				// float scale = (float) TARGET_LENGTH / Math.max(screennail.getWidth(), screennail.getHeight());
				width = Math.round(screennail.getWidth() * scale);
				height = Math.round(screennail.getHeight() * scale);
			}
			return new ImageData(width, height, screennail);
		}

		public void next()
		{
			if (mImageIndex >= mVisibleItems.size() - 1)
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(ViewImage.this, R.string.lastImage, Toast.LENGTH_SHORT).show();
					}

				});
				return;
			}
			incrementImageIndex();
			Bitmap[] screenNails = mScreenNails;

			if (screenNails[INDEX_PREVIOUS] != null)
			{
				screenNails[INDEX_PREVIOUS].recycle();
				screenNails[INDEX_PREVIOUS] = null;
			}
			screenNails[INDEX_PREVIOUS] = screenNails[INDEX_CURRENT];
			screenNails[INDEX_CURRENT] = screenNails[INDEX_NEXT];
			screenNails[INDEX_NEXT] = null;

			if (mLargeBitmap != null)
			{
				mLargeBitmap.recycle();
				mLargeBitmap = null;
			}

			requestNextImageWithMeta();
		}

		public void previous()
		{
			if (mImageIndex == 0)
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(ViewImage.this, R.string.firstImage, Toast.LENGTH_SHORT).show();
					}
				});
				return;
			}
			decrementImageIndex();
			Bitmap[] screenNails = mScreenNails;

			if (screenNails[INDEX_NEXT] != null)
			{
				screenNails[INDEX_NEXT].recycle();
				screenNails[INDEX_NEXT] = null;
			}
			screenNails[INDEX_NEXT] = screenNails[INDEX_CURRENT];
			screenNails[INDEX_CURRENT] = screenNails[INDEX_PREVIOUS];
			screenNails[INDEX_PREVIOUS] = null;

			if (mLargeBitmap != null)
			{
				mLargeBitmap.recycle();
				mLargeBitmap = null;
			}

			requestNextImageWithMeta();
		}

		public void deleteToPrevious()
		{
			Bitmap[] screenNails = mScreenNails;

			if (screenNails[INDEX_CURRENT] != null)
			{
				screenNails[INDEX_CURRENT].recycle();
				screenNails[INDEX_CURRENT] = null;
			}
			screenNails[INDEX_CURRENT] = screenNails[INDEX_PREVIOUS];
			screenNails[INDEX_PREVIOUS] = null;

			if (mLargeBitmap != null)
			{
				mLargeBitmap.recycle();
				mLargeBitmap = null;
			}

			mImageViewer.initiateDeleteTransition();
		}

		public void deleteToNext()
		{
			Bitmap[] screenNails = mScreenNails;

			if (mImageIndex >= mVisibleItems.size())
			{
				// swap out the deleted image
				previous();
			}
			else
			{
				// swap out the deleted image
				com.android.gallery3d.util.Utils.swap(screenNails, INDEX_CURRENT, INDEX_PREVIOUS);
				// then tap the existing next functionality by stepping back index
				decrementImageIndex();
				next();
			}
		}

		public void updateScreenNail(int index, Bitmap screenNail)
		{
			int offset = (index - mImageIndex) + 1; // Zero-based -1,0,1 (0,1,2)

			if (screenNail != null)
			{
				if (offset < 0 || offset > 2)
				{
					screenNail.recycle();
					return;
				}
				mScreenNails[offset] = screenNail;
				mImageViewer.notifyScreenNailInvalidated(offset);
			}
			// requestNextImage();
		}

		public void updateLargeImage(int index, BitmapRegionDecoder largeBitmap)
		{
			int offset = (index - mImageIndex) + 1;

			if (largeBitmap != null)
			{
				if (offset != INDEX_CURRENT)
				{
					largeBitmap.recycle();
					return;
				}

				mLargeBitmap = largeBitmap;
				mImageViewer.notifyLargeBitmapInvalidated();
				// We need to update the estimated width and height
				mImageViewer.notifyScreenNailInvalidated(INDEX_CURRENT);
			}
			// requestNextImage();
		}

		public void requestNextImageWithMeta()
		{
			// mImageViewer.setRotation(0);
			loadExif();
			requestNextImage();
		}

		public void requestNextImage()
		{
			// First request the current screen nail
			if (mScreenNails[INDEX_CURRENT] == null)
			{
				MediaObject current = getCurrentImage();
				if (current != null)
				{
					CurrentImageLoader cml = new CurrentImageLoader();
					cml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex, current);
					// return;
				}
			}
			else
			{
				HistogramTask ht = new HistogramTask();
				ht.execute(mScreenNails[INDEX_CURRENT]);
			}

			// Next, the next screen nail if not last image
			if (mScreenNails[INDEX_NEXT] == null && !(mImageIndex >= mVisibleItems.size() - 1))
			{
				MediaObject next = mVisibleItems.get(mImageIndex + 1);
				if (next != null)
				{
					SmallImageLoader sml = new SmallImageLoader();
					sml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex + 1, next);
					// return;
				}
			}

			// Next, the previous screen nail if not the first image
			if (mScreenNails[INDEX_PREVIOUS] == null && mImageIndex > 0)
			{
				MediaObject previous = mVisibleItems.get(mImageIndex - 1);
				if (previous != null)
				{
					SmallImageLoader sml = new SmallImageLoader();
					sml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex - 1, previous);
					// return;
				}
			}

			// Next, the full size image
			if (mLargeBitmap == null)
			{
				MediaObject current = getCurrentImage();
				if (current != null)
				{
					LargeImageLoader lml = new LargeImageLoader();
					lml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex, current);
					// return;
				}
			}
		}
	}

	public void nextImage()
	{
		mModel.next();
	}

	public void deleteToNext()
	{
		mModel.deleteToNext();
	}

	public void deleteToPrevious()
	{
		mModel.deleteToPrevious();
	}

	public void previousImage()
	{
		mModel.previous();
	}

	private class CurrentImageLoader extends SmallImageLoader
	{
		@Override
		protected void onPostExecute(Bitmap result)
		{
			super.onPostExecute(result);
			HistogramTask ht = new HistogramTask();
			ht.execute(result);
		}
	}

	private class SmallImageLoader extends AsyncTask<Object, Void, Bitmap>
	{
		int mIndex;

		@Override
		protected Bitmap doInBackground(Object... params)
		{
			mIndex = (Integer) params[0];
			MediaObject mMedia = (MediaObject) params[1];
			byte[] imageData = mMedia.getThumb();
			if (imageData == null)
				return null;
			return Utils.createBitmapLarge(imageData, ViewImage.displayWidth, ViewImage.displayHeight, true);
		}

		@Override
		protected void onPostExecute(Bitmap result)
		{
			mGLRootView.lockRenderThread();
			mModel.updateScreenNail(mIndex, result);
			mGLRootView.unlockRenderThread();
		}
	}

	private class LargeImageLoader extends AsyncTask<Object, Void, BitmapRegionDecoder>
	{
		int mIndex;

		@Override
		protected BitmapRegionDecoder doInBackground(Object... params)
		{
			mIndex = (Integer) params[0];
			MediaObject mMedia = (MediaObject) params[1];
			byte[] imageData = mMedia.getThumb();
			if (imageData == null)
				return null;

			try
			{
				return BitmapRegionDecoder.newInstance(imageData, 0, imageData.length, false);
			}
			catch (IOException e)
			{
				return null;
			}
		}

		@Override
		protected void onPostExecute(BitmapRegionDecoder result)
		{
			mGLRootView.lockRenderThread();
			mModel.updateLargeImage(mIndex, result);
			mGLRootView.unlockRenderThread();
		}
	}

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
					catch(Exception e)
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
	public void onScaleChanged(float currentScale)
	{
		final String scale = String.valueOf((int) (currentScale * 100) + "%");
		zoomLevel.post(new Runnable()
		{
			@Override
			public void run()
			{
				zoomLevel.setText(scale);
			}
		});
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{		
		if (key == FullSettingsActivity.KEY_ShowNativeFiles)
		{
			updateViewerItems();
			
			MediaObject media = getCurrentImage();
			
			// If current images are native and viewing is turned off finish activity
			if (!sharedPreferences.getBoolean(key, false) && isNative(new File(media.getPath())))
			{
				finish();
			}					
		}
	}
}