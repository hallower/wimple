package kr.blogspot.charlie0301.wimple.impl

import android.util.Log
import org.json.JSONObject

/**
 * Whooing notification helper.
 *
 * GET /api/notifications.json — returns the user's unread badgeCount under
 * `results.notification.badgeCount`. We only ever READ that count; we never
 * send the corresponding PUT, so the user's unread status on whooing.com is
 * left intact for them to clear by visiting the site themselves.
 */
object WhooingNotifications {

    private const val LOG_TAG = "WhooingNotifications"
    private const val PATH = "api/notifications.json"

    /**
     * Synchronously fetches the unread badge count.
     *
     * Returns 0 if the user isn't authenticated yet or if the call fails for
     * any reason — the indicator simply stays hidden in those cases instead
     * of surfacing an error to the user.
     */
    fun fetchBadgeCount(wimple: WimpleImpl): Int {
        if (wimple.isAuthed != true || wimple.isInitializedFinished != true) {
            return 0
        }
        return try {
            val json: JSONObject? = wimple.invokeRESTAPI(
                RestAPIInvoker.HTTPMethod.GET, PATH, ""
            )
            json?.optJSONObject("results")
                ?.optJSONObject("notification")
                ?.optInt("badgeCount", 0)
                ?: 0
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "fetchBadgeCount failed", t)
            0
        }
    }
}
