package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ActionProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
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
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.content.Meta;
import com.anthonymandra.dcraw.LibRaw.Margins;
import com.anthonymandra.rawdroid.Constants;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.LicenseManager;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.rawdroid.GalleryActivity;
import com.anthonymandra.rawdroid.XmpEditFragment;
import com.anthonymandra.widget.HistogramView;

import org.openintents.filemanager.FileManagerActivity;
import org.openintents.intents.FileManagerIntents;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public abstract class ViewerActivity extends CoreActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener, ActionProvider.SubUiVisibilityListener,
        ScaleChangedListener, DataListener
{

    private static final String TAG = ViewerActivity.class.getSimpleName();

    protected static final int REQUEST_CODE_PICK_FILE_OR_DIRECTORY = 1;
    protected static final int REQUEST_CODE_KEYWORDS = 2;
    protected static final int REQUEST_CODE_EDIT = 3;

    public static String EXTRA_START_INDEX = "viewer_index";
    public static String EXTRA_START_URI = "viewer_start_uri";
    public static String EXTRA_URIS = "viewer_uris";

    protected HistogramView histView;
    protected View metaFragment;
    protected View navFragment;
    protected View viewerLayout;
    protected View metaPanel;
    protected ImageButton buttonPrev;
    protected ImageButton buttonNext;
    protected View navigationPanel;
    protected TextView zoomLevel;
    protected CheckBox zoomButton;
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

    protected XmpEditFragment mXmpFragment;

    protected Timer autoHide;

    protected boolean isInterfaceHidden;

    public static int displayWidth;
    public static int displayHeight;

    protected int mImageIndex;

    protected XmpEditFragment.XmpEditValues mPendingXmpChanges;
    protected Uri mCurrentUri;

    public abstract MediaItem getCurrentItem();
    public abstract Bitmap getCurrentBitmap();
    public abstract void goToPrevPicture();
    public abstract void goToNextPicture();
    public abstract void goToFirstPicture();

    protected List<Uri> mMediaItems = new ArrayList<>();

    /**
     * Since initial image configuration can occur BEFORE image generation
     * this flag allows us to specifically update a null histogram.  Without
     * flag, histogram could be regenerated for each layer (thumb, big, full raw, etc)
     */
    protected boolean mRequiresHistogramUpdate;
    protected HistogramTask mHistogramTask;

    @Override
    protected LicenseHandler getLicenseHandler()
    {
        return new ViewerLicenseHandler(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentView());
        Toolbar toolbar = (Toolbar) findViewById(R.id.viewerToolbar);
        setSupportActionBar(toolbar);
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
            Cursor c = getContentResolver().query(
                Meta.Data.CONTENT_URI,
                dbQuery.getStringArray(META_PROJECTION_KEY),
                dbQuery.getString(META_SELECTION_KEY),
                dbQuery.getStringArray(META_SELECTION_ARGS_KEY),
                dbQuery.getString(META_SORT_ORDER_KEY));
            if (c.getCount() < 1)
            {
                c.close();
                //TODO: Error message.
                return;
            }
            else
            {
                while(c.moveToNext())
                {
                    mMediaItems.add(Uri.parse(c.getString(Meta.URI_COLUMN)));
                }
            }
            c.close();
        }
        else  // External intent
        {
            mImageIndex = 0;

            String path;
            Uri data = getIntent().getData();
            if (data.getAuthority().equals(MediaStore.AUTHORITY))
            {
                //Attempt to acquire the file
                path = Util.getRealPathFromURI(this, data);
            }
            else
            {
                path = data.getPath();
            }

            File file = new File(path);
            Uri uri = Uri.fromFile(new File(path));
//            LocalImage image = new LocalImage(this, file);
            mMediaItems.add(uri);
//            Uri entry = addDatabaseReference(image);
//            getIntent().setData(uri);   // reset the data with a file uri
        }
    }


    /**
     * Subclasses must define the layout id here.  It will be loaded in {@link #onCreate}.
     * The layout should conform to viewer template (xmp, meta, historgram, etc).
     * @return The resource id of the layout to load
     */
    public abstract int getContentView();

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
        writeXmpModifications();    // If the lifecycle changes commit pending changes
    }

    @Override
    public void onPhotoChanged(int index, Uri item)
    {
        if (mCurrentUri != null && mPendingXmpChanges != null)
        {
            writeXmpModifications();
        }
        mCurrentUri = item;

        setShareUri(getCurrentItem().getSwapUri());
        updateImageDetails();
    }

    protected void writeXmpModifications()
    {
        if (mCurrentUri != null && mPendingXmpChanges != null)
        {
            ContentValues cv = new ContentValues();
            cv.put(Meta.Data.LABEL, mPendingXmpChanges.Label);
            cv.put(Meta.Data.RATING, mPendingXmpChanges.Rating);
            cv.put(Meta.Data.SUBJECT, ImageUtils.convertArrayToString(mPendingXmpChanges.Subject));

            getContentResolver().update(
                    Meta.Data.CONTENT_URI,
                    cv, Meta.Data.URI + " = ?",
                    new String[]{mCurrentUri.toString()});

            writeXmp(getCurrentItem().getUri(), mPendingXmpChanges);
            mPendingXmpChanges = null;
        }
    }

    @TargetApi(19)
    protected void setImmersive()
    {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    protected void initialize()
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

    protected void setWatermark(boolean demo)
    {
        View watermark = findViewById(R.id.watermark);
        if (!demo)
            watermark.setVisibility(View.INVISIBLE);
        else
            watermark.setVisibility(View.VISIBLE);
    }

    protected void setDisplayMetrics()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        displayWidth = metrics.widthPixels;
        displayHeight = metrics.heightPixels;
    }

    @Override
    public void onBackPressed() {
        if (!mXmpFragment.isHidden())
        {
            toggleEditXmpFragment();
            return;
        }

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
        
        viewerLayout = findViewById(R.id.viewerLayout);

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
        mXmpFragment = (XmpEditFragment) getSupportFragmentManager().findFragmentById(R.id.editFragment);
        toggleEditXmpFragment(); // Keep fragment visible in designer, but hide initially
    }

    protected void onZoomLockChanged(boolean locked) {
        // no base implementation
    }

    protected void attachButtons()
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
        mXmpFragment.setListener(new XmpEditFragment.MetaChangedListener()
        {
            @Override
            public void onMetaChanged(Integer rating, String label, String[] subject)
            {
                mPendingXmpChanges = new XmpEditFragment.XmpEditValues();
                mPendingXmpChanges.Label = label;
                mPendingXmpChanges.Subject = subject;
                mPendingXmpChanges.Rating = rating;
            }
        });
    }

    @Override
    public void onSubUiVisibilityChanged(boolean isVisible) {
        if (isVisible && autoHide != null)
            autoHide.cancel();
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
                    getSupportActionBar().show();
                }
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
        LoadMetadataTask task = new LoadMetadataTask();
        task.execute(getCurrentItem().getUri());
    }

    protected void updateImageDetails()
    {
        updateMetaData();
        updateHistogram(getCurrentBitmap());
    }

    protected Cursor getMetaCursor(Uri uri)
    {
        return getContentResolver().query(Meta.Data.CONTENT_URI,
                null,
                Meta.Data.URI + "=?",
                new String[]{uri.toString()},
                null);
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

    protected void populateMeta(ContentValues cursor)
    {
        if (autoHide != null)
            autoHide.cancel();

        if (metaDate == null)
        {
            Toast.makeText(this,
                    "Could not access metadata views, please email me!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Assuming cursor is pointing properly...
        metaDate.setText(cursor.getAsString(Meta.Data.TIMESTAMP));
        metaModel.setText(cursor.getAsString(Meta.Data.MODEL));
        metaIso.setText(cursor.getAsString(Meta.Data.ISO));
        metaExposure.setText(cursor.getAsString(Meta.Data.EXPOSURE));
        metaAperture.setText(cursor.getAsString(Meta.Data.APERTURE));
        metaFocal.setText(cursor.getAsString(Meta.Data.FOCAL_LENGTH));
        metaDimensions.setText(cursor.getAsString(Meta.Data.WIDTH) + " x " + cursor.getAsString(Meta.Data.HEIGHT));
        metaAlt.setText(cursor.getAsString(Meta.Data.ALTITUDE));
        metaFlash.setText(cursor.getAsString(Meta.Data.FLASH));
        metaLat.setText(cursor.getAsString(Meta.Data.LATITUDE));
        metaLon.setText(cursor.getAsString(Meta.Data.LONGITUDE));
        metaName.setText(cursor.getAsString(Meta.Data.NAME));
        metaWb.setText(cursor.getAsString(Meta.Data.WHITE_BALANCE));
        metaLens.setText(cursor.getAsString(Meta.Data.LENS_MODEL));
        metaDriveMode.setText(cursor.getAsString(Meta.Data.DRIVE_MODE));
        metaExposureMode.setText(cursor.getAsString(Meta.Data.EXPOSURE_MODE));
        metaExposureProgram.setText(cursor.getAsString(Meta.Data.EXPOSURE_PROGRAM));

        String rating = cursor.getAsString(Meta.Data.RATING);  //Use string since double returns 0 for null
        mXmpFragment.initXmp(
                rating == null ? null : (int) Double.parseDouble(rating),
                ImageUtils.convertStringToArray(cursor.getAsString(Meta.Data.SUBJECT)),
                cursor.getAsString(Meta.Data.LABEL));

        autoHide = new Timer();
        autoHide.schedule(new AutoHideMetaTask(), 3000);

        if (PreferenceManager.getDefaultSharedPreferences(ViewerActivity.this).getBoolean(FullSettingsActivity.KEY_ShowImageInterface,
                GalleryActivity.PREFS_AUTO_INTERFACE_DEFAULT))
        {
            showPanels();
        }

//        writeXmpModifications();    //TODO: This would be best handled as an image changed listener, rather than leveraging meta update
    }

    private void toggleEditXmpFragment()
    {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (mXmpFragment.isHidden())
        {
            ft.show(mXmpFragment);
            ft.setCustomAnimations(android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left);
        }
        else
        {
            ft.hide(mXmpFragment);
            ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        }
        ft.commit();
    }

    public class LoadMetadataTask extends AsyncTask<Uri, Void, ContentValues>
    {
        @Override
        protected ContentValues doInBackground(Uri... params)
        {
            final Uri uri = params[0];
            long initialTime = System.currentTimeMillis();

            // If the meta is processed populate it
            Cursor c = getMetaCursor(uri);
            c.moveToFirst();
            ContentValues values = new ContentValues();

            // Check if meta is already processed
            if (c.getInt(Meta.PROCESSED_COLUMN) != 0)
            {
                long preCursorConvert = System.currentTimeMillis();
                DatabaseUtils.cursorRowToContentValues(c, values);
                Log.d(TAG, "cursorRowToContentValues: " + (System.currentTimeMillis() - preCursorConvert));
            }
            else
            {
                long preContentValues = System.currentTimeMillis();
                final ContentValues cv = ImageUtils.getContentValues(ViewerActivity.this, uri);
                values = cv;
                Log.d(TAG, "getContentValues: " + (System.currentTimeMillis() - preContentValues));
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getContentResolver().update(Meta.Data.CONTENT_URI,
                                cv,
                                Meta.Data.URI + " = ?",
                                new String[]{uri.toString()});
                    }
                }).start();
            }
            c.close();
            Log.d(TAG, "LoadMetadataTask: " + (System.currentTimeMillis() - initialTime));

            return values;
        }

        @Override
        protected void onPostExecute(ContentValues result)
        {
            populateMeta(result);
        }
    }

    class AutoHideMetaTask extends TimerTask
    {
        @Override
        public void run()
        {
            hidePanels();
        }
    }

    public class HistogramTask extends AsyncTask<Bitmap, Void, Histogram>
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

            int[] pixels = new int[input.getWidth() * input.getHeight()];
            try
            {
                input.getPixels(pixels, 0, input.getWidth(), 0, 0, input.getWidth(), input.getHeight());
                for (int pixel : pixels)
//              for (int pixel = 0; pixel < pixels.length; pixel += stride)
                {
                    if (isCancelled())
                        return null;
                    result.processPixel(pixel);
                }
            }
            catch(IllegalStateException e)
            {
                Toast.makeText(ViewerActivity.this,
                        "Memory Error: Histogram failed due to recycled image.  Try swapping slower or using Legacy Viewer.",
                        Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return null;
            }
//            int stride = pixels.length / 4095;

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
        Intent intent = new Intent(this, FileManagerActivity.class);
        intent.setAction(FileManagerIntents.ACTION_PICK_FILE);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String startPath = settings.getString(GalleryActivity.PREFS_MOST_RECENT_SAVE, null);

//        String name = getCurrentItem().getString(Meta.NAME_COLUMN);
        String name = getCurrentItem().getName();

        if (startPath == null)
        {
            startPath = "";
        }

        File saveFile = new File(
                Util.swapExtention(startPath + File.separator + name, ".jpg"));

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
                deleteImage(getCurrentItem().getUri());
                return true;
            case R.id.view_recycle:
                showRecycleBin();
                return true;
//            case R.id.viewSettings:
//                startSettings();
//                return true;
//            case R.id.viewHelp:
//                Toast.makeText(this, R.string.prefTitleComingSoon, Toast.LENGTH_SHORT).show();
//                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void editImage()
    {
        MediaItem media = getCurrentItem();

        Intent action = new Intent(Intent.ACTION_EDIT);
        action.setDataAndType(media.getUri(), "");

        // Convert if no editor for raw exists
        if (!intentExists(action))
        {
            action.setDataAndType(media.getSwapUri(), "image/jpeg");
            action.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            action.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
            case REQUEST_CODE_PICK_FILE_OR_DIRECTORY:
                if (resultCode == RESULT_OK && data != null)
                {
                    File dest = new File(data.getData().getPath());
                    handleSaveImage(dest);
                }
                break;
            case REQUEST_CODE_EDIT:
                // This doesn't seem to return a result.
//                if (resultCode == RESULT_OK)
//                {
//                    Log.d(TAG, data.getDataString());
//                }
        }
    }

    private void handleSaveImage(File dest)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(GalleryActivity.PREFS_MOST_RECENT_SAVE, dest.getParent());
        editor.apply();

        boolean showWatermark = settings.getBoolean(FullSettingsActivity.KEY_EnableWatermark, false);
        String watermarkText = settings.getString(FullSettingsActivity.KEY_WatermarkText, "");
        int watermarkAlpha = settings.getInt(FullSettingsActivity.KEY_WatermarkAlpha, 75);
        int watermarkSize = settings.getInt(FullSettingsActivity.KEY_WatermarkSize, 150);
        String watermarkLocation = settings.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");
        Margins margins = new Margins(settings);

        MediaItem source = getCurrentItem();

        Bitmap watermark;
        byte[] waterData = null;
        boolean processWatermark = false;
        int waterWidth = 0, waterHeight = 0;
        if (Constants.VariantCode > 8 && LicenseManager.getLastResponse() != License.LicenseState.pro)
        {
        	processWatermark = true;
            watermark = Util.getDemoWatermark(this, source.getWidth());
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
                Toast.makeText(this, R.string.warningBlankWatermark, Toast.LENGTH_LONG);
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
        
        boolean success;
		if (processWatermark)
		{
			success = writeThumbWatermark(source.getUri(), dest, waterData, waterWidth, waterHeight, margins);
		}
        else
        {
        	success = writeThumb(source.getUri(), dest);
        }	
		
		if (!success)
        {
            Toast.makeText(this, "Thumbnail generation failed.  If you are watermarking, check settings/sizes!", Toast.LENGTH_LONG).show();
        }
		else
        {
            onImageAdded(Uri.fromFile(dest));
            //Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
        }
    }

    private void setWallpaper()
    {
    	byte[] imageData = getCurrentItem().getThumb();
		try
		{
		    WallpaperManager.getInstance(this).setBitmap(Util.createBitmapToSize(
		            imageData, displayWidth, displayHeight));
		}
		catch (Exception e)
		{
		    Log.e(TAG, e.toString());
		    Toast.makeText(ViewerActivity.this, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show();
		}
    }

    private void tagImage()
    {
        toggleEditXmpFragment();
    }

    //TODO: Should not be needed anymore
//    protected void updateViewerItems()
//    {
//
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        boolean showNative = prefs.getBoolean(FullSettingsActivity.KEY_ShowNativeFiles, true);
//
//        mVisibleItems.clear();
//        mVisibleItems.addAll(mRawImages);
//        if (showNative)
//            mVisibleItems.addAll(mNativeImages);
//    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        setMetaVisibility();
        MediaItem media = getCurrentItem();

        //FIXME: Show native files?
/*        if (key.equals(FullSettingsActivity.KEY_ShowNativeFiles))
        {
            updateViewerItems();

            // If current images are native and viewing is turned off finish activity
            if (media == null || !sharedPreferences.getBoolean(key, true) && Util.isNative(new File(media.getFilePath())))
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
        else*/ if (key.equals(FullSettingsActivity.KEY_UseLegacyViewer))
        {
            Intent viewer = getViewerIntent();
            viewer.setData(media.getUri());
            //TODO: finish() before startActivity???
            finish();
            startActivity(viewer);
        }
    }

    protected void setImageFocus()
    {
//        MediaItem current = getCurrentItem();
//        if (current == null)
//            return;
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

    protected static class ViewerLicenseHandler extends LicenseHandler
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
