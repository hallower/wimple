package kr.blogspot.charlie0301.wimple

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import android.webkit.CookieManager
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.model.Section
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class SettingsFragment : PreferenceFragmentCompat(), IWimpleFragment {
    private val wimple = WimpleImpl.getInstance()

    private var wimpleActivity: WeakReference<WimpleActivity>? = null
    private lateinit var listSections: ListPreference


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        wimple.getAllSections(true)

        addPreferencesFromResource(R.xml.settings)

        listSections = findPreference("preference_sections") as ListPreference
        run {
            val entries = arrayOfNulls<String>(1)
            val entryValues = arrayOfNulls<String>(1)
            entries[0] = "Please wait for seconds"
            entryValues[0] = "Please wait for seconds"
            listSections.entries = entries
            listSections.entryValues = entryValues
            listSections.setValueIndex(0)
        }

        val listPages = findPreference("preference_floating_button") as MultiSelectListPreference
        run {
            val entries = arrayOf<String>(
                    resources.getString(R.string.title_transaction_insert_fragment),
                    resources.getString(R.string.title_transaction_list_fragment),
                    resources.getString(R.string.drawer_menu_financial_state),
                    resources.getString(R.string.title_saving),
                    resources.getString(R.string.title_debt),
                    resources.getString(R.string.drawer_menu_income_expense),
                    resources.getString(R.string.title_income),
                    resources.getString(R.string.title_expense))
            val entryValues: Array<String> = arrayOf(
                    "1menu_transaction_insert",
                    "2menu_transaction_list",
                    "3menu_financial_overview",
                    "4menu_saving",
                    "5menu_debt",
                    "6menu_income_expense_overview",
                    "7menu_income",
                    "8menu_expense")
            listPages.entries = entries
            listPages.entryValues = entryValues
            /*
            listPages.setOnPreferenceChangeListener { preference, newValue ->
                Log.e(LOG_TAG, "newValue = ${newValue.toString()}")
                true
            }
            */
        }

        val logout = findPreference("preference_logout")
        logout.onPreferenceClickListener = OnPreferenceClickListener {
            wimple.cleanAuth()
            wimple.clearAllDBRecords()

            if (context != null) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.removeAllCookies(null)
                cookieManager.flush()

                context!!.deleteDatabase("webview.db")
                context!!.deleteDatabase("webviewCache.db")

                // clear biometric option
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(context!!)
                sharedPref.edit().putBoolean(KEY_BIOMETRIC_OPTION, false).apply()
            }

            //System.runFinalizersOnExit(true);
            //System.exit(0);

            val intent = Intent(context, SplashScreenActivity::class.java)
            intent.putExtra("auth_again", "")
            startActivity(intent)
            activity!!.finish()

            false
        }

        val biometricCheckBox = findPreference("pref_enableBiometricSignIn") as CheckBoxPreference
        biometricCheckBox.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(resources.getString(R.string.biometric_title))
                    .setSubtitle(resources.getString(R.string.biometric_option_description))
                    .setNegativeButtonText(resources.getString(R.string.user_cancel))
                    .build()
            val toBe = newValue as Boolean
            val biometricPrompt = BiometricPrompt(this, Executors.newSingleThreadExecutor(),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int,
                                                           errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            activity?.runOnUiThread {
                                Toast.makeText(activity?.applicationContext, errString, Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            activity?.runOnUiThread{
                                (preference as CheckBoxPreference).isChecked = toBe
                            }
                        }

                    })

            biometricPrompt.authenticate(promptInfo)
            false
        }
    }

    override fun handleMessage(msg: Message) {
        val command = msg.what
        val booleanStatus = msg.arg1 == 1
        val obj = msg.obj

        // if fragment is added or not to the activity
        if (!isAdded) {
            return
        }

        if (null == context) {
            return
        }

        when (command) {

            CommandID.GET_ALL_SECTION_RECEIVED -> {

                @Suppress("UNCHECKED_CAST") val list = obj as Collection<Section>
                if (list.isEmpty()) {
                    return
                }

                val entries = arrayOfNulls<String>(list.size)
                val entryValues = arrayOfNulls<String>(list.size)
                var idx = 0

                list.forEachIndexed { index, section ->
                    entries[index] = section.title
                    if (null != wimple.defaultSectionID && 0 == section.id.compareTo(wimple.defaultSectionID)) {
                        idx = index
                    }
                    entryValues[index] = section.id
                }

                listSections.entries = entries
                listSections.entryValues = entryValues
                listSections.setValueIndex(idx)
                listSections.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                    if (0 == newValue.toString().compareTo(listSections.value)) {
                        return@OnPreferenceChangeListener false
                    }

                    val selectedIdx = listSections.findIndexOfValue(newValue.toString())
                    val tmpEntries = listSections.entries
                    if (-1 == selectedIdx)
                        return@OnPreferenceChangeListener false

                    wimple.defaultSectionID = newValue.toString()
                    wimple.defaultSectionName = tmpEntries[selectedIdx].toString()
                    wimple.clearAllDBRecords()

                    if (context != null) {
                        val settings = context!!.getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE)
                        settings.edit().putString("section_id", wimple.defaultSectionID).apply()
                        settings.edit().putString("section_name", wimple.defaultSectionName).apply()
                    }

                    val intent = Intent(context, SplashScreenActivity::class.java)
                    startActivity(intent)
                    activity!!.finish()

                    false
                }
            }

            CommandID.POST_PAYMENT_RESPONSE_RECEIVED -> {
                if (booleanStatus) {
                    Toast.makeText(context, resources.getString(R.string.settings_sms_send_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, resources.getString(R.string.settings_sms_send_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {
        wimpleActivity = WeakReference(instance)
    }

    companion object {

        //private const val LOG_TAG = "SettingsFragment"

        const val KEY_MONTHLY_ITEM_COUNT = "pref_monthlyItemCount"
        const val KEY_MONTHLY_ITEM_DISPLAY = "pref_monthlyItemDisplay"
        const val KEY_FINANCIAL_STATE_AUTO_REFRESH = "pref_financialStateAutoRefresh"
        const val KEY_FINANCIAL_STATE_SHOW_GROUP = "pref_financialStateShowGroup"
        const val KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH = "pref_incomeExpenseStateAutoRefresh"
        const val KEY_DISABLE_MEMO = "pref_disableMemo"
        const val KEY_INCOME_EXPENSE_ENABLE_BUDGET = "pref_incomeExpenseStateEnableBudget"
        const val KEY_INCOME_EXPENSE_SHOW_GROUP = "pref_incomeExpenseStateShowGroup"
        const val KEY_FLOATING_BUTTON = "preference_floating_button"
        const val KEY_BIOMETRIC_OPTION = "pref_enableBiometricSignIn"
    }
}
