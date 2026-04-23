package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object BankNotifications {

    private const val LOG_TAG = "BankNotifications"
    private const val PREFS_NAME = "wimple.banknotifications"
    private const val KEY_STORED_JSON = "stored_json"
    private const val KEY_PENDING_JSON = "pending_json"
    private const val POST_PAYMENT_PATH = "api/entries/outside.json"

    private val sending = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class StoredNotification(
        val time: Long,
        val packageName: String,
        val title: String,
        val text: String
    )

    @Synchronized
    fun add(ctx: Context, pkg: String, title: String, text: String, time: Long = System.currentTimeMillis()): Int {
        val prefs = prefs(ctx)
        val arr = loadArray(prefs, KEY_STORED_JSON)
        val obj = JSONObject().apply {
            put("t", time)
            put("p", pkg)
            put("title", title)
            put("text", text)
        }
        arr.put(obj)
        prefs.edit().putString(KEY_STORED_JSON, arr.toString()).apply()
        return arr.length()
    }

    fun getAll(ctx: Context): List<StoredNotification> =
        arrayToList(loadArray(prefs(ctx), KEY_STORED_JSON))

    fun getPending(ctx: Context): List<StoredNotification> =
        arrayToList(loadArray(prefs(ctx), KEY_PENDING_JSON))

    fun count(ctx: Context): Int = loadArray(prefs(ctx), KEY_STORED_JSON).length()

    fun pendingCount(ctx: Context): Int = loadArray(prefs(ctx), KEY_PENDING_JSON).length()

    fun hasAnyUnsent(ctx: Context): Boolean = count(ctx) > 0 || pendingCount(ctx) > 0

    @Synchronized
    fun clear(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_STORED_JSON, "[]")
            .putString(KEY_PENDING_JSON, "[]")
            .apply()
    }

    @Synchronized
    fun removeAt(ctx: Context, index: Int) {
        val prefs = prefs(ctx)
        val arr = loadArray(prefs, KEY_STORED_JSON)
        if (index < 0 || index >= arr.length()) return
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            if (i != index) newArr.put(arr.get(i))
        }
        prefs.edit().putString(KEY_STORED_JSON, newArr.toString()).apply()
    }

    /**
     * Attempt to forward all unsent notifications (stored + previously pending) to Whooing.
     *
     * Storage model:
     * - `stored` — newly captured notifications, not yet attempted.
     * - `pending` — notifications currently being sent or left over from a previous failed attempt.
     *
     * On call:
     * 1. Merge stored into pending (preserving order; pending first, stored appended).
     * 2. Clear stored so newly-arriving notifications don't mix into the current batch.
     * 3. Fire a synchronous HTTP POST to Whooing on a background thread.
     * 4. On HTTP 2xx → clear pending (batch confirmed sent).
     *    On failure → leave pending intact for the next retry.
     *
     * @param onDone callback invoked on the main thread with the actual send result.
     * @return `true` if a send attempt was started, `false` if nothing to send / not authed / already sending.
     */
    @Synchronized
    fun forwardToWhooing(ctx: Context, onDone: ((Boolean) -> Unit)? = null): Boolean {
        val wimple = WimpleImpl.getInstance()
        if (wimple.isAuthed != true || wimple.isInitializedFinished != true) {
            Log.w(LOG_TAG, "[forward] skipped - not authed/initialized")
            return false
        }

        val prefs = prefs(ctx)
        val mergedPendingSize = mergeStoredIntoPending(prefs)
        if (mergedPendingSize == 0) {
            Log.d(LOG_TAG, "[forward] nothing to send")
            return false
        }

        if (!sending.compareAndSet(false, true)) {
            Log.d(LOG_TAG, "[forward] another send in progress, skipping")
            return false
        }

        val appCtx = ctx.applicationContext
        Thread {
            var success = false
            try {
                success = doSynchronousSend(appCtx, wimple)
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "[forward] exception", t)
            } finally {
                sending.set(false)
                if (onDone != null) mainHandler.post { onDone(success) }
            }
        }.start()
        return true
    }

    /** Called on app start (after authentication) to retry any leftover pending batch from previous run. */
    fun retryIfPending(ctx: Context) {
        if (pendingCount(ctx) == 0 && count(ctx) == 0) return
        forwardToWhooing(ctx)
    }

    @Synchronized
    private fun mergeStoredIntoPending(prefs: SharedPreferences): Int {
        val pending = loadArray(prefs, KEY_PENDING_JSON)
        val stored = loadArray(prefs, KEY_STORED_JSON)
        if (stored.length() > 0) {
            for (i in 0 until stored.length()) pending.put(stored.get(i))
            prefs.edit()
                .putString(KEY_PENDING_JSON, pending.toString())
                .putString(KEY_STORED_JSON, "[]")
                .apply()
        }
        return pending.length()
    }

    private fun doSynchronousSend(ctx: Context, wimple: WimpleImpl): Boolean {
        val prefs = prefs(ctx)
        val pending = loadArray(prefs, KEY_PENDING_JSON)
        if (pending.length() == 0) return false

        val payload = buildPayloadFromArray(pending)
        if (payload.isEmpty()) return false

        val content = "section_id=" + wimple.defaultSectionID +
            "&rows=" + TextUtils.htmlEncode(payload)

        val json: JSONObject? = try {
            wimple.invokeRESTAPI(RestAPIInvoker.HTTPMethod.POST, POST_PAYMENT_PATH, content)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "[forward] REST invocation failed", e)
            null
        }

        val success = json != null && json.optString("code").startsWith("2")
        if (success) {
            // Only clear items we actually sent. New notifications that arrived during the HTTP
            // call are in `stored` (not pending) so they're safely preserved.
            synchronized(this) {
                prefs.edit().putString(KEY_PENDING_JSON, "[]").apply()
            }
            Log.d(LOG_TAG, "[forward] success, cleared ${pending.length()} pending notifications")
        } else {
            val msg = json?.optString("message") ?: "null response"
            Log.e(LOG_TAG, "[forward] failed - $msg, pending preserved for retry")
        }
        return success
    }

    private fun buildPayloadFromArray(arr: JSONArray): String {
        val sb = StringBuilder()
        val fmt = DateFormatUtils.getSMSDateFormat()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            val text = o.optString("text")
            if (title.isNotEmpty()) sb.append(title).append(": ")
            sb.append(text).append('\n')
            sb.append(fmt.format(Date(o.optLong("t")))).append('\n')
        }
        return sb.toString()
    }

    private fun arrayToList(arr: JSONArray): List<StoredNotification> {
        val list = ArrayList<StoredNotification>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(StoredNotification(
                time = o.optLong("t"),
                packageName = o.optString("p"),
                title = o.optString("title"),
                text = o.optString("text")
            ))
        }
        return list
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadArray(prefs: SharedPreferences, key: String): JSONArray {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
