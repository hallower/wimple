package kr.blogspot.charlie0301.wimple

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Calendar

/**
 * Cells-only adapter for the home-screen widget. The factory does NOT touch
 * the network — it only reads from the per-widget per-month JSON cache that
 * [MonthlySummaryWidgetProvider] populates. Whatever causes the framework
 * to call [Factory.onDataSetChanged] (initial bind, partial update, host
 * recreation, etc.) only triggers a single SharedPrefs read, so there is no
 * fetch-broadcast-rebind loop.
 */
class MonthlySummaryWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return Factory(applicationContext, widgetId)
    }

    private class Factory(
        private val ctx: Context,
        private val widgetId: Int
    ) : RemoteViewsFactory {

        // 7 columns × 6 rows always — months never need more, and a fixed
        // grid keeps cell sizing stable across month changes.
        private val gridSize = 42

        private var leadingEmpty: Int = 0
        private var daysInMonth: Int = 0
        private var perDay: Map<Int, LongArray> = emptyMap()
        // false ⇒ getCount() returns 0 so the framework swaps in the empty
        // view (which Provider has seeded with "loading" / "login required").
        private var hasUsableData: Boolean = false
        // Cached at onDataSetChanged time so getViewAt — which can fire dozens
        // of times per refresh — doesn't re-hit SharedPreferences per cell.
        private var daySizeSp: Float = 16f
        private var amountSizeSp: Float = 12f

        override fun onCreate() {}
        override fun onDestroy() {
            perDay = emptyMap()
        }

        // Return 0 when there's nothing to show so setEmptyView surfaces the
        // loading / login-required text. Returning 42 unconditionally was
        // letting the GridView render a fully-empty calendar shell during
        // loading, which on resize occasionally pushed the empty view into
        // half the column and made the grid look like it dropped off-screen.
        override fun getCount(): Int = if (hasUsableData) gridSize else 0
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
        override fun getLoadingView(): RemoteViews? = null

        override fun onDataSetChanged() {
            val (year, month) = MonthlySummaryWidgetProvider.computeYearMonth(ctx, widgetId)

            val firstOfMonth = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Sunday-first grid: SUNDAY=1 → 0 leading cells, MONDAY=2 → 1, …
            leadingEmpty = firstOfMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

            val cache = MonthlySummaryWidgetProvider.readCache(ctx, widgetId, year, month)
            perDay = cache?.perDay ?: emptyMap()
            // Show the grid as soon as we have a successful (auth-ready) fetch
            // for this month, even if the user had zero transactions — the
            // calendar layout itself is still useful information.
            hasUsableData = cache != null && cache.authReady

            daySizeSp = MonthlySummaryWidgetProvider.cellDaySize(ctx, widgetId)
            amountSizeSp = MonthlySummaryWidgetProvider.cellAmountSize(ctx, widgetId)
        }

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_day_cell)
            val cellDay = position - leadingEmpty + 1

            // Out-of-range cells (before day 1 or after last day) render blank.
            if (cellDay < 1 || cellDay > daysInMonth) {
                views.setTextViewText(R.id.widget_cell_day, "")
                views.setTextViewText(R.id.widget_cell_income, "")
                views.setTextViewText(R.id.widget_cell_expense, "")
                views.setTextViewText(R.id.widget_cell_net, "")
                return views
            }

            views.setTextViewText(R.id.widget_cell_day, cellDay.toString())
            views.setTextViewTextSize(R.id.widget_cell_day, TypedValue.COMPLEX_UNIT_SP, daySizeSp)
            views.setTextViewTextSize(R.id.widget_cell_income, TypedValue.COMPLEX_UNIT_SP, amountSizeSp)
            views.setTextViewTextSize(R.id.widget_cell_expense, TypedValue.COMPLEX_UNIT_SP, amountSizeSp)
            views.setTextViewTextSize(R.id.widget_cell_net, TypedValue.COMPLEX_UNIT_SP, amountSizeSp)
            // Korean calendar convention: Sunday red, Saturday blue.
            when (position % 7) {
                0 -> views.setTextColor(R.id.widget_cell_day, COLOR_SUNDAY)
                6 -> views.setTextColor(R.id.widget_cell_day, COLOR_SATURDAY)
                // Weekdays leave the XML-default ?attr/textColorPrimary in place.
            }

            val pair = perDay[cellDay]
            val income = pair?.get(0) ?: 0L
            val expense = pair?.get(1) ?: 0L

            if (income == 0L && expense == 0L) {
                views.setTextViewText(R.id.widget_cell_income, "")
                views.setTextViewText(R.id.widget_cell_expense, "")
                views.setTextViewText(R.id.widget_cell_net, "")
            } else {
                if (income != 0L) {
                    views.setTextViewText(
                        R.id.widget_cell_income,
                        "+${MonthlySummaryWidgetProvider.formatCompact(income)}"
                    )
                    views.setTextColor(
                        R.id.widget_cell_income,
                        MonthlySummaryWidgetProvider.incomeColorFor(income)
                    )
                } else {
                    views.setTextViewText(R.id.widget_cell_income, "")
                }

                if (expense != 0L) {
                    views.setTextViewText(
                        R.id.widget_cell_expense,
                        "-${MonthlySummaryWidgetProvider.formatCompact(expense)}"
                    )
                    views.setTextColor(
                        R.id.widget_cell_expense,
                        MonthlySummaryWidgetProvider.expenseColorFor(expense)
                    )
                } else {
                    views.setTextViewText(R.id.widget_cell_expense, "")
                }

                views.setTextViewText(
                    R.id.widget_cell_net,
                    MonthlySummaryWidgetProvider.formatCompact(income - expense)
                )
            }
            return views
        }
    }

    companion object {
        private const val COLOR_SUNDAY = 0xFFD32F2F.toInt()
        private const val COLOR_SATURDAY = 0xFF1976D2.toInt()
    }
}
