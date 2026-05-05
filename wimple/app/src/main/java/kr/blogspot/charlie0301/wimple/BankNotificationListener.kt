package kr.blogspot.charlie0301.wimple

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import kr.blogspot.charlie0301.wimple.impl.BankNotifications
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue

// Pixel and One UI wrap notification text in Unicode bidi-isolation marks (U+2068 FIRST
// STRONG ISOLATE / U+2069 POP DIRECTIONAL ISOLATE). They're invisible but break parsers
// that anchor on a bank label at the start of a line. Built from char codes so the marks
// don't sit invisibly in source.
private val BIDI_MARKS = charArrayOf(0x2068.toChar(), 0x2069.toChar())

private fun String.stripBidiMarks(): String {
    if (BIDI_MARKS.none { it in this }) return this
    val sb = StringBuilder(length)
    for (c in this) if (c !in BIDI_MARKS) sb.append(c)
    return sb.toString()
}

class BankNotificationListener : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (!prefs.getBoolean(KEY_BANK_NOTI_ENABLE, false)) return

        val selectedPackages = prefs.getStringSet(KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet()
        if (sbn.packageName !in selectedPackages) return

        if (sbn.notification?.flags?.and(Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = sbn.notification?.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // EXTRA_BIG_TEXT carries the full transaction body (account, amount, balance) for
        // collapsed notifications; EXTRA_TEXT often only carries a one-line summary like
        // "거래내역 알림". Prefer bigText so the Whooing parser sees the actual payload.
        val rawBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val rawNormalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val rawText = if (!rawBigText.isNullOrEmpty()) rawBigText else rawNormalText

        // Strip Unicode bidi-isolation marks (U+2068 FSI / U+2069 PDI). Pixel and One UI wrap
        // notification text with these for RTL-safety; they're invisible but defeat parsers
        // that anchor on a bank label at the start of the line.
        val title = rawTitle.stripBidiMarks()
        val text = rawText.stripBidiMarks()

        if (title.isBlank() && text.isBlank()) return

        // Resolve the source app's user-facing label (e.g. "카카오뱅크") at capture time so the
        // forwarded payload carries it even if the app is later uninstalled. Falls back to the
        // package name if the label can't be resolved.
        val appLabel = try {
            val info = applicationContext.packageManager.getApplicationInfo(sbn.packageName, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val (count, added) = BankNotifications.add(
            applicationContext,
            sbn.packageName,
            appLabel,
            title,
            text,
            sbn.postTime
        )
        Log.d(LOG_TAG, "captured notification from $appLabel (${sbn.packageName}) (stored=$count, added=$added)")

        // Mirror the same notification into the local review queue when that feature is on. The
        // two queues are independent: outside.json forwarding still proceeds via BankNotifications,
        // and review/confirm runs from LocalReviewQueue. This is the user-acknowledged duplicate-
        // possibility tradeoff (R1) — a settings-time warning informs them.
        if (prefs.getBoolean(KEY_BANK_NOTI_LOCAL_REVIEW, false)) {
            val reviewResult = LocalReviewQueue.add(
                applicationContext,
                sbn.packageName,
                appLabel,
                title,
                text,
                sbn.postTime
            )
            Log.d(LOG_TAG, "review queue (count=${reviewResult.count}, added=${reviewResult.added})")
        }

        // If this was a duplicate of the previous entry, don't re-toast or re-trigger the
        // threshold flush — count didn't actually grow.
        if (!added) return

        if (prefs.getBoolean(KEY_BANK_NOTI_TOAST, true)) {
            mainHandler.post {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.bank_noti_toast_stored, count),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val threshold = prefs.getString(KEY_BANK_NOTI_THRESHOLD, "5")?.toIntOrNull()?.coerceAtLeast(2) ?: 5
        if (count >= threshold) {
            val showToast = prefs.getBoolean(KEY_BANK_NOTI_TOAST, true)
            val started = BankNotifications.forwardToWhooing(applicationContext) { success ->
                Log.d(LOG_TAG, "forward to whooing done: success=$success")
                if (showToast) {
                    val msgId = if (success) R.string.bank_noti_toast_sent else R.string.bank_noti_toast_send_failed
                    Toast.makeText(applicationContext, msgId, Toast.LENGTH_SHORT).show()
                }
            }
            if (!started && showToast) {
                // Either not authed or another send in progress; pending data is preserved for retry.
                Log.d(LOG_TAG, "forward to whooing not started; will retry later")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    companion object {
        private const val LOG_TAG = "BankNotiListener"

        const val KEY_BANK_NOTI_ENABLE = "pref_bankNotiEnable"
        const val KEY_BANK_NOTI_APPS = "pref_bankNotiApps"
        const val KEY_BANK_NOTI_CUSTOM_APPS = "pref_bankNotiCustomApps"
        const val KEY_BANK_NOTI_THRESHOLD = "pref_bankNotiThreshold"
        const val KEY_BANK_NOTI_TOAST = "pref_bankNotiToast"
        const val KEY_BANK_NOTI_SEND_NOW = "pref_bankNotiSendNow"
        const val KEY_BANK_NOTI_VIEW_LIST = "pref_bankNotiViewList"
        const val KEY_BANK_NOTI_VIEW_UNSUPPORTED = "pref_bankNotiViewUnsupported"
        const val KEY_BANK_NOTI_ADD_APP = "pref_bankNotiAddApp"
        const val KEY_BANK_NOTI_ACCESS_INFO_SHOWN = "pref_bankNotiAccessInfoShown"
        const val KEY_BANK_NOTI_ACCESS_REQUESTED = "pref_bankNotiAccessRequested"
        const val KEY_BANK_NOTI_SORT_ORDER = "pref_bankNotiSortOrder"
        const val KEY_BANK_NOTI_INITIAL_PICKER_DONE = "pref_bankNotiInitialPickerDone"
        const val KEY_BANK_NOTI_LOCAL_REVIEW = "pref_bankNotiLocalReview"
        // One-shot — set after the user acknowledges the on-device-AI data-handling notice
        // shown the first time KEY_BANK_NOTI_LOCAL_REVIEW is turned on. Cleared on logout
        // via the default-prefs blanket clear so a new account on the same device sees it
        // again. This replaces the old first-launch BiometricOnboarding popup as the app's
        // entry-time disclosure surface.
        const val KEY_BANK_NOTI_LOCAL_REVIEW_INFO_SHOWN = "pref_bankNotiLocalReviewInfoShown"

        fun isNotificationAccessGranted(ctx: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val me = ctx.packageName
            return flat.split(":").any { it.contains(me) }
        }
    }
}
