package kr.blogspot.charlie0301.wimple

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kr.blogspot.charlie0301.wimple.impl.BankNotifications
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.model.Section
import java.util.concurrent.Executors

/**
 * "일반" sub-screen of the host settings two-pane layout. Owns logout, section switcher,
 * floating-button picker, and biometric toggle. Receives [CommandID.GET_ALL_SECTION_RECEIVED]
 * via [SettingsHostFragment] to populate the section list once the server response arrives.
 */
class GeneralSettingsFragment : PreferenceFragmentCompat(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()
    private lateinit var listSections: ListPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        wimple.getAllSections(true)

        addPreferencesFromResource(R.xml.settings_general)

        listSections = findPreference("preference_sections")!!
        run {
            val placeholder = arrayOf<CharSequence>("Please wait for seconds")
            listSections.entries = placeholder
            listSections.entryValues = placeholder
            listSections.setValueIndex(0)
        }

        val listPages = findPreference<MultiSelectListPreference>("preference_floating_button")!!
        run {
            val entries = arrayOf(
                resources.getString(R.string.title_transaction_insert_fragment),
                resources.getString(R.string.title_transaction_list_fragment),
                resources.getString(R.string.drawer_menu_financial_state),
                resources.getString(R.string.title_saving),
                resources.getString(R.string.title_debt),
                resources.getString(R.string.drawer_menu_income_expense),
                resources.getString(R.string.title_income),
                resources.getString(R.string.title_expense)
            )
            val entryValues = arrayOf(
                "1menu_transaction_insert",
                "2menu_transaction_list",
                "3menu_financial_overview",
                "4menu_saving",
                "5menu_debt",
                "6menu_income_expense_overview",
                "7menu_income",
                "8menu_expense"
            )
            listPages.entries = entries
            listPages.entryValues = entryValues
        }

        findPreference<Preference>("preference_logout")?.onPreferenceClickListener = OnPreferenceClickListener {
            wimple.cleanAuth()
            wimple.clearAllDBRecords()

            if (context != null) {
                val ctx = requireContext()

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.removeAllCookies(null)
                cookieManager.flush()

                ctx.deleteDatabase("webview.db")
                ctx.deleteDatabase("webviewCache.db")

                // Wipe ALL user preferences so the next account starts at the
                // XML-declared defaults. Selective clears (the old biometric-only
                // path) leak per-account state into the next session — most
                // notably pref_bankNotiEnable (forward) / pref_bankNotiApps, which would
                // keep capturing notifications under the previous user's setup.
                PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().apply()
                // Re-seed XML defaultValue declarations across every settings sub-screen
                // (bank-noti threshold/sort/toast, monthly-item count, financial-state
                // auto-refresh, floating button rotation, …) so the new session reads
                // documented defaults rather than each call site's hard-coded fallback.
                PreferenceManager.setDefaultValues(ctx, R.xml.settings_general, true)
                PreferenceManager.setDefaultValues(ctx, R.xml.settings_bank_noti, true)
                PreferenceManager.setDefaultValues(ctx, R.xml.settings_entry, true)
                PreferenceManager.setDefaultValues(ctx, R.xml.settings_financial_state, true)
                PreferenceManager.setDefaultValues(ctx, R.xml.settings_income_expense, true)

                BankNotifications.clear(ctx)
                // Wipe every local user-specific store so prior-user data doesn't leak into
                // the next account. Default-prefs clear above already drops the dev unlock
                // flag and review-queue toggle; the stores below live in their own files
                // (review queue / AI log SharedPreferences, plus two SQLite DBs for the
                // mapping cache and extraction shots) and need explicit cleanup.
                kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue.clear(ctx)
                kr.blogspot.charlie0301.wimple.impl.AiClassificationLog.clear(ctx)
                kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler(ctx).clear()
                kr.blogspot.charlie0301.wimple.impl.db.ExtractionExampleDBHandler(ctx).clear()
            }

            val intent = Intent(context, SplashScreenActivity::class.java)
            intent.putExtra("auth_again", "")
            startActivity(intent)
            requireActivity().finish()

            false
        }

        findPreference<CheckBoxPreference>("pref_enableBiometricSignIn")?.onPreferenceChangeListener =
            OnPreferenceChangeListener { preference, newValue ->
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(resources.getString(R.string.biometric_title))
                    .setSubtitle(resources.getString(R.string.biometric_option_description))
                    .setNegativeButtonText(resources.getString(R.string.user_cancel))
                    .build()
                val toBe = newValue as Boolean
                val biometricPrompt = BiometricPrompt(this, Executors.newSingleThreadExecutor(),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            activity?.runOnUiThread {
                                Toast.makeText(activity?.applicationContext, errString, Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            activity?.runOnUiThread {
                                (preference as CheckBoxPreference).isChecked = toBe
                            }
                        }
                    })
                biometricPrompt.authenticate(promptInfo)
                false
            }
    }

    override fun handleMessage(msg: Message) {
        if (!isAdded || context == null) return

        when (msg.what) {
            CommandID.GET_ALL_SECTION_RECEIVED -> {
                @Suppress("UNCHECKED_CAST") val list = msg.obj as Collection<Section>
                if (list.isEmpty()) return

                val entries = arrayOfNulls<String>(list.size)
                val entryValues = arrayOfNulls<String>(list.size)
                var idx = 0
                list.forEachIndexed { index, section ->
                    entries[index] = section.title
                    if (wimple.defaultSectionID != null && section.id.compareTo(wimple.defaultSectionID) == 0) {
                        idx = index
                    }
                    entryValues[index] = section.id
                }

                listSections.entries = entries
                listSections.entryValues = entryValues
                listSections.setValueIndex(idx)
                listSections.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                    if (newValue.toString().compareTo(listSections.value) == 0) {
                        return@OnPreferenceChangeListener false
                    }
                    val selectedIdx = listSections.findIndexOfValue(newValue.toString())
                    if (selectedIdx == -1) return@OnPreferenceChangeListener false

                    wimple.defaultSectionID = newValue.toString()
                    wimple.defaultSectionName = listSections.entries[selectedIdx].toString()
                    wimple.clearAllDBRecords()

                    if (context != null) {
                        val settings = requireContext().getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE)
                        settings.edit().putString("section_id", wimple.defaultSectionID).apply()
                        settings.edit().putString("section_name", wimple.defaultSectionName).apply()
                    }

                    val intent = Intent(context, SplashScreenActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                    false
                }
            }

            CommandID.POST_PAYMENT_RESPONSE_RECEIVED -> {
                val ok = msg.arg1 == 1
                val resId = if (ok) R.string.settings_sms_send_success else R.string.settings_sms_send_failed
                Toast.makeText(context, resources.getString(resId), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {
        // No-op: sub-fragments don't track the WimpleActivity directly; the host owns it.
    }
}
