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
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.dcraw.LibRaw.Margins;
import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.RawDroid;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

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
    private static Margins mMargins;
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
               
                File swapFile = new File(Util.getDiskCacheDir(getContext(),
                        GalleryActivity.SWAP_BIN_DIR),
                        uri.getLastPathSegment());
                        
                // Don't keep recreating the swap file
                // Some receivers may call multiple times
                if (!swapFile.exists())
                {
                	LocalImage image = new LocalImage(new File(uri.getFragment()));
	                byte[] imageData = image.getThumb();
	                if (imageData == null)
	                    return null;

	                Bitmap watermark;
	                byte[] waterData = null;
	                boolean processWatermark = false;
	                int waterWidth = 0, waterHeight = 0;
	                Margins margin = null;
	                
	                if (!mLicenseManager.isLicensed())
	                {
	                	processWatermark = true;
	                    watermark = Util.getDemoWatermark(getContext(), image.getWidth());
	                    waterData = Util.getBitmapBytes(watermark);
	                    waterWidth = watermark.getWidth();
	                    waterHeight = watermark.getHeight();
	                    margin = Margins.LowerRight;

	                }
	                else if (mShowWatermark)
	                {
	                	processWatermark = true;
	                    watermark = Util.getWatermarkText(mWatermarkText, mWatermarkAlpha, mWatermarkSize, mWatermarkLocation);
	                    waterData = Util.getBitmapBytes(watermark);
	                    waterWidth = watermark.getWidth();
	                    waterHeight = watermark.getHeight();
	                    margin = mMargins;
	                }
	                
	                boolean success;
					if (processWatermark)
					{
						success = image.writeThumbWatermark(swapFile, waterData, waterWidth, waterHeight, margin);
					}
					else
					{
						success = image.writeThumb(swapFile);	
					}
					
					if (!success)
						Toast.makeText(getContext(), "Thumbnail generation failed.  If you are watermarking, check settings/sizes!", Toast.LENGTH_LONG).show();
                }

                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(swapFile, ParcelFileDescriptor.MODE_READ_WRITE);
                return pfd;            

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
        mMargins = new Margins(pref);
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
