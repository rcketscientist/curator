package com.anthonymandra.framework;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.rawdroid.FullSettingsActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by amand_000 on 9/12/13.
 */
public class SwapProvider extends ContentProvider implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SwapProvider.class.getSimpleName();

    // The authority is the symbolic name for the provider class
    public static final String AUTHORITY = "com.anthonymandra.rawdroid.SwapProvider";

    // UriMatcher used to match against incoming requests
    private UriMatcher uriMatcher;
    private static boolean mShowWatermark;
    private static String mWatermarkText;
    private static int mWatermarkAlpha;
    private static int mWatermarkSize;
    private static String mWatermarkLocation;
    private static LicenseManager mLicenseManager;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Add a URI to the matcher which will match against the form
        // 'content://com.anthonymandra.rawdroid.swapprovider/*'
        // and return 1 in the case that the incoming Uri matches this pattern
        uriMatcher.addURI(AUTHORITY, "*", 1);
        updateWatermark();
        mLicenseManager = new LicenseManager(getContext(), new Handler());

        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);

        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {

        Log.v(TAG, "Called with uri: '" + uri + "'." + uri.getLastPathSegment());

        // Check incoming Uri against the matcher
        switch (uriMatcher.match(uri)) {

            // If it returns 1 - then it matches the Uri defined in onCreate
            case 1:

                LocalImage image = new LocalImage(new File(uri.getFragment()));
                File swapFile = new File(Util.getDiskCacheDir(getContext(),
                        GalleryActivity.SWAP_BIN_DIR),
                        uri.getLastPathSegment());

                InputStream imageData = image.getThumb();
                if (imageData == null)
                    return null;

                try
                {
                    Bitmap bmp = BitmapFactory.decodeStream(imageData);

                    if (!mLicenseManager.isLicensed())
                    {
                        bmp = Util.addWatermark(getContext(), bmp);
                    }
                    else if (mShowWatermark)
                    {
                        bmp = Util.addCustomWatermark(bmp, mWatermarkText, mWatermarkAlpha, mWatermarkSize, mWatermarkLocation);
                    }

                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(swapFile));

                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(swapFile, ParcelFileDescriptor.MODE_READ_WRITE);
                    return pfd;
                }
                catch(Exception e){  }
                finally {
                    Utils.closeSilently(imageData);
                }



                // Create & return a ParcelFileDescriptor pointing to the file
                // Note: I don't care what mode they ask for - they're only getting read only


            // Otherwise unrecognised Uri
            default:
                Log.v(TAG, "Unsupported uri: '" + uri + "'.");
                throw new FileNotFoundException("Unsupported uri: " + uri.toString());
        }
    }

    private void updateWatermark()
    {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
        mShowWatermark = pref.getBoolean(FullSettingsActivity.KEY_EnableWatermark, false);
        mWatermarkText = pref.getString(FullSettingsActivity.KEY_WatermarkText, "");
        mWatermarkAlpha = pref.getInt(FullSettingsActivity.KEY_WatermarkAlpha, 75);
        mWatermarkSize = pref.getInt(FullSettingsActivity.KEY_WatermarkSize, 12);
        mWatermarkLocation = pref.getString(FullSettingsActivity.KEY_WatermarkLocation, "Center");
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
