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
 *  - Disabled when the device lacks on-device Gemini Nano (R2).
 *  - Disabled when bank-notification capture itself is off, since the queue is fed by the
 *    listener — without capture there's nothing to review.
 *  - On enable, if outside.json forwarding is active too, prompt a one-shot dual-use warning
 *    (R1) — same notification can otherwise be recorded twice.
 *
 * Re-runs the disable logic in onResume so the toggle reflects changes the user made on the
 * neighbouring "은행 알림 자동 기록" screen (which gates capture).
 */
class LocalReviewSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_local_review)
        wireToggle()
    }

    override fun onResume() {
        super.onResume()
        wireToggle()
    }

    private fun wireToggle() {
        val ctx = context ?: return
        val toggle = findPreference<CheckBoxPreference>(
            BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW) ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

        val genaiStatus = GenAiAvailability.check(ctx)
        if (!GenAiAvailability.isUsable(genaiStatus)) {
            toggle.isChecked = false
            toggle.isEnabled = false
            toggle.summary = ctx.getString(R.string.bank_noti_local_review_unsupported_summary)
            return
        }

        val captureOn = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_ENABLE, false)
        if (!captureOn) {
            toggle.isChecked = false
            toggle.isEnabled = false
            toggle.summary = ctx.getString(R.string.bank_noti_local_review_capture_off_summary)
            return
        }

        toggle.isEnabled = true
        toggle.summary = ctx.getString(R.string.bank_noti_local_review_summary)
        toggle.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val turningOn = newValue as Boolean
            if (!turningOn) return@OnPreferenceChangeListener true

            // KEY_BANK_NOTI_ENABLE gates BOTH the outside.json forwarding path AND the local
            // review path today (single capture switch upstream). When Phase 4 splits these,
            // this should compare against the forwarding-only sub-toggle.
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
