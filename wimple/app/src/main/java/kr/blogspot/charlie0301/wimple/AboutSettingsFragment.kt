package kr.blogspot.charlie0301.wimple

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class AboutSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_about)

        findPreference<Preference>("pref_contact")?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false
            val url = ctx.getString(R.string.settings_contact_url)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, R.string.settings_contact_open_failed, Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<Preference>("pref_opensourceLicenses")?.setOnPreferenceClickListener {
            startActivity(Intent(context, OpenSourceLicensesActivity::class.java))
            true
        }
    }
}
