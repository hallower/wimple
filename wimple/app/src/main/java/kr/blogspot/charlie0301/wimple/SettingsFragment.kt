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
                // notably pref_bankNotiEnable / pref_bankNotiApps, which would
                // keep capturing notifications under the previous user's setup.
                PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().apply()
                // Re-seed XML defaultValue declarations (bank-noti threshold/sort/
                // toast, monthly-item count, financial-state auto-refresh, floating
                // button rotation, …) so the new session reads the documented
                // defaults rather than each call site's hard-coded fallback.
                PreferenceManager.setDefaultValues(ctx, R.xml.settings, true)

                // Drop captured/pending bank-noti payloads from the private prefs
                // file — these are tied to the previous account and must not be
                // forwarded after re-login.
                BankNotifications.clear(ctx)
            }

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

        preferenceScreen.findPreference<Preference>("pref_opensourceLicenses")
            ?.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(context, OpenSourceLicensesActivity::class.java))
                false
            }
    }

    private fun setupBankNotificationPreferences() {
        val enableBox = preferenceScreen.findPreference<CheckBoxPreference>(
            BankNotificationListener.KEY_BANK_NOTI_ENABLE)!!
        enableBox.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val turningOn = newValue as Boolean
            if (turningOn && context != null) {
                val ctx = requireContext()
                if (!BankNotificationListener.isNotificationAccessGranted(ctx)) {
                    // No OS permission — guide the user to settings; the data-handling
                    // notice + initial finance-app picker fire later from onResume()
                    // once they return with permission granted.
                    showNotificationAccessGuideDialog()
                } else {
                    // Permission already granted at the OS level. This is the common
                    // post-logout case: notification-listener access persists across
                    // logout (the OS holds it), but our onboarding flags were wiped.
                    // Without this branch the user re-enables silently and never sees
                    // the disclosure or picker again — run them inline here since we
                    // won't be making the settings round-trip that gates onResume.
                    maybeRunPostAccessOnboarding(ctx)
                }
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

        // One-time migration: legacy installs had preset packages in the monitored set that
        // weren't tracked in the custom-apps list. Pull them in so they remain visible.
        BankNotifications.migrateLegacyMonitoredApps(requireContext())

        refreshBankAppEntries()
    }

    /**
     * Rebuild the MultiSelectListPreference from the user's custom-apps list. There are no
     * presets anymore — everything in the list was added either via [BankAppPickerActivity]
     * or via the legacy-migration step. Labels are resolved through PackageManager; any
     * package whose app has since been uninstalled falls back to the raw package name.
     *
     * Respects [BankNotificationListener.KEY_BANK_NOTI_SORT_ORDER]:
     *  - "added" (default): reverse insertion order (newest-added first).
     *  - "name": alphabetical by display label (case/locale-insensitive).
     *
     * Re-syncs the preference's in-memory values from SharedPreferences — critical because
     * [BankAppPickerActivity] writes directly to the underlying StringSet, which doesn't
     * invalidate the preference's cached `mValues`. Without this, picked apps appear
     * unchecked despite actually being monitored.
     *
     * Also installs a change listener so unchecking an app in the dialog removes it from
     * the custom-apps list entirely (making the app disappear from the visible list).
     */
    private fun refreshBankAppEntries() {
        val ctx = context ?: return
        val appsPref = preferenceScreen.findPreference<MultiSelectListPreference>(
            BankNotificationListener.KEY_BANK_NOTI_APPS) ?: return

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
        val sorted = ArrayList<Pair<String, String>>(customEntries.size)
        when (sortOrder) {
            "name" -> {
                for (i in customEntries.indices) sorted.add(customEntries[i] to customValues[i])
                sorted.sortBy { it.first.lowercase() }
            }
            else -> {
                // "added" — newest-added first (reverse insertion order).
                for (i in customEntries.indices.reversed()) sorted.add(customEntries[i] to customValues[i])
            }
        }

        appsPref.entries = sorted.map { it.first }.toTypedArray()
        appsPref.entryValues = sorted.map { it.second }.toTypedArray()

        val monitored = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
        if (appsPref.values != monitored) {
            appsPref.values = HashSet(monitored)
        }

        // Unchecking an app in the dialog removes it from the custom list entirely
        // (there are no more presets that would stay as "inert" entries).
        appsPref.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            @Suppress("UNCHECKED_CAST")
            val newSelected = (newValue as? Set<String>) ?: return@OnPreferenceChangeListener true
            val oldSelected = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
            val removed = oldSelected - newSelected
            if (removed.isEmpty()) return@OnPreferenceChangeListener true

            val updated = BankNotifications.getCustomApps(ctx).toMutableList()
            updated.removeAll(removed)
            BankNotifications.setCustomApps(ctx, updated)

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
     * Run the post-access onboarding sequence: data-handling disclosure → (if not yet
     * launched) initial finance-app picker. Idempotent via
     * [BankNotificationListener.KEY_BANK_NOTI_ACCESS_INFO_SHOWN] so enable/disable cycles
     * within the same login session don't re-nag — the flag is wiped on logout
     * (SettingsFragment logout handler), so a returning user sees the sequence again.
     *
     * Invoked from two paths:
     *  - Enable toggle when OS permission is already granted (common post-logout).
     *  - onResume() after the user returns from a settings round-trip with permission newly granted.
     */
    private fun maybeRunPostAccessOnboarding(ctx: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_INFO_SHOWN, false)) return

        prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_INFO_SHOWN, true).apply()
        val initialPickerDone = prefs.getBoolean(
            BankNotificationListener.KEY_BANK_NOTI_INITIAL_PICKER_DONE, false)
        showDataHandlingInfoDialog(onDismiss = {
            if (!initialPickerDone) launchInitialFinancePicker()
        })
    }

    /**
     * Privacy/data-handling disclosure shown once per login session, right after the user
     * grants (or re-confirms) notification access. Tells them we only read notifications
     * from apps they selected, cache locally in app-private storage, and forward to Whooing
     * only — nowhere else.
     *
     * @param onDismiss invoked on the OK button press; used to chain the initial
     *                  finance-app picker onto the end of the onboarding flow.
     */
    private fun showDataHandlingInfoDialog(onDismiss: (() -> Unit)? = null) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_info_dialog_title)
            .setMessage(R.string.bank_noti_info_dialog_message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> onDismiss?.invoke() }
            .show()
    }

    /**
     * Launch the app picker with the finance pre-filter turned on. Used as the final step
     * of initial onboarding so the user sees bank/card/pay apps right away. Set the
     * "picker done" flag so this never auto-runs again; subsequent additions go through the
     * "앱 목록에서 추가" preference (no pre-filter).
     *
     * Shows an immediate toast so the user sees feedback during the brief activity-launch
     * transition — otherwise the device just looks frozen for a moment after they tap OK
     * on the info dialog.
     */
    private fun launchInitialFinancePicker() {
        val ctx = context ?: return
        Toast.makeText(ctx, R.string.bank_noti_picker_opening, Toast.LENGTH_SHORT).show()
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_INITIAL_PICKER_DONE, true)
            .apply()
        val intent = Intent(ctx, BankAppPickerActivity::class.java)
            .putExtra(BankAppPickerActivity.EXTRA_FINANCE_FILTER, true)
        startActivity(intent)
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

        // If we sent the user to notification-access settings and they returned with
        // permission granted, run the post-access onboarding (data-handling notice +
        // initial finance-app picker). The "info shown" flag inside the helper guards
        // against re-running across enable/disable cycles within the same login session;
        // it is reset on logout so a re-login sees the sequence again.
        val requested = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false)
        if (requested) {
            prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false).apply()
            if (granted) {
                maybeRunPostAccessOnboarding(ctx)
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
