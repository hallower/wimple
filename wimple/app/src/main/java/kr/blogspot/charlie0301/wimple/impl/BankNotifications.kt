package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object BankNotifications {

    private const val LOG_TAG = "BankNotifications"
    private const val PREFS_NAME = "wimple.banknotifications"
    private const val KEY_STORED_JSON = "stored_json"
    private const val KEY_PENDING_JSON = "pending_json"
    private const val POST_PAYMENT_PATH = "api/entries/outside.json"

    // Default-SharedPreferences key for user-added (custom) monitored apps, stored as an
    // ordered JSON array string so we can sort by insertion order in the settings UI.
    private const val KEY_CUSTOM_APPS = "pref_bankNotiCustomApps"

    private val sending = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class StoredNotification(
        val time: Long,
        val packageName: String,
        val appLabel: String,
        val title: String,
        val text: String
    )

    @Synchronized
    fun add(ctx: Context, pkg: String, appLabel: String, title: String, text: String,
            time: Long = System.currentTimeMillis()): Int {
        val prefs = prefs(ctx)
        val arr = loadArray(prefs, KEY_STORED_JSON)
        val obj = JSONObject().apply {
            put("t", time)
            put("p", pkg)
            put("label", appLabel)   // captured here so the payload carries the bank name
                                      // even if the source app is later uninstalled
            put("title", title)
            put("text", text)
        }
        arr.put(obj)
        prefs.edit().putString(KEY_STORED_JSON, arr.toString()).apply()

        Log.d(LOG_TAG, "[add] new item saved:")
        Log.d(LOG_TAG, "       pkg   = $pkg")
        Log.d(LOG_TAG, "       label = '$appLabel'")
        Log.d(LOG_TAG, "       title = '$title'")
        Log.d(LOG_TAG, "       text  = '$text'")
        Log.d(LOG_TAG, "       time  = $time (${Date(time)})")
        val pendingArr = loadArray(prefs, KEY_PENDING_JSON)
        Log.d(LOG_TAG, "[add] counts: stored=${arr.length()}, pending=${pendingArr.length()}, total=${arr.length() + pendingArr.length()}")
        dumpArray("stored", arr)
        dumpArray("pending", pendingArr)
        return arr.length()
    }

    private fun dumpArray(label: String, arr: JSONArray) {
        if (arr.length() == 0) {
            Log.d(LOG_TAG, "       [$label] (empty)")
            return
        }
        Log.d(LOG_TAG, "       [$label] contents:")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            Log.d(LOG_TAG, "         [$i] [${o.optString("label")}] ${o.optString("p")} | title='${o.optString("title")}' text='${o.optString("text")}'")
        }
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

        // IMPORTANT: acquire the send lock BEFORE touching pending/stored. If another send
        // is already in progress, we must not merge stored→pending, because the in-flight
        // send will clear the whole pending key on success, which would silently drop the
        // freshly-merged items. Leaving them in stored lets the next forward call (threshold
        // or retryIfPending) handle them.
        if (!sending.compareAndSet(false, true)) {
            val prefs = prefs(ctx)
            Log.d(LOG_TAG, "[forward] another send in progress, skipping (stored=${loadArray(prefs, KEY_STORED_JSON).length()} preserved)")
            return false
        }

        val prefs = prefs(ctx)
        val mergedPendingSize = mergeStoredIntoPending(prefs)
        if (mergedPendingSize == 0) {
            Log.d(LOG_TAG, "[forward] nothing to send")
            sending.set(false)
            return false
        }
        Log.d(LOG_TAG, "[forward] starting send: $mergedPendingSize items moved into pending")
        dumpArray("pending-to-send", loadArray(prefs, KEY_PENDING_JSON))

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

    /**
     * Retry a previously-failed send batch on app start/resume.
     *
     * Only fires when [pendingCount] > 0 — i.e. a real failed/interrupted batch exists.
     * Must NOT trigger on stored-only (freshly captured, threshold not yet met) because
     * that would bypass the user's "batch size" preference: a single captured notification
     * would get sent the moment the user opens the app.
     *
     * Stored items that haven't hit the threshold simply wait. They'll be flushed when
     * (a) threshold is reached, (b) user taps "지금 전송", or (c) a retry-triggered send
     * happens to merge them in (which is fine — we were making an HTTP call anyway).
     */
    fun retryIfPending(ctx: Context) {
        if (pendingCount(ctx) == 0) return
        Log.d(LOG_TAG, "[retryIfPending] pending=${pendingCount(ctx)} — retrying failed batch")
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

        Log.d(LOG_TAG, "[send] posting ${pending.length()} items to Whooing, payload bytes=${payload.length}")
        Log.d(LOG_TAG, "[send] payload:\n$payload")

        val content = "section_id=" + wimple.defaultSectionID +
            "&rows=" + URLEncoder.encode(payload, "UTF-8")

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

    /**
     * Build the `rows` payload for api/entries/outside.json.
     *
     * The Whooing API doc only specifies `rows = "외부데이터 내용 (예: 은행 SMS 문자 내용)"`,
     * no detailed format. The server parses SMS-style raw bank/card messages, so we mimic the
     * legacy (2019) SMSReceiver format as closely as possible:
     *
     *   {title} {text}\n
     *   MM/dd HH:mm\n         <- only appended if the message doesn't already contain a date
     *
     * Title and text are joined with a single space so the combined string reads like a flat
     * bank SMS (e.g. "출금 10,000원 입출금통장(1596) → 김철승 잔액 2,672,023원"). The server
     * parser looks for patterns like amounts, bank names, and MM/dd tokens; prepending "title:"
     * with a colon is non-standard and likely confuses it, so we drop that.
     *
     * Newlines inside a single notification's text are collapsed to spaces so each captured
     * notification lands on its own line — matching how multi-SMS batches were concatenated
     * in the original implementation.
     */
    private fun buildPayloadFromArray(arr: JSONArray): String {
        val sb = StringBuilder()
        val fmt = DateFormatUtils.getSMSDateFormat()
        val dateInMessageRegex = Regex("""\d{1,2}/\d{1,2}""")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label").trim()
            val title = o.optString("title").trim()
            val text = o.optString("text").replace(Regex("""[\r\n]+"""), " ").trim()

            // Prepend the source app label in [brackets] so the line reads like a real bank SMS
            // (e.g. "[KB국민은행] 출금 10,000원 ..."). The Whooing outside-input parser uses bank
            // name keywords to select per-bank parsing rules — without this hint, notifications
            // from banks whose name isn't in the title/text body can fall back to generic rules
            // that miss amount/account fields.
            val prefix = if (label.isNotEmpty()) "[$label] " else ""
            val body = when {
                title.isNotEmpty() && text.isNotEmpty() -> "$title $text"
                title.isNotEmpty() -> title
                else -> text
            }
            if (body.isEmpty()) continue
            val combined = prefix + body

            sb.append(combined).append('\n')

            // Legacy SMSReceiver logic: only append the capture timestamp if the message body
            // doesn't already contain a MM/dd token. Avoids duplicate dates confusing the parser.
            if (!dateInMessageRegex.containsMatchIn(combined)) {
                sb.append(fmt.format(Date(o.optLong("t")))).append('\n')
            }
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
                appLabel = o.optString("label"),
                title = o.optString("title"),
                text = o.optString("text")
            ))
        }
        return list
    }

    /**
     * Ordered list of user-added custom bank app package names.
     * Reads from DEFAULT SharedPreferences (not the banknotifications private file) because
     * the MultiSelectListPreference in settings.xml uses the default store.
     * Transparently migrates any legacy StringSet value to the new JSON-array format.
     */
    fun getCustomApps(ctx: Context): List<String> {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val raw: String = try {
            prefs.getString(KEY_CUSTOM_APPS, "[]") ?: "[]"
        } catch (_: ClassCastException) {
            // Legacy StringSet (pre-ordering) — migrate in place.
            val legacy = prefs.getStringSet(KEY_CUSTOM_APPS, emptySet()) ?: emptySet()
            val arr = JSONArray()
            for (p in legacy) arr.put(p)
            prefs.edit().remove(KEY_CUSTOM_APPS).putString(KEY_CUSTOM_APPS, arr.toString()).apply()
            arr.toString()
        }
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.optString(it) }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setCustomApps(ctx: Context, list: List<String>) {
        val arr = JSONArray()
        for (p in list) arr.put(p)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putString(KEY_CUSTOM_APPS, arr.toString())
            .apply()
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
