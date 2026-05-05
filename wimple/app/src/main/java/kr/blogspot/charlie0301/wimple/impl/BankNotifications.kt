package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object BankNotifications {

    private const val LOG_TAG = "BankNotifications"
    private const val PREFS_NAME = "wimple.banknotifications"
    private const val KEY_STORED_JSON = "stored_json"
    private const val KEY_PENDING_JSON = "pending_json"
    /**
     * Items the Whooing parser rejected with HTTP 400 ("지원하지 않는 형식"). Held aside so the
     * user can review them in settings, manually re-attempt forwarding (e.g. after Whooing adds
     * a parser for that bank's format), or delete. Capped via TTL + max-count pruning so this
     * store never grows unbounded — see [REJECTED_TTL_MS] and [MAX_REJECTED_ITEMS].
     */
    private const val KEY_REJECTED_JSON = "rejected_json"
    private const val POST_PAYMENT_PATH = "api/entries/outside.json"

    // Rejected-store retention bounds: drop entries older than 2 weeks, and keep at most 100
    // total (oldest-first eviction). The intent is the user reviews failures soon after they
    // happen — old rejections from formats Whooing will likely never parse aren't actionable.
    private const val REJECTED_TTL_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_REJECTED_ITEMS = 100

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

    /**
     * A notification that the Whooing parser rejected. Carries an [id] (stable across list
     * reloads, so multi-select works against a moving JSONArray) and the [rejectedTime] at
     * which the 400 response came back — used both for display and for TTL pruning.
     */
    data class RejectedNotification(
        val id: String,
        val rejectedTime: Long,
        val time: Long,
        val packageName: String,
        val appLabel: String,
        val title: String,
        val text: String
    )

    /** Result of [add]: current `stored` count + whether this call actually persisted a new entry. */
    data class AddResult(val count: Int, val added: Boolean)

    @Synchronized
    fun add(ctx: Context, pkg: String, appLabel: String, title: String, text: String,
            time: Long = System.currentTimeMillis()): AddResult {
        val prefs = prefs(ctx)
        val arr = loadArray(prefs, KEY_STORED_JSON)

        // Suppress immediate duplicates. Android can re-post identical notifications when the
        // user expands/dismisses the panel, when groups are reflowed, or due to OEM quirks —
        // without this guard the same transaction would be stored (and forwarded) twice.
        // We persist the comparison rather than holding it in memory like the reference does,
        // so dedup survives listener rebinds.
        if (arr.length() > 0) {
            val last = arr.optJSONObject(arr.length() - 1)
            if (last != null
                && last.optString("p") == pkg
                && last.optString("title") == title
                && last.optString("text") == text) {
                Log.d(LOG_TAG, "[add] duplicate of last stored entry (pkg=$pkg), skipping")
                return AddResult(arr.length(), added = false)
            }
        }

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
        return AddResult(arr.length(), added = true)
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
            .putString(KEY_REJECTED_JSON, "[]")
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
            val payload = BankNotificationPayloadBuilder.buildFromArray(g.items)
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
                    // Retrying identical bytes won't change the outcome, so we don't keep them
                    // in `pending` (which would block every future send). Instead we move them
                    // to the rejected store: the user can review them in settings, manually
                    // re-attempt later (in case Whooing adds parser support), or delete them.
                    val msg = json?.optString("message") ?: "no message"
                    Log.w(LOG_TAG, "[send] ${g.pkg} SERVER REJECTED code=$code msg='$msg' — " +
                        "moving ${g.items.length()} items to rejected store for user review.")
                    Log.w(LOG_TAG, "[send] ${g.pkg} rejected payload:\n$payload")
                    appendRejected(prefs, g.items)
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

    // -------------------- Rejected store --------------------

    /**
     * Returns all currently-held rejected notifications, oldest-first by rejection time.
     * Pruning runs as a side-effect so callers always see a fresh, bounded view.
     */
    @Synchronized
    fun getRejected(ctx: Context): List<RejectedNotification> {
        val prefs = prefs(ctx)
        val arr = pruneAndPersistRejected(prefs)
        val list = ArrayList<RejectedNotification>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(RejectedNotification(
                id = o.optString("id"),
                rejectedTime = o.optLong("rt"),
                time = o.optLong("t"),
                packageName = o.optString("p"),
                appLabel = o.optString("label"),
                title = o.optString("title"),
                text = o.optString("text")
            ))
        }
        return list
    }

    @Synchronized
    fun rejectedCount(ctx: Context): Int = pruneAndPersistRejected(prefs(ctx)).length()

    @Synchronized
    fun clearRejected(ctx: Context) {
        prefs(ctx).edit().putString(KEY_REJECTED_JSON, "[]").apply()
    }

    /** Remove the rejected entries with the given ids. Unknown ids are ignored. */
    @Synchronized
    fun removeRejectedByIds(ctx: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        val prefs = prefs(ctx)
        val arr = loadArray(prefs, KEY_REJECTED_JSON)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") !in ids) newArr.put(o)
        }
        prefs.edit().putString(KEY_REJECTED_JSON, newArr.toString()).apply()
    }

    /**
     * Move the selected rejected entries back into [KEY_PENDING_JSON] and trigger a forward.
     * Per spec: items the user explicitly chose to send are removed from the rejected list
     * immediately, regardless of forward outcome. If the server rejects them again, they'll
     * land back in the rejected store via the normal 400 path in [doSynchronousSend].
     *
     * Items are stripped of rejected-store-only fields (`id`, `rt`) before being re-queued
     * so the pending payload shape matches freshly-captured notifications.
     */
    @Synchronized
    fun resendRejectedByIds(ctx: Context, ids: Set<String>, onDone: ((Boolean) -> Unit)? = null): Boolean {
        if (ids.isEmpty()) return false
        val prefs = prefs(ctx)
        val rejected = loadArray(prefs, KEY_REJECTED_JSON)
        val pending = loadArray(prefs, KEY_PENDING_JSON)
        val newRejected = JSONArray()
        var moved = 0
        for (i in 0 until rejected.length()) {
            val o = rejected.optJSONObject(i) ?: continue
            if (o.optString("id") in ids) {
                pending.put(JSONObject().apply {
                    put("t", o.optLong("t"))
                    put("p", o.optString("p"))
                    put("label", o.optString("label"))
                    put("title", o.optString("title"))
                    put("text", o.optString("text"))
                })
                moved++
            } else {
                newRejected.put(o)
            }
        }
        if (moved == 0) return false
        prefs.edit()
            .putString(KEY_REJECTED_JSON, newRejected.toString())
            .putString(KEY_PENDING_JSON, pending.toString())
            .apply()
        Log.d(LOG_TAG, "[resend] moved $moved item(s) from rejected → pending; invoking forward")
        return forwardToWhooing(ctx, onDone)
    }

    /**
     * Append the items in [items] (each shaped {t,p,label,title,text}) into the rejected store
     * with a fresh `rt` (rejected time) and stable `id` per row, then prune.
     */
    private fun appendRejected(prefs: SharedPreferences, items: JSONArray) {
        if (items.length() == 0) return
        val arr = loadArray(prefs, KEY_REJECTED_JSON)
        val now = System.currentTimeMillis()
        for (i in 0 until items.length()) {
            val src = items.optJSONObject(i) ?: continue
            val o = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("rt", now)
                put("t", src.optLong("t"))
                put("p", src.optString("p"))
                put("label", src.optString("label"))
                put("title", src.optString("title"))
                put("text", src.optString("text"))
            }
            arr.put(o)
        }
        val pruned = pruneRejectedArray(arr)
        prefs.edit().putString(KEY_REJECTED_JSON, pruned.toString()).apply()
    }

    private fun pruneAndPersistRejected(prefs: SharedPreferences): JSONArray {
        val arr = loadArray(prefs, KEY_REJECTED_JSON)
        val pruned = pruneRejectedArray(arr)
        if (pruned.length() != arr.length()) {
            prefs.edit().putString(KEY_REJECTED_JSON, pruned.toString()).apply()
        }
        return pruned
    }

    /**
     * Drop entries older than [REJECTED_TTL_MS], then keep only the most recent
     * [MAX_REJECTED_ITEMS]. Order in the result is preserved as oldest-first.
     */
    private fun pruneRejectedArray(arr: JSONArray): JSONArray {
        if (arr.length() == 0) return arr
        val cutoff = System.currentTimeMillis() - REJECTED_TTL_MS
        val kept = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("rt") >= cutoff) kept.add(o)
        }
        // Keep only the newest MAX_REJECTED_ITEMS — drop the oldest excess.
        val overflow = kept.size - MAX_REJECTED_ITEMS
        val start = if (overflow > 0) overflow else 0
        val out = JSONArray()
        for (i in start until kept.size) out.put(kept[i])
        return out
    }

    // -------------------- Internals --------------------

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
