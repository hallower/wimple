package kr.blogspot.charlie0301.wimple

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kr.blogspot.charlie0301.wimple.impl.util.GenAiAvailability

/**
 * One-shot suggestion that pitches the on-device AI classification feature to users on supported
 * devices. Replaces the old BiometricOnboarding popup as the app's "first launch / fresh state"
 * disclosure surface.
 *
 * Trigger conditions (all must hold):
 *   1. Suggestion not yet shown for the current app version. Version-gated rather than a boolean
 *      flag so app updates re-pitch the feature when its UX or model coverage changes.
 *   2. User hasn't already enabled local review (no point pitching a feature they've turned on).
 *   3. GenAi status is usable. UNKNOWN triggers a refresh + re-evaluation; UNAVAILABLE skips
 *      silently and marks the version so we don't re-check on every launch.
 *
 * The three trigger scenarios the spec calls out — logout, fresh install, app update — all fall
 * out of the version-gate plus the SharedPreferences blanket-clear in
 * [GeneralSettingsFragment]'s logout flow.
 *
 * Acceptance is one-tap: the dialog message itself contains the disclosure bullets that
 * [LocalReviewSettingsFragment]'s data-handling notice would otherwise show, so tapping
 * accept directly flips the toggle (after permission, if needed) and skips the second dialog.
 * INFO_SHOWN is marked at the same time so a later toggle-off / toggle-on through settings
 * doesn't re-prompt.
 */
object LocalReviewSuggestion {

    const val KEY_LAST_VERSION = "local_review_suggestion_last_version"

    /**
     * Set when the user accepts the suggestion but notification access isn't granted yet — the
     * acceptance is "in flight" while they're in system settings. Cleared by [resumeIfPending]
     * once they return, regardless of whether they ended up granting.
     */
    private const val KEY_PENDING_ENABLE = "local_review_suggestion_pending_enable"

    fun showIfNeeded(activity: AppCompatActivity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val currentVersion = BuildConfig.VERSION_CODE
        val lastShownVersion = prefs.getInt(KEY_LAST_VERSION, 0)
        if (lastShownVersion >= currentVersion) return

        // Already on? Mark the version so we don't repeatedly re-evaluate, then bail.
        if (prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW, false)) {
            markShown(prefs, currentVersion)
            return
        }

        when (GenAiAvailability.check(activity)) {
            GenAiAvailability.Status.UNAVAILABLE -> {
                // Device can't run on-device Gemini Nano — no point pitching the feature.
                // Mark to avoid re-checking next launch (a future app update will re-arm).
                markShown(prefs, currentVersion)
            }
            GenAiAvailability.Status.UNKNOWN -> {
                // First-time launch with no cached availability yet. Kick off a refresh and
                // re-enter once it lands. Refresh's onComplete fires on Main; activity may
                // have been destroyed by then on a fast pause, so re-check liveness.
                GenAiAvailability.refresh(activity) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showIfNeeded(activity)
                    }
                }
            }
            GenAiAvailability.Status.AVAILABLE,
            GenAiAvailability.Status.DOWNLOADABLE,
            GenAiAvailability.Status.DOWNLOADING -> {
                showDialog(activity, prefs, currentVersion)
            }
        }
    }

    /**
     * Called from [WimpleActivity.onResume] to finish a "user accepted but had to go grant
     * permission" round-trip. No-op when no pending acceptance is recorded.
     */
    fun resumeIfPending(activity: AppCompatActivity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!prefs.getBoolean(KEY_PENDING_ENABLE, false)) return
        // Clear the flag regardless — either grant landed (we'll proceed) or didn't (silent
        // bail; the user can re-enable from settings if they change their mind).
        prefs.edit().putBoolean(KEY_PENDING_ENABLE, false).apply()
        if (BankNotificationListener.isNotificationAccessGranted(activity)) {
            enableAndConfirm(activity, prefs)
        }
    }

    private fun showDialog(activity: AppCompatActivity, prefs: SharedPreferences, version: Int) {
        // Mark BEFORE showing so a dialog dismissal (or activity destruction mid-show) doesn't
        // re-trigger the prompt next launch.
        markShown(prefs, version)
        AlertDialog.Builder(activity)
            .setTitle(R.string.local_review_suggestion_title)
            .setMessage(R.string.local_review_suggestion_message)
            .setPositiveButton(R.string.local_review_suggestion_enable) { _, _ ->
                acceptSuggestion(activity, prefs)
            }
            .setNegativeButton(R.string.local_review_suggestion_skip, null)
            .show()
    }

    private fun acceptSuggestion(activity: AppCompatActivity, prefs: SharedPreferences) {
        // The suggestion dialog already presented the disclosure bullets — record it as
        // "info shown" so a later off/on cycle through settings doesn't re-prompt.
        prefs.edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW_INFO_SHOWN, true)
            .apply()

        if (BankNotificationListener.isNotificationAccessGranted(activity)) {
            enableAndConfirm(activity, prefs)
        } else {
            // Notification access requires a system-level grant. Mark a pending-enable so
            // resumeIfPending() can finish the job after the user returns from system settings.
            prefs.edit().putBoolean(KEY_PENDING_ENABLE, true).apply()
            showAccessGuideDialog(activity)
        }
    }

    private fun enableAndConfirm(activity: AppCompatActivity, prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW, true)
            .apply()
        Toast.makeText(
            activity,
            R.string.local_review_suggestion_enabled,
            Toast.LENGTH_SHORT
        ).show()
        // 앱 선택 없이 AI 분류 베타만 켠 경우 금융앱 선택창을 띄워 KEY_BANK_NOTI_APPS를 채운다.
        // 이미 외부입력 플로우를 거쳐 앱이 선택돼 있으면 스킵한다.
        val apps = prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
        if (apps.isEmpty()) {
            Toast.makeText(activity, R.string.bank_noti_picker_opening, Toast.LENGTH_SHORT).show()
            prefs.edit().putBoolean(BankNotificationListener.KEY_BANK_NOTI_INITIAL_PICKER_DONE, true).apply()
            activity.startActivity(
                Intent(activity, BankAppPickerActivity::class.java)
                    .putExtra(BankAppPickerActivity.EXTRA_FINANCE_FILTER, true)
            )
        }
    }

    private fun showAccessGuideDialog(activity: AppCompatActivity) {
        val appName = activity.getString(R.string.app_name)
        AlertDialog.Builder(activity)
            .setTitle(R.string.bank_noti_access_dialog_title)
            .setMessage(activity.getString(R.string.bank_noti_access_dialog_message, appName))
            .setCancelable(false)
            .setPositiveButton(R.string.bank_noti_access_dialog_open) { _, _ ->
                openNotificationAccessSettings(activity)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                // User declined to go grant — clear the pending marker so we don't try to
                // resume on the next foreground.
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putBoolean(KEY_PENDING_ENABLE, false)
                    .apply()
            }
            .show()
    }

    private fun openNotificationAccessSettings(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val componentName = ComponentName(activity, BankNotificationListener::class.java)
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        componentName.flattenToString()
                    )
                activity.startActivity(intent)
                return
            } catch (_: Exception) {
                // fall through to list screen
            }
        }
        try {
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(
                activity,
                activity.getString(
                    R.string.bank_noti_access_toast_find_app, activity.getString(R.string.app_name)
                ),
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.bank_noti_access_settings_open_failed, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun markShown(prefs: SharedPreferences, version: Int) {
        prefs.edit().putInt(KEY_LAST_VERSION, version).apply()
    }
}
