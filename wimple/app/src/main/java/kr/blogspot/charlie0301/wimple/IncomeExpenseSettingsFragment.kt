package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class IncomeExpenseSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_income_expense)
    }
}
