package kr.blogspot.charlie0301.wimple

import android.app.AlertDialog
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kr.blogspot.charlie0301.wimple.impl.util.GenAiAvailability

/**
 * "알림 검수 후 직접 입력" sub-screen of the host settings two-pane layout. Owns the local
 * review toggle and its disable rules:
 *  - Disabled when the device lacks on-device Gemini Nano (R2). Status is queried via
 *    [GenAiAvailability.refresh] in onResume; while the check is in flight the toggle stays
 *    disabled with a "checking…" summary so users on supported devices don't see a brief
 *    flash of "unsupported".
 *  - Disabled when bank-notification capture itself is off, since the queue is fed by the
 *    listener — without capture there's nothing to review.
 *  - On enable, a one-shot dual-use warning prompts when outside.json forwarding is also
 *    active (R1) — same notification can otherwise be recorded twice.
 *
 * Re-runs the rule evaluation in onResume so the toggle reflects changes the user made on
 * the neighbouring "은행 알림 자동 기록" screen (which gates capture) and the latest GenAi
 * availability result.
 */
class LocalReviewSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_local_review)
    }

    override fun onResume() {
        super.onResume()
        applyState()
        // Kick a fresh availability check on every entry — cheap binder call into AICore.
        // When it completes we re-apply state so DOWNLOADABLE / DOWNLOADING / AVAILABLE
        // resolve to the right summary even if the cached value was stale.
        val ctx = context ?: return
        GenAiAvailability.refresh(ctx) {
            // Fragment may have been detached before the callback; check before touching prefs UI.
            if (isAdded) applyState()
        }
    }

    /**
     * Single source of truth for the toggle state. Priority order:
     *   1. GenAi availability — UNKNOWN shows checking, UNAVAILABLE locks off.
     *   2. Bank-noti capture toggle — must be on; otherwise the queue is never fed.
     *   3. Otherwise: enabled, with the dual-use warning intercepting the off→on transition.
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
                // proceed to capture-toggle check below
                applyCaptureGate(ctx, toggle, prefs, status)
            }
        }
    }

    private fun applyCaptureGate(
        ctx: android.content.Context,
        toggle: CheckBoxPreference,
        prefs: android.content.SharedPreferences,
        status: GenAiAvailability.Status
    ) {
        val captureOn = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ENABLE, false)
        if (!captureOn) {
            toggle.isChecked = false
            toggle.isEnabled = false
            toggle.summary = ctx.getString(R.string.bank_noti_local_review_capture_off_summary)
            return
        }

        toggle.isEnabled = true
        toggle.summary = when (status) {
            GenAiAvailability.Status.DOWNLOADABLE ->
                ctx.getString(R.string.bank_noti_local_review_downloadable_summary)
            GenAiAvailability.Status.DOWNLOADING ->
                ctx.getString(R.string.bank_noti_local_review_downloading_summary)
            else -> ctx.getString(R.string.bank_noti_local_review_summary)
        }

        toggle.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val turningOn = newValue as Boolean
            if (!turningOn) return@OnPreferenceChangeListener true

            // KEY_BANK_NOTI_ENABLE gates BOTH the outside.json forwarding path AND the local
            // review path today (single capture switch upstream). When forwarding is split
            // out, this should compare against the forwarding-only sub-toggle.
            AlertDialog.Builder(ctx)
                .setTitle(R.string.bank_noti_local_review_dual_warning_title)
                .setMessage(R.string.bank_noti_local_review_dual_warning_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> toggle.isChecked = true }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            // Defer the actual flip to the dialog's positive button so cancelling leaves it off.
            false
        }
    }
}
