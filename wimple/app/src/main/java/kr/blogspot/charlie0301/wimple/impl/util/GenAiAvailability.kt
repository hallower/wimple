package kr.blogspot.charlie0301.wimple.impl.util

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * On-device Gemini Nano (ML Kit GenAI Prompt API) availability check.
 *
 * Phase 1 stub: returns [Status.UNAVAILABLE] by default so the local-review settings toggle
 * is correctly disabled on real devices until Phase 4 wires up the real ML Kit status query.
 * A hidden SharedPreferences override ([KEY_DEV_OVERRIDE]) lets developers force AVAILABLE
 * for UI testing without flashing a Pixel 9.
 *
 * Phase 4 will replace [check] with a real `GenerativeModel.checkStatus()` (or equivalent)
 * call that distinguishes:
 *   - AVAILABLE     model is on-device and ready
 *   - DOWNLOADABLE  hardware is supported but model needs download (trigger first-use)
 *   - UNAVAILABLE   chipset/OS not supported
 */
object GenAiAvailability {

    enum class Status { AVAILABLE, DOWNLOADABLE, UNAVAILABLE }

    /** Hidden prefs flag — flip via adb to force AVAILABLE during development. */
    const val KEY_DEV_OVERRIDE = "pref_genaiDevOverride"

    fun check(ctx: Context): Status {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.getBoolean(KEY_DEV_OVERRIDE, false)) return Status.AVAILABLE
        // TODO(phase 4): replace with ML Kit GenAI Prompt API status check.
        return Status.UNAVAILABLE
    }

    fun isUsable(status: Status): Boolean =
        status == Status.AVAILABLE || status == Status.DOWNLOADABLE
}
