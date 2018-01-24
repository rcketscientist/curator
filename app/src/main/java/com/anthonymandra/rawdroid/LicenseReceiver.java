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
		int result = intent.getIntExtra(GalleryActivity.Companion.getLICENSE_RESULT(), GalleryActivity.Companion.getLICENSE_ERROR());

		switch (result)
		{
			case GalleryActivity.Companion.getLICENSE_ALLOW():
				Log.i(TAG, "License Allow");
				break;
			case GalleryActivity.Companion.getLICENSE_DISALLOW():
				Log.i(TAG, "License Disallow");
				break;
			case GalleryActivity.Companion.getLICENSE_ERROR():
				Log.i(TAG, "License Error");
				break;
		}
	}

}
