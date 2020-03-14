package com.anthonymandra.rawdroid.settings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anthonymandra.framework.License
import com.anthonymandra.framework.Util
import com.anthonymandra.rawdroid.LicenseManager
import com.anthonymandra.rawdroid.R
import com.google.android.material.snackbar.Snackbar

class LicenseSettingsFragment : PreferenceFragmentCompat() {
    var license: Preference? = null

    private val mLicenseHandler = Handler(Handler.Callback { message ->
        val state = message.data.getSerializable(License.KEY_LICENSE_RESPONSE) as License.LicenseState
        val license: Preference? = findPreference(KEY_license)

        // This might happen if the user switches tabs quickly while looking up license
        license?.title = state.toString()
        when (state) {
            License.LicenseState.error -> {
                license?.summary = activity?.getString(R.string.prefSummaryLicenseError)
                setBuyButton()
            }
            License.LicenseState.pro -> license?.summary = activity?.getString(R.string.prefSummaryLicense)
            License.LicenseState.demo -> {
                license?.summary = activity?.getString(R.string.buypro)
                setBuyButton()
            }
            License.LicenseState.modified_0x000,
            License.LicenseState.modified_0x001,
            License.LicenseState.modified_0x002,
            License.LicenseState.modified_0x003 -> {
                license?.setSummary(R.string.prefSummaryLicenseModified)
                setBuyButton()
            }
            else -> {
                license?.summary = """
                        ${activity?.getString(R.string.prefSummaryLicenseError)}
                        ${activity?.getString(R.string.buypro)}
                        """.trimIndent()
                setBuyButton()
            }
        }
        true
    })

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_license, rootKey)
        license = findPreference(KEY_license)

        val manual: Preference? = findPreference(KEY_ManualLicense)
        manual?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            LicenseManager.getLicense(activity, mLicenseHandler)
            view?.let{
                Snackbar.make(it, R.string.licenseRequestSent, Snackbar.LENGTH_SHORT).show()
            }
            true
        }

        val email: Preference? = findPreference(KEY_Contact)
        email?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val emailIntent = Intent(Intent.ACTION_SEND)
            emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("rawdroid@anthonymandra.com"))
            emailIntent.type = "plain/text"
            activity?.startActivity(Intent.createChooser(emailIntent, "Send email..."))
            true
        }
    }

    private fun setBuyButton() {
        license?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val store = Util.getStoreIntent(activity, "com.anthonymandra.rawdroidpro")
            if (store != null) activity?.startActivity(store)
            true
        }
    }

    companion object {
        const val KEY_license = "prefKeyLicense"
        const val KEY_ManualLicense = "prefKeyManualLicense"
        const val KEY_Contact = "prefKeyContact"
    }
}
