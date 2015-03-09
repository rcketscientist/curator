package com.anthonymandra.rawdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.anthonymandra.framework.Util;

public class ViewerChooser extends Activity {

    private static int REQUEST_VIEWER = 1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent viewer = new Intent();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.contains(FullSettingsActivity.KEY_UseLegacyViewer))
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(FullSettingsActivity.KEY_UseLegacyViewer, !Util.hasHoneycomb());
            editor.apply();
        }

        if(settings.getBoolean(FullSettingsActivity.KEY_UseLegacyViewer, false))
        {
            viewer.setClass(this, LegacyViewerActivity.class);
        }
        else
        {
            viewer.setClass(this, ImageViewActivity.class);
        }

        viewer.setData(getIntent().getData());
        startActivityForResult(viewer, REQUEST_VIEWER);
    }

    /**
     * Simply forward on the result
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        setResult(resultCode, data);
        finish();
    }
}