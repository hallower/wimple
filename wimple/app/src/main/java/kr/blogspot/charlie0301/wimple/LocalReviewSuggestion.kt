package kr.blogspot.charlie0301.wimple

import android.content.Intent
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
 * [GeneralSettingsFragment]'s logout flow:
 *  - Fresh install: KEY_LAST_VERSION absent → default 0 → 0 < current_version → show.
 *  - Logout: blanket-clear wipes KEY_LAST_VERSION → same as above on next launch.
 *  - Update: code's VERSION_CODE bumps past the stored value → show again on first launch after
 *    update.
 */
object LocalReviewSuggestion {

    const val KEY_LAST_VERSION = "local_review_suggestion_last_version"

    fun showIfNeeded(activity: AppCompatActivity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val currentVersion = BuildConfig.VERSION_CODE
        val lastShownVersion = prefs.getInt(KEY_LAST_VERSION, 0)
        if (lastShownVersion >= currentVersion) return

        // Already on? Mark the version so we don't repeatedly re-evaluate, then bail.
        if (prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW, false)) {
            markShown(activity, currentVersion)
            return
        }

        when (GenAiAvailability.check(activity)) {
            GenAiAvailability.Status.UNAVAILABLE -> {
                // Device can't run on-device Gemini Nano — no point pitching the feature.
                // Mark to avoid re-checking next launch (a future app update will re-arm).
                markShown(activity, currentVersion)
            }
            GenAiAvailability.Status.UNKNOWN -> {
                // First-time launch with no cached availability yet. Kick off a refresh and
                // re-enter once it lands. Refresh's onComplete fires on Main; activity may
                // have been destroyed by then on a fast pause, so re-check isFinishing.
                GenAiAvailability.refresh(activity) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showIfNeeded(activity)
                    }
                }
            }
            GenAiAvailability.Status.AVAILABLE,
            GenAiAvailability.Status.DOWNLOADABLE,
            GenAiAvailability.Status.DOWNLOADING -> {
                showDialog(activity, currentVersion)
            }
        }
    }

    private fun showDialog(activity: AppCompatActivity, version: Int) {
        // Mark BEFORE showing so a dialog dismissal (or activity destruction mid-show) doesn't
        // re-trigger the prompt next launch.
        markShown(activity, version)
        AlertDialog.Builder(activity)
            .setTitle(R.string.local_review_suggestion_title)
            .setMessage(R.string.local_review_suggestion_message)
            .setPositiveButton(R.string.local_review_suggestion_enable) { _, _ ->
                navigateToSettings(activity)
            }
            .setNegativeButton(R.string.local_review_suggestion_skip, null)
            .show()
    }

    private fun navigateToSettings(activity: AppCompatActivity) {
        // Re-enter WimpleActivity with the settings menu preselected. SingleTask + CLEAR_TOP
        // routes through onNewIntent → replaceWimpleFragment, swapping in SettingsHostFragment.
        // Same plumbing the dual-use banner uses from BankNotificationReviewActivity.
        val intent = Intent(activity, WimpleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WimpleActivity.EXTRA_OPEN_MENU, R.id.menu_preference)
        activity.startActivity(intent)
    }

    private fun markShown(activity: AppCompatActivity, version: Int) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putInt(KEY_LAST_VERSION, version)
            .apply()
    }
}
