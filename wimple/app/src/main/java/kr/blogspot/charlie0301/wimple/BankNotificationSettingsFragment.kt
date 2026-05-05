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
import android.provider.Settings
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kr.blogspot.charlie0301.wimple.impl.BankNotifications

/**
 * "은행 알림 자동 기록" sub-screen of the host settings two-pane layout. Owns the entire
 * notification-listener feature surface: enable toggle, monitored-app list, picker shortcut,
 * sort/threshold/toast options, manual send, and entry points to the saved/unsupported lists.
 *
 * Onboarding (data-handling disclosure + initial finance-app picker) runs on first enable,
 * and again after a logout-clear when the user re-enables — see [maybeRunPostAccessOnboarding].
 */
class BankNotificationSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_bank_noti)
        setupBankNotificationPreferences()
    }

    private fun setupBankNotificationPreferences() {
        val enableBox = findPreference<CheckBoxPreference>(
            BankNotificationListener.KEY_BANK_NOTI_FORWARD)!!
        enableBox.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val turningOn = newValue as Boolean
            if (turningOn && context != null) {
                val ctx = requireContext()
                if (!BankNotificationListener.isNotificationAccessGranted(ctx)) {
                    showNotificationAccessGuideDialog()
                } else {
                    maybeRunPostAccessOnboarding(ctx)
                }
            }
            true
        }

        findPreference<Preference>(BankNotificationListener.KEY_BANK_NOTI_SEND_NOW)?.onPreferenceClickListener =
            OnPreferenceClickListener {
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

        findPreference<Preference>(BankNotificationListener.KEY_BANK_NOTI_VIEW_LIST)?.onPreferenceClickListener =
            OnPreferenceClickListener {
                startActivity(Intent(context, BankNotificationListActivity::class.java))
                false
            }

        findPreference<Preference>(BankNotificationListener.KEY_BANK_NOTI_VIEW_UNSUPPORTED)?.onPreferenceClickListener =
            OnPreferenceClickListener {
                startActivity(Intent(context, UnsupportedBankNotificationListActivity::class.java))
                false
            }

        findPreference<Preference>(BankNotificationListener.KEY_BANK_NOTI_ADD_APP)?.onPreferenceClickListener =
            OnPreferenceClickListener {
                startActivity(Intent(context, BankAppPickerActivity::class.java))
                false
            }

        // One-time migration: legacy installs had preset packages in the monitored set that
        // weren't tracked in the custom-apps list. Pull them in so they remain visible.
        BankNotifications.migrateLegacyMonitoredApps(requireContext())

        refreshBankAppEntries()
    }

    /**
     * Rebuild the MultiSelectListPreference from the user's custom-apps list. Mirrors the
     * pre-refactor logic — entries come from PackageManager (with package-name fallback for
     * uninstalled apps), order respects [BankNotificationListener.KEY_BANK_NOTI_SORT_ORDER],
     * and unchecking a row removes the package from the custom list entirely.
     *
     * Re-syncs the preference's in-memory values from SharedPreferences — critical because
     * [BankAppPickerActivity] writes directly to the underlying StringSet, which doesn't
     * invalidate the preference's cached `mValues`. Without this, picked apps appear
     * unchecked despite actually being monitored.
     */
    private fun refreshBankAppEntries() {
        val ctx = context ?: return
        val appsPref = findPreference<MultiSelectListPreference>(
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
                for (i in customEntries.indices.reversed()) sorted.add(customEntries[i] to customValues[i])
            }
        }

        appsPref.entries = sorted.map { it.first }.toTypedArray()
        appsPref.entryValues = sorted.map { it.second }.toTypedArray()

        val monitored = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
        if (appsPref.values != monitored) {
            appsPref.values = HashSet(monitored)
        }

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

        val sortPref = findPreference<ListPreference>(BankNotificationListener.KEY_BANK_NOTI_SORT_ORDER)
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
            .setPositiveButton(R.string.bank_noti_access_dialog_open) { _, _ -> openNotificationAccessSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openNotificationAccessSettings() {
        val ctx = context ?: return

        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, true)
            .apply()

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

    private fun showDataHandlingInfoDialog(onDismiss: (() -> Unit)? = null) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_info_dialog_title)
            .setMessage(R.string.bank_noti_info_dialog_message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> onDismiss?.invoke() }
            .show()
    }

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

        val enableBox = findPreference<CheckBoxPreference>(BankNotificationListener.KEY_BANK_NOTI_FORWARD)
        val granted = BankNotificationListener.isNotificationAccessGranted(ctx)
        if (enableBox != null) {
            if (enableBox.isChecked && !granted) {
                enableBox.isChecked = false
            } else if (enableBox.isChecked && granted) {
                BankNotifications.retryIfPending(ctx)
            }
        }

        val requested = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false)
        if (requested) {
            prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false).apply()
            if (granted) {
                maybeRunPostAccessOnboarding(ctx)
            }
        }

        refreshBankAppEntries()
    }
}
