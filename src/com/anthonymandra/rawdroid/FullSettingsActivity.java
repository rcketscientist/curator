package com.anthonymandra.rawdroid;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.openintents.intents.FileManagerIntents;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.Util;

@SuppressWarnings("deprecation")
public class FullSettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	public static final String PREFS_STORAGE = "com.anthonymandra.rawdroid.PREFS_STORAGE";
	public static final String PREFS_VIEW = "com.anthonymandra.rawdroid.PREFS_VIEW";
	public static final String PREFS_METADATA = "com.anthonymandra.rawdroid.PREFS_METADATA";
	public static final int REQUEST_CODE_PICK_KEYWORD_FILE = 1;

	public static final String KEY_ShowImageInterface = "prefKeyShowImageInterface";
	public static final String KEY_ShowNav = "prefKeyShowNav";
	public static final String KEY_ShowXmpFiles = "prefKeyShowXmpFiles";
	public static final String KEY_ShowNativeFiles = "prefKeyShowNativeFiles";
	public static final String KEY_ShowUnknownFiles = "prefKeyShowUnknownFiles";
	public static final String KEY_RecycleBinSize = "prefKeyRecycleBinSize";
	public static final String KEY_ComingSoon = "prefKeyComingSoon";
	public static final String KEY_DeleteConfirmation = "prefKeyDeleteConfirmation";
	public static final String KEY_UseRecycleBin = "prefKeyUseRecycleBin";
	public static final String KEY_ImportKeywords = "prefKeyImportKeywords";
	public static final String KEY_ClearGalleryCache = "prefKeyClearGalleryCache";
	public static final String KEY_ShowMeta = "prefKeyShowMeta";
	public static final String KEY_ShowHist = "prefKeyShowHist";
	public static final String KEY_XmpRed = "prefKeyXmpRed";
	public static final String KEY_XmpBlue = "prefKeyXmpBlue";
	public static final String KEY_XmpGreen = "prefKeyXmpGreen";
	public static final String KEY_XmpYellow = "prefKeyXmpYellow";
	public static final String KEY_XmpPurple = "prefKeyXmpPurple";
	public static final String KEY_CustomKeywords = "prefKeyCustomKeywords";
    public static final String KEY_UseLegacyViewer = "prefKeyUseLegacyViewer";

    public static final String KEY_ExifName = "prefKeyName";
    public static final String KEY_ExifDate = "prefKeyExifDate";
    public static final String KEY_ExifModel = "prefKeyExifModel";
    public static final String KEY_ExifIso = "prefKeyExifIso";
    public static final String KEY_ExifExposure = "prefKeyExifExposure";
    public static final String KEY_ExifAperture = "prefKeyExifAperture";
    public static final String KEY_ExifFocal = "prefKeyExifFocal";
    public static final String KEY_ExifDimensions = "prefKeyExifDimensions";
    public static final String KEY_ExifAltitude = "prefKeyExifAltitude";
    public static final String KEY_ExifFlash = "prefKeyExifFlash";
    public static final String KEY_ExifLatitude = "prefKeyExifLatitude";
    public static final String KEY_ExifLongitude = "prefKeyExifLongitude";
    public static final String KEY_ExifWhiteBalance = "prefKeyExifWhiteBalance";
    public static final String KEY_ExifLens = "prefKeyExifLens";
    public static final String KEY_ExifDriveMode = "prefKeyExifDriveMode";
    public static final String KEY_ExifExposureMode = "prefKeyExifExposureMode";
    public static final String KEY_ExifExposureProgram = "prefKeyExifExposureProgram";

	public static final int defRecycleBin = 50;

	private static final int minRecycleBin = 30;
	private static final int maxRecycleBin = 500;

	private String[] prefShowOptions;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		prefShowOptions = getResources().getStringArray(R.array.showOptions);

		String action = getIntent().getAction();

		if (action != null)
		{
			if (action.equals(PREFS_STORAGE))
			{
				addPreferencesFromResource(R.xml.preferences_storage);
			}
			else if (action.equals(PREFS_METADATA))
			{
				addPreferencesFromResource(R.xml.preferences_metadata);
				attachMetaButtons(this, getPreferenceManager());
				updateXmpColors();
			}
			else if (action.equals(PREFS_VIEW))
			{
				addPreferencesFromResource(R.xml.preferences_view);
				updateShowOptions();
			}
		}
		else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			addPreferencesFromResource(R.xml.preference_headers_legacy);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_CODE_PICK_KEYWORD_FILE:
				if (resultCode == RESULT_OK && data != null)
				{
					handleKeywordResult(data.getData().getPath());
				}
				break;
		}
	}

	// Called only on Honeycomb and later
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onBuildHeaders(List<Header> target)
	{
		loadHeadersFromResource(R.xml.preference_headers, target);
	}

	/**
	 * Attachs meta related buttons. Static to be called via legacy and fragment methods techniques.
	 * 
	 * @param activity
	 */
	private static void attachMetaButtons(final Activity activity, final PreferenceManager manager)
	{
		Preference button = (Preference) manager.findPreference(KEY_ImportKeywords);
		if (button != null)
		{
			button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference arg0)
				{
					requestImportKeywords(activity);
					return true;
				}
			});
		}
	}

	/**
	 * Requests the keyword import intent. Static to be called via legacy and fragment techniques.
	 * 
	 * @param activity
	 */
	private static void requestImportKeywords(Activity activity)
	{
		Intent keywords = new Intent(FileManagerIntents.ACTION_PICK_FILE);

		// Set fancy title and button (optional)
		keywords.putExtra(FileManagerIntents.EXTRA_TITLE, R.string.choosefile);
		keywords.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, R.string.import1);

		try
		{
			activity.startActivityForResult(keywords, REQUEST_CODE_PICK_KEYWORD_FILE);
		}
		catch (ActivityNotFoundException e)
		{
			// No compatible file manager was found.
			Toast.makeText(activity, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleKeywordResult(final String sourcePath)
	{
		if (!Util.isTabDelimited(sourcePath))
		{
			Toast.makeText(this, R.string.warningFileWrongFormat, Toast.LENGTH_SHORT).show();
			return;
		}

		try
		{
			BufferedInputStream source = new BufferedInputStream(new FileInputStream(sourcePath));
			Util.copy(source, GalleryActivity.getKeywordFile(this));
			Toast.makeText(this, R.string.resultImportSuccessful, Toast.LENGTH_SHORT).show();
		}
		catch (IOException e)
		{
			Toast.makeText(this, R.string.resultImportFailed, Toast.LENGTH_SHORT).show();
			return;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentStorage extends PreferenceFragment implements OnSharedPreferenceChangeListener
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences_storage);
			updateRecycleBin();
		}

		@Override
		public void onResume()
		{
			super.onResume();
			getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause()
		{
			super.onPause();
			getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}

		private void updateRecycleBin()
		{
			EditTextPreference option = (EditTextPreference) getPreferenceManager().findPreference(KEY_RecycleBinSize);
			option.setTitle(getString(R.string.prefTitleRecycleBin) + " (" + option.getText() + "MB)");
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			if (key.equals(KEY_RecycleBinSize))
			{
				EditTextPreference option = (EditTextPreference) getPreferenceManager().findPreference(KEY_RecycleBinSize);
				int value = Integer.parseInt(option.getText());
				if (value < minRecycleBin)
				{
					option.setText(String.valueOf(minRecycleBin));
				}
				else if (value > maxRecycleBin)
				{
					option.setText(String.valueOf(maxRecycleBin));
				}
				updateRecycleBin();
			}
		}
	}

	private void updateRecycleBin()
	{
		EditTextPreference option = (EditTextPreference) getPreferenceManager().findPreference(KEY_RecycleBinSize);
		option.setTitle(getString(R.string.prefTitleRecycleBin) + " (" + option.getText() + "MB)");
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentMeta extends PreferenceFragment implements OnSharedPreferenceChangeListener
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.preferences_metadata);
			attachMetaButtons(getActivity(), getPreferenceManager());
			updateXmpColors();
		}

		@Override
		public void onResume()
		{
			super.onResume();
			getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause()
		{
			super.onPause();
			getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}

		private void updateXmpColors()
		{
			SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
			updateRed(sharedPreferences);
			updateBlue(sharedPreferences);
			updateGreen(sharedPreferences);
			updateYellow(sharedPreferences);
			updatePurple(sharedPreferences);
		}

		private void updateRed(SharedPreferences sharedPreferences)
		{
			Preference showMeta = findPreference(KEY_XmpRed);
			showMeta.setTitle(sharedPreferences.getString(KEY_XmpRed, "Red"));
		}

		private void updateBlue(SharedPreferences sharedPreferences)
		{
			Preference blue = findPreference(KEY_XmpBlue);
			blue.setTitle(sharedPreferences.getString(KEY_XmpBlue, "Blue"));
		}

		private void updateGreen(SharedPreferences sharedPreferences)
		{
			Preference green = findPreference(KEY_XmpGreen);
			green.setTitle(sharedPreferences.getString(KEY_XmpGreen, "Green"));
		}

		private void updateYellow(SharedPreferences sharedPreferences)
		{
			Preference yellow = findPreference(KEY_XmpYellow);
			yellow.setTitle(sharedPreferences.getString(KEY_XmpYellow, "Yellow"));
		}

		private void updatePurple(SharedPreferences sharedPreferences)
		{
			Preference purple = findPreference(KEY_XmpPurple);
			purple.setTitle(sharedPreferences.getString(KEY_XmpPurple, "Purple"));
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			if (key.equals(KEY_XmpRed))
			{
				updateRed(sharedPreferences);
			}
			else if (key.equals(KEY_XmpBlue))
			{
				updateBlue(sharedPreferences);
			}
			else if (key.equals(KEY_XmpGreen))
			{
				updateGreen(sharedPreferences);
			}
			else if (key.equals(KEY_XmpYellow))
			{
				updateYellow(sharedPreferences);
			}
			else if (key.equals(KEY_XmpPurple))
			{
				updatePurple(sharedPreferences);
			}
		}
	}

	private void updateXmpColors()
	{
		SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
		updateRed(sharedPreferences);
		updateBlue(sharedPreferences);
		updateGreen(sharedPreferences);
		updateYellow(sharedPreferences);
		updatePurple(sharedPreferences);
	}

	private void updateRed(SharedPreferences sharedPreferences)
	{
		Preference showMeta = findPreference(KEY_XmpRed);
		showMeta.setTitle(sharedPreferences.getString(KEY_XmpRed, "Red"));
	}

	private void updateBlue(SharedPreferences sharedPreferences)
	{
		Preference blue = findPreference(KEY_XmpBlue);
		blue.setTitle(sharedPreferences.getString(KEY_XmpBlue, "Blue"));
	}

	private void updateGreen(SharedPreferences sharedPreferences)
	{
		Preference green = findPreference(KEY_XmpGreen);
		green.setTitle(sharedPreferences.getString(KEY_XmpGreen, "Green"));
	}

	private void updateYellow(SharedPreferences sharedPreferences)
	{
		Preference yellow = findPreference(KEY_XmpYellow);
		yellow.setTitle(sharedPreferences.getString(KEY_XmpYellow, "Yellow"));
	}

	private void updatePurple(SharedPreferences sharedPreferences)
	{
		Preference purple = findPreference(KEY_XmpPurple);
		purple.setTitle(sharedPreferences.getString(KEY_XmpPurple, "Purple"));
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentView extends PreferenceFragment implements OnSharedPreferenceChangeListener
	{
		private String[] prefShowOptions;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			prefShowOptions = getResources().getStringArray(R.array.showOptions);

			addPreferencesFromResource(R.xml.preferences_view);
			updateShowOptions();
		}

		@Override
		public void onResume()
		{
			super.onResume();
			getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause()
		{
			super.onPause();
			getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}

		private void updateShowOptions()
		{
			SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
			updateShowMeta(sharedPreferences);
			updateShowNav(sharedPreferences);
			updateShowHist(sharedPreferences);
		}

		private void updateShowMeta(SharedPreferences sharedPreferences)
		{
			Preference showMeta = findPreference(KEY_ShowMeta);
			showMeta.setSummary(getShowOptionsText(sharedPreferences.getString(KEY_ShowMeta, "Automatic")));
		}

		private void updateShowNav(SharedPreferences sharedPreferences)
		{
			Preference showNav = findPreference(KEY_ShowNav);
			showNav.setSummary(getShowOptionsText(sharedPreferences.getString(KEY_ShowNav, "Automatic")));
		}

		private void updateShowHist(SharedPreferences sharedPreferences)
		{
			Preference showHist = findPreference(KEY_ShowHist);
			showHist.setSummary(getShowOptionsText(sharedPreferences.getString(KEY_ShowHist, "Automatic")));
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			if (key.equals(KEY_ShowMeta))
			{
				updateShowMeta(sharedPreferences);
			}
			else if (key.equals(KEY_ShowNav))
			{
				updateShowNav(sharedPreferences);
			}
			else if (key.equals(KEY_ShowHist))
			{
				updateShowHist(sharedPreferences);
			}
		}

		private String getShowOptionsText(String result)
		{
			if (result.equals("Always"))
			{
				return prefShowOptions[1];
			}
			else if (result.equals("Never"))
			{
				return prefShowOptions[2];
			}
			else
			// Automatic
			{
				return prefShowOptions[0];
			}
		}
	}

	private void updateShowOptions()
	{
		SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
		updateShowMeta(sharedPreferences);
		updateShowNav(sharedPreferences);
		updateShowHist(sharedPreferences);
	}

	private void updateShowMeta(SharedPreferences sharedPreferences)
	{
		ListPreference showMeta = (ListPreference) findPreference(KEY_ShowMeta);
		showMeta.setSummary(getShowOptionsText(sharedPreferences.getString(KEY_ShowMeta, "Automatic")));
	}

	private void updateShowNav(SharedPreferences sharedPreferences)
	{
		ListPreference showNav = (ListPreference) findPreference(KEY_ShowNav);
		showNav.setSummary(getShowOptionsText(sharedPreferences.getString(KEY_ShowNav, "Automatic")));
	}

	private void updateShowHist(SharedPreferences sharedPreferences)
	{
		ListPreference showHist = (ListPreference) findPreference(KEY_ShowHist);
		showHist.setSummary(getShowOptionsText(sharedPreferences.getString(KEY_ShowHist, "Automatic")));
	}

	private String getShowOptionsText(String result)
	{
		if (result.equals("Always"))
		{
			return prefShowOptions[1];
		}
		else if (result.equals("Never"))
		{
			return prefShowOptions[2];
		}
		else
		// Automatic
		{
			return prefShowOptions[0];
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(KEY_RecycleBinSize))
		{
			EditTextPreference option = (EditTextPreference) getPreferenceManager().findPreference(KEY_RecycleBinSize);
			int value = Integer.parseInt(option.getText());
			if (value < minRecycleBin)
			{
				option.setText(String.valueOf(minRecycleBin));
			}
			else if (value > maxRecycleBin)
			{
				option.setText(String.valueOf(maxRecycleBin));
			}
			updateRecycleBin();
		}
		else if (key.equals(KEY_ShowMeta))
		{
			updateShowMeta(sharedPreferences);
		}
		else if (key.equals(KEY_ShowNav))
		{
			updateShowNav(sharedPreferences);
		}
		else if (key.equals(KEY_ShowHist))
		{
			updateShowHist(sharedPreferences);
		}
		else if (key.equals(KEY_XmpRed))
		{
			updateRed(sharedPreferences);
		}
		else if (key.equals(KEY_XmpBlue))
		{
			updateBlue(sharedPreferences);
		}
		else if (key.equals(KEY_XmpGreen))
		{
			updateGreen(sharedPreferences);
		}
		else if (key.equals(KEY_XmpYellow))
		{
			updateYellow(sharedPreferences);
		}
		else if (key.equals(KEY_XmpPurple))
		{
			updatePurple(sharedPreferences);
		}
	}
}