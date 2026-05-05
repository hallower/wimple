package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local review queue for bank notifications. Sits in parallel to [BankNotifications] which
 * forwards captured notifications to Whooing's `outside.json` endpoint — items here are NOT
 * auto-forwarded; the user reviews and confirms each one inside Wimple, and the confirmed
 * entry is created via the normal `WimpleImpl.makeEntry()` path.
 *
 * Storage is a single JSONArray under [PREFS_NAME] / [KEY_QUEUE_JSON]. Each item carries a
 * stable [ReviewItem.id] so the UI can target removals after async classification — index-
 * based removal would race with new notifications arriving while the review screen is open.
 */
object LocalReviewQueue {

    private const val LOG_TAG = "LocalReviewQueue"
    private const val PREFS_NAME = "wimple.localreview"
    private const val KEY_QUEUE_JSON = "queue_json"

    data class ReviewItem(
        val id: String,
        val time: Long,
        val packageName: String,
        val appLabel: String,
        val title: String,
        val text: String
    )

    /** Result of [add]: queue size after the call + whether this call actually persisted a new entry. */
    data class AddResult(val count: Int, val added: Boolean)

    @Synchronized
    fun add(ctx: Context, pkg: String, appLabel: String, title: String, text: String,
            time: Long = System.currentTimeMillis()): AddResult {
        val prefs = prefs(ctx)
        val arr = loadArray(prefs)

        // Dedupe identical-to-last — same rationale as BankNotifications: Android can repost the
        // same notification on panel expand/collapse / OEM quirks, and we don't want the user
        // to see two identical review cards.
        if (arr.length() > 0) {
            val last = arr.optJSONObject(arr.length() - 1)
            if (last != null
                && last.optString("p") == pkg
                && last.optString("title") == title
                && last.optString("text") == text) {
                Log.d(LOG_TAG, "[add] duplicate of last queued entry (pkg=$pkg), skipping")
                return AddResult(arr.length(), added = false)
            }
        }

        val obj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("t", time)
            put("p", pkg)
            put("label", appLabel)
            put("title", title)
            put("text", text)
        }
        arr.put(obj)
        prefs.edit().putString(KEY_QUEUE_JSON, arr.toString()).apply()

        Log.d(LOG_TAG, "[add] queued pkg=$pkg label='$appLabel' title='$title' (count=${arr.length()})")
        return AddResult(arr.length(), added = true)
    }

    fun getAll(ctx: Context): List<ReviewItem> = arrayToList(loadArray(prefs(ctx)))

    fun count(ctx: Context): Int = loadArray(prefs(ctx)).length()

    @Synchronized
    fun removeById(ctx: Context, id: String): Boolean {
        val prefs = prefs(ctx)
        val arr = loadArray(prefs)
        val newArr = JSONArray()
        var removed = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                removed = true
            } else {
                newArr.put(o)
            }
        }
        if (removed) {
            prefs.edit().putString(KEY_QUEUE_JSON, newArr.toString()).apply()
        }
        return removed
    }

    @Synchronized
    fun clear(ctx: Context) {
        prefs(ctx).edit().putString(KEY_QUEUE_JSON, "[]").apply()
    }

    // -------------------- Internals --------------------

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadArray(prefs: SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY_QUEUE_JSON, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun arrayToList(arr: JSONArray): List<ReviewItem> {
        val out = ArrayList<ReviewItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                ReviewItem(
                    id = o.optString("id"),
                    time = o.optLong("t"),
                    packageName = o.optString("p"),
                    appLabel = o.optString("label"),
                    title = o.optString("title"),
                    text = o.optString("text")
                )
            )
        }
        return out
    }
}
