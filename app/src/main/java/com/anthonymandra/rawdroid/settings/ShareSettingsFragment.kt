package com.anthonymandra.rawdroid.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.anthonymandra.rawdroid.R

class ShareSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_share, rootKey)
    }

    companion object {
        const val KEY_ShareFormat = "prefKeyShareFormat"
        const val KEY_EditFormat = "prefKeyEditFormat"
    }
}
