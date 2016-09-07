package com.anthonymandra.framework;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.anthonymandra.content.Meta;
import com.anthonymandra.imageprocessor.ImageProcessor;
import com.anthonymandra.imageprocessor.Margins;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.rawdroid.Constants;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.LicenseManager;
import com.anthonymandra.rawdroid.R;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtils;
import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class SwapProvider extends ContentProvider implements SharedPreferences.OnSharedPreferenceChangeListener  {
    private static final String TAG = SwapProvider.class.getSimpleName();

    // The authority is the symbolic name for the provider class
    public static final String AUTHORITY = BuildConfig.PROVIDER_AUTHORITY_SWAP;

    // UriMatcher used to match against incoming requests
    private UriMatcher uriMatcher;
    private static boolean mShowWatermark;
    private static String mWatermarkText;
    private static int mWatermarkAlpha;
    private static int mWatermarkSize;
    private static String mWatermarkLocation;
    private static Margins mMargins;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Add a URI to the matcher which will match against the form
        // 'content://com.anthonymandra.rawdroid.swapprovider/*'
        // and return 1 in the case that the incoming Uri matches this pattern
        uriMatcher.addURI(AUTHORITY, "*", 1);
        updateWatermark();

        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
        return true;
    }

	/**
	 * Generates a uri to request a swap file
     * @param uri uri of the source image
     * @return String uri to request a swap file
     */
    @Nullable
    public static Uri createSwapUri(Uri uri)
    {
        if (uri == null)
        {
            Crashlytics.logException(new Exception("null uri requested swap)"));
            return null;
        }
        String name = uri.getLastPathSegment(); //this is risky
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(SwapProvider.AUTHORITY)
                .fragment(uri.toString())
                .build();
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {

        Log.d(TAG, "Called with uri: '" + uri.getFragment() + "'.");

        Uri sourceUri = Uri.parse(uri.getFragment());
        // If it's a native file, just share it directly.
        if (ImageUtils.isNative(sourceUri))
        {
            return FileUtil.getParcelFileDescriptor(getContext(), sourceUri, mode);
        }

        UsefulDocumentFile image = UsefulDocumentFile.fromUri(getContext(), sourceUri);
        String name = image.getName();
        String jpg = FileUtil.swapExtention(name, "jpg");

        File swapFile = new File(FileUtil.getDiskCacheDir(getContext(),
                CoreActivity.SWAP_BIN_DIR),
                jpg);

        Log.d(TAG, "Swap.exists(" + swapFile.exists() + "): " + swapFile.getPath());

        // Don't keep recreating the swap file
        // Some receivers may call multiple times
        if (!swapFile.exists())
        {
            try
            {
                swapFile.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            byte[] imageData = ImageUtils.getThumb(getContext(), image.getUri());
            if (imageData == null)
                return null;

            Bitmap watermark;
            byte[] waterData = null;
            boolean processWatermark = false;
            int waterWidth = 0, waterHeight = 0;
            Margins margin = null;

            if (Constants.VariantCode < 10 || LicenseManager.getLastResponse() != License.LicenseState.pro)
            {
                processWatermark = true;
                try (Cursor c = ImageUtils.getMetaCursor(getContext(), image.getUri()))
                {
                    if (c != null && c.moveToFirst())
                    {
                        final int widthColumn = c.getColumnIndex(Meta.WIDTH);
                        if (widthColumn != -1)
                        {
                            watermark = ImageUtils.getDemoWatermark(getContext(), c.getInt(widthColumn));
                            waterData = ImageUtils.getBitmapBytes(watermark);
                            waterWidth = watermark.getWidth();
                            waterHeight = watermark.getHeight();
                            margin = Margins.LowerRight;
                        }
                    }
                }
            }
            else if (mShowWatermark)
            {
                processWatermark = true;
                if (mWatermarkText.isEmpty())
                {
                    Toast.makeText(getContext(), R.string.warningBlankWatermark, Toast.LENGTH_LONG).show();
                    processWatermark = false;
                }
                else
                {
                    watermark = ImageUtils.getWatermarkText(mWatermarkText, mWatermarkAlpha, mWatermarkSize, mWatermarkLocation);
                    waterData = ImageUtils.getBitmapBytes(watermark);
                    waterWidth = watermark.getWidth();
                    waterHeight = watermark.getHeight();
                    margin = mMargins;
                }
            }

            boolean success;
            if (processWatermark)
            {
                success = writeThumbWatermark(image.getUri(), swapFile, waterData, waterWidth, waterHeight, margin);
            }
            else
            {
                success = writeThumb(image.getUri(), swapFile);
            }

            if (!success)
            {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Toast.makeText(getContext(), "Thumbnail generation failed.", Toast.LENGTH_LONG).show();
                    }
                } );
            }
        }

        return ParcelFileDescriptor.open(swapFile, modeToMode(mode));
    }

    /**
     * Copied from ContentResolver.java
     */
    private static int modeToMode(String mode) {
        int modeBits;
        if ("r".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + mode);
        }
        return modeBits;
    }

    private void updateWatermark()
    {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
        mShowWatermark = pref.getBoolean(FullSettingsActivity.KEY_EnableWatermark, false);
        mWatermarkText = pref.getString(FullSettingsActivity.KEY_WatermarkText, "");
        mWatermarkAlpha = pref.getInt(FullSettingsActivity.KEY_WatermarkAlpha, 75);
        mWatermarkSize = pref.getInt(FullSettingsActivity.KEY_WatermarkSize, 150);
        mWatermarkLocation = pref.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");
        int top = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkTopMargin, "-1"));
        int bottom = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkBottomMargin, "-1"));
        int right = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkRightMargin, "-1"));
        int left = Integer.parseInt(pref.getString(FullSettingsActivity.KEY_WatermarkLeftMargin, "-1"));
        Margins margins = new Margins(top, left, bottom, right);
    }

    // It's safe to write these directly since it's app storage space
    protected boolean writeThumb(Uri uri, File destination)
    {
        try (
            ParcelFileDescriptor source = getContext().getContentResolver().openFileDescriptor(uri, "r");
            ParcelFileDescriptor dest = ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_WRITE)) {
            return ImageProcessor.writeThumb(source.getFd(), 100, dest.getFd());
        }
        catch(Exception e)
        {
            return false;
        }
    }

    // It's safe to write these directly since it's app storage space
    protected boolean writeThumbWatermark(Uri source, File destination, byte[] waterMap,
                                          int waterWidth, int waterHeight, Margins waterMargins)
    {
        try (
            ParcelFileDescriptor src = getContext().getContentResolver().openFileDescriptor(source, "r");
            ParcelFileDescriptor dest = ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_WRITE)) {
            return ImageProcessor.writeThumb(src.getFd(), 100, dest.getFd(), waterMap, waterMargins.getArray(), waterWidth, waterHeight);
        }
        catch(Exception e)
        {
            return false;
        }
    }

    // //////////////////////////////////////////////////////////////
    // Not supported / used / required for this example
    // //////////////////////////////////////////////////////////////

    @Override
    public int update(Uri uri, ContentValues contentvalues, String s,
                      String[] as) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String s, String[] as) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentvalues) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String s, String[] as1,
                        String s1) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if (key.equals(FullSettingsActivity.KEY_WatermarkText) ||
                key.equals(FullSettingsActivity.KEY_WatermarkAlpha) ||
                key.equals(FullSettingsActivity.KEY_WatermarkSize) ||
                key.equals(FullSettingsActivity.KEY_WatermarkLocation) ||
                key.equals(FullSettingsActivity.KEY_EnableWatermark))
        {
            updateWatermark();
        }
    }
}
