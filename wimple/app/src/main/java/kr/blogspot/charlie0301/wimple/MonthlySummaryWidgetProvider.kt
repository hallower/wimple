package kr.blogspot.charlie0301.wimple

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import kr.blogspot.charlie0301.wimple.impl.RestAPIInvoker
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

/**
 * Home-screen widget showing daily income / expense / net for one month.
 *
 * Render path is intentionally cache-first to keep the widget from flicker-
 * looping in launchers that reapply RemoteViews on partial updates:
 *   1. [renderWidget] always paints from the per-widget JSON cache in
 *      SharedPreferences. If the cache is missing the cells stay empty and
 *      the empty view shows a "loading" hint.
 *   2. [MonthlySummaryWidgetService.Factory] is also pure cache-read; the
 *      adapter rebinds that the framework triggers cost a single SharedPrefs
 *      read instead of an HTTP call, so they don't propagate.
 *   3. Network fetches run only when explicitly requested — first paint with
 *      no cache, the refresh button, or stepping into a month we haven't
 *      cached yet — and run on a background thread inside [triggerFetch].
 *      On completion we write the cache and call [renderWidget] +
 *      [AppWidgetManager.notifyAppWidgetViewDataChanged] once each, then
 *      stop. No partiallyUpdateAppWidget anywhere, so no rebind cascade.
 */
class MonthlySummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            renderWidget(context, appWidgetManager, id)
            // First paint with no cache → kick off a fetch so the widget
            // populates instead of sitting blank forever.
            val (year, month) = computeYearMonth(context, id)
            if (readCache(context, id, year, month) == null) {
                triggerFetch(context, id)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val mgr = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_PREV_MONTH -> {
                shiftMonthOffset(context, widgetId, -1)
                renderWidget(context, mgr, widgetId)
                val (y, m) = computeYearMonth(context, widgetId)
                if (readCache(context, widgetId, y, m) == null) {
                    triggerFetch(context, widgetId)
                } else {
                    mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_day_grid)
                }
            }
            ACTION_NEXT_MONTH -> {
                shiftMonthOffset(context, widgetId, 1)
                renderWidget(context, mgr, widgetId)
                val (y, m) = computeYearMonth(context, widgetId)
                if (readCache(context, widgetId, y, m) == null) {
                    triggerFetch(context, widgetId)
                } else {
                    mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_day_grid)
                }
            }
            ACTION_REFRESH -> {
                val (y, m) = computeYearMonth(context, widgetId)
                invalidateCache(context, widgetId, y, m)
                renderWidget(context, mgr, widgetId)
                triggerFetch(context, widgetId)
            }
            ACTION_GOTO_TODAY -> {
                // Reset offset to 0 (current month). If user was already on
                // current month, this is a no-op render — the cache hit means
                // no network call.
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(monthOffsetKey(widgetId), 0)
                    .apply()
                renderWidget(context, mgr, widgetId)
                val (y, m) = computeYearMonth(context, widgetId)
                if (readCache(context, widgetId, y, m) == null) {
                    triggerFetch(context, widgetId)
                } else {
                    mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_day_grid)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Resize signal — recompute text sizes and repaint. Cache stays valid
        // (data didn't change), so the GridView reload is a pure SharedPrefs read.
        renderWidget(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_day_grid)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove(monthOffsetKey(id))
            editor.remove(daySizeKey(id))
            editor.remove(amountSizeKey(id))
            // Clear all cached months for this widget id.
            for (key in prefs.all.keys) {
                if (key.startsWith("widget_data_${id}_")) editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun shiftMonthOffset(ctx: Context, widgetId: Int, delta: Int) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newOffset = prefs.getInt(monthOffsetKey(widgetId), 0) + delta
        prefs.edit().putInt(monthOffsetKey(widgetId), newOffset).apply()
    }

    /**
     * Paints the entire widget surface (header text + click intents + adapter
     * intent + footer totals + empty-view text) from the cache for the
     * currently-selected month. Safe to call from any thread because
     * [AppWidgetManager.updateAppWidget] is thread-safe and never triggers
     * its own re-fetch — the GridView's [Factory.onDataSetChanged] only does
     * cache reads.
     */
    private fun renderWidget(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(ctx.packageName, R.layout.widget_monthly_summary)

        // Compute responsive text sizes from the host's allocated dimensions
        // and persist the cell sizes so the GridView Factory can pick them up
        // when it inflates each cell.
        val sizes = computeAndStoreSizes(ctx, mgr, widgetId)

        val (year, month) = computeYearMonth(ctx, widgetId)
        views.setTextViewText(
            R.id.widget_month_label,
            ctx.getString(R.string.widget_month_label, year, month)
        )
        views.setTextViewTextSize(
            R.id.widget_month_label,
            TypedValue.COMPLEX_UNIT_SP,
            sizes.monthLabel
        )
        views.setTextViewTextSize(
            R.id.widget_total_income,
            TypedValue.COMPLEX_UNIT_SP,
            sizes.footer
        )
        views.setTextViewTextSize(
            R.id.widget_total_expense,
            TypedValue.COMPLEX_UNIT_SP,
            sizes.footer
        )
        views.setTextViewTextSize(
            R.id.widget_total_net,
            TypedValue.COMPLEX_UNIT_SP,
            sizes.footer
        )
        views.setTextViewTextSize(
            R.id.widget_empty_view,
            TypedValue.COMPLEX_UNIT_SP,
            sizes.footer
        )

        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            broadcastPendingIntent(ctx, widgetId, ACTION_PREV_MONTH)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            broadcastPendingIntent(ctx, widgetId, ACTION_NEXT_MONTH)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_refresh,
            broadcastPendingIntent(ctx, widgetId, ACTION_REFRESH)
        )
        // Tap the month label to jump back to the current month.
        views.setOnClickPendingIntent(
            R.id.widget_month_label,
            broadcastPendingIntent(ctx, widgetId, ACTION_GOTO_TODAY)
        )

        // Encoding the widgetId into the intent's data Uri is what makes the
        // platform cache one factory per widget instead of sharing a single
        // factory across every placed widget.
        val serviceIntent = Intent(ctx, MonthlySummaryWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_day_grid, serviceIntent)
        views.setEmptyView(R.id.widget_day_grid, R.id.widget_empty_view)

        val cache = readCache(ctx, widgetId, year, month)
        when {
            cache == null -> {
                // Either first-paint or just invalidated. The follow-up
                // triggerFetch will repaint when the network call returns.
                views.setTextViewText(
                    R.id.widget_empty_view,
                    ctx.getString(R.string.widget_loading)
                )
                views.setTextViewText(R.id.widget_total_income, "")
                views.setTextViewText(R.id.widget_total_expense, "")
                views.setTextViewText(R.id.widget_total_net, "")
            }
            !cache.authReady -> {
                views.setTextViewText(
                    R.id.widget_empty_view,
                    ctx.getString(R.string.widget_login_required)
                )
                views.setTextViewText(R.id.widget_total_income, "")
                views.setTextViewText(R.id.widget_total_expense, "")
                views.setTextViewText(R.id.widget_total_net, "")
            }
            else -> {
                views.setTextViewText(
                    R.id.widget_empty_view,
                    ctx.getString(R.string.widget_no_data)
                )
                views.setTextViewText(
                    R.id.widget_total_income,
                    ctx.getString(
                        R.string.widget_total_income,
                        formatCompact(cache.totalIncome)
                    )
                )
                views.setTextViewText(
                    R.id.widget_total_expense,
                    ctx.getString(
                        R.string.widget_total_expense,
                        formatCompact(-cache.totalExpense)
                    )
                )
                views.setTextViewText(
                    R.id.widget_total_net,
                    ctx.getString(
                        R.string.widget_total_net,
                        formatCompact(cache.totalIncome - cache.totalExpense)
                    )
                )
            }
        }

        mgr.updateAppWidget(widgetId, views)
    }

    /**
     * Background-thread network fetch + cache write. Once cache is populated
     * we repaint the chrome ([renderWidget]) so the footer totals update,
     * then ping the GridView so the [Factory] reloads cells from the new
     * cache. Both calls are one-shot; no broadcast loop possible.
     */
    private fun triggerFetch(ctx: Context, widgetId: Int) {
        val appCtx = ctx.applicationContext
        Thread {
            val (year, month) = computeYearMonth(appCtx, widgetId)
            val data = fetchMonth(year, month)
            writeCache(appCtx, widgetId, year, month, data)
            val mgr = AppWidgetManager.getInstance(appCtx)
            renderWidget(appCtx, mgr, widgetId)
            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_day_grid)
        }.start()
    }

    /**
     * Reads the host-allocated dimensions for [widgetId] and derives sp text
     * sizes so the calendar text scales as the user resizes the widget. The
     * cell-level sizes (day / amount) are persisted so [MonthlySummaryWidgetService.Factory]
     * can apply them per cell without re-reading the widget options itself.
     *
     * Sizing is the smaller of two budgets:
     *  - **width**: cell must fit ~5 amount characters ("+100만") horizontally.
     *  - **height**: each cell stacks 4 lines (day + income + expense + net),
     *    and 6 rows must fit between the header and footer chrome. Without the
     *    height check, large widgets get text taller than the row, so the
     *    bottom rows clip outside the widget bounds.
     */
    private fun computeAndStoreSizes(ctx: Context, mgr: AppWidgetManager, widgetId: Int): WidgetSizes {
        val opts = mgr.getAppWidgetOptions(widgetId)
        // MIN_WIDTH/MIN_HEIGHT are the portrait baseline (smallest dimension
        // the host will ever lay us out with). Using both keeps text fitting
        // when the device rotates to portrait without re-firing the options
        // change.
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat()
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250).toFloat()

        // 7 cells + 6×1dp grid gap + ~12dp root padding budget.
        val cellWidthDp = ((minWidthDp - 18f) / 7f).coerceAtLeast(20f)

        // Chrome budget: header (~36dp), weekday row (~22dp), footer (~22dp),
        // and a few dp of vertical padding/margins. Generous on purpose so a
        // narrowly-resized widget still leaves room for the grid.
        val chromeDp = 86f
        val gridHeightDp = (minHeightDp - chromeDp).coerceAtLeast(60f)
        // 5 inter-row 1dp gaps between 6 rows.
        val rowHeightDp = ((gridHeightDp - 5f) / 6f).coerceAtLeast(20f)

        // Amount text needs to fit ~5 chars worst-case ("+100만"). 0.55 is the
        // empirical char-width / textSize ratio for default-weight Roboto.
        val amountByWidth = cellWidthDp / (5f * 0.55f)
        // Per row stacks day + 3×amount with day at 1.4× = 4.4 amount-units of
        // text height + ~6dp cell padding. Solve: rowHeight = 4.4*amount + 6.
        val amountByHeight = (rowHeightDp - 6f) / 4.4f
        val amount = minOf(amountByWidth, amountByHeight).coerceIn(10f, 26f)
        // Day number sits alone on its own line; safe to make it ~40% larger
        // than the amount text without crowding it horizontally.
        val day = (amount * 1.4f).coerceIn(14f, 36f)
        // Header / footer are bound by total widget width, not cell width.
        val monthLabel = (minWidthDp / 18f).coerceIn(14f, 24f)
        val footer = (minWidthDp / 25f).coerceIn(11f, 18f)

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(daySizeKey(widgetId), day)
            .putFloat(amountSizeKey(widgetId), amount)
            .apply()

        return WidgetSizes(day = day, amount = amount, monthLabel = monthLabel, footer = footer)
    }

    private data class WidgetSizes(
        val day: Float,
        val amount: Float,
        val monthLabel: Float,
        val footer: Float
    )

    private fun broadcastPendingIntent(ctx: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(ctx, MonthlySummaryWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Unique data Uri per (widgetId, action) so PendingIntent.getBroadcast
            // returns distinct intents instead of folding them all into one.
            data = Uri.parse("wimple-widget://$widgetId/$action")
        }
        return PendingIntent.getBroadcast(
            ctx,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val PREFS_NAME = "widget_monthly_summary"
        const val ACTION_PREV_MONTH = "kr.blogspot.charlie0301.wimple.widget.PREV_MONTH"
        const val ACTION_NEXT_MONTH = "kr.blogspot.charlie0301.wimple.widget.NEXT_MONTH"
        const val ACTION_REFRESH = "kr.blogspot.charlie0301.wimple.widget.REFRESH"
        const val ACTION_GOTO_TODAY = "kr.blogspot.charlie0301.wimple.widget.GOTO_TODAY"

        private const val LOG_TAG = "MonthlySummaryWidget"

        // ---------- per-widget month state ----------

        private fun monthOffsetKey(id: Int) = "widget_${id}_month_offset"
        private fun daySizeKey(id: Int) = "widget_${id}_day_size"
        private fun amountSizeKey(id: Int) = "widget_${id}_amount_size"

        fun monthOffset(ctx: Context, widgetId: Int): Int =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(monthOffsetKey(widgetId), 0)

        fun computeYearMonth(ctx: Context, widgetId: Int): Pair<Int, Int> {
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset(ctx, widgetId)) }
            return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
        }

        /**
         * sp text size for the day-number line in each calendar cell,
         * computed by the provider from the widget's allocated width and
         * persisted so the GridView Factory can read it cell by cell.
         */
        fun cellDaySize(ctx: Context, widgetId: Int): Float =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(daySizeKey(widgetId), 16f)

        /**
         * sp text size for the income / expense / net lines in each cell.
         * See [cellDaySize] for the storage rationale.
         */
        fun cellAmountSize(ctx: Context, widgetId: Int): Float =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(amountSizeKey(widgetId), 12f)

        // ---------- cached monthly data ----------

        /** In-memory representation of one cached month. */
        data class MonthlyCacheData(
            /** day-of-month → (income, expense). Days with zero activity are absent. */
            val perDay: Map<Int, LongArray>,
            val totalIncome: Long,
            val totalExpense: Long,
            /** false ⇒ widget shows "login required" instead of zeros. */
            val authReady: Boolean,
            /** epoch millis, recorded for future stale-checking but not consulted today. */
            val ts: Long
        )

        private fun cacheKey(widgetId: Int, year: Int, month: Int): String =
            "widget_data_${widgetId}_${year}${month.toString().padStart(2, '0')}"

        fun readCache(ctx: Context, widgetId: Int, year: Int, month: Int): MonthlyCacheData? {
            val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(cacheKey(widgetId, year, month), null) ?: return null
            return try {
                val obj = JSONObject(raw)
                val perDay = HashMap<Int, LongArray>()
                val perDayObj = obj.optJSONObject("perDay") ?: JSONObject()
                val keys = perDayObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = perDayObj.optJSONArray(k) ?: continue
                    val d = k.toIntOrNull() ?: continue
                    perDay[d] = longArrayOf(arr.optLong(0, 0L), arr.optLong(1, 0L))
                }
                MonthlyCacheData(
                    perDay = perDay,
                    totalIncome = obj.optLong("totalIncome", 0L),
                    totalExpense = obj.optLong("totalExpense", 0L),
                    authReady = obj.optBoolean("authReady", true),
                    ts = obj.optLong("ts", 0L)
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "[cache] parse failed for widget=$widgetId $year-$month", e)
                null
            }
        }

        private fun writeCache(
            ctx: Context,
            widgetId: Int,
            year: Int,
            month: Int,
            data: MonthlyCacheData
        ) {
            val perDayObj = JSONObject()
            for ((day, pair) in data.perDay) {
                perDayObj.put(
                    day.toString(),
                    JSONArray().put(pair[0]).put(pair[1])
                )
            }
            val obj = JSONObject().apply {
                put("perDay", perDayObj)
                put("totalIncome", data.totalIncome)
                put("totalExpense", data.totalExpense)
                put("authReady", data.authReady)
                put("ts", data.ts)
            }
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(cacheKey(widgetId, year, month), obj.toString())
                .apply()
        }

        private fun invalidateCache(ctx: Context, widgetId: Int, year: Int, month: Int) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(cacheKey(widgetId, year, month))
                .apply()
        }

        // ---------- network fetch (background thread only) ----------

        private const val ENTRIES_ALL_PATH = "api/entries.json_array"

        /**
         * Synchronously fetches one month's worth of entries from Whooing and
         * aggregates them into per-day income/expense buckets. Caller is
         * responsible for running this off the main thread.
         */
        private fun fetchMonth(year: Int, month: Int): MonthlyCacheData {
            val wimple = WimpleImpl.getInstance()
            val now = System.currentTimeMillis()
            if (wimple == null ||
                wimple.isAuthed != true ||
                wimple.isInitializedFinished != true
            ) {
                return MonthlyCacheData(
                    perDay = emptyMap(),
                    totalIncome = 0L,
                    totalExpense = 0L,
                    authReady = false,
                    ts = now
                )
            }

            val firstOfMonth = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            val accountMap = wimple.accountIdMap

            val perDay = HashMap<Int, LongArray>()
            var totalIncome = 0L
            var totalExpense = 0L
            try {
                val sectionId = wimple.defaultSectionID ?: ""
                val startDate = DateFormatUtils.getServerDateString(firstOfMonth.timeInMillis)
                val endCal = (firstOfMonth.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, daysInMonth)
                }
                val endDate = DateFormatUtils.getServerDateString(endCal.timeInMillis)
                // limit=500 covers typical home-accounting volume per month;
                // heavy users would lose entries beyond that until we paginate.
                val path = "?section_id=$sectionId" +
                    "&start_date=$startDate" +
                    "&end_date=$endDate" +
                    "&limit=500"
                val json = wimple.invokeRESTAPI(
                    RestAPIInvoker.HTTPMethod.GET,
                    ENTRIES_ALL_PATH + path,
                    ""
                )
                if (json != null && json.optString("code").startsWith("2")) {
                    val rows = json.optJSONObject("results")?.optJSONArray("rows")
                    if (rows != null) {
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            val lAccountId = row.optString("l_account_id")
                            val rAccountId = row.optString("r_account_id")
                            // x0 is Whooing's tombstone for deleted entries.
                            if (lAccountId.equals("x0", ignoreCase = true) ||
                                rAccountId.equals("x0", ignoreCase = true)) continue
                            val item = row.optString("item")
                            // Auto-generated rebalancing entries — pl.json hides
                            // them too, so we do the same.
                            if (item.startsWith("Adjusted to close")) continue
                            val amount = parseAmount(row.optString("money")) ?: continue
                            val entryDate = row.optString("entry_date") // yyyyMMdd
                            val day = entryDate.takeLast(2).toIntOrNull() ?: continue
                            if (day < 1 || day > daysInMonth) continue

                            val lWhat = accountMap[lAccountId]?.what
                            val rWhat = accountMap[rAccountId]?.what

                            val arr = perDay.getOrPut(day) { longArrayOf(0L, 0L) }
                            // Whooing's bookkeeping convention:
                            //   income recorded on the right (credit)
                            //   expense recorded on the left (debit)
                            // Reversals (refunds) flip the sides → subtract.
                            // Pure asset/debt/capital legs are transfers and don't count.
                            when {
                                rWhat == "income" -> {
                                    arr[0] += amount; totalIncome += amount
                                }
                                lWhat == "income" -> {
                                    arr[0] -= amount; totalIncome -= amount
                                }
                                lWhat == "expenses" -> {
                                    arr[1] += amount; totalExpense += amount
                                }
                                rWhat == "expenses" -> {
                                    arr[1] -= amount; totalExpense -= amount
                                }
                            }
                        }
                    }
                } else {
                    Log.w(LOG_TAG, "[fetch] non-2xx response: code=${json?.optString("code")}")
                }
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "[fetch] failed", t)
            }

            return MonthlyCacheData(
                perDay = perDay,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                authReady = true,
                ts = now
            )
        }

        private fun parseAmount(s: String): Long? {
            if (s.isEmpty()) return null
            return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong()
        }

        // ---------- formatting helpers (also used by Factory) ----------

        /**
         * Compact KRW-style number formatting that fits inside ~50dp wide cells.
         * Bands chosen to keep widgets readable while staying recognizable to
         * Korean users (만 = 10⁴, 억 = 10⁸). Sub-만 amounts are shown as
         * "0.x만" so the unit is consistent across the calendar.
         */
        fun formatCompact(amount: Long): String {
            val sign = if (amount < 0) "-" else ""
            val abs = abs(amount)
            return when {
                abs == 0L -> "0"
                abs < 10_000L ->
                    "$sign${java.lang.String.format(java.util.Locale.US, "%.1f", abs / 10_000.0)}만"
                abs < 100_000_000L -> "$sign${abs / 10_000}만"
                else -> "$sign${abs / 100_000_000}억"
            }
        }

        /**
         * Income amount → green shade. Four bands (≤10만 / ≤30만 / ≤100만 / >100만)
         * map to progressively darker Material green (400 / 600 / 800 / 900) so
         * magnitude reads at a glance from across the home screen.
         */
        fun incomeColorFor(amount: Long): Int {
            val a = abs(amount)
            return when {
                a <= 100_000L -> 0xFF66BB6A.toInt()       // green 400
                a <= 300_000L -> 0xFF43A047.toInt()       // green 600
                a <= 1_000_000L -> 0xFF2E7D32.toInt()     // green 800
                else -> 0xFF1B5E20.toInt()                // green 900
            }
        }

        /**
         * Expense counterpart to [incomeColorFor]; same four bands mapped to
         * Material red 400 / 600 / 800 / 900.
         */
        fun expenseColorFor(amount: Long): Int {
            val a = abs(amount)
            return when {
                a <= 100_000L -> 0xFFEF5350.toInt()       // red 400
                a <= 300_000L -> 0xFFE53935.toInt()       // red 600
                a <= 1_000_000L -> 0xFFC62828.toInt()     // red 800
                else -> 0xFFB71C1C.toInt()                // red 900
            }
        }
    }
}
