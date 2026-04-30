package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class FinancialStateSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_financial_state)
    }
}
