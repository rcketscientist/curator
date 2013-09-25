package com.anthonymandra.framework;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

/**
 * Created by amand_000 on 9/10/13.
 */
public class LicenseManager extends ContentObserver {
    private static final String TAG = LicenseManager.class.getSimpleName();
    private static Context mContext;

    private static LicenseState licenseState = LicenseState.Unlicensed;

    public static final int LICENSED = 0x100;
    public static final int UNLICENSED = 0x101;
    public static final int ERROR = 0x102;
    public static final int MODIFIED = 0x103;

    // TODO: Remove eventually
    public static final int OLD_LICENSED = 1;
    public static final int OLD_UNLICENSED = 0;
    public static final int OLD_ERROR = 2;

    public static enum LicenseState { Licensed, Unlicensed, NoResponse, NoProvider, Error, Modified };
    private static final String LICENSE_AUTHORITY = "content://com.anthonymandra.rawdroidpro.LicenseProvider";

    public LicenseManager(Context context, Handler handler)
    {
        super(handler);
        mContext = context;
    }

    public void initialize()
    {
        forceLicenseCheck();
        checkLicense();
    }

    public void forceLicenseCheck()
    {
        // This is a hack that forces a license recheck.
        if (mContext.getContentResolver().getType(Uri.parse(LICENSE_AUTHORITY)) != null)
            mContext.getContentResolver().insert(Uri.parse(LICENSE_AUTHORITY), new ContentValues());
    }

    public LicenseState getLicenseState()
    {
        return licenseState;
    }

    public boolean isLicensed()
    {
        return licenseState == LicenseState.Licensed;
    }

    protected void checkLicense()
    {
        Cursor license = mContext.getContentResolver().query(Uri.parse(LICENSE_AUTHORITY), null, null, null, null);

        if (license != null && license.moveToFirst())
        {
            int result = license.getInt(1);
            Log.d(TAG, "" + result);
            switch (result)
            {
                case OLD_UNLICENSED:
                case UNLICENSED:
                    licenseState = LicenseState.Unlicensed; break;
                case LICENSED:
                case OLD_LICENSED:
                    licenseState = LicenseState.Licensed; break;
                case ERROR:
                case OLD_ERROR:
                    licenseState = licenseState.Error; break;
                case MODIFIED:
                    licenseState = licenseState.Modified; break;
                default: licenseState = licenseState.NoResponse;
            }
        }
        else
        {
            licenseState = LicenseState.NoProvider;
        }

        Log.d(TAG, licenseState.toString());
    }

    public void registerObserver()
    {
        mContext.getContentResolver().registerContentObserver(Uri.parse(LICENSE_AUTHORITY), true, this);
    }

    public void unregisterObserver()
    {
        mContext.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange) {
        Log.d(TAG, "onChange");
        super.onChange(selfChange);
        checkLicense();
    }
}
