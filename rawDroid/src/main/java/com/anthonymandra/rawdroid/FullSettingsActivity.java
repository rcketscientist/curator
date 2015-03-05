package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.anthonymandra.framework.GalleryActivity;
import com.anthonymandra.framework.License;
import com.anthonymandra.framework.Util;
import com.anthonymandra.widget.SeekBarPreference;

import org.openintents.filemanager.FileManagerActivity;
import org.openintents.intents.FileManagerIntents;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@SuppressWarnings("deprecation")
public class FullSettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	public static final String PREFS_STORAGE = "com.anthonymandra.rawdroid.PREFS_STORAGE";
	public static final String PREFS_VIEW = "com.anthonymandra.rawdroid.PREFS_VIEW";
	public static final String PREFS_METADATA = "com.anthonymandra.rawdroid.PREFS_METADATA";
    public static final String PREFS_LICENSE = "com.anthonymandra.rawdroid.PREFS_LICENSE";
    public static final String PREFS_WATERMARK = "com.anthonymandra.rawdroid.PREFS_WATERMARK";

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
    public static final String KEY_ShowToolbar = "prefKeyShowToolbar";
	public static final String KEY_XmpRed = "prefKeyXmpRed";
	public static final String KEY_XmpBlue = "prefKeyXmpBlue";
	public static final String KEY_XmpGreen = "prefKeyXmpGreen";
	public static final String KEY_XmpYellow = "prefKeyXmpYellow";
	public static final String KEY_XmpPurple = "prefKeyXmpPurple";
	public static final String KEY_CustomKeywords = "prefKeyCustomKeywords";
    public static final String KEY_UseLegacyViewer = "prefKeyUseLegacyViewer";
    public static final String KEY_UseImmersive = "prefKeyUseImmersive";

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

    public static final String KEY_license = "prefKeyLicense";
    public static final String KEY_ManualLicense = "prefKeyManualLicense";
    public static final String KEY_Contact = "prefKeyContact";

    public static final String KEY_EnableWatermark = "prefKeyEnableWatermark";
    public static final String KEY_WatermarkText = "prefKeyWatermarkText";
    public static final String KEY_WatermarkSize = "prefKeyWatermarkSize";
    public static final String KEY_WatermarkLocation = "prefKeyWatermarkLocation";
    public static final String KEY_WatermarkAlpha = "prefKeyWatermarkAlpha";
    public static final String KEY_WatermarkTopMargin = "prefKeyWatermarkTopMargin";
    public static final String KEY_WatermarkBottomMargin = "prefKeyWatermarkBottomMargin";
    public static final String KEY_WatermarkLeftMargin = "prefKeyWatermarkLeftMargin";
    public static final String KEY_WatermarkRightMargin = "prefKeyWatermarkRightMargin";

	public static final int defRecycleBin = 50;

	private static final int minRecycleBin = 30;
	private static final int maxRecycleBin = 500;

	private static String[] prefShowOptions;
    private static String[] prefWatermarkLocations;

    private static Activity mActivity;
    private static PreferenceManager mPreferenceManager;

    private static Handler licenseHandler;

    public enum WatermarkLocations
    {
        Center, LowerLeft, LowerRight, UpperLeft, UpperRight
    }

    @Override
    protected boolean isValidFragment (String fragmentName) {
        return
        SettingFragment.class.getName().equals(fragmentName) ||
        SettingsFragmentLicense.class.getName().equals(fragmentName) ||
        SettingsFragmentMeta.class.getName().equals(fragmentName) ||
        SettingsFragmentStorage.class.getName().equals(fragmentName) ||
        SettingsFragmentView.class.getName().equals(fragmentName) ||
        SettingsFragmentWatermark.class.getName().equals(fragmentName);
    }

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        mPreferenceManager = getPreferenceManager();
        mActivity = this;
        prefShowOptions = getResources().getStringArray(R.array.showOptions);
        prefWatermarkLocations = getResources().getStringArray(R.array.watermarkLocations);

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
				attachMetaButtons();
				updateXmpColors();
			}
			else if (action.equals(PREFS_VIEW))
			{
				addPreferencesFromResource(R.xml.preferences_view);
				updateShowOptions();
			}
            else if (action.equals(PREFS_LICENSE))
            {
                addPreferencesFromResource(R.xml.preferences_license);
                attachLicenseButtons();
            }
            else if (action.equals(PREFS_WATERMARK))
            {
                addPreferencesFromResource(R.xml.preferences_watermark);
                updateWatermarkOptions();
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
	 */
	private static void attachMetaButtons()
	{
		Preference button = (Preference) mPreferenceManager.findPreference(KEY_ImportKeywords);
		if (button != null)
		{
			button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference arg0)
				{
					requestImportKeywords();
					return true;
				}
			});
		}
	}

    /**
     * Attachs meta related buttons. Static to be called via legacy and fragment methods techniques.
     */
    private static void attachLicenseButtons()
    {
        Preference manual = (Preference) mPreferenceManager.findPreference(KEY_ManualLicense);
        if (manual != null)
        {
            manual.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference arg0)
                {
                    LicenseManager.getLicense(mActivity.getBaseContext(), licenseHandler);
                    Preference check = (Preference) mPreferenceManager.findPreference(KEY_ManualLicense);
                    check.setTitle(mActivity.getString(R.string.prefTitleManualLicense) + " (Request Sent)");
                    return true;
                }
            });
        }

        Preference email = (Preference) mPreferenceManager.findPreference(KEY_Contact);
        if (email != null)
        {
            email.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference arg0)
                {
                    requestEmail();
                    return true;
                }
            });
        }
    }

    private static void requestBuyPro()
    {
        Intent store = Util.getStoreIntent(mActivity, "com.anthonymandra.rawdroidpro");
        if (store != null)
            mActivity.startActivity(store);
    }

	/**
	 * Requests the keyword import intent. Static to be called via legacy and fragment techniques.
	 */
	private static void requestImportKeywords()
	{
//		Intent keywords = new Intent(FileManagerIntents.ACTION_PICK_FILE);
        Intent intent = new Intent(mActivity, FileManagerActivity.class);
        intent.setAction(FileManagerIntents.ACTION_PICK_FILE);

		// Set fancy title and button (optional)
        intent.putExtra(FileManagerIntents.EXTRA_TITLE, R.string.choosefile);
        intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, R.string.import1);

		try
		{
			mActivity.startActivityForResult(intent, REQUEST_CODE_PICK_KEYWORD_FILE);
		}
		catch (ActivityNotFoundException e)
		{
			// No compatible file manager was found.
			Toast.makeText(mActivity, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

    private static void requestEmail()
    {
        Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"rawdroid@anthonymandra.com"});
        emailIntent.setType("plain/text");
        mActivity.startActivity(Intent.createChooser(emailIntent, "Send email..."));
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
			Toast.makeText(this, R.string.resultImportSuccessful, Toast.LENGTH_LONG).show();
		}
		catch (IOException e)
		{
			Toast.makeText(this, R.string.resultImportFailed, Toast.LENGTH_LONG).show();
			return;
		}
	}

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentStorage extends SettingFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences_storage);
		}

        @Override
        public void onResume() {
            super.onResume();
            updateRecycleBin();
        }
    }

	private static void updateRecycleBin()
	{
		EditTextPreference option = (EditTextPreference) mPreferenceManager.findPreference(KEY_RecycleBinSize);
		option.setTitle(mActivity.getString(R.string.prefTitleRecycleBin) + " (" + option.getText() + "MB)");
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentMeta extends SettingFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences_metadata);
		}


        @Override
        public void onResume() {
            super.onResume();
            attachMetaButtons();
            updateXmpColors();
        }
	}

	private static void updateXmpColors()
	{
		SharedPreferences sharedPreferences = mPreferenceManager.getSharedPreferences();
		updateRed(sharedPreferences);
		updateBlue(sharedPreferences);
		updateGreen(sharedPreferences);
		updateYellow(sharedPreferences);
		updatePurple(sharedPreferences);
	}

	private static void updateRed(SharedPreferences sharedPreferences)
	{
		Preference showMeta = mPreferenceManager.findPreference(KEY_XmpRed);
		showMeta.setTitle(sharedPreferences.getString(KEY_XmpRed, "Red"));
	}

	private static void updateBlue(SharedPreferences sharedPreferences)
	{
		Preference blue = mPreferenceManager.findPreference(KEY_XmpBlue);
		blue.setTitle(sharedPreferences.getString(KEY_XmpBlue, "Blue"));
	}

	private static void updateGreen(SharedPreferences sharedPreferences)
	{
		Preference green = mPreferenceManager.findPreference(KEY_XmpGreen);
		green.setTitle(sharedPreferences.getString(KEY_XmpGreen, "Green"));
	}

	private static void updateYellow(SharedPreferences sharedPreferences)
	{
		Preference yellow = mPreferenceManager.findPreference(KEY_XmpYellow);
		yellow.setTitle(sharedPreferences.getString(KEY_XmpYellow, "Yellow"));
	}

	private static void updatePurple(SharedPreferences sharedPreferences)
	{
		Preference purple = mPreferenceManager.findPreference(KEY_XmpPurple);
		purple.setTitle(sharedPreferences.getString(KEY_XmpPurple, "Purple"));
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentView extends SettingFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences_view);
		}

        @Override
        public void onResume() {
            super.onResume();
            updateShowOptions();
        }
	}

	private static void updateShowOptions()
	{
		SharedPreferences sharedPreferences = mPreferenceManager.getSharedPreferences();
		updateShowMeta(sharedPreferences);
		updateShowNav(sharedPreferences);
		updateShowHist(sharedPreferences);
        updateShowToolbar(sharedPreferences);
	}

	private static void updateShowMeta(SharedPreferences sharedPreferences)
	{
        updateShowView(sharedPreferences, KEY_ShowMeta);
	}

	private static void updateShowNav(SharedPreferences sharedPreferences)
	{
        updateShowView(sharedPreferences, KEY_ShowNav);
	}

	private static void updateShowHist(SharedPreferences sharedPreferences)
	{
        updateShowView(sharedPreferences, KEY_ShowHist);
	}

    private static void updateShowToolbar(SharedPreferences sharedPreferences)
    {
        updateShowView(sharedPreferences, KEY_ShowToolbar);
    }

    private static void updateShowView(SharedPreferences sharedPreferences, String key)
    {
        ListPreference showViewPref = (ListPreference) mPreferenceManager.findPreference(key);
        showViewPref.setSummary(translateShowOptionsText(sharedPreferences.getString(key, "Automatic")));
    }


    private static String translateShowOptionsText(String result)
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

    private static String translateWatermarkLocations(String result)
    {
        if (result.equals("Lower Left"))
        {
            return prefWatermarkLocations[1];
        }
        else if (result.equals("Lower Right"))
        {
            return prefWatermarkLocations[2];
        }

        else if (result.equals("Upper Left"))
        {
            return prefWatermarkLocations[3];
        }

        else if (result.equals("Upper Right"))
        {
            return prefWatermarkLocations[4];
        }
        else
        {
            return prefWatermarkLocations[0];
        }
    }

    private static void onSharedPreferenceChangedBase(SharedPreferences sharedPreferences, String key)
    {
        if (key.equals(KEY_RecycleBinSize))
        {
            int value;
            EditTextPreference option = (EditTextPreference) mPreferenceManager.findPreference(KEY_RecycleBinSize);
            try
            {
                value = Integer.parseInt(option.getText());
            }
            catch (NumberFormatException e)
            {
                value = 0;
            }
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
        else if (key.equals(KEY_ShowToolbar))
        {
            updateShowToolbar(sharedPreferences);
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
        else if (key.equals(KEY_EnableWatermark))
        {
            updateWatermarkEnabled();
        }
        else if (key.equals(KEY_WatermarkLocation))
        {
            updateWatermarkLocation();
        }
        else if (key.equals(KEY_WatermarkTopMargin) ||
        		key.equals(KEY_WatermarkBottomMargin) ||
        		key.equals(KEY_WatermarkLeftMargin) ||
        		key.equals(KEY_WatermarkRightMargin))
        {
        	updateWatermarkMargins();
        }        		
        else if (key.equals(KEY_WatermarkSize))
        {
            updateWatermarkSize();
        }
        else if (key.equals(KEY_WatermarkAlpha))
        {
            updateWatermarkAlpha();
        }
        else if (key.equals(KEY_WatermarkText))
        {
            updateWatermarkText();
        }
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
        onSharedPreferenceChangedBase(sharedPreferences, key);
	}

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static abstract class SettingFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onResume()
        {
            super.onResume();
            mPreferenceManager = getPreferenceManager();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause()
        {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
        {
            onSharedPreferenceChangedBase(sharedPreferences, key);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SettingsFragmentLicense extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_license);

            licenseHandler = new Handler()
            {
                @Override
                public void handleMessage(Message msg) {
                    License.LicenseState state = (License.LicenseState) msg.getData().getSerializable(License.KEY_LICENSE_RESPONSE);
                    updateLicense(state);
                }
            };
        }

        @Override
        public void onResume() {
            super.onResume();
            mPreferenceManager = getPreferenceManager();
            LicenseManager.getLicense(mActivity.getBaseContext(), licenseHandler);
            attachLicenseButtons();
        }
    }

    private static void updateLicense(License.LicenseState state)
    {
        Preference license = (Preference) mPreferenceManager.findPreference(KEY_license);
        Preference check = (Preference) mPreferenceManager.findPreference(KEY_ManualLicense);

        // This might happen if the user switches tabs quickly while looking up license
        if (check == null || license == null)
            return;

        check.setTitle(mActivity.getString(R.string.prefTitleManualLicense));
        license.setTitle(state.toString());
        switch (state)
        {
            case error:
                license.setSummary(mActivity.getString(R.string.prefSummaryLicenseError));
                setBuyButton();
                break;
            case pro: license.setSummary(mActivity.getString(R.string.prefSummaryLicense));
                break;
            case demo: license.setSummary(mActivity.getString(R.string.buypro));
                setBuyButton();
                break;
            case modified_0x000:
            case modified_0x001:
            case modified_0x002:
            case modified_0x003:
                license.setSummary(R.string.prefSummaryLicenseModified);
                setBuyButton();
                break;
            default: license.setSummary(mActivity.getString(R.string.prefSummaryLicenseError) + "\n" +
                    mActivity.getString(R.string.buypro));
                setBuyButton();
        }
    }

    private static void setBuyButton()
    {
        Preference license = (Preference) mPreferenceManager.findPreference(KEY_license);
        if (license != null)
        {
            license.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference arg0)
                {
                    requestBuyPro();
                    return true;
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SettingsFragmentWatermark extends SettingFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_watermark);
        }

        @Override
        public void onResume() {
            super.onResume();
            updateWatermarkOptions();
        }
    }

    private static void updateWatermarkOptions()
    {
        updateWatermarkEnabled();
        updateWatermarkLocation();
        updateWatermarkMargins();
        updateWatermarkAlpha();
        updateWatermarkSize();
        updateWatermarkText();
    }

    private static void updateWatermarkText()
    {
        EditTextPreference watermarkText = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkText);
        watermarkText.setSummary(mPreferenceManager.getSharedPreferences().getString(KEY_WatermarkText, ""));
    }

    private static void updateWatermarkSize()
    {
        SeekBarPreference watermarkSize = (SeekBarPreference) mPreferenceManager.findPreference(KEY_WatermarkSize);
        watermarkSize.setSummary(mPreferenceManager.getSharedPreferences().getInt(KEY_WatermarkSize, 150)
                + "\n" +
                mActivity.getString(R.string.prefSummaryWatermarkSize));
    }

    private static void updateWatermarkAlpha()
    {
        SeekBarPreference watermarkAlpha = (SeekBarPreference) mPreferenceManager.findPreference(KEY_WatermarkAlpha);
        watermarkAlpha.setSummary("" + mPreferenceManager.getSharedPreferences().getInt(KEY_WatermarkAlpha, 75));
    }

    private static void updateWatermarkEnabled()
    {
        boolean isLicensed = Constants.VariantCode > 8 && LicenseManager.getLastResponse() == License.LicenseState.pro;
        CheckBoxPreference enableWatermark = (CheckBoxPreference) mPreferenceManager.findPreference(KEY_EnableWatermark);
        enableWatermark.setEnabled(isLicensed);
        if (!isLicensed)
        {
            enableWatermark.setChecked(false);
        }
        else
        {
            enableWatermark.setChecked(mPreferenceManager.getSharedPreferences().getBoolean(KEY_EnableWatermark, false));
        }
    }
    
    private static void updateWatermarkMargins()
    {
    	EditTextPreference top = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkTopMargin);
    	EditTextPreference bottom = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkBottomMargin);
    	EditTextPreference left = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkLeftMargin);
    	EditTextPreference right = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkRightMargin);
    	
    	// Clean up disabled (-1) values
    	if (top.getText() != null)
    	{
    		String topValue = top.getText().equals("-1") ? "" : ": " + top.getText();
    		top.setTitle(mActivity.getString(R.string.prefTitleTopMargin) + topValue);
    	}
    	
    	if (bottom.getText() != null)
    	{
    		String bottomValue = bottom.getText().equals("-1") ? "" : ": " + bottom.getText();
    		bottom.setTitle(mActivity.getString(R.string.prefTitleBottomMargin) + bottomValue);
    	}
    	
    	if (left.getText() != null)
    	{
    		String leftValue = left.getText().equals("-1") ? "" : ": " + left.getText(); 	    	
    		left.setTitle(mActivity.getString(R.string.prefTitleLeftMargin) + leftValue);
    	}
    	
    	if (right.getText() != null)
    	{
    		String rightValue = right.getText().equals("-1") ? "" : ": " + right.getText();
    		right.setTitle(mActivity.getString(R.string.prefTitleRightMargin) + rightValue);
    	}
    }

    private static void updateWatermarkLocation()
    {
        ListPreference location = (ListPreference) mPreferenceManager.findPreference(KEY_WatermarkLocation);
        String position = mPreferenceManager.getSharedPreferences().getString(KEY_WatermarkLocation, "Center");
        location.setSummary(translateWatermarkLocations(position));
    	EditTextPreference top = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkTopMargin);
    	EditTextPreference bottom = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkBottomMargin);
    	EditTextPreference left = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkLeftMargin);
    	EditTextPreference right = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkRightMargin);
        
        if (position.equals(mActivity.getString(R.string.upperLeft)))
		{
        	top.setText("0");
        	top.setEnabled(true);
        	left.setText("0");
        	left.setEnabled(true);
        	
        	bottom.setText("-1");
        	bottom.setEnabled(false);
        	right.setText("-1");
        	right.setEnabled(false);
		}
        else if (position.equals(mActivity.getString(R.string.upperRight)))
		{
        	top.setText("0");
        	top.setEnabled(true);
        	right.setText("0");
        	right.setEnabled(true);
        	
        	bottom.setText("-1");
        	bottom.setEnabled(false);
        	left.setText("-1");
        	left.setEnabled(false);
		}
        else if (position.equals(mActivity.getString(R.string.lowerLeft)))
		{
        	bottom.setText("0");
        	bottom.setEnabled(true);
        	left.setText("0");
        	left.setEnabled(true);
        	
        	top.setText("-1");
        	top.setEnabled(false);
        	right.setText("-1");
        	right.setEnabled(false);
		}
        else if (position.equals(mActivity.getString(R.string.lowerRight)))
		{
        	bottom.setText("0");
        	bottom.setEnabled(true);
        	right.setText("0");
        	right.setEnabled(true);
        	
        	top.setText("-1");
        	top.setEnabled(false);
        	left.setText("-1");
        	left.setEnabled(false);
		}
        else //center
		{
        	top.setText("-1");
        	top.setEnabled(false);
        	bottom.setText("-1");
        	bottom.setEnabled(false);
        	
        	left.setText("-1");
        	left.setEnabled(false);
        	right.setText("-1");
        	right.setEnabled(false);
		}
    }
}