package com.anthonymandra.curator.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anthonymandra.framework.RecycleBin
import com.anthonymandra.curator.R
import com.google.android.material.snackbar.Snackbar

class StorageSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_storage, rootKey)
    }

    override fun onResume() {
        super.onResume()
        val size: Preference? = findPreference(KEY_RecycleBinSize)
        size?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, maxSize: Any ->
            RecycleBin.setMaxSize(maxSize as Int * 1024 * 1024L)
            true
        }

        val button: Preference? = findPreference(KEY_ResetSaveDefault)
        button?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val editor = preferenceManager.sharedPreferences.edit()
            editor.remove(KEY_DefaultSaveConfig)
            editor.remove(KEY_DefaultSaveType)
            editor.apply()
            view?.let {
                Snackbar.make(it, "Save default cleared!", Snackbar.LENGTH_SHORT).show()
            }
            true
        }

    }

    companion object {
        const val KEY_ResetSaveDefault = "prefKeyResetSaveDefault"
        const val KEY_DefaultSaveType = "prefKeyDefaultSaveType"
        const val KEY_DefaultSaveConfig = "prefKeyDefaultSaveConfig"
        const val KEY_RecycleBinSize = "prefKeyRecycleBinSize"
        const val KEY_DeleteConfirmation = "prefKeyDeleteConfirmation"
        const val KEY_UseRecycleBin = "prefKeyUseRecycleBin"
        const val DEFAULT_RECYCLE_BIN = 50
    }
}
