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

    /**
     * Forward pending notifications to Whooing as one POST per source app.
     *
     * Grouping rationale — from Whooing dev (흥반장) on the dev forum (posts 45307/45685):
     *   > "api로 보내주실 때에는 각 번호별로 해주시는게 좋을 것 같습니다."
     *   > "임시저장소에 보낼 때에 각 번호별로 보내는 것을 권장"
     *
     * The server parses each `rows` payload as SMS from a SINGLE sender with a single bank-
     * specific format. Mixing multiple banks/apps into one batch causes the parser to pick
     * one format and silently miss the others — this is exactly why 하나카드·우리카드 entries
     * used to disappear from the 임시저장소. Splitting per-package gives each group its own
     * parse pass.
     *
     * Response schema — confirmed from forum logs:
     *   { "code":"200", "message":"", "error_parameters":[], "rest_of_api":N,
     *     "results":{ "cnt": <recognized count> } }
     *
     * Failure isolation: each group is POSTed independently. On group failure, the items in
     * that group stay in pending for a later retry; successful groups are dropped. This way
     * a single bank's format quirk doesn't block the whole batch from getting through.
     */
    private fun doSynchronousSend(ctx: Context, wimple: WimpleImpl): Boolean {
        val prefs = prefs(ctx)
        val pending = loadArray(prefs, KEY_PENDING_JSON)
        if (pending.length() == 0) return false

        // Group pending items by source package while remembering each item's original index,
        // so we can precisely rebuild `pending` with only the failed items afterwards.
        data class Group(val pkg: String, val items: JSONArray, val originalIndices: List<Int>)
        val groupMap = LinkedHashMap<String, Pair<JSONArray, MutableList<Int>>>()
        for (i in 0 until pending.length()) {
            val o = pending.optJSONObject(i) ?: continue
            val pkg = o.optString("p").ifEmpty { "_unknown" }
            val entry = groupMap.getOrPut(pkg) { JSONArray() to mutableListOf() }
            entry.first.put(o)
            entry.second.add(i)
        }
        val groups = groupMap.map { (pkg, p) -> Group(pkg, p.first, p.second) }
        Log.d(LOG_TAG, "[send] split ${pending.length()} items into ${groups.size} per-package group(s): " +
            groups.joinToString { "${it.pkg}(${it.items.length()})" })

        val failedIndices = HashSet<Int>()
        var totalRecognized = 0
        val sectionId = wimple.defaultSectionID

        for (g in groups) {
            val payload = buildPayloadFromArray(g.items)
            if (payload.isEmpty()) {
                // Empty payload (e.g., all items had blank title+text) — treat as not-worth-retrying
                // and just drop, by not adding to failedIndices.
                continue
            }

            Log.d(LOG_TAG, "[send] → ${g.pkg} (${g.items.length()} items, ${payload.length} bytes)")
            Log.d(LOG_TAG, "[send] payload:\n$payload")

            val content = "section_id=" + sectionId +
                "&rows=" + URLEncoder.encode(payload, "UTF-8")

            val json: JSONObject? = try {
                wimple.invokeRESTAPI(RestAPIInvoker.HTTPMethod.POST, POST_PAYMENT_PATH, content)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "[send] REST invocation failed for ${g.pkg}", e)
                null
            }
            Log.d(LOG_TAG, "[send] ${g.pkg} response: $json")

            // Classify the response so we can decide retry vs drop. Whooing responses use
            // stringy codes ("200","400"); fall back to -1 if the response is null/malformed.
            val code = json?.optString("code")?.toIntOrNull() ?: -1
            when {
                code in 200..299 -> {
                    // results.cnt = number of entries the server actually recognized and stored
                    // in 임시저장소. cnt=0 with code=200 means accepted but unparsed — format
                    // mismatch for that bank; item is still lost from our side's perspective,
                    // and retrying won't help, so we let it drop with a prominent warning.
                    val cnt = json?.optJSONObject("results")?.optInt("cnt", -1) ?: -1
                    totalRecognized += cnt.coerceAtLeast(0)
                    if (cnt == 0) {
                        Log.w(LOG_TAG, "[send] ${g.pkg} code=200 but server recognized 0 entries — " +
                            "this bank's notification format is NOT being parsed. Report a sample " +
                            "to Whooing support with the payload below so they can add a parser:")
                        Log.w(LOG_TAG, "[send] ${g.pkg} unparsed payload:\n$payload")
                    } else {
                        Log.d(LOG_TAG, "[send] ${g.pkg} ok, results.cnt=$cnt (of ${g.items.length()} sent)")
                    }
                    // Don't add to failedIndices — items get cleared from pending.
                }
                code in 400..499 -> {
                    // Permanent rejection — "지원하지 않는 형식입니다." per Whooing docs.
                    // Retrying identical bytes won't change the outcome. Dropping prevents an
                    // infinite retry loop that would block every future send (e.g. a subscribed
                    // app sends non-transactional notifications like 카카오페이증권's stock price
                    // alerts, which the server legitimately can't parse as a transaction).
                    val msg = json?.optString("message") ?: "no message"
                    Log.w(LOG_TAG, "[send] ${g.pkg} SERVER REJECTED code=$code msg='$msg' — " +
                        "permanent failure, dropping ${g.items.length()} items (no retry). " +
                        "If this app's notifications ARE real transactions, report the payload below:")
                    Log.w(LOG_TAG, "[send] ${g.pkg} rejected payload:\n$payload")
                    // Don't add to failedIndices — drop from pending.
                }
                else -> {
                    // Network error (null json), 5xx, or unknown code — transient, worth retrying.
                    val msg = json?.optString("message") ?: "null response"
                    Log.e(LOG_TAG, "[send] ${g.pkg} transient failure code=$code msg='$msg' — " +
                        "${g.items.length()} items will be kept for retry")
                    failedIndices.addAll(g.originalIndices)
                }
            }
        }

        // Rebuild pending with only the items from groups that failed.
        val newPending = JSONArray()
        for (i in 0 until pending.length()) {
            if (i in failedIndices) newPending.put(pending.get(i))
        }
        synchronized(this) {
            prefs.edit().putString(KEY_PENDING_JSON, newPending.toString()).apply()
        }
        val clearedCount = pending.length() - newPending.length()
        Log.d(LOG_TAG, "[forward] done: groups=${groups.size}, " +
            "totalRecognized=$totalRecognized, cleared=$clearedCount, preservedForRetry=${newPending.length()}")

        // "Success" = no group failed. Partial success (some recognized, some not recognized
        // by server) still returns true — those items ARE gone from our side; whether the
        // server understood them is a server-parser issue surfaced via results.cnt in logs.
        return failedIndices.isEmpty()
    }

    /**
     * Build the `rows` payload for api/entries/outside.json.
     *
     * Format per line (one captured notification = one line):
     *
     *   {bank} {title?} {text} {MM/dd HH:mm?}\n
     *
     * Shape decisions based on diagnostic logs where group-by-package POSTs returned
     * `results.cnt` per group:
     *
     *  - Bank name leads, no brackets — mirrors real bank SMS convention.
     *  - **Title is dropped if it equals the bank label.** Some apps (e.g. MG새마을금고) use
     *    the app name as the notification title, which previously produced duplicated
     *    prefixes like "MG새마을금고 MG새마을금고 [입금] …" and threw off the parser.
     *  - Body stays as `title text` (single space join) when they differ, preserving the
     *    transaction-type + details shape that the parser's regex anchors on.
     *  - **Timestamp goes at the END of the line**, not the middle. The working format
     *    observed in logs for 하나은행 has all payload content flat and ends with the
     *    MM/dd token — matching that shape gives us our best shot at parser compatibility.
     *  - Timestamp is only appended when the body doesn't already contain an MM/dd token,
     *    to avoid duplicate dates confusing the parser.
     *  - Newlines → spaces, runs of 2+ spaces → single space: bank apps often pad for
     *    visual alignment (`"김철승         잔액 …"`) which breaks whitespace tokenization.
     */
    private fun buildPayloadFromArray(arr: JSONArray): String {
        val sb = StringBuilder()
        val fmt = DateFormatUtils.getSMSDateFormat()
        val dateInMessageRegex = Regex("""\d{1,2}/\d{1,2}""")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label").trim()
            val rawTitle = o.optString("title").trim()
            // Drop the title when it just restates the app label — otherwise MG새마을금고 and
            // similar apps that use the app name as the notification title would emit the
            // bank name twice in a row.
            val title = if (label.isNotEmpty() && rawTitle.equals(label, ignoreCase = true)) ""
                        else rawTitle
            val text = o.optString("text")
                .replace(Regex("""[\r\n]+"""), " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()

            val body = when {
                title.isNotEmpty() && text.isNotEmpty() -> "$title $text"
                title.isNotEmpty() -> title
                else -> text
            }
            if (body.isEmpty()) continue

            if (label.isNotEmpty()) sb.append(label).append(' ')
            sb.append(body)
            if (!dateInMessageRegex.containsMatchIn(body)) {
                sb.append(' ').append(fmt.format(Date(o.optLong("t"))))
            }
            sb.append('\n')
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

    /**
     * One-time migration for installs upgrading from the preset-list era. Any package name
     * still sitting in the monitored set (`KEY_BANK_NOTI_APPS`) but not in the user's custom
     * list gets appended to the custom list so it remains visible/manageable in the settings
     * screen. No-op once all monitored apps are accounted for.
     */
    fun migrateLegacyMonitoredApps(ctx: Context) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val monitored = prefs.getStringSet(
            kr.blogspot.charlie0301.wimple.BankNotificationListener.KEY_BANK_NOTI_APPS,
            emptySet()
        ) ?: emptySet()
        if (monitored.isEmpty()) return
        val existing = getCustomApps(ctx)
        val existingSet = existing.toHashSet()
        val missing = monitored.filter { it !in existingSet }
        if (missing.isEmpty()) return
        val merged = existing.toMutableList().apply { addAll(missing) }
        setCustomApps(ctx, merged)
        Log.d(LOG_TAG, "[migrate] added ${missing.size} legacy preset apps to custom list: $missing")
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
