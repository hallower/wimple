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
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

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

        val count = BankNotifications.add(
            applicationContext,
            sbn.packageName,
            appLabel,
            title,
            text,
            sbn.postTime
        )
        Log.d(LOG_TAG, "captured notification from $appLabel (${sbn.packageName}) (stored=$count)")

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
        const val KEY_BANK_NOTI_ADD_APP = "pref_bankNotiAddApp"
        const val KEY_BANK_NOTI_ACCESS_INFO_SHOWN = "pref_bankNotiAccessInfoShown"
        const val KEY_BANK_NOTI_ACCESS_REQUESTED = "pref_bankNotiAccessRequested"
        const val KEY_BANK_NOTI_SORT_ORDER = "pref_bankNotiSortOrder"

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
