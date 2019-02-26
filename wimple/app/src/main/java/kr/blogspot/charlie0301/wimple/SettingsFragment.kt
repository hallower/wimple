package kr.blogspot.charlie0301.wimple

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference.OnPreferenceChangeListener
import android.support.v7.preference.Preference.OnPreferenceClickListener
import android.support.v7.preference.PreferenceFragmentCompat
import android.webkit.CookieManager
import android.widget.Toast
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.model.Section
import java.lang.ref.WeakReference

class SettingsFragment : PreferenceFragmentCompat(), IWimpleFragment {
    private val wimple = WimpleImpl.getInstance()

    private var wimpleActivity: WeakReference<WimpleActivity>? = null
    internal lateinit var listSections: ListPreference

    override fun onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {

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
            }

            //System.runFinalizersOnExit(true);
            //System.exit(0);

            val intent = Intent(context, SplashScreenActivity::class.java)
            intent.putExtra("auth_again", "")
            startActivity(intent)
            activity!!.finish()

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
                var i = 0
                var idx = 0
                for (section in list) {
                    entries[i] = section.title
                    if (null != wimple.defaultSectionID && 0 == section.id.compareTo(wimple.defaultSectionID)) {
                        idx = i
                    }
                    entryValues[i] = section.id
                    i++
                }
                listSections.entries = entries
                listSections.entryValues = entryValues
                listSections.setValueIndex(idx)
                listSections.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                    if (0 == newValue.toString().compareTo(listSections.value)) {
                        return@OnPreferenceChangeListener false
                    }

                    val _idx = listSections.findIndexOfValue(newValue.toString())
                    val _entries = listSections.entries
                    if (-1 == _idx)
                        return@OnPreferenceChangeListener false

                    wimple.defaultSectionID = newValue.toString()
                    wimple.defaultSectionName = _entries[idx].toString()
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

        private const val LOG_TAG = "SettingsFragment"

        const val KEY_MONTHLY_ITEM_COUNT = "pref_monthlyItemCount"
        const val KEY_MONTHLY_ITEM_DISPLAY = "pref_monthlyItemDisplay"
        const val KEY_FINANCIAL_STATE_AUTO_REFRESH = "pref_financialStateAutoRefresh"
        const val KEY_FINANCIAL_STATE_SHOW_GROUP = "pref_financialStateShowGroup"
        const val KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH = "pref_incomeExpenseStateAutoRefresh"
        const val KEY_DISABLE_MEMO = "pref_disableMemo"
        const val KEY_INCOME_EXPENSE_ENABLE_BUDGET = "pref_incomeExpenseStateEnableBudget"
        const val KEY_INCOME_EXPENSE_SHOW_GROUP = "pref_incomeExpenseStateShowGroup"
    }
}
