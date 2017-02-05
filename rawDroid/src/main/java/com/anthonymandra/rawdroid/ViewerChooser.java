package com.anthonymandra.rawdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.anthonymandra.framework.Util;

public class ViewerChooser extends Activity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent viewer = getIntent();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.contains(FullSettingsActivity.KEY_UseLegacyViewer))
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(FullSettingsActivity.KEY_UseLegacyViewer, !Util.hasHoneycomb());
            editor.apply();
        }

//        if(settings.getBoolean(FullSettingsActivity.KEY_UseLegacyViewer, false))
//        {
//            viewer.setClass(this, LegacyViewerActivity.class);
//        }
//        else
//        {
            viewer.setClass(this, ImageViewActivity.class);
//            viewer.setClass(this, PagerViewActivity.class);
//        }

//        viewer.setData(getIntent().getData());
        viewer.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);   //Ensure result is passed to caller
        startActivity(viewer);
        finish();
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