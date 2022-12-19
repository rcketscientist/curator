package com.anthonymandra.curator.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.anthonymandra.curator.R

class ViewSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_view, rootKey)
    }

    companion object {
        const val KEY_ShowImageInterface = "prefKeyShowImageInterface"
        const val KEY_ShowNav = "prefKeyShowNav"
        const val KEY_ShowMeta = "prefKeyShowMeta"
        const val KEY_ShowHist = "prefKeyShowHist"
        const val KEY_ShowToolbar = "prefKeyShowToolbar"
    }
}
