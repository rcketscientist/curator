package com.anthonymandra.rawdroid.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anthonymandra.rawdroid.R
import com.anthonymandra.util.ImageUtil

class MetaSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_metadata, rootKey)
        val importKeywords: Preference? = findPreference(KEY_ImportKeywords)
        importKeywords?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "text/plain"
            startActivityForResult(intent, REQUEST_CODE_PICK_KEYWORD_FILE)
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_PICK_KEYWORD_FILE -> data?.let {
                if (resultCode == Activity.RESULT_OK) {
                    ImageUtil.importKeywords(context, it.data)
                }
            }
        }
    }

    companion object {
        const val REQUEST_CODE_PICK_KEYWORD_FILE = 1

        const val KEY_MetaSize = "prefKeyMetaSize"
        const val KEY_ImportKeywords = "prefKeyImportKeywords"
        const val KEY_ShowMeta = "prefKeyShowMeta"
        const val KEY_ShowHist = "prefKeyShowHist"
        const val KEY_ShowToolbar = "prefKeyShowToolbar"
        const val KEY_XmpRed = "prefKeyXmpRed"
        const val KEY_XmpBlue = "prefKeyXmpBlue"
        const val KEY_XmpGreen = "prefKeyXmpGreen"
        const val KEY_XmpYellow = "prefKeyXmpYellow"
        const val KEY_XmpPurple = "prefKeyXmpPurple"
        const val KEY_UseLegacyViewer = "prefKeyUseLegacyViewer"
        const val KEY_UseImmersive = "prefKeyUseImmersive"
        const val KEY_ExifName = "prefKeyName"
        const val KEY_ExifDate = "prefKeyExifDate"
        const val KEY_ExifModel = "prefKeyExifModel"
        const val KEY_ExifIso = "prefKeyExifIso"
        const val KEY_ExifExposure = "prefKeyExifExposure"
        const val KEY_ExifAperture = "prefKeyExifAperture"
        const val KEY_ExifFocal = "prefKeyExifFocal"
        const val KEY_ExifDimensions = "prefKeyExifDimensions"
        const val KEY_ExifAltitude = "prefKeyExifAltitude"
        const val KEY_ExifFlash = "prefKeyExifFlash"
        const val KEY_ExifLatitude = "prefKeyExifLatitude"
        const val KEY_ExifLongitude = "prefKeyExifLongitude"
        const val KEY_ExifWhiteBalance = "prefKeyExifWhiteBalance"
        const val KEY_ExifLens = "prefKeyExifLens"
        const val KEY_ExifDriveMode = "prefKeyExifDriveMode"
        const val KEY_ExifExposureMode = "prefKeyExifExposureMode"
        const val KEY_ExifExposureProgram = "prefKeyExifExposureProgram"
    }
}
