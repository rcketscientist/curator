package com.anthonymandra.curator.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.anthonymandra.curator.R

class MainSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
    }
}
