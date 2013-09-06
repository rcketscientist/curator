package com.anthonymandra.framework;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.RawDroid;
import com.anthonymandra.rawdroid.ViewerChooser;
import com.anthonymandra.rawdroid.XmpFragment;
import com.anthonymandra.widget.HistogramView;

import org.openintents.intents.FileManagerIntents;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by amand_000 on 8/27/13.
 */
public abstract class ViewerActivity extends GalleryActivity implements
        ShareActionProvider.OnShareTargetSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = ViewerActivity.class.getSimpleName();

    protected static final int REQUEST_CODE_PICK_FILE_OR_DIRECTORY = 1;
    protected static final int REQUEST_CODE_KEYWORDS = 2;
    protected static final int REQUEST_CODE_EDIT = 3;

    protected HistogramView histView;
    protected View metaFragment;
    protected View navFragment;
    protected View viewerLayout;
    protected View metaPanel;
    protected ImageButton buttonRotate;
    protected ImageButton buttonPrev;
    protected ImageButton buttonNext;
    protected View navigationPanel;
    protected TextView zoomLevel;
    protected TextView metaDate;
    protected TextView metaModel;
    protected TextView metaIso;
    protected TextView metaExposure;
    protected TextView metaAperture;
    protected TextView metaFocal;
    protected TextView metaDimensions;
    protected TextView metaAlt;
    protected TextView metaFlash;
    protected TextView metaLat;
    protected TextView metaLon;
    protected TextView metaName;
    protected TextView metaWb;
    protected TextView metaLens;
    protected TextView metaDriveMode;
    protected TextView metaExposureMode;
    protected TextView metaExposureProgram;

    protected TableRow rowDate;
    protected TableRow rowModel;
    protected TableRow rowIso;
    protected TableRow rowExposure;
    protected TableRow rowAperture;
    protected TableRow rowFocal;
    protected TableRow rowDimensions;
    protected TableRow rowAlt;
    protected TableRow rowFlash;
    protected TableRow rowLat;
    protected TableRow rowLon;
    protected TableRow rowName;
    protected TableRow rowWhiteBalance;
    protected TableRow rowLens;
    protected TableRow rowDriveMode;
    protected TableRow rowExposureMode;
    protected TableRow rowExposureProgram;

    protected Timer autoHide;

    protected XmpFragment xmpFrag;

    protected boolean isInterfaceHidden;

    protected ShareActionProvider mShareProvider;

    public static int displayWidth;
    public static int displayHeight;

    protected int mImageIndex;

    public abstract MediaItem getCurrentItem();
    public abstract Bitmap getCurrentBitmap();
    public abstract void goToPrevPicture();
    public abstract void goToNextPicture();
    public abstract void goToFirstPicture();

//    protected HistogramTask mHistogramTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        super.onCreate(savedInstanceState);
        mImageIndex = setPathFromIntent();
    }

    protected void initialize()
    {
        lookupViews();
        setMetaVisibility();
        setDisplayMetrics();
        attachButtons();
        setActionBar();
    }

    protected void setDisplayMetrics()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        displayWidth = metrics.widthPixels;
        displayHeight = metrics.heightPixels;
    }

    protected int setPathFromIntent()
    {
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

        if (isNative(input))
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);
            editor.commit();
        }

        int indexHint = 0;
        if (input.isDirectory())
        {
            setPath(input);
        }
        else
        {
            File parent = input.getParentFile();
            if (parent.exists())
                setPath(parent);
            else
                setSingleImage(input);

            indexHint = findMediaByFilename(input.getPath());
            if (indexHint == FILE_NOT_FOUND)
            {
                indexHint = 0;
                if (!parent.exists() || parent.listFiles().length > 0)
                {
                    Toast.makeText(this, "Path could not be found, please email me if this continues", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
        updateViewerItems();

        return indexHint;
    }

    @Override
    public void onBackPressed() {
        setImageFocus();
        super.onBackPressed();
    }

    protected void setMetaVisibility()
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Default true
        rowAperture.setVisibility(settings.getBoolean(
                FullSettingsActivity.KEY_ExifAperture, true) ? View.VISIBLE : View.GONE);
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

    protected void lookupViews()
    {
        metaFragment = findViewById(R.id.metaPanel);
        navFragment = findViewById(R.id.navPanel);
        
        viewerLayout = findViewById(R.id.frameLayoutViewer);

        histView = (HistogramView) findViewById(R.id.histogramView1);
        metaPanel = findViewById(R.id.tableLayoutMeta);
        navigationPanel = findViewById(R.id.leftNavigation);

        // Nav fragment
        buttonRotate = (ImageButton) findViewById(R.id.imageButtonRotate);
        zoomLevel = (TextView) findViewById(R.id.textViewScale);
        buttonPrev = (ImageButton) findViewById(R.id.imageButtonLeftBack);
        buttonNext = (ImageButton) findViewById(R.id.imageButtonLeftFwd);

        // Initially set the interface to GONE to allow settings to implement
        metaPanel.setVisibility(View.GONE);
        navigationPanel.setVisibility(View.GONE);
        histView.setVisibility(View.GONE);

        // Meta fragment
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

    protected void setActionBar()
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
            ((ViewGroup.MarginLayoutParams) sideBarWrapper.getLayoutParams()).setMargins(0, actionBarHeight, 0, 0);
            sideBarWrapper.requestLayout();
        }

        ((ViewGroup.MarginLayoutParams) metaFragment.getLayoutParams()).setMargins(0, actionBarHeight, 0, 0);
        ((ViewGroup.MarginLayoutParams) navFragment.getLayoutParams()).setMargins(0, actionBarHeight, 0, 0);
        metaFragment.requestLayout();
        navFragment.requestLayout();

        // Hide title text and set home as up
        actionBar.setDisplayShowTitleEnabled(false);
        // actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.translucent_black_rect));
    }

    protected void attachButtons()
    {
        buttonPrev.setOnClickListener(new PreviousImageClickListener());
        buttonNext.setOnClickListener(new NextImageClickListener());
        // Rotate
        buttonRotate.setOnClickListener(new View.OnClickListener()
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

    class PreviousImageClickListener implements View.OnClickListener
    {

        @Override
        public void onClick(View v)
        {
            goToPrevPicture();
        }
    }

    class NextImageClickListener implements View.OnClickListener
    {
        @Override
        public void onClick(View v)
        {
            goToNextPicture();
        }
    }

    private void showPanels()
    {
        isInterfaceHidden = false;
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        viewerLayout.post(new Runnable()
        {
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
//                buttonRotate.setVisibility(View.VISIBLE);
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
        viewerLayout.post(new Runnable()
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
//                buttonRotate.setVisibility(View.GONE);
//                metaPanel.setVisibility(View.GONE);
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

    protected void updateImageDetails()
    {
        new LoadMetadataTask().execute();
        updateHistogram(getCurrentBitmap());
    }

    protected void updateHistogram(Bitmap bitmap)
    {
        new HistogramTask().execute(bitmap);
//        if (mHistogramTask != null)
//            mHistogramTask.cancel(true);
//        mHistogramTask = new HistogramTask();
//        mHistogramTask.execute(bitmap);
    }

    protected void populateExif()
    {
        if (autoHide != null)
            autoHide.cancel();

        MediaItem meta = getCurrentItem();

        if (metaDate == null || meta == null)
        {
            Toast.makeText(this,
                    "Could not access metadata views, please email rawdroid@anthonymandra.com!",
                    Toast.LENGTH_LONG).show();
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
    public class LoadMetadataTask extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... params)
        {
            MediaItem current = getCurrentItem();
            if (current != null)
                current.readMetadata();
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            populateExif();

            if (PreferenceManager.getDefaultSharedPreferences(ViewerActivity.this).getBoolean(FullSettingsActivity.KEY_ShowImageInterface,
                    RawDroid.PREFS_AUTO_INTERFACE_DEFAULT))
            {
                showPanels();
            }

            if (xmpFrag != null && xmpFrag.isAdded())
                xmpFrag.setMediaObject(getCurrentItem());
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

            int[] pixels = new int[input.getWidth() * input.getHeight()];
            input.getPixels(pixels, 0, input.getWidth(), 0, 0, input.getWidth(), input.getHeight());

//            int stride = pixels.length / 4095;

            for (int pixel : pixels)
//            for (int pixel = 0; pixel < pixels.length; pixel += stride)
            {
                if (isCancelled())
                    return null;
                result.processPixel(pixel);
            }

            return result;
        }

        @Override
        protected void onPostExecute(Histogram result)
        {
            if (result != null && !isCancelled())
                histView.updateHistogram(result);
        }
    }

    protected void saveImage()
    {
        Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String startPath = settings.getString(RawDroid.PREFS_MOST_RECENT_SAVE, null);
        MediaItem media = getCurrentItem();

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
            Toast.makeText(ViewerActivity.this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateImageDetails();   // For small screens this will fix the meta panel shape
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
        share(intent, getCurrentItem());
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
                deleteImage(getCurrentItem());
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
        Intent settings = new Intent(ViewerActivity.this, FullSettingsActivity.class);
        startActivity(settings);
    }

    private void editImage()
    {
        MediaItem media = getCurrentItem();
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

                    MediaItem source = getCurrentItem();
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

    private void setWallpaper()
    {
        try
        {
            byte[] imageData = getCurrentItem().getThumb();
            WallpaperManager.getInstance(this).setBitmap(Util.createBitmapToSize(imageData, displayWidth, displayHeight));
        }
        catch (Exception e)
        {
            Log.e(TAG, e.toString());
            Toast.makeText(ViewerActivity.this, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show();
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
                xmpFrag = XmpFragment.newInstance((MetaMedia) getCurrentItem());
                transaction.add(R.id.frameLayoutViewer, xmpFrag);
                transaction.addToBackStack(null);
                transaction.commit();
            }
        }
    }

    protected void updateViewerItems()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showNative = prefs.getBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);

        mVisibleItems.clear();
        mVisibleItems.addAll(mRawImages);
        if (showNative)
            mVisibleItems.addAll(mNativeImages);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        setMetaVisibility();

        if (key.equals(FullSettingsActivity.KEY_ShowNativeFiles))
        {
            updateViewerItems();

            MediaItem media = getCurrentItem();

            // If current images are native and viewing is turned off finish activity
            if (media == null || !sharedPreferences.getBoolean(key, true) && isNative(new File(media.getFilePath())))
            {
                if (mVisibleItems.size() > 0)
                {
                    goToFirstPicture();
                }
                else
                {
                    Toast.makeText(this, "All images were native, returning to gallery.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
        else if (key.equals(FullSettingsActivity.KEY_UseLegacyViewer))
        {
            Intent viewer = new Intent(this, ViewerChooser.class);
            viewer.setData(getCurrentItem().getUri());
            finish();
            startActivity(viewer);
        }
    }

    protected void setImageFocus()
    {
        Intent data = new Intent();
        data.setData(getCurrentItem().getUri());
        setResult(RESULT_OK, data);
    }

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
}
