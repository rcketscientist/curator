package com.anthonymandra.rawdroid;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.anthonymandra.framework.Util;

public class AppRater
{
	private final static String APP_TITLE = "Rawdroid";
	private final static String APP_PNAME = "com.anthonymandra.rawdroid";
	private final static String PREF_NAME = "Rawdroid.AppRater";

	private final static int DAYS_UNTIL_PROMPT = 5;
	private final static int LAUNCHES_UNTIL_PROMPT = 7;

	public static void app_launched(Context mContext)
	{
		SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, 0);
		if (prefs.getBoolean("dontshowagain", false))
		{
			return;
		}

		SharedPreferences.Editor editor = prefs.edit();

		// Increment launch counter
		long launch_count = prefs.getLong("launch_count", 0) + 1;
		editor.putLong("launch_count", launch_count);

		// Get date of first launch
		Long date_firstLaunch = prefs.getLong("date_firstlaunch", 0);
		if (date_firstLaunch == 0)
		{
			date_firstLaunch = System.currentTimeMillis();
			editor.putLong("date_firstlaunch", date_firstLaunch);
		}

		// Wait at least n days before opening
		if (launch_count >= LAUNCHES_UNTIL_PROMPT)
		{
			if (System.currentTimeMillis() >= date_firstLaunch + (DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000))
			{
				showRateDialog(mContext, editor);
			}
		}

		editor.commit();
	}

	public static void showRateDialog(final Context mContext, final SharedPreferences.Editor editor)
	{
		Dialog dialog = new Dialog(mContext);

		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setMessage(R.string.ratingRequest).setTitle("Rate " + APP_TITLE).setIcon(mContext.getApplicationInfo().icon).setCancelable(false)
				.setPositiveButton("Rate Now", new DialogInterface.OnClickListener()
				{

					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						editor.putBoolean("dontshowagain", true);
						editor.commit();
                        Intent store = Util.getStoreIntent(mContext, APP_PNAME);
                        if (store != null)
                            mContext.startActivity(store);
						dialog.dismiss();
					}
				}).setNeutralButton("Later", new DialogInterface.OnClickListener()
				{

					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						dialog.dismiss();

					}
				}).setNegativeButton("No, Thanks", new DialogInterface.OnClickListener()
				{

					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (editor != null)
						{
							editor.putBoolean("dontshowagain", true);
							editor.commit();
						}
						dialog.dismiss();

					}
				});
		dialog = builder.create();

		dialog.show();
	}

	// public static void showRateDialog(final Context mContext, final SharedPreferences.Editor editor)
	// {
	// final Dialog dialog = new Dialog(mContext);// , R.style.Sherlock___Theme_Dialog);
	// dialog.setTitle("Rate " + APP_TITLE);
	//
	// LinearLayout ll = new LinearLayout(mContext);
	// ll.setOrientation(LinearLayout.VERTICAL);
	// LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
	// LinearLayout.LayoutParams.WRAP_CONTENT);
	// layoutParams.setMargins(10, 10, 10, 10);
	//
	// TextView tv = new TextView(mContext);
	// tv.setText(R.string.ratingRequest);
	// tv.setWidth(240);
	// tv.setPadding(4, 0, 4, 10);
	// ll.addView(tv, layoutParams);
	//
	// Button b1 = new Button(mContext);
	// b1.setText("Rate " + APP_TITLE);
	// b1.setBackgroundResource(R.drawable.button_black);
	// b1.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + APP_PNAME)));
	// dialog.dismiss();
	// }
	// });
	// ll.addView(b1, layoutParams);
	//
	// Button b2 = new Button(mContext);
	// b2.setText("Remind me later");
	// b2.setBackgroundResource(R.drawable.button_black);
	// b2.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// dialog.dismiss();
	// }
	// });
	// ll.addView(b2, layoutParams);
	//
	// Button b3 = new Button(mContext);
	// b3.setText("No, thanks");
	// b3.setBackgroundResource(R.drawable.button_black);
	// b3.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// if (editor != null)
	// {
	// editor.putBoolean("dontshowagain", true);
	// editor.commit();
	// }
	// dialog.dismiss();
	// }
	// });
	// ll.addView(b3, layoutParams);
	//
	// dialog.setContentView(ll);
	// dialog.show();
	// }
}