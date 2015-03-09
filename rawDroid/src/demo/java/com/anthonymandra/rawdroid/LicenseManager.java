package com.anthonymandra.rawdroid;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.anthonymandra.framework.License;

public class LicenseManager extends License {
    private static final String TAG = LicenseManager.class.getSimpleName();
    private static final LicenseState INITIAL_RESPONSE = LicenseState.demo;

    private static Handler licenseHandler;
    private static LicenseState lastResponse = LicenseState.demo;

    private static void updateLicense(LicenseState state) {
        Message response = licenseHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putSerializable(KEY_LICENSE_RESPONSE, INITIAL_RESPONSE);
        response.setData(b);
        licenseHandler.sendMessage(response);
    }

    public static void getLicense(Context context, Handler h) {
        licenseHandler = h;
        updateLicense(LicenseState.demo);
    }

    public static LicenseState getLastResponse()
    {
        return lastResponse;
    }
}
