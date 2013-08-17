package com.anthonymandra.rawdroid;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import org.openintents.intents.FileManagerIntents;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
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
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.actionbarsherlock.widget.ShareActionProvider.OnShareTargetSelectedListener;
import com.android.gallery3d.app.PhotoDataAdapter.DataListener;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.anthonymandra.framework.AsyncTask;
import com.anthonymandra.framework.Histogram;
import com.anthonymandra.framework.MetaMedia;
import com.anthonymandra.framework.Util;
import com.anthonymandra.widget.HistogramView;

public class ImageViewActivity extends PhotoPage implements OnShareTargetSelectedListener,
        OnSharedPreferenceChangeListener, DataListener
{
	private static final String TAG = ImageViewActivity.class.getSimpleName();

	protected static final int REQUEST_CODE_PICK_FILE_OR_DIRECTORY = 1;
	protected static final int REQUEST_CODE_KEYWORDS = 2;
	protected static final int REQUEST_CODE_EDIT = 3;

	private boolean isInterfaceHidden;

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

    private TableRow rowDate;
    private TableRow rowModel;
    private TableRow rowIso;
    private TableRow rowExposure;
    private TableRow rowAperture;
    private TableRow rowFocal;
    private TableRow rowDimensions;
    private TableRow rowAlt;
    private TableRow rowFlash;
    private TableRow rowLat;
    private TableRow rowLon;
    private TableRow rowName;
    private TableRow rowWhiteBalance;
    private TableRow rowLens;
    private TableRow rowDriveMode;
    private TableRow rowExposureMode;
    private TableRow rowExposureProgram;

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

		lookupViews();
        setMetaVisibility();

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayWidth = metrics.widthPixels;
		displayHeight = metrics.heightPixels;

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);

		attachButtons();

		setActionBar();
	}

    private void setMetaVisibility()
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Default true
        rowAperture.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifAperture, true) ? View.VISIBLE : View.GONE);
        rowDate.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifDate, true) ? View.VISIBLE : View.GONE);
        rowExposure.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifExposure, true) ? View.VISIBLE : View.GONE);
        rowFocal.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifFocal, true) ? View.VISIBLE : View.GONE);
        rowModel.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifModel, true) ? View.VISIBLE : View.GONE);
        rowIso.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifIso, true) ? View.VISIBLE : View.GONE);
        rowLens.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifLens, true) ? View.VISIBLE : View.GONE);
        rowName.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifName, true) ? View.VISIBLE : View.GONE);

        // Default false
        rowAlt.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifAltitude, false) ? View.VISIBLE : View.GONE);
        rowDimensions.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifDimensions, false) ? View.VISIBLE : View.GONE);
        rowDriveMode.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifDriveMode, false) ? View.VISIBLE : View.GONE);
        rowExposureMode.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifExposureMode, false) ? View.VISIBLE : View.GONE);
        rowExposureProgram.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifExposureProgram, false) ? View.VISIBLE : View.GONE);
        rowFlash.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifFlash, false) ? View.VISIBLE : View.GONE);
        rowLat.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifLatitude, false) ? View.VISIBLE : View.GONE);
        rowLon.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifLongitude, false) ? View.VISIBLE : View.GONE);
        rowWhiteBalance.setVisibility(settings.getBoolean(FullSettingsActivity.KEY_ExifWhiteBalance, false) ? View.VISIBLE : View.GONE);

    }

	private void lookupViews()
	{
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

        rowDate = (TableRow) findViewById(R.id.rowDate);
        rowModel = (TableRow) findViewById(R.id.rowModel);
        rowIso = (TableRow) findViewById(R.id.rowIso);
        rowExposure = (TableRow) findViewById(R.id.rowExposure);
        rowAperture = (TableRow) findViewById(R.id.rowAperture);
        rowFocal = (TableRow) findViewById(R.id.rowFocal);
        rowDimensions = (TableRow) findViewById(R.id.rowDimensions);
        rowAlt = (TableRow) findViewById(R.id.rowAltitude);
        rowFlash = (TableRow) findViewById(R.id.rowFlash);
        rowLat = (TableRow) findViewById(R.id.rowLatitude);
        rowLon = (TableRow) findViewById(R.id.rowLongitude);
        rowName = (TableRow) findViewById(R.id.rowName);
        rowWhiteBalance = (TableRow) findViewById(R.id.rowWhiteBalance);
        rowLens = (TableRow) findViewById(R.id.rowLens);
        rowDriveMode = (TableRow) findViewById(R.id.rowDriveMode);
        rowExposureMode = (TableRow) findViewById(R.id.rowExposureMode);
        rowExposureProgram = (TableRow) findViewById(R.id.rowExposureProgram);

        if (getResources().getBoolean(R.bool.hasTwoPanes))
        {
            xmpFrag = (XmpFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentSideBar);
            hideSidebar();
        }
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
		if (sideBarWrapper != null) // Portrait doesn't require the wrapper
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
		super.onDestroy();
	}

	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public void onPause()
	{
		super.onPause();
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
		MediaItem media = mModel.getCurrentItem();
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

                    MediaItem source = mModel.getCurrentItem();
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
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		imagePanels.post(new Runnable()
		{
			@TargetApi(11)
			public void run()
			{
				// imagePanels.setVisibility(View.VISIBLE);
				if (!settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic")
						.equals("Never"))
					navigationPanel.setVisibility(View.VISIBLE);
				if (!settings.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic")
						.equals("Never"))
					metaPanel.setVisibility(View.VISIBLE);
				if (!settings.getString(FullSettingsActivity.KEY_ShowHist, "Automatic")
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
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		imagePanels.post(new Runnable()
		{
			public void run()
			{

				if (!settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic")
						.equals("Always"))
					navigationPanel.setVisibility(View.INVISIBLE);
				if (!settings.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic")
						.equals("Always"))
					metaPanel.setVisibility(View.INVISIBLE);
				if (!settings.getString(FullSettingsActivity.KEY_ShowHist, "Automatic")
						.equals("Always"))
					histView.setVisibility(View.INVISIBLE);
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

		MediaItem meta = mModel.getCurrentItem();

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
        MediaItem media = mModel.getCurrentItem();

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
	}

	private void updateImageSource()
	{
		updateViewerItems();
		notifyContentChanged();
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
				xmpFrag = XmpFragment.newInstance((MetaMedia) mModel.getCurrentItem());
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
            mPhotoView.goToPrevPicture();
		}
	}

	class NextImageClickListener implements View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
            mPhotoView.goToNextPicture();
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
        setMetaVisibility();

		if (key == FullSettingsActivity.KEY_ShowNativeFiles)
		{
			updateViewerItems();

			MediaObject media = mModel.getCurrentItem();

			// If current images are native and viewing is turned off finish activity
			if (media == null || !sharedPreferences.getBoolean(key, true) && isNative(new File(media.getFilePath())))
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
	public void onSingleTapConfirmed()
	{
		togglePanels();
	}

	@Override
	/**
	 * This seems to occur when the first image is loaded.
	 */
	public void onCurrentImageUpdated()
	{
        super.onCurrentImageUpdated();
		updateImageDetails();
	}

	@Override
	/**
	 * This occurs whenever the current image changes.
	 */
	public void onPhotoChanged(int index, Uri path)
	{
        super.onPhotoChanged(index, path);
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

    private WeakHashMap<ContentListener, Object> mListeners =
            new WeakHashMap<ContentListener, Object>();

    // NOTE: The MediaSet only keeps a weak reference to the listener. The
    // listener is automatically removed when there is no other reference to
    // the listener.
    public void addContentListener(ContentListener listener) {
        mListeners.put(listener, null);
    }

    public void removeContentListener(ContentListener listener) {
        mListeners.remove(listener);
    }

    // This should be called by subclasses when the content is changed.
    public void notifyContentChanged() {
        for (ContentListener listener : mListeners.keySet()) {
            listener.onContentDirty();
        }
    }
}