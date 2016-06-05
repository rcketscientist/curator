package com.anthonymandra.framework;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ActionProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.app.DataListener;
import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.Constants;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.GalleryActivity;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.util.ImageUtils;
import com.anthonymandra.widget.HistogramView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public abstract class ViewerActivity extends CoreActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener, ActionProvider.SubUiVisibilityListener,
        ScaleChangedListener, DataListener
{

    private static final String TAG = ViewerActivity.class.getSimpleName();

    private static final int REQUEST_CODE_EDIT = 3;

    public static String EXTRA_START_INDEX = "viewer_index";
    private static String EXTRA_URIS = "viewer_uris";

    private HistogramView histView;
    private View metaPanel;
    private ImageButton buttonPrev;
    private ImageButton buttonNext;
    private View navigationPanel;
    private TextView zoomLevel;
    private CheckBox zoomButton;
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

    private boolean isInterfaceHidden;

    public static int displayWidth;
    public static int displayHeight;

    protected int mImageIndex;

    private Uri mCurrentUri;

    public abstract Uri getCurrentItem();
    public abstract Bitmap getCurrentBitmap();
    public abstract void goToPrevPicture();
    public abstract void goToNextPicture();

    protected List<Uri> mMediaItems = new ArrayList<>();

    /**
     * Since initial image configuration can occur BEFORE image generation
     * this flag allows us to specifically update a null histogram.  Without
     * flag, histogram could be regenerated for each layer (thumb, big, full raw, etc)
     */
    protected boolean mRequiresHistogramUpdate;
    private HistogramTask mHistogramTask;

    private IntentFilter mResponseIntentFilter = new IntentFilter();

    @Override
    protected LicenseHandler getLicenseHandler()
    {
        return new ViewerLicenseHandler(this);
    }

    @Override
    protected List<Uri> getSelectedImages()
    {
        List<Uri> currentImage = new ArrayList<>();
        currentImage.add(mCurrentUri);
        return currentImage;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toolbar toolbar = (Toolbar) findViewById(R.id.viewerToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        initialize();

        if (getIntent().hasExtra(EXTRA_URIS))
        {
            mImageIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

            String[] uris = getIntent().getStringArrayExtra(EXTRA_URIS);
            for (String uri : uris)
            {
                mMediaItems.add(Uri.parse(uri));
            }
        }
        else if (getIntent().hasExtra(EXTRA_META_BUNDLE))
        {
            mImageIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

            Bundle dbQuery = getIntent().getBundleExtra(EXTRA_META_BUNDLE);
            try(Cursor c = getContentResolver().query(
                Meta.Data.CONTENT_URI,
                dbQuery.getStringArray(META_PROJECTION_KEY),
                dbQuery.getString(META_SELECTION_KEY),
                dbQuery.getStringArray(META_SELECTION_ARGS_KEY),
                dbQuery.getString(META_SORT_ORDER_KEY))) {
                if (c == null || c.getCount() < 1)
                    return;
                else
                {
                    while (c.moveToNext()) {
                        String uri = c.getString(Meta.URI_COLUMN);
                        if (uri == null)
                            continue;
                        mMediaItems.add(Uri.parse(uri));
                    }
                }
            }
        }
        else  // External intent
        {
            mImageIndex = 0;
            mMediaItems.add(getIntent().getData());
        }

        mResponseIntentFilter.addAction(MetaService.BROADCAST_REQUESTED_META);
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                switch(intent.getAction())
                {
                    case MetaService.BROADCAST_REQUESTED_META:
                        Uri uri = Uri.parse(intent.getStringExtra(MetaService.EXTRA_URI));
                        if (uri.equals(getCurrentItem()))
                        {
                            ContentValues values = intent.getParcelableExtra(MetaService.EXTRA_METADATA);
                            populateMeta(values);
                        }
                        break;
                }
            }
        }, mResponseIntentFilter);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus)
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            boolean useImmersive = settings.getBoolean(FullSettingsActivity.KEY_UseImmersive, true);
            if (Util.hasKitkat() && useImmersive)
            {
                setImmersive();
            }
            else
            {
                this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Constants.VariantCode < 12)
        {
            setWatermark(true);
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
//        writeXmpModifications();    // If the lifecycle changes commit pending changes
    }

    @Override
    public void onPhotoChanged(int index, Uri item)
    {
        mCurrentUri = item;

        setShareUri(SwapProvider.createSwapUri(item));
        updateImageDetails();
    }

    @TargetApi(19)
    private void setImmersive()
    {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void initialize()
    {
        lookupViews();
        setMetaVisibility();
        setDisplayMetrics();
        attachButtons();
//        setActionBar();
    }

    @Override
    protected void onImageAdded(Uri item)
    {
        mMediaItems.add(item);
    }

    @Override
    protected void onImageRemoved(Uri item)
    {
        mMediaItems.remove(item);
    }

    private void setWatermark(boolean demo)
    {
        View watermark = findViewById(R.id.watermark);
        if (!demo)
            watermark.setVisibility(View.INVISIBLE);
        else
            watermark.setVisibility(View.VISIBLE);
    }

    private void setDisplayMetrics()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        displayWidth = metrics.widthPixels;
        displayHeight = metrics.heightPixels;
    }

    @Override
    public void onBackPressed() {
    	setImageFocus();
    	super.onBackPressed();
    }

    private void setMetaVisibility()
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
        histView = (HistogramView) findViewById(R.id.histogramView1);
        metaPanel = findViewById(R.id.tableLayoutMeta);
        navigationPanel = findViewById(R.id.leftNavigation);

        // Nav fragment
        zoomLevel = (TextView) findViewById(R.id.textViewScale);
        zoomButton = (CheckBox) findViewById(R.id.zoomButton);
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
    }

    protected void onZoomLockChanged(boolean locked) {
        // no base implementation
    }

    private void attachButtons()
    {
        buttonPrev.setOnClickListener(new PreviousImageClickListener());
        buttonNext.setOnClickListener(new NextImageClickListener());
        zoomButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                onZoomLockChanged(isChecked);
            }
        });
        findViewById(R.id.xmpSidebarButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleEditXmpFragment();
            }
        });
    }

    @Override
    public void onSubUiVisibilityChanged(boolean isVisible) {
        if (isVisible && autoHide != null)
            autoHide.cancel();
    }

    private class PreviousImageClickListener implements View.OnClickListener
    {

        @Override
        public void onClick(View v)
        {
            goToPrevPicture();
        }
    }

    private class NextImageClickListener implements View.OnClickListener
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
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (!settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic")
                        .equals("Never"))
                {
                    navigationPanel.setVisibility(View.VISIBLE);
                }
                if (!settings.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic")
                        .equals("Never"))
                {
                    metaPanel.setVisibility(View.VISIBLE);
                }
                if (!settings.getString(FullSettingsActivity.KEY_ShowHist, "Automatic")
                        .equals("Never"))
                {
                    histView.setVisibility(View.VISIBLE);
                }
                if (!settings.getString(FullSettingsActivity.KEY_ShowToolbar, "Always")
                        .equals("Never"))
                {
                    if (getSupportActionBar() != null)
                        getSupportActionBar().show();
                }
            }
        });
    }

    private void hidePanels()
    {
        isInterfaceHidden = true;
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (!settings.getString(FullSettingsActivity.KEY_ShowNav, "Automatic")
                        .equals("Always"))
                {
                    navigationPanel.setVisibility(View.INVISIBLE);
                }
                if (!settings.getString(FullSettingsActivity.KEY_ShowMeta, "Automatic")
                        .equals("Always"))
                {
                    metaPanel.setVisibility(View.INVISIBLE);
                }
                if (!settings.getString(FullSettingsActivity.KEY_ShowHist, "Automatic")
                        .equals("Always"))
                {
                    histView.setVisibility(View.INVISIBLE);
                }
                if (!settings.getString(FullSettingsActivity.KEY_ShowToolbar, "Always")
                        .equals("Always"))
                {
                    if (getSupportActionBar() != null)
                        getSupportActionBar().hide();
                }
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

    protected void updateMetaData()
    {
        Uri image = getCurrentItem();
        if (image == null)
            return;

        clearMeta();    // clear panel during load to avoid confusion

        // Start a meta check/process on a high priority.
        MetaWakefulReceiver.startPriorityMetaService(ViewerActivity.this, image);
    }

    private void updateImageDetails()
    {
        updateMetaData();
        updateHistogram(getCurrentBitmap());
        if (PreferenceManager.getDefaultSharedPreferences(ViewerActivity.this).getBoolean(FullSettingsActivity.KEY_ShowImageInterface,
                GalleryActivity.PREFS_AUTO_INTERFACE_DEFAULT))
        {
            showPanels();
        }
    }

    protected void updateHistogram(Bitmap bitmap)
    {
        if (bitmap == null) {
            mRequiresHistogramUpdate = true;
            return;
        }
        mRequiresHistogramUpdate = false;
        if (mHistogramTask != null)
            mHistogramTask.cancel(true);
        mHistogramTask = new HistogramTask();
        mHistogramTask.execute(bitmap);
    }

    @SuppressLint("SetTextI18n")
    private void populateMeta(final ContentValues values)
    {
        if (values == null)
            return;

        if (autoHide != null)
            autoHide.cancel();

        if (metaDate == null)
        {
            Toast.makeText(this,
                    "Could not access metadata views, please email me!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Long timestamp = values.getAsLong(Meta.Data.TIMESTAMP);
        if (timestamp != null)
        {
            Date d = new Date(timestamp);
            java.text.DateFormat df = DateFormat.getDateFormat(this);
            java.text.DateFormat tf = DateFormat.getTimeFormat(this);
            metaDate.setText(df.format(d) + " " + tf.format(d));
        }
        metaModel.setText(values.getAsString(Meta.Data.MODEL));
        metaIso.setText(values.getAsString(Meta.Data.ISO));
        metaExposure.setText(values.getAsString(Meta.Data.EXPOSURE));
        metaAperture.setText(values.getAsString(Meta.Data.APERTURE));
        metaFocal.setText(values.getAsString(Meta.Data.FOCAL_LENGTH));
        metaDimensions.setText(values.getAsString(Meta.Data.WIDTH) + " x " + values.getAsString(Meta.Data.HEIGHT));
        metaAlt.setText(values.getAsString(Meta.Data.ALTITUDE));
        metaFlash.setText(values.getAsString(Meta.Data.FLASH));
        metaLat.setText(values.getAsString(Meta.Data.LATITUDE));
        metaLon.setText(values.getAsString(Meta.Data.LONGITUDE));
        metaName.setText(values.getAsString(Meta.Data.NAME));
        metaWb.setText(values.getAsString(Meta.Data.WHITE_BALANCE));
        metaLens.setText(values.getAsString(Meta.Data.LENS_MODEL));
        metaDriveMode.setText(values.getAsString(Meta.Data.DRIVE_MODE));
        metaExposureMode.setText(values.getAsString(Meta.Data.EXPOSURE_MODE));
        metaExposureProgram.setText(values.getAsString(Meta.Data.EXPOSURE_PROGRAM));

        String rating = values.getAsString(Meta.Data.RATING);  //Use string since double returns 0 for null
        mXmpFragment.initXmp(
                rating == null ? null : (int) Double.parseDouble(rating),
                ImageUtils.convertStringToArray(values.getAsString(Meta.Data.SUBJECT)),
                values.getAsString(Meta.Data.LABEL));

        autoHide = new Timer();
        autoHide.schedule(new AutoHideMetaTask(), 3000);
    }

    private void clearMeta()
    {
        metaDate.setText("");
        metaModel.setText("");
        metaIso.setText("");
        metaExposure.setText("");
        metaAperture.setText("");
        metaFocal.setText("");
        metaDimensions.setText("");
        metaAlt.setText("");
        metaFlash.setText("");
        metaLat.setText("");
        metaLon.setText("");
        metaName.setText("");
        metaWb.setText("");
        metaLens.setText("");
        metaDriveMode.setText("");
        metaExposureMode.setText("");
        metaExposureProgram.setText("");
    }

    private class AutoHideMetaTask extends TimerTask
    {
        @Override
        public void run()
        {
            hidePanels();
        }
    }

    protected class HistogramTask extends AsyncTask<Bitmap, Void, Histogram>
    {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            histView.clear();
        }

        // http://zerocool.is-a-geek.net/?p=269
        @Override
        protected Histogram doInBackground(Bitmap... params)
        {
            Bitmap input = params[0];
            Histogram result = new Histogram();

            if (input == null || input.isRecycled())
                return null;

            result.processBitmap(input);
            return result;

//            int[] pixels = new int[input.getWidth() * input.getHeight()];
//            try
//            {
//                //TODO: Forkjoin row by row? http://stackoverflow.com/questions/17740059/how-to-generate-image-histogram-in-android
//                input.getPixels(pixels, 0, input.getWidth(), 0, 0, input.getWidth(), input.getHeight());
//                for (int pixel : pixels)
////              for (int pixel = 0; pixel < pixels.length; pixel += stride)
//                {
//                    if (isCancelled())
//                        return null;
//                    result.processPixel(pixel);
//                }
//            }
//            catch(IllegalStateException e)
//            {
//                Toast.makeText(ViewerActivity.this,
//                        "Memory Error: Histogram failed due to recycled image.  Try swapping slower or using Legacy Viewer.",
//                        Toast.LENGTH_SHORT).show();
//                e.printStackTrace();
//                return null;
//            }
////            int stride = pixels.length / 4095;
//
//            return result;
        }

        @Override
        protected void onPostExecute(Histogram result)
        {
            if (result != null && !isCancelled())
                histView.updateHistogram(result);
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
        getMenuInflater().inflate(R.menu.viewer_options, menu);
        MenuItem actionItem = menu.findItem(R.id.viewShare);
        if (actionItem != null)
        {
            mShareProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(actionItem);
            mShareProvider.setShareIntent(mShareIntent);
            mShareProvider.setSubUiVisibilityListener(this);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (autoHide != null)
            autoHide.cancel();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId())
        {
            case R.id.view_edit:
                editImage();
                return true;
            case R.id.view_wallpaper:
                setWallpaper();
                return true;
            case R.id.view_delete:
                deleteImage(getCurrentItem());
                return true;
            case R.id.view_recycle:
                showRecycleBin();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void editImage()
    {
        Uri media = getCurrentItem();

        Intent action = new Intent(Intent.ACTION_EDIT);
        action.setDataAndType(media, "");
        action.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        action.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Convert if no editor for raw exists
        if (!intentExists(action))
        {
            action.setDataAndType(SwapProvider.createSwapUri(media), "image/jpeg");
        }

        // Otherwise convert
        Intent chooser = Intent.createChooser(action, getResources().getString(R.string.edit));
        startActivityForResult(chooser, REQUEST_CODE_EDIT);
    }

    private boolean intentExists(Intent intent)
    {
        return intent.resolveActivity(getPackageManager()) != null;
    }

    @Override
    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case REQUEST_CODE_EDIT:
                // This doesn't seem to return a result.
//                if (resultCode == RESULT_OK)
//                {
//                    Log.d(TAG, data.getDataString());
//                }
        }
    }

    private void setWallpaper()
    {
		try
		{
		    WallpaperManager.getInstance(this).setBitmap(ImageUtils.createBitmapToSize(
                    ImageUtils.getThumb(this, getCurrentItem()), displayWidth, displayHeight));
		}
		catch (Exception e)
		{
		    Log.e(TAG, e.toString());
		    Toast.makeText(ViewerActivity.this, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show();
		}
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        setMetaVisibility();
        Uri media = getCurrentItem();

        if (key.equals(FullSettingsActivity.KEY_UseLegacyViewer))
        {
            Intent viewer = getViewerIntent();
            viewer.setData(media);
            //TODO: finish() before startActivity???
            finish();
            startActivity(viewer);
        }
    }

    private void setImageFocus()
    {
        Intent data = new Intent();
        data.putExtra(GalleryActivity.GALLERY_INDEX_EXTRA, mImageIndex);
        setResult(RESULT_OK, data);
    }

    @Override
    public void onScaleChanged(float currentScale)
    {
        if (zoomLevel == null) return;

        final String zoom = String.valueOf((int) (currentScale * 100) + "%");
        zoomLevel.post(new Runnable()
        {
            @Override
            public void run()
            {
                zoomLevel.setText(zoom);
            }
        });
    }

    static class ViewerLicenseHandler extends LicenseHandler
    {
        private final WeakReference<ViewerActivity> mViewer;

        public ViewerLicenseHandler(ViewerActivity viewer)
        {
            super(viewer);
            this.mViewer = new WeakReference<>(viewer);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            License.LicenseState state = (License.LicenseState) msg.getData().getSerializable(License.KEY_LICENSE_RESPONSE);
            mViewer.get().setLicenseState(state);
        }
    }

    protected void setLicenseState(License.LicenseState state)
    {
        boolean isPro = state == License.LicenseState.pro;
        setWatermark(!isPro);
        zoomButton.setEnabled(isPro);
    }
}
