package com.anthonymandra.rawdroid.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.anthonymandra.framework.License
import com.anthonymandra.rawdroid.Constants
import com.anthonymandra.rawdroid.LicenseManager
import com.anthonymandra.rawdroid.R

class WatermarkSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var sharedPreferences: SharedPreferences
    private var top: EditTextPreference? = null
    private var bottom: EditTextPreference? = null
    private var left: EditTextPreference? = null
    private var right: EditTextPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_watermark, rootKey)
        sharedPreferences = getDefaultSharedPreferences(context)

        top = findPreference(KEY_WatermarkTopMargin)
        bottom = findPreference(KEY_WatermarkBottomMargin)
        left = findPreference(KEY_WatermarkLeftMargin)
        right = findPreference(KEY_WatermarkRightMargin)
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        updateWatermarkOptions()
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            KEY_EnableWatermark -> updateWatermarkEnabled()
            KEY_WatermarkLocation -> updateWatermarkLocation()
        }
    }

    private fun updateWatermarkOptions() {
        updateWatermarkEnabled()
        updateWatermarkLocation()
    }

    private fun updateWatermarkEnabled() {
        val isLicensed = Constants.VariantCode > 8 && LicenseManager.getLastResponse() == License.LicenseState.pro
        val enableWatermark: CheckBoxPreference? = findPreference(KEY_EnableWatermark)
        enableWatermark?.isEnabled = isLicensed
        if (!isLicensed) {
            enableWatermark?.isChecked = false
        } else {
            enableWatermark?.isChecked = sharedPreferences.getBoolean(KEY_EnableWatermark, false)
        }
    }

    private fun updateWatermarkLocation() {
        when (sharedPreferences.getString(KEY_WatermarkLocation, "Center")) {
            "Upper Left" -> {
                resetMargin(top, true)
                resetMargin(bottom, false)
                resetMargin(left, true)
                resetMargin(right, false)
            }
            "Upper Right" -> {
                resetMargin(top, true)
                resetMargin(bottom, false)
                resetMargin(left, false)
                resetMargin(right, true)
            }
            "Lower Left" -> {
                resetMargin(top, false)
                resetMargin(bottom, true)
                resetMargin(left, true)
                resetMargin(right, false)
            }
            "Lower Right" -> {
                resetMargin(top, false)
                resetMargin(bottom, true)
                resetMargin(left, false)
                resetMargin(right, true)
            }
            else -> {  //center
                resetMargin(top, false)
                resetMargin(bottom, false)
                resetMargin(left, false)
                resetMargin(right, false)
            }
        }
    }

    private fun resetMargin(margin: EditTextPreference?, enable: Boolean) {
        margin?.text = if (enable) "0" else "-1"
        margin?.isVisible = enable
    }

    companion object {
        const val KEY_EnableWatermark = "prefKeyEnableWatermark"
        const val KEY_WatermarkText = "prefKeyWatermarkText"
        const val KEY_WatermarkSize = "prefKeyWatermarkSize"
        const val KEY_WatermarkLocation = "prefKeyWatermarkLocation"
        const val KEY_WatermarkAlpha = "prefKeyWatermarkAlpha"
        const val KEY_WatermarkTopMargin = "prefKeyWatermarkTopMargin"
        const val KEY_WatermarkBottomMargin = "prefKeyWatermarkBottomMargin"
        const val KEY_WatermarkLeftMargin = "prefKeyWatermarkLeftMargin"
        const val KEY_WatermarkRightMargin = "prefKeyWatermarkRightMargin"
    }
}
