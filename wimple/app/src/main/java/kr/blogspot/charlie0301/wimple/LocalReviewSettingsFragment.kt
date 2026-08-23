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
import android.text.format.DateFormat
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import kr.blogspot.charlie0301.wimple.impl.BankNotifications
import kr.blogspot.charlie0301.wimple.impl.ClassificationBackupManager
import kr.blogspot.charlie0301.wimple.impl.util.GenAiAvailability

/**
 * "은행 알림 AI 분류 입력" sub-screen of the host settings two-pane layout. Owns the local
 * review toggle and its disable rules:
 *  - Disabled when the device lacks on-device Gemini Nano (R2). Status is queried via
 *    [GenAiAvailability.refresh] in onResume; while the check is in flight the toggle stays
 *    disabled with a "checking…" summary so users on supported devices don't see a brief
 *    flash of "unsupported".
 *
 * Notification access permission is required (the system-level NotificationListenerService
 * is the upstream source). When the user toggles ON without it, we route them to grant via
 * the same dialog the forward-to-Whooing screen uses; the toggle stays off until access is
 * granted and the user re-enables.
 *
 * Local review is INDEPENDENT of forward-to-Whooing — either path can be on alone or together.
 * The dual-use warning fires only when forward is ALSO on (same notification recorded twice
 * via two paths).
 */
class LocalReviewSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_local_review)
        findPreference<Preference>(BankNotificationListener.KEY_BANK_NOTI_ADD_APP)
            ?.setOnPreferenceClickListener {
                startActivity(Intent(context, BankAppPickerActivity::class.java))
                false
            }
        refreshBankAppEntries()
        wireBackupPreferences()
    }

    // -------------------- Task 5: 분류 학습 데이터 백업/복원 --------------------

    private fun wireBackupPreferences() {
        findPreference<Preference>(KEY_BACKUP_NOW)?.setOnPreferenceClickListener {
            triggerBackupNow()
            true
        }
        findPreference<Preference>(KEY_BACKUP_RESTORE)?.setOnPreferenceClickListener {
            triggerBackupRestore()
            true
        }
    }

    private fun triggerBackupNow() {
        val ctx = context ?: return
        if (ClassificationBackupManager.storageSupport() != ClassificationBackupManager.StorageSupport.SUPPORTED) {
            Toast.makeText(ctx, R.string.bank_noti_backup_summary_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val ok = ClassificationBackupManager.backupNow(ctx)
            if (!isAdded) return@launch
            Toast.makeText(
                ctx,
                if (ok) R.string.bank_noti_backup_now_success else R.string.bank_noti_backup_now_failed,
                Toast.LENGTH_SHORT
            ).show()
            refreshBackupSummary()
        }
    }

    private fun triggerBackupRestore() {
        val ctx = context ?: return
        if (ClassificationBackupManager.storageSupport() != ClassificationBackupManager.StorageSupport.SUPPORTED) {
            Toast.makeText(ctx, R.string.bank_noti_backup_summary_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val uri = ClassificationBackupManager.findExistingBackupUri(ctx)
        if (uri == null) {
            Toast.makeText(ctx, R.string.bank_noti_backup_restore_none_found, Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = ClassificationBackupManager.lastBackupTimestamp(ctx)
        val whenText = timestamp?.let { formatBackupTimestamp(ctx, it) } ?: "-"
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_backup_restore_confirm_title)
            .setMessage(getString(R.string.bank_noti_backup_restore_confirm_message, whenText))
            .setPositiveButton(android.R.string.ok) { _, _ -> performRestore(ctx, uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performRestore(ctx: Context, uri: android.net.Uri) {
        lifecycleScope.launch {
            val json = ClassificationBackupManager.readBackupJson(ctx, uri)
            val result = json?.let { ClassificationBackupManager.restore(ctx, it) }
            if (!isAdded) return@launch
            if (result == null) {
                Toast.makeText(ctx, R.string.bank_noti_backup_restore_failed, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    ctx,
                    getString(
                        R.string.bank_noti_backup_restore_result,
                        result.mappingsRestored, result.examplesRestored
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshBackupSummary() {
        val ctx = context ?: return
        val pref = findPreference<Preference>(KEY_BACKUP_NOW) ?: return
        if (ClassificationBackupManager.storageSupport() != ClassificationBackupManager.StorageSupport.SUPPORTED) {
            pref.isEnabled = false
            pref.summary = ctx.getString(R.string.bank_noti_backup_summary_unsupported)
            return
        }
        pref.isEnabled = true
        val timestamp = ClassificationBackupManager.lastBackupTimestamp(ctx)
        pref.summary = if (timestamp == null) {
            ctx.getString(R.string.bank_noti_backup_summary_never)
        } else {
            ctx.getString(R.string.bank_noti_backup_summary_last, formatBackupTimestamp(ctx, timestamp))
        }
    }

    private fun formatBackupTimestamp(ctx: Context, epochMs: Long): String =
        DateFormat.format("yyyy-MM-dd HH:mm", epochMs).toString()

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

    override fun onResume() {
        super.onResume()
        applyState()
        refreshBankAppEntries()
        refreshBackupSummary()
        // Kick a fresh availability check on every entry — cheap binder call into AICore.
        // When it completes we re-apply state so DOWNLOADABLE / DOWNLOADING / AVAILABLE
        // resolve to the right summary even if the cached value was stale.
        val ctx = context ?: return
        GenAiAvailability.refresh(ctx) {
            // Fragment may have been detached before the callback; check before touching prefs UI.
            if (isAdded) applyState()
        }
        // After returning from system notification-access settings, the user may have just
        // granted permission. Drop the "requested" flag and (if granted) move on to the data-
        // handling notice they came for. Mirrors BankNotificationSettingsFragment.onResume.
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val requested = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false)
        if (requested && BankNotificationListener.isNotificationAccessGranted(ctx)) {
            prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, false).apply()
            val toggle = findPreference<CheckBoxPreference>(
                BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW)
            if (toggle != null && !toggle.isChecked) {
                advanceFromAccess(ctx, prefs, toggle)
            }
        }
        // After the initial bank-app picker launched from the AI-enable flow, navigate to the
        // review screen so the first-time tutorial fires immediately.
        if (prefs.getBoolean(BankNotificationListener.KEY_LOCAL_REVIEW_POST_PICKER_PENDING, false)) {
            prefs.edit().putBoolean(BankNotificationListener.KEY_LOCAL_REVIEW_POST_PICKER_PENDING, false).apply()
            startActivity(
                Intent(ctx, BankNotificationReviewActivity::class.java)
                    .putExtra(BankNotificationReviewActivity.EXTRA_SHOW_TUTORIAL, true)
            )
        }
    }

    /**
     * Single source of truth for the toggle state. GenAi availability fully drives the
     * enabled/summary state; the toggle handler runs the rest of the chain (access check →
     * info notice → dual-use warning) when the user actually flips it.
     */
    private fun applyState() {
        val ctx = context ?: return
        val toggle = findPreference<CheckBoxPreference>(
            BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW) ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

        when (val status = GenAiAvailability.check(ctx)) {
            GenAiAvailability.Status.UNAVAILABLE -> {
                toggle.isChecked = false
                toggle.isEnabled = false
                toggle.summary = ctx.getString(R.string.bank_noti_local_review_unsupported_summary)
                return
            }
            GenAiAvailability.Status.UNKNOWN -> {
                toggle.isEnabled = false
                toggle.summary = ctx.getString(R.string.bank_noti_local_review_checking_summary)
                return
            }
            GenAiAvailability.Status.DOWNLOADABLE,
            GenAiAvailability.Status.DOWNLOADING,
            GenAiAvailability.Status.AVAILABLE -> {
                toggle.isEnabled = true
                toggle.summary = when (status) {
                    GenAiAvailability.Status.DOWNLOADABLE ->
                        ctx.getString(R.string.bank_noti_local_review_downloadable_summary)
                    GenAiAvailability.Status.DOWNLOADING ->
                        ctx.getString(R.string.bank_noti_local_review_downloading_summary)
                    else -> ctx.getString(R.string.bank_noti_local_review_summary)
                }
                wireToggleHandler(ctx, prefs, toggle)
            }
        }
    }

    private fun wireToggleHandler(
        ctx: android.content.Context,
        prefs: android.content.SharedPreferences,
        toggle: CheckBoxPreference
    ) {
        toggle.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val turningOn = newValue as Boolean
            if (!turningOn) return@OnPreferenceChangeListener true

            if (!BankNotificationListener.isNotificationAccessGranted(ctx)) {
                showNotificationAccessGuideDialog(ctx)
                return@OnPreferenceChangeListener false
            }
            advanceFromAccess(ctx, prefs, toggle)
            // Defer the flip to dialog confirmations so cancelling leaves it off.
            false
        }
    }

    /**
     * Notification access is granted — proceed with the post-access onboarding chain. First-
     * time users see the on-device-AI data-handling notice; subsequent enables skip straight
     * to the dual-use warning (only when forward is ALSO on).
     */
    private fun advanceFromAccess(
        ctx: android.content.Context,
        prefs: android.content.SharedPreferences,
        toggle: CheckBoxPreference
    ) {
        val infoShown = prefs.getBoolean(
            BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW_INFO_SHOWN, false
        )
        if (!infoShown) {
            showFirstTimeNotice(ctx, prefs, toggle)
        } else {
            showDualUseWarningOrEnable(ctx, prefs, toggle)
        }
    }

    private fun showFirstTimeNotice(
        ctx: android.content.Context,
        prefs: android.content.SharedPreferences,
        toggle: CheckBoxPreference
    ) {
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_local_review_info_title)
            .setMessage(R.string.bank_noti_local_review_info_message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit()
                    .putBoolean(BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW_INFO_SHOWN, true)
                    .apply()
                showDualUseWarningOrEnable(ctx, prefs, toggle)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show the dual-use warning iff the forward path is currently ON — that's the only state
     * where a single notification can produce two entries (outside.json forward + AI-confirmed
     * makeEntry). When forward is off, AI is the only writer and the warning would be noise,
     * so we skip straight to flipping the toggle.
     */
    private fun showDualUseWarningOrEnable(
        ctx: android.content.Context,
        prefs: android.content.SharedPreferences,
        toggle: CheckBoxPreference
    ) {
        val forwardOn = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_FORWARD, false)
        if (!forwardOn) {
            toggle.isChecked = true
            maybeShowAppPickerForAiEnable(ctx, prefs)
            return
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_local_review_dual_warning_title)
            .setMessage(R.string.bank_noti_local_review_dual_warning_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                toggle.isChecked = true
                maybeShowAppPickerForAiEnable(ctx, prefs)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 외부입력(forward) 없이 AI 분류 베타만 처음 켤 때 금융앱 선택창을 띄운다.
     * KEY_BANK_NOTI_APPS가 이미 채워져 있으면 (외부입력 플로우를 먼저 거친 경우) 스킵한다.
     */
    private fun maybeShowAppPickerForAiEnable(ctx: Context, prefs: android.content.SharedPreferences) {
        val apps = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
        if (apps.isNotEmpty()) return
        Toast.makeText(ctx, R.string.bank_noti_picker_opening, Toast.LENGTH_SHORT).show()
        prefs.edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_INITIAL_PICKER_DONE, true)
            .putBoolean(BankNotificationListener.KEY_LOCAL_REVIEW_POST_PICKER_PENDING, true)
            .apply()
        startActivity(
            Intent(ctx, BankAppPickerActivity::class.java)
                .putExtra(BankAppPickerActivity.EXTRA_FINANCE_FILTER, true)
        )
    }

    private fun showNotificationAccessGuideDialog(ctx: android.content.Context) {
        val appName = ctx.getString(R.string.app_name)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.bank_noti_access_dialog_title)
            .setMessage(ctx.getString(R.string.bank_noti_access_dialog_message, appName))
            .setCancelable(false)
            .setPositiveButton(R.string.bank_noti_access_dialog_open) { _, _ ->
                openNotificationAccessSettings(ctx)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openNotificationAccessSettings(ctx: android.content.Context) {
        // Mark requested so onResume can resume the AI-enable flow once permission lands —
        // mirrors BankNotificationSettingsFragment.openNotificationAccessSettings.
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_ACCESS_REQUESTED, true)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val componentName = ComponentName(ctx, BankNotificationListener::class.java)
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        componentName.flattenToString()
                    )
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

    companion object {
        private const val KEY_BACKUP_NOW = "pref_bankNotiBackupNow"
        private const val KEY_BACKUP_RESTORE = "pref_bankNotiBackupRestore"
    }
}
