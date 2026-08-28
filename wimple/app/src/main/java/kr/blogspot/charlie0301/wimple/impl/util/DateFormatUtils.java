package kr.blogspot.charlie0301.wimple.impl.util;

import android.content.res.Resources;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtils {
    private static final Locale locale = Resources.getSystem().getConfiguration().locale;
    private static final NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
    private static final DecimalFormat formatCalcNum = (DecimalFormat) nf;
    private static final NumberFormat nf2 = NumberFormat.getCurrencyInstance(locale);
    private static final DecimalFormat formatCalcNumNoPoint = (DecimalFormat) nf2;

    static {
        formatCalcNum.applyPattern("###,###.####");
        formatCalcNumNoPoint.applyPattern("###,###");
    }

    public static final Locale getDefaultLocale() {
        return locale;
    }

    public static final SimpleDateFormat getServerDateFormat() {
        return new SimpleDateFormat("yyyyMMdd", locale);
    }

    public static final SimpleDateFormat getGUIDateFormat() {
        return new SimpleDateFormat("yy-MM-dd E", locale);
    }

    public static final SimpleDateFormat getDBDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd", locale);
    }

    public static final SimpleDateFormat getSMSDateFormat() {
        return new SimpleDateFormat("MM/dd HH:mm", locale);
    }

    public static final NumberFormat getNumberFormat() {
        return nf;
    }

    public static final DecimalFormat getDecimalFormat() {
        return formatCalcNum;
    }

    public static final DecimalFormat getNoPointDecimalFormat() {
        return formatCalcNumNoPoint;
    }

    public static final String getCurrentDateString() {
        Long today = Calendar.getInstance().getTimeInMillis();
        return getServerDateFormat().format(today);
    }

    /**
     * Day-of-month component of an epoch-millis timestamp. Shared by the bank-notification
     * monthly-item matcher and the monthly-item list ordering so both read "due day" the
     * same way.
     */
    public static final int dayOfMonth(long epochMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMs);
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Day-of-month distance treating the month as a 31-slot circle, so due-day 1 and
     * due-day 31 are 1 apart instead of 30 — a plain |a-b| would otherwise rank a
     * tomorrow-if-wrapped due date as the furthest possible match instead of the nearest.
     * Used by the bank-notification monthly-item matcher for its symmetric due-day ±window
     * match (a notification can land a day or two either side of the due date).
     */
    public static final int dayDiffWrap(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, 31 - d);
    }

    /**
     * Forward-only rotation offset from day-of-month [from] to day-of-month [to], wrapping a
     * 31-slot ring: 0 when to==from (today), increasing through the rest of the month, then
     * continuing past the month boundary (day 31 → day 1) before finally reaching from-1
     * (yesterday) at the highest offset, 30. Unlike [dayDiffWrap] this is NOT symmetric — it's
     * "how many days forward from today, wrapping" rather than "how close either direction" —
     * which is what a calendar-order monthly-item list wants: today first, then tomorrow, …,
     * around through next month's first days, ending with yesterday last. Sorting ascending by
     * this offset reproduces exactly that order.
     */
    public static final int dayForwardOffset(int from, int to) {
        return ((to - from) % 31 + 31) % 31;
    }

    /** How many days in the past a due day is still shown as "overdue" before the list treats
     *  it as settled and rolls it over to next month's occurrence instead (see
     *  {@link #dayNextOccurrenceOffset}). */
    public static final int MONTHLY_OVERDUE_GRACE_DAYS = 3;

    /**
     * Signed day offset from today's day-of-month [today] to a monthly item's due day
     * [dueDay], for slotting its next occurrence into the same chronological line as real
     * ledger entries. A due day today or later this month keeps its natural positive
     * difference (due day 30 when today is 28 → +2). A due day already past but by at most
     * [MONTHLY_OVERDUE_GRACE_DAYS] stays a small negative number (due day 27 when today is 28
     * → −1) — still shown, on the assumption it likely hasn't been entered yet. A due day
     * overdue by MORE than the grace window is instead read as next month's occurrence,
     * wrapping forward by 31 (due day 1 when today is 28 → −27, past the −3 grace cutoff, so
     * becomes (1 − 28 + 31) = +4) — it no longer reads as "overdue", it reads as "next due in
     * 4 days". Sorting a monthly list DESCENDING by this offset puts the furthest-future item
     * at the top and the most-overdue-within-grace item at the bottom, right above where the
     * real entries (newest-first) begin.
     */
    public static final int dayNextOccurrenceOffset(int today, int dueDay) {
        int diff = dueDay - today;
        if (diff < -MONTHLY_OVERDUE_GRACE_DAYS) {
            diff += 31;
        }
        return diff;
    }

    public static final String getCurrentDateStringForSMS() {
        Long today = Calendar.getInstance().getTimeInMillis();
        return getSMSDateFormat().format(today);
    }

    public static final String getServerDateString(Long date) {
        return getServerDateFormat().format(date);
    }

    public static final String getServerDateString(String today) {
        Calendar cal = Calendar.getInstance();

        if (!today.isEmpty()) {
            try {
                String dateString = today;
                int pos = dateString.indexOf(".");
                if (pos > 0) {
                    dateString = dateString.substring(0, pos);
                }

                Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
                cal.setTime(date);

            } catch (Exception e) {
            }
        }

        return getServerDateString(cal.getTimeInMillis());
    }

    public static final String getServerDateString(String today, int days) {
        Calendar cal = Calendar.getInstance();

        if (!today.isEmpty()) {
            try {
                String dateString = today;
                int pos = dateString.indexOf(".");
                if (pos > 0) {
                    dateString = dateString.substring(0, pos);
                }

                Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
                cal.setTime(date);

            } catch (Exception e) {
            }
        }
        cal.add(Calendar.DAY_OF_MONTH, days);
        return getServerDateString(cal.getTimeInMillis());
    }

    public static final String getYesterdayDateString(Long today) {
        Calendar cal = Calendar.getInstance();

        if (today != 0L) {
            cal.setTime(new Date(today));
        }

        cal.add(Calendar.DAY_OF_MONTH, -1);

        return getServerDateString(cal.getTimeInMillis());
    }

    public static final String getLastMonthDateString(Long today) {
        Calendar cal = Calendar.getInstance();

        if (today != 0L) {
            cal.setTime(new Date(today));
        }

        cal.add(Calendar.MONTH, -1);

        return getServerDateString(cal.getTimeInMillis());
    }

    public static final String getLastMonthDateString(String today) {
        Calendar cal = Calendar.getInstance();

        if (!today.isEmpty()) {
            try {
                String dateString = today;
                int pos = dateString.indexOf(".");
                if (pos > 0) {
                    dateString = dateString.substring(0, pos);
                }

                Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
                cal.setTime(date);

            } catch (Exception e) {
            }
        }

        cal.add(Calendar.MONTH, -1);
        return getServerDateString(cal.getTimeInMillis());
    }


}
