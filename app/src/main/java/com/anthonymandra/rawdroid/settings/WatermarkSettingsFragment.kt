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
    private lateinit var prefWatermarkLocations: Array<String>
    private lateinit var sharedPreferences: SharedPreferences
    private var top: EditTextPreference? = null
    private var bottom: EditTextPreference? = null
    private var left: EditTextPreference? = null
    private var right: EditTextPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_watermark, rootKey)
        prefWatermarkLocations = resources.getStringArray(R.array.watermarkLocations)
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
            "enableWatermark" -> updateWatermarkEnabled()
            "watermarkLocation" -> updateWatermarkLocation()
            "watermarkTopMargin",
            "watermarkBottomMargin",
            "watermarkLeftMargin",
            "watermarkRightMargin" -> updateWatermarkMargins()
        }
    }

    private fun updateWatermarkOptions() {
        updateWatermarkEnabled()
        updateWatermarkLocation()
        updateWatermarkMargins()
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

    private fun updateWatermarkMargins() {
        // Clean up disabled (-1) values
        if (top?.text != null) {
            val topValue = if (top?.text == "-1") "" else ": " + top?.text
            top?.title = getString(R.string.prefTitleTopMargin) + topValue
        }
        if (bottom?.text != null) {
            val bottomValue = if (bottom?.text == "-1") "" else ": " + bottom?.text
            bottom?.title = getString(R.string.prefTitleBottomMargin) + bottomValue
        }
        if (left?.text != null) {
            val leftValue = if (left?.text == "-1") "" else ": " + left?.text
            left?.title = getString(R.string.prefTitleLeftMargin) + leftValue
        }
        if (right?.text != null) {
            val rightValue = if (right?.text == "-1") "" else ": " + right?.text
            right?.title = getString(R.string.prefTitleRightMargin) + rightValue
        }
    }

    private fun updateWatermarkLocation() {
        val location: ListPreference? = findPreference(KEY_WatermarkLocation)
        val position = sharedPreferences.getString(KEY_WatermarkLocation, "Center")
        location?.summary = translateWatermarkLocations(position)

        when (position) {
            getString(R.string.upperLeft) -> {
                top?.text = "0"
                top?.isEnabled = true
                left?.text = "0"
                left?.isEnabled = true
                bottom?.text = "-1"
                bottom?.isEnabled = false
                right?.text = "-1"
                right?.isEnabled = false
            }
            getString(R.string.upperRight) -> {
                top?.text = "0"
                top?.isEnabled = true
                right?.text = "0"
                right?.isEnabled = true
                bottom?.text = "-1"
                bottom?.isEnabled = false
                left?.text = "-1"
                left?.isEnabled = false
            }
            getString(R.string.lowerLeft) -> {
                bottom?.text = "0"
                bottom?.isEnabled = true
                left?.text = "0"
                left?.isEnabled = true
                top?.text = "-1"
                top?.isEnabled = false
                right?.text = "-1"
                right?.isEnabled = false
            }
            getString(R.string.lowerRight) -> {
                bottom?.text = "0"
                bottom?.isEnabled = true
                right?.text = "0"
                right?.isEnabled = true
                top?.text = "-1"
                top?.isEnabled = false
                left?.text = "-1"
                left?.isEnabled = false
            }
            else -> {  //center
                top?.text = "-1"
                top?.isEnabled = false
                bottom?.text = "-1"
                bottom?.isEnabled = false
                left?.text = "-1"
                left?.isEnabled = false
                right?.text = "-1"
                right?.isEnabled = false
            }
        }
    }

    private fun translateWatermarkLocations(result: String?): String {
        return when (result) {
            "Lower Left" -> prefWatermarkLocations[1]
            "Lower Right" -> prefWatermarkLocations[2]
            "Upper Left" -> prefWatermarkLocations[3]
            "Upper Right" -> prefWatermarkLocations[4]
            else -> prefWatermarkLocations[0]
        }
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
