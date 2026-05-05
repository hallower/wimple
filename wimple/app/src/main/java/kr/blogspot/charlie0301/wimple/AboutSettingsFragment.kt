package kr.blogspot.charlie0301.wimple

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class AboutSettingsFragment : PreferenceFragmentCompat() {

    /**
     * In-memory tap counter for the "10-tap unlock" easter egg on the OSS license row. Lives
     * only as long as this fragment, so leaving Settings (and coming back) starts fresh —
     * matches Android's "Tap Build number" pattern: rapid intent within a session, not a
     * forever-counting state machine.
     */
    private var ossTapCount = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_about)
        wirePreferences()
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate dev menu visibility — the user might have just unlocked it elsewhere
        // (returning from logout flow or a deep link).
        applyDevLogVisibility()
    }

    private fun wirePreferences() {
        findPreference<Preference>("pref_contact")?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false
            val url = ctx.getString(R.string.settings_contact_url)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, R.string.settings_contact_open_failed, Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<Preference>("pref_opensourceLicenses")?.setOnPreferenceClickListener {
            handleOssTap()
            true
        }

        findPreference<Preference>(KEY_DEV_AI_LOG)?.setOnPreferenceClickListener {
            startActivity(Intent(context, AiClassificationLogActivity::class.java))
            true
        }

        applyDevLogVisibility()
    }

    /**
     * Each tap increments the counter. Most taps still launch the OSS activity normally
     * (so casual users browsing licenses aren't disrupted); from tap 7 onward we swallow
     * the launch and surface a countdown toast so a deliberate unlocker knows where they
     * stand. The 10th tap flips the persistent flag and refreshes the menu in place.
     */
    private fun handleOssTap() {
        val ctx = context ?: return
        ossTapCount++

        if (ossTapCount >= UNLOCK_THRESHOLD) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            prefs.edit().putBoolean(KEY_DEV_AI_LOG_ENABLED, true).apply()
            Toast.makeText(ctx, R.string.dev_ai_log_unlock_done, Toast.LENGTH_SHORT).show()
            ossTapCount = 0
            applyDevLogVisibility()
            return
        }

        if (ossTapCount in COUNTDOWN_START until UNLOCK_THRESHOLD) {
            val remaining = UNLOCK_THRESHOLD - ossTapCount
            Toast.makeText(
                ctx,
                ctx.getString(R.string.dev_ai_log_unlock_remaining, remaining),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Sub-countdown taps: behave like a normal preference click.
        startActivity(Intent(context, OpenSourceLicensesActivity::class.java))
    }

    private fun applyDevLogVisibility() {
        val ctx = context ?: return
        val pref = findPreference<Preference>(KEY_DEV_AI_LOG) ?: return
        val flag = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(KEY_DEV_AI_LOG_ENABLED, false)
        pref.isVisible = flag
    }

    companion object {
        const val KEY_DEV_AI_LOG = "pref_devAiLog"
        const val KEY_DEV_AI_LOG_ENABLED = "pref_devAiLogEnabled"

        // Show the "X more taps" toast starting at this tap. Below this we treat the click as
        // a normal license-view; at this point and above we suppress the launch.
        private const val COUNTDOWN_START = 7
        private const val UNLOCK_THRESHOLD = 10
    }
}
