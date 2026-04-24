package kr.blogspot.charlie0301.wimple

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.preference.PreferenceManager
import java.util.concurrent.Executors

/**
 * One-shot biometric enrollment onboarding shown on first main-activity launch after a fresh
 * login, assuming the device supports biometric auth and the user hasn't already enabled it.
 *
 * Flow:
 *   1. `showIfNeeded()` checks three gates: already-shown flag, already-enabled preference,
 *      device capability. Any failure silently returns.
 *   2. Marks the "shown" flag immediately so a dialog-dismissal doesn't re-trigger the prompt
 *      next time the activity resumes.
 *   3. Presents a material-style consent dialog. On "enable", runs the system BiometricPrompt.
 *   4. On successful biometric verification, flips [SettingsFragment.KEY_BIOMETRIC_OPTION] to
 *      true — from this point on, [SplashScreenActivity] will require biometric on every launch.
 *
 * The "shown" flag is intentionally cleared on logout (in [SettingsFragment]) so a new user on
 * the same device gets their own onboarding.
 */
object BiometricOnboarding {

    const val KEY_BIOMETRIC_ONBOARDING_SHOWN = "biometric_onboarding_shown"

    fun showIfNeeded(activity: AppCompatActivity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (prefs.getBoolean(KEY_BIOMETRIC_ONBOARDING_SHOWN, false)) return
        if (prefs.getBoolean(SettingsFragment.KEY_BIOMETRIC_OPTION, false)) return

        val canAuth = BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

        prefs.edit().putBoolean(KEY_BIOMETRIC_ONBOARDING_SHOWN, true).apply()
        showDialog(activity)
    }

    private fun showDialog(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.biometric_onboarding_title)
            .setMessage(R.string.biometric_onboarding_message)
            .setCancelable(false)
            .setPositiveButton(R.string.biometric_onboarding_enable) { _, _ ->
                runEnrollmentPrompt(activity)
            }
            .setNegativeButton(R.string.biometric_onboarding_skip, null)
            .show()
    }

    private fun runEnrollmentPrompt(activity: AppCompatActivity) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_title))
            .setSubtitle(activity.getString(R.string.biometric_option_description))
            .setNegativeButtonText(activity.getString(R.string.user_cancel))
            .build()

        BiometricPrompt(
            activity,
            Executors.newSingleThreadExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    activity.runOnUiThread {
                        Toast.makeText(activity.applicationContext, errString, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    activity.runOnUiThread {
                        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
                            .edit()
                            .putBoolean(SettingsFragment.KEY_BIOMETRIC_OPTION, true)
                            .apply()
                        Toast.makeText(
                            activity.applicationContext,
                            activity.getString(R.string.biometric_onboarding_enabled),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        ).authenticate(promptInfo)
    }
}
