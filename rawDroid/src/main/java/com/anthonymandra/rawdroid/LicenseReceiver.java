package com.anthonymandra.rawdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LicenseReceiver extends BroadcastReceiver
{
	private static final String TAG = LicenseReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent)
	{
		int result = intent.getIntExtra(GalleryActivity.LICENSE_RESULT, GalleryActivity.LICENSE_ERROR);

		switch (result)
		{
			case GalleryActivity.LICENSE_ALLOW:
				Log.i(TAG, "License Allow");
				break;
			case GalleryActivity.LICENSE_DISALLOW:
				Log.i(TAG, "License Disallow");
				break;
			case GalleryActivity.LICENSE_ERROR:
				Log.i(TAG, "License Error");
				break;
		}
	}

}
