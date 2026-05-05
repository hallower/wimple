package kr.blogspot.charlie0301.wimple.impl.util

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-device Gemini Nano (ML Kit GenAI Prompt API) availability check, plus a settings-screen
 * dev override for testing the feature flow on devices that aren't actually supported.
 *
 * The real ML Kit call is `GenerativeModel.checkStatus()`, which is suspend (binder IPC into
 * AICore). We don't want to block the UI thread on it, so the public API is split:
 *
 *  - [check] returns the cached status synchronously. UNKNOWN on first call before refresh
 *    has run.
 *  - [refresh] kicks off a background coroutine that calls `checkStatus()` and updates the
 *    cache, then invokes [onComplete] on the main thread. Settings screens call this in
 *    onResume so the toggle reflects the current device state.
 *
 * Cache lives in default SharedPreferences. Status is stable on a given device (model stays
 * installed across runs) so no aggressive TTL — refresh is just driven by user-visible UI
 * entry points.
 */
object GenAiAvailability {

    enum class Status { UNKNOWN, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

    /** Hidden prefs flag — flip via adb / debug build to force AVAILABLE for UI testing. */
    const val KEY_DEV_OVERRIDE = "pref_genaiDevOverride"

    private const val LOG_TAG = "GenAiAvailability"
    private const val KEY_CACHED_STATUS = "pref_genaiCachedStatus"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun check(ctx: Context): Status {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.getBoolean(KEY_DEV_OVERRIDE, false)) return Status.AVAILABLE
        val raw = prefs.getString(KEY_CACHED_STATUS, null) ?: return Status.UNKNOWN
        return runCatching { Status.valueOf(raw) }.getOrDefault(Status.UNKNOWN)
    }

    fun isUsable(status: Status): Boolean =
        status == Status.AVAILABLE || status == Status.DOWNLOADABLE || status == Status.DOWNLOADING

    /**
     * Resolve current device support status by querying AICore via ML Kit. Result is cached
     * and [onComplete] fires on the main thread. Safe to call repeatedly — concurrent calls
     * each post their own result; cache is last-write-wins.
     *
     * Errors (no AICore service, library missing on this OS image, etc.) are mapped to
     * [Status.UNAVAILABLE] so callers don't have to handle exceptions.
     */
    fun refresh(ctx: Context, onComplete: ((Status) -> Unit)? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.getBoolean(KEY_DEV_OVERRIDE, false)) {
            // Override short-circuits — never hit AICore so dev testing works on any device.
            // Dispatch via Main to keep the contract uniform with the real-call path.
            if (onComplete != null) {
                scope.launch(Dispatchers.Main) { onComplete(Status.AVAILABLE) }
            }
            return
        }

        scope.launch {
            val status = try {
                val model = Generation.getClient()
                val raw = model.checkStatus()
                mapFeatureStatus(raw)
            } catch (t: Throwable) {
                // ML Kit throws when the OS image lacks AICore (older builds, non-supported
                // chipset). Treat as UNAVAILABLE rather than propagating — the toggle should
                // just stay off, not crash the settings screen.
                Log.w(LOG_TAG, "checkStatus failed; treating as UNAVAILABLE", t)
                Status.UNAVAILABLE
            }
            prefs.edit().putString(KEY_CACHED_STATUS, status.name).apply()
            if (onComplete != null) {
                withContext(Dispatchers.Main) { onComplete(status) }
            }
        }
    }

    private fun mapFeatureStatus(@FeatureStatus raw: Int): Status = when (raw) {
        FeatureStatus.AVAILABLE -> Status.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> Status.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> Status.DOWNLOADING
        FeatureStatus.UNAVAILABLE -> Status.UNAVAILABLE
        else -> Status.UNKNOWN
    }
}
