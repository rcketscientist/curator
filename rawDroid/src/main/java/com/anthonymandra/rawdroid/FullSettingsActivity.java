package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
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
import android.support.design.widget.Snackbar;

import com.anthonymandra.framework.License;
import com.anthonymandra.framework.Util;
import com.anthonymandra.util.ImageUtil;
import com.anthonymandra.widget.SeekBarPreference;

import java.util.List;

public class FullSettingsActivity extends PreferenceActivity
{
	public static final int REQUEST_CODE_PICK_KEYWORD_FILE = 1;

	public static final String KEY_ShowImageInterface = "prefKeyShowImageInterface";
	public static final String KEY_ShowNav = "prefKeyShowNav";
	public static final String KEY_ResetSaveDefault = "prefKeyResetSaveDefault";
	public static final String KEY_DefaultSaveType = "prefKeyDefaultSaveType";
	public static final String KEY_DefaultSaveConfig = "prefKeyDefaultSaveConfig";
	public static final String KEY_RecycleBinSize = "prefKeyRecycleBinSize";
	public static final String KEY_DeleteConfirmation = "prefKeyDeleteConfirmation";
	public static final String KEY_UseRecycleBin = "prefKeyUseRecycleBin";
	public static final String KEY_ImportKeywords = "prefKeyImportKeywords";
	public static final String KEY_ShowMeta = "prefKeyShowMeta";
	public static final String KEY_ShowHist = "prefKeyShowHist";
    public static final String KEY_ShowToolbar = "prefKeyShowToolbar";
	public static final String KEY_XmpRed = "prefKeyXmpRed";
	public static final String KEY_XmpBlue = "prefKeyXmpBlue";
	public static final String KEY_XmpGreen = "prefKeyXmpGreen";
	public static final String KEY_XmpYellow = "prefKeyXmpYellow";
	public static final String KEY_XmpPurple = "prefKeyXmpPurple";
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

	public static final String KEY_ShareFormat = "prefKeyShareFormat";
	public static final String KEY_EditFormat = "prefKeyEditFormat";

	public static final String KEY_MetaSize = "prefKeyMetaSize";

	public static final int defRecycleBin = 50;

	private static final int minRecycleBin = 30;
	private static final int maxRecycleBin = 500;

	private static String[] prefShowOptions;
    private static String[] prefWatermarkLocations;

    @Override
    protected boolean isValidFragment (String fragmentName) {
        return
        SettingsFragmentLicense.class.getName().equals(fragmentName) ||
        SettingsFragmentMeta.class.getName().equals(fragmentName) ||
        SettingsFragmentStorage.class.getName().equals(fragmentName) ||
        SettingsFragmentView.class.getName().equals(fragmentName) ||
        SettingsFragmentWatermark.class.getName().equals(fragmentName) ||
	    SettingsFragmentShare.class.getName().equals(fragmentName);
    }

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        prefShowOptions = getResources().getStringArray(R.array.showOptions);
        prefWatermarkLocations = getResources().getStringArray(R.array.watermarkLocations);
	}

	@Override
	public void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_CODE_PICK_KEYWORD_FILE:
				if (resultCode == RESULT_OK && data != null)
				{
					handleKeywordResult(data.getData());
				}
				break;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onBuildHeaders(List<Header> target)
	{
		loadHeadersFromResource(R.xml.preference_headers, target);
	}

    private void handleKeywordResult(final Uri keywordUri)
	{
		ImageUtil.importKeywords(this, keywordUri);
	}

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentStorage extends PreferenceFragment
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
	        Preference button = getPreferenceManager().findPreference(KEY_ResetSaveDefault);
	        if (button != null)
	        {
		        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		        {
			        @Override
			        public boolean onPreferenceClick(Preference arg0)
			        {
			        SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
			        editor.remove(KEY_DefaultSaveConfig);
			        editor.remove(KEY_DefaultSaveType);
			        editor.apply();

				    if (getView() != null)
			            Snackbar.make(getView(), "Save default cleared!", Snackbar.LENGTH_SHORT ).show();
			        return true;
		        }
	        });
	        }
        }
    }

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentMeta extends PreferenceFragment
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
            Preference button = getPreferenceManager().findPreference(KEY_ImportKeywords);
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

		private void requestImportKeywords()
        {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.setType("text/plain");
			getActivity().startActivityForResult(intent, REQUEST_CODE_PICK_KEYWORD_FILE);
        }
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentView extends PreferenceFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences_view);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class SettingsFragmentShare extends PreferenceFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences_share);
		}
	}

	public static int getMetaStyle(Context c)
	{
		String styleName = PreferenceManager.getDefaultSharedPreferences(c).getString(KEY_MetaSize, "Medium");

		if ("Small".equals(styleName))
		{
			return R.style.MetaStyle_Small;
		}
		else if ("Large".equals(styleName))
		{
			return R.style.MetaStyle_Large;
		}
		else
		{
			return R.style.MetaStyle_Medium;
		}
	}

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static abstract class SettingFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
    {
	    protected PreferenceManager mPreferenceManager;
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
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SettingsFragmentLicense extends SettingFragment
    {
	    final Handler mLicenseHandler = new Handler(new Handler.Callback()
	    {
		    @Override
		    public boolean handleMessage(Message message)
		    {
			    License.LicenseState state = (License.LicenseState) message.getData().getSerializable(License.KEY_LICENSE_RESPONSE);
			    updateLicense(state);
			    return true;
		    }
	    });

	    @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_license);
        }

        @Override
        public void onResume() {
            super.onResume();
            mPreferenceManager = getPreferenceManager();
            LicenseManager.getLicense(getActivity(), mLicenseHandler);
            attachLicenseButtons();
        }

	    /**
	     * Attaches meta related buttons.
	     */
	    private void attachLicenseButtons()
	    {
		    Preference manual = mPreferenceManager.findPreference(KEY_ManualLicense);
		    if (manual != null)
		    {
			    manual.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			    {
				    @Override
				    public boolean onPreferenceClick(Preference arg0)
				    {
					    LicenseManager.getLicense(getActivity(), mLicenseHandler);

					    if (getView() != null)
					        Snackbar.make(getView(), R.string.licenseRequestSent, Snackbar.LENGTH_SHORT).show();
					    return true;
				    }
			    });
		    }

		    Preference email = mPreferenceManager.findPreference(KEY_Contact);
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

	    private void requestEmail()
	    {
		    Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		    emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"rawdroid@anthonymandra.com"});
		    emailIntent.setType("plain/text");
		    getActivity().startActivity(Intent.createChooser(emailIntent, "Send email..."));
	    }

	    private void updateLicense(License.LicenseState state)
	    {
		    Preference license = mPreferenceManager.findPreference(KEY_license);

		    // This might happen if the user switches tabs quickly while looking up license
		    if (license == null)
			    return;

		    license.setTitle(state.toString());
		    switch (state)
		    {
			    case error:
				    license.setSummary(getActivity().getString(R.string.prefSummaryLicenseError));
				    setBuyButton();
				    break;
			    case pro: license.setSummary(getActivity().getString(R.string.prefSummaryLicense));
				    break;
			    case demo: license.setSummary(getActivity().getString(R.string.buypro));
				    setBuyButton();
				    break;
			    case modified_0x000:
			    case modified_0x001:
			    case modified_0x002:
			    case modified_0x003:
				    license.setSummary(R.string.prefSummaryLicenseModified);
				    setBuyButton();
				    break;
			    default: license.setSummary(getActivity().getString(R.string.prefSummaryLicenseError) + "\n" +
					    getActivity().getString(R.string.buypro));
				    setBuyButton();
		    }
	    }

	    private void setBuyButton()
	    {
		    Preference license = mPreferenceManager.findPreference(KEY_license);
		    if (license != null)
		    {
			    license.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			    {
				    @Override
				    public boolean onPreferenceClick(Preference arg0)
				    {
					    Intent store = Util.getStoreIntent(getActivity(), "com.anthonymandra.rawdroidpro");
					    if (store != null)
						    getActivity().startActivity(store);
					    return true;
				    }
			    });
		    }
	    }

	    @Override
	    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s)
	    {
		    // do nothing
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

	    @Override
	    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	    {
		    switch (key)
		    {
			    case KEY_EnableWatermark:
				    updateWatermarkEnabled();
				    break;
			    case KEY_WatermarkLocation:
				    updateWatermarkLocation();
				    break;
			    case KEY_WatermarkTopMargin:
			    case KEY_WatermarkBottomMargin:
			    case KEY_WatermarkLeftMargin:
			    case KEY_WatermarkRightMargin:
				    updateWatermarkMargins();
				    break;
		    }
	    }

	    private void updateWatermarkOptions()
	    {
		    updateWatermarkEnabled();
		    updateWatermarkLocation();
		    updateWatermarkMargins();
	    }

	    private void updateWatermarkEnabled()
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

	    private void updateWatermarkMargins()
	    {
		    EditTextPreference top = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkTopMargin);
		    EditTextPreference bottom = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkBottomMargin);
		    EditTextPreference left = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkLeftMargin);
		    EditTextPreference right = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkRightMargin);

		    // Clean up disabled (-1) values
		    if (top.getText() != null)
		    {
			    String topValue = top.getText().equals("-1") ? "" : ": " + top.getText();
			    top.setTitle(getActivity().getString(R.string.prefTitleTopMargin) + topValue);
		    }

		    if (bottom.getText() != null)
		    {
			    String bottomValue = bottom.getText().equals("-1") ? "" : ": " + bottom.getText();
			    bottom.setTitle(getActivity().getString(R.string.prefTitleBottomMargin) + bottomValue);
		    }

		    if (left.getText() != null)
		    {
			    String leftValue = left.getText().equals("-1") ? "" : ": " + left.getText();
			    left.setTitle(getActivity().getString(R.string.prefTitleLeftMargin) + leftValue);
		    }

		    if (right.getText() != null)
		    {
			    String rightValue = right.getText().equals("-1") ? "" : ": " + right.getText();
			    right.setTitle(getActivity().getString(R.string.prefTitleRightMargin) + rightValue);
		    }
	    }

	    private void updateWatermarkLocation()
	    {
		    ListPreference location = (ListPreference) mPreferenceManager.findPreference(KEY_WatermarkLocation);
		    String position = mPreferenceManager.getSharedPreferences().getString(KEY_WatermarkLocation, "Center");
		    location.setSummary(translateWatermarkLocations(position));
		    EditTextPreference top = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkTopMargin);
		    EditTextPreference bottom = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkBottomMargin);
		    EditTextPreference left = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkLeftMargin);
		    EditTextPreference right = (EditTextPreference) mPreferenceManager.findPreference(KEY_WatermarkRightMargin);

		    if (position.equals(getActivity().getString(R.string.upperLeft)))
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
		    else if (position.equals(getActivity().getString(R.string.upperRight)))
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
		    else if (position.equals(getActivity().getString(R.string.lowerLeft)))
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
		    else if (position.equals(getActivity().getString(R.string.lowerRight)))
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

	    private String translateWatermarkLocations(String result)
	    {
		    switch (result)
		    {
			    case "Lower Left":
				    return prefWatermarkLocations[1];
			    case "Lower Right":
				    return prefWatermarkLocations[2];
			    case "Upper Left":
				    return prefWatermarkLocations[3];
			    case "Upper Right":
				    return prefWatermarkLocations[4];
			    default:
				    return prefWatermarkLocations[0];
		    }
	    }
    }
}