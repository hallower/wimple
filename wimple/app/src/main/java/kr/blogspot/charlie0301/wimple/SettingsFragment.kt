package kr.blogspot.charlie0301.wimple

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import androidx.preference.PreferenceManager
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
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.BankNotifications
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

        listSections = preferenceScreen.findPreference("preference_sections")!!
        run {
            val entries = arrayOfNulls<String>(1)
            val entryValues = arrayOfNulls<String>(1)
            entries[0] = "Please wait for seconds"
            entryValues[0] = "Please wait for seconds"
            listSections.entries = entries
            listSections.entryValues = entryValues
            listSections.setValueIndex(0)
        }

        val listPages = preferenceScreen.findPreference<MultiSelectListPreference>("preference_floating_button")!!
        run {
            val entries = arrayOf(
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

        val logout = preferenceScreen.findPreference<Preference>("preference_logout")!!
        logout.onPreferenceClickListener = OnPreferenceClickListener {
            wimple.cleanAuth()
            wimple.clearAllDBRecords()

            if (context != null) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.removeAllCookies(null)
                cookieManager.flush()

                requireContext().deleteDatabase("webview.db")
                requireContext().deleteDatabase("webviewCache.db")

                // clear biometric option and onboarding flag
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
                sharedPref.edit()
                    .putBoolean(KEY_BIOMETRIC_OPTION, false)
                    .putBoolean(WimpleActivity.KEY_BIOMETRIC_ONBOARDING_SHOWN, false)
                    .apply()
            }

            //System.runFinalizersOnExit(true);
            //System.exit(0);

            val intent = Intent(context, SplashScreenActivity::class.java)
            intent.putExtra("auth_again", "")
            startActivity(intent)
            requireActivity().finish()

            false
        }

        val biometricCheckBox = preferenceScreen.findPreference<CheckBoxPreference>("pref_enableBiometricSignIn")!!
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

        setupBankNotificationPreferences()
    }

    private fun setupBankNotificationPreferences() {
        val enableBox = preferenceScreen.findPreference<CheckBoxPreference>(
            BankNotificationListener.KEY_BANK_NOTI_ENABLE)!!
        enableBox.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val turningOn = newValue as Boolean
            if (turningOn && context != null &&
                !BankNotificationListener.isNotificationAccessGranted(requireContext())) {
                showNotificationAccessGuideDialog()
            }
            true
        }

        val sendNow = preferenceScreen.findPreference<Preference>(
            BankNotificationListener.KEY_BANK_NOTI_SEND_NOW)!!
        sendNow.onPreferenceClickListener = OnPreferenceClickListener {
            val ctx = context ?: return@OnPreferenceClickListener false
            if (!BankNotifications.hasAnyUnsent(ctx)) {
                Toast.makeText(ctx, R.string.bank_noti_send_none, Toast.LENGTH_SHORT).show()
                return@OnPreferenceClickListener false
            }
            Toast.makeText(ctx, R.string.bank_noti_sending, Toast.LENGTH_SHORT).show()
            BankNotifications.forwardToWhooing(ctx) { success ->
                val msg = if (success) R.string.bank_noti_toast_sent else R.string.bank_noti_toast_send_failed
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
            false
        }

        val viewList = preferenceScreen.findPreference<Preference>(
            BankNotificationListener.KEY_BANK_NOTI_VIEW_LIST)!!
        viewList.onPreferenceClickListener = OnPreferenceClickListener {
            startActivity(Intent(context, BankNotificationListActivity::class.java))
            false
        }

        val addApp = preferenceScreen.findPreference<Preference>(
            BankNotificationListener.KEY_BANK_NOTI_ADD_APP)!!
        addApp.onPreferenceClickListener = OnPreferenceClickListener {
            startActivity(Intent(context, BankAppPickerActivity::class.java))
            false
        }

        refreshBankAppEntries()
    }

    /**
     * Rebuild the MultiSelectListPreference so it shows preset banks plus any custom packages
     * the user added via [BankAppPickerActivity]. Custom entries resolve their display label
     * from PackageManager; uninstalled packages fall back to the raw package name.
     *
     * Respects [BankNotificationListener.KEY_BANK_NOTI_SORT_ORDER]:
     *  - "added" (default): most recently added custom app FIRST (reverse insertion order),
     *    then presets in their declared order. Newly-added apps stay on top so the user can
     *    find them quickly.
     *  - "name": full combined list sorted by display label (locale-insensitive, case-insensitive).
     *
     * Also re-syncs the preference's in-memory [MultiSelectListPreference.getValues] from
     * SharedPreferences — critical because [BankAppPickerActivity] writes directly to the
     * underlying StringSet, which doesn't invalidate the preference's cached `mValues`.
     * Without this, picked apps appear unchecked in the dialog even though they're monitored.
     *
     * Also installs a change listener that removes a user-added custom app from the ordered
     * list when unchecked in the dialog. Unchecking a preset merely stops monitoring it;
     * presets are never removed from the visible list.
     */
    private fun refreshBankAppEntries() {
        val ctx = context ?: return
        val appsPref = preferenceScreen.findPreference<MultiSelectListPreference>(
            BankNotificationListener.KEY_BANK_NOTI_APPS) ?: return

        val presetEntries = resources.getStringArray(R.array.bank_app_entries).toList()
        val presetValues = resources.getStringArray(R.array.bank_app_values).toList()

        val customValues = BankNotifications.getCustomApps(ctx)
        val pm = ctx.packageManager
        val customEntries = customValues.map { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val sortOrder = prefs.getString(BankNotificationListener.KEY_BANK_NOTI_SORT_ORDER, "added")
        val sorted = ArrayList<Pair<String, String>>(presetEntries.size + customEntries.size)
        when (sortOrder) {
            "name" -> {
                for (i in presetEntries.indices) sorted.add(presetEntries[i] to presetValues[i])
                for (i in customEntries.indices) sorted.add(customEntries[i] to customValues[i])
                sorted.sortBy { it.first.lowercase() }
            }
            else -> {
                // "added" — custom apps in REVERSE insertion order (newest first), then presets.
                for (i in customEntries.indices.reversed()) sorted.add(customEntries[i] to customValues[i])
                for (i in presetEntries.indices) sorted.add(presetEntries[i] to presetValues[i])
            }
        }

        appsPref.entries = sorted.map { it.first }.toTypedArray()
        appsPref.entryValues = sorted.map { it.second }.toTypedArray()

        // Re-sync the preference's in-memory values with what was just written to SharedPreferences
        // (e.g., by BankAppPickerActivity.save()). MultiSelectListPreference caches its values
        // field internally and only reloads on initial bind — without this, newly-picked apps
        // look unchecked in the dialog despite actually being in the monitored set.
        val monitored = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
        if (appsPref.values != monitored) {
            appsPref.values = HashSet(monitored)
        }

        // When user unticks a custom app inside the dialog, also remove it from the ordered
        // custom list so it disappears from the entries entirely. Preset values are left alone.
        appsPref.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            @Suppress("UNCHECKED_CAST")
            val newSelected = (newValue as? Set<String>) ?: return@OnPreferenceChangeListener true
            val oldSelected = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
            val removed = oldSelected - newSelected
            if (removed.isEmpty()) return@OnPreferenceChangeListener true

            val presetSet = presetValues.toHashSet()
            val customRemoved = removed.filter { it !in presetSet }
            if (customRemoved.isEmpty()) return@OnPreferenceChangeListener true

            val updated = BankNotifications.getCustomApps(ctx).toMutableList()
            updated.removeAll(customRemoved)
            BankNotifications.setCustomApps(ctx, updated)

            // Refresh entries after the preference has finished committing the new value.
            Handler(Looper.getMainLooper()).post { refreshBankAppEntries() }
            true
        }

        // Re-sort entries when the sort-order preference changes.
        val sortPref = preferenceScreen.findPreference<ListPreference>(
            BankNotificationListener.KEY_BANK_NOTI_SORT_ORDER)
        if (sortPref != null && sortPref.onPreferenceChangeListener == null) {
            sortPref.onPreferenceChangeListener = OnPreferenceChangeListener { _, _ ->
                Handler(Looper.getMainLooper()).post { refreshBankAppEntries() }
                true
            }
        }
    }

    private fun showNotificationAccessGuideDialog() {
        val ctx = context ?: return
        val appName = ctx.getString(R.string.app_name)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_access_dialog_title)
            .setMessage(ctx.getString(R.string.bank_noti_access_dialog_message, appName))
            .setCancelable(false)
            .setPositiveButton(R.string.bank_noti_access_dialog_open) { _, _ ->
                openNotificationAccessSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openNotificationAccessSettings() {
        val ctx = context ?: return

        // Mark that we sent the user to system settings so we can detect their return
        // in onResume() and show the data-handling info dialog if they granted access.
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, true)
            .apply()

        // API 31+ : deep-link directly to our listener's detail screen (simple on/off toggle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val componentName = ComponentName(ctx, BankNotificationListener::class.java)
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
                startActivity(intent)
                return
            } catch (_: Exception) {
                // fall through to list screen
            }
        }

        // Fallback: open the list of all notification listeners
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(
                ctx,
                ctx.getString(R.string.bank_noti_access_toast_find_app, ctx.getString(R.string.app_name)),
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(ctx, R.string.bank_noti_access_settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Privacy/data-handling disclosure shown once, right after the user grants notification
     * access. Tells them we only read notifications from apps they selected, cache locally
     * in app-private storage, and forward to Whooing only — nowhere else.
     */
    private fun showDataHandlingInfoDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_info_dialog_title)
            .setMessage(R.string.bank_noti_info_dialog_message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

        // Keep the enable checkbox in sync with actual system permission state.
        val enableBox = preferenceScreen.findPreference<CheckBoxPreference>(
            BankNotificationListener.KEY_BANK_NOTI_ENABLE)
        val granted = BankNotificationListener.isNotificationAccessGranted(ctx)
        if (enableBox != null) {
            if (enableBox.isChecked && !granted) {
                enableBox.isChecked = false
            } else if (enableBox.isChecked && granted) {
                // Opportunistic retry of any pending batch left over from a previous offline run.
                BankNotifications.retryIfPending(ctx)
            }
        }

        // If we previously sent the user to notification-access settings and they granted it,
        // show the one-time data-handling disclosure. We keep the "accessInfoShown" flag so
        // toggling off/on later doesn't re-nag the user.
        val requested = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false)
        if (requested) {
            prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false).apply()
            val alreadyShown = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_INFO_SHOWN, false)
            if (granted && !alreadyShown) {
                prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_INFO_SHOWN, true).apply()
                showDataHandlingInfoDialog()
            }
        }

        // Refresh the app list preference so any apps added via the picker are visible.
        refreshBankAppEntries()
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
