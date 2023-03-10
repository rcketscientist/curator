package com.anthonymandra.rawdroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.preference.PreferenceManager;
import android.provider.Settings;

import com.anthonymandra.framework.License;
import com.crashlytics.android.Crashlytics;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;
import com.google.android.vending.licensing.ValidationException;

public class LicenseManager extends License {
    private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt3QP8g57yUxTR7MGJdNhJvnl4nNVqs3fdCHLscKXFY16o7YoMR9qSCYfKUpEu8jVSMVKJES/utXkxm606tOQXUlWAe1WNSvfUIhiUWcRddKHkzrKLEfUDLgZmKG3waEyMTIdaZ7H/doczwOCzaj+k3n3IIrG69QeFP1FbGDdHfV0VlHVuuulvVfDJTaqhn6v090su2QFD2FCoPf04K4FG5Ij+oix/2Vrl9QiojK8HXvctQ2TNQokuqy7UvjuenxZWJKfsouodKBcTSP56eFI8D1Q+tJPArXZF3o/4IOqay0RbRMSKmk1O8oI9+DYHXTI61RLn0mdhF/OgIaNWH9I+QIDAQAB";
    // Generate your own 20 random bytes, and put them here.
    private static final byte[] SALT = new byte[]
            {-8, 32, 21, -126, -43, -87, 4, -79, 33, 8, -67, -41, 44, -111, -118, -45, -31, 17, -14, 88};

    private static final String key = "Fuckoff";
    // Encrypted package names, crypto.obfuscate
    private static final String package1 = "1DQvmSrxhT8OuXV58ugIQJfCAfIXzSBaEoL4bC1KFnqK+UXsYx7dzHwSvolOWkXwoZw+5zw+ltdoCSWAJ86AXIGriy8spOXfCIi4+zNFIhjy9ZR0K8nXrL2qL3EFubiD";
    private static final String package2 = "1DQvmSrxhT8OuXV58ugIQJfCAfIXzSBaEoL4bC1KFnqK+UXsYx7dzHwSvolOWkXwheVyo1dAmnP8+BIWwnVsC6dCndSX9V9x3zeRAaUqWT8=";
    private static final String goog = "1DQvmSrxhT8OuXV58ugIQJfCAfIXzSBaEoL4bC1KFnqK+UXsYx7dzHwSvolOWkXwvnA+AH6ODRqjEm7RYPBCwdlFzTzRldNO7HjN6Ix2tyU=";
    private static final String amzn = "1DQvmSrxhT8OuXV58ugIQJfCAfIXzSBaEoL4bC1KFnqK+UXsYx7dzHwSvolOWkXw2dcOWWB8Iqtr4TENF+k4ebUVprlM9Nsg4cI5PLiqLkA=";

    private static final String firstInstall = "firstInstall";
    private static final long trialPeriod = 1200000;

    private static LicenseState lastResponse = LicenseState.pro;

	/**
     * Requests license in the background and resonds via handler
     * @param c context
     * @param h handler for async response
     */
    public static void getLicense(final Context c, final Handler h) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                checkLicense(c, h);
            }
        }).start();
    }

    public static LicenseState getLastResponse()
    {
        return lastResponse;
    }

    private static void checkLicense(final Context c , final Handler h) {
        AESObfuscator crypto = new AESObfuscator(SALT, "GoFuckYourself", "Leech");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);

        try {
            final String g = crypto.unobfuscate(goog, key);
            final String a = crypto.unobfuscate(amzn, key);
            final String pkg1 = crypto.unobfuscate(package1, key);
            final String pkg2 = crypto.unobfuscate(package2, key);

            if (settings.contains("license_modified"))
            {
                String state = settings.getString("license_modified", LicenseState.modified_0x000.toString());
                updateLicense(h, Enum.valueOf(LicenseState.class, state));
                return;
            }
            else if (packageExists(c, pkg1))
            {
                LicenseState state = LicenseState.modified_0x001;
                settings.edit().putString("license_modified", state.toString()).apply();
                updateLicense(h, state);
                return;
            }
            else if (packageExists(c, pkg2))
            {
                LicenseState state = LicenseState.modified_0x002;
                settings.edit().putString("license_modified", state.toString()).apply();
                updateLicense(h, state);
                return;
            }
            else if (!BuildConfig.DEBUG && scuttle(c, g, a))
            {
                LicenseState state = LicenseState.modified_0x003;
                settings.edit().putString("license_modified", state.toString()).apply();
                updateLicense(h, state);
                return;
            }

        } catch (ValidationException e) {
            e.printStackTrace();
        }

        if (!settings.contains(firstInstall)) {
            settings.edit().putLong(firstInstall, System.currentTimeMillis()).apply();
        }
        long installTime = settings.getLong(firstInstall, 0);

        // Until trial period is up pro, after trial period check
        if (System.currentTimeMillis() > installTime + trialPeriod) {
            // Try to use more data here. ANDROID_ID is a single point of attack.
            @SuppressLint("HardwareIds")
            String deviceId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
            // Construct the LicenseChecker with a policy.
            ServerManagedPolicy smp = new ServerManagedPolicy(c, new AESObfuscator(SALT, c.getPackageName(), deviceId));
            LicenseChecker checker = new LicenseChecker(c, smp, BASE64_PUBLIC_KEY);

            // Library calls this when it's done.
            LicenseCheckerCallback mLicenseCheckerCallback = new RawdroidLicenseCheckerCallback(h, checker);
            checker.checkAccess(mLicenseCheckerCallback);
        } else {
            updateLicense(h, LicenseState.pro);
        }
    }

    private static class RawdroidLicenseCheckerCallback implements LicenseCheckerCallback {
        private LicenseChecker mChecker;
        private Handler mHandler;

        RawdroidLicenseCheckerCallback(Handler h, LicenseChecker checker)
        {
            mChecker = checker;
            mHandler = h;
        }

        public void allow(int policyReason) {
            updateLicense(mHandler, LicenseState.pro);
            mChecker.onDestroy();
        }

        public void dontAllow(int policyReason) {
            updateLicense(mHandler, LicenseState.demo);
            mChecker.onDestroy();
        }

        public void applicationError(int errorCode) {
            updateLicense(mHandler, LicenseState.error);
            mChecker.onDestroy();
        }
    }

    private static void updateLicense(final Handler h, final LicenseState state) {
        lastResponse = state;
        Message response = h.obtainMessage();
        Bundle b = new Bundle();
        b.putSerializable(KEY_LICENSE_RESPONSE, lastResponse);
        response.setData(b);
        h.sendMessage(response);
    }

    private static boolean packageExists(final Context c, final String packageName) {
        try {
            ApplicationInfo info = c.getPackageManager().getApplicationInfo(packageName, 0);

            return info != null;

        } catch (Exception ex) {
            // If we get here only means the Package does not exist
        }

        return false;
    }

    private static boolean scuttle(final Context c, String google, String amazon)
    {
        //Scallywags renamed your app?

        if (c.getPackageName().compareTo(BuildConfig.APPLICATION_ID) != 0)
        {
            Crashlytics.logException(new Exception("SC001: " + c.getPackageName()));
            return true; // BOOM!
        }

        //Rogues relocated your app?

        String installer = c.getPackageManager().getInstallerPackageName(BuildConfig.APPLICATION_ID);

        if (installer == null)
        {
            Crashlytics.logException(new Exception("SC002: " + c.getPackageName()));
            return true; // BOOM!
        }

        if (installer.compareTo(google) != 0 && installer.compareTo(amazon) != 0)
        {
            Crashlytics.logException(new Exception("SC003: " + installer));
            return true; // BOOM!
        }

        return false;
    }
}
