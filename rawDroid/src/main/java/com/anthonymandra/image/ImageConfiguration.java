package com.anthonymandra.image;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.anthonymandra.rawdroid.FullSettingsActivity;

public abstract class ImageConfiguration {
    public enum  ImageType
    {
        jpeg,
        tiff,
        raw
    }

    // For more than one config setting
    protected static final String DELIMITER = "_";

    public abstract ImageType getType();
    public abstract String getExtension();
    protected abstract void parse(String config);
    protected abstract String convertToPreference();

    public static ImageConfiguration loadPreference(Context c)
    {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        String typeString = pref.getString(FullSettingsActivity.KEY_DefaultSaveType, null);
        if (typeString == null)
            return null;
        String config = pref.getString(FullSettingsActivity.KEY_DefaultSaveConfig, null);

        ImageType type = Enum.valueOf(ImageType.class, typeString);
        switch(type)
        {
            case jpeg:
                JpegConfiguration jc = new JpegConfiguration();
                jc.parse(config);
                return jc;
            case tiff:
                TiffConfiguration tc = new TiffConfiguration();
                tc.parse(config);
                return tc;
            default:
                return null;
        }
    }

    public void savePreference(Context c)
    {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(FullSettingsActivity.KEY_DefaultSaveType, getType().toString());
        editor.putString(FullSettingsActivity.KEY_DefaultSaveConfig, convertToPreference());
        editor.apply();
    }
}
