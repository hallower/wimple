package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Developer-facing ring buffer of recent classifier runs. Each entry captures the full
 * prompt(s) and AI response(s) for a single notification so we can audit extraction quality
 * and similarity scoring against real bank traffic on a real device.
 *
 * Lives in a separate SharedPreferences file (`wimple.ailog`) — not the default file — so
 * the logout flow's blanket `getDefaultSharedPreferences().edit().clear()` doesn't reach it
 * incidentally. The logout handler still wipes this store explicitly via [clear] so prior-
 * user transactions don't leak across accounts. App reinstall covers itself: `allowBackup`
 * is off, so on first launch this file is absent.
 *
 * Visibility into the log is gated by a dev flag set via 10 taps on the OSS license entry
 * in settings — see [AboutSettingsFragment]. Flag itself lives in default SharedPreferences,
 * which logout DOES blanket-clear, so the dev menu hides automatically across user changes.
 */
object AiClassificationLog {

    private const val LOG_TAG = "AiClassificationLog"
    private const val PREFS_NAME = "wimple.ailog"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 30

    data class Stage(val label: String, val prompt: String, val response: String?)

    data class Entry(
        val timestamp: Long,
        val packageName: String,
        val appLabel: String,
        val notiTitle: String,
        val notiText: String,
        val stages: List<Stage>,
        val resultJson: String?,
        val durationMs: Long
    ) {
        fun summary(): String {
            val src = appLabel.ifBlank { packageName }
            val firstLine = notiText.lineSequence().firstOrNull()?.take(60).orEmpty()
            return "[$src] $firstLine"
        }
    }

    /**
     * Append a classifier run to the log, oldest-first eviction at [MAX_ENTRIES]. Cheap to
     * call from the IO dispatcher since the underlying apply() is async; the caller doesn't
     * have to await.
     */
    @Synchronized
    fun record(ctx: Context, entry: Entry) {
        try {
            val prefs = prefs(ctx)
            val arr = loadArray(prefs)
            arr.put(toJson(entry))
            // Drop oldest until we're at cap. Cheap because we're already going through the
            // whole array; rebuilding once per record is fine at this volume.
            while (arr.length() > MAX_ENTRIES) {
                val trimmed = JSONArray()
                for (i in 1 until arr.length()) trimmed.put(arr.get(i))
                prefs.edit().putString(KEY_ENTRIES, trimmed.toString()).apply()
                return
            }
            prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "record failed", t)
        }
    }

    fun getAll(ctx: Context): List<Entry> {
        val arr = loadArray(prefs(ctx))
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(fromJson(o) ?: continue)
        }
        return out
    }

    @Synchronized
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ENTRIES).apply()
    }

    /**
     * Pretty-printed JSON dump of every stored entry, intended for the
     * "export and email" flow in [AiClassificationLogActivity]. Reads the
     * underlying JSONArray and re-serializes with two-space indentation so
     * the resulting attachment is browsable in any text editor.
     */
    fun exportJson(ctx: Context): String =
        loadArray(prefs(ctx)).toString(2)

    // -------------------- Internals --------------------

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadArray(prefs: SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun toJson(e: Entry): JSONObject = JSONObject().apply {
        put("ts", e.timestamp)
        put("p", e.packageName)
        put("label", e.appLabel)
        put("title", e.notiTitle)
        put("text", e.notiText)
        put("duration_ms", e.durationMs)
        e.resultJson?.let { put("result", it) }
        val stagesArr = JSONArray()
        for (s in e.stages) {
            stagesArr.put(JSONObject().apply {
                put("label", s.label)
                put("prompt", s.prompt)
                s.response?.let { put("response", it) }
            })
        }
        put("stages", stagesArr)
    }

    private fun fromJson(o: JSONObject): Entry? {
        return try {
            val stagesArr = o.optJSONArray("stages") ?: JSONArray()
            val stages = ArrayList<Stage>(stagesArr.length())
            for (i in 0 until stagesArr.length()) {
                val s = stagesArr.optJSONObject(i) ?: continue
                stages.add(
                    Stage(
                        label = s.optString("label"),
                        prompt = s.optString("prompt"),
                        response = if (s.has("response") && !s.isNull("response"))
                            s.optString("response") else null
                    )
                )
            }
            Entry(
                timestamp = o.optLong("ts"),
                packageName = o.optString("p"),
                appLabel = o.optString("label"),
                notiTitle = o.optString("title"),
                notiText = o.optString("text"),
                stages = stages,
                resultJson = if (o.has("result") && !o.isNull("result"))
                    o.optString("result") else null,
                durationMs = o.optLong("duration_ms")
            )
        } catch (_: Exception) {
            null
        }
    }
}
