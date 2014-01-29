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
		int result = intent.getIntExtra(RawDroid.LICENSE_RESULT, RawDroid.LICENSE_ERROR);

		switch (result)
		{
			case RawDroid.LICENSE_ALLOW:
				Log.i(TAG, "License Allow");
				break;
			case RawDroid.LICENSE_DISALLOW:
				Log.i(TAG, "License Disallow");
				break;
			case RawDroid.LICENSE_ERROR:
				Log.i(TAG, "License Error");
				break;
		}
	}

}
