package kr.blogspot.charlie0301.wimple.impl.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateFormatUtilsTest {

    @Test
    fun dayForwardOffset_today_isZero() {
        assertEquals(0, DateFormatUtils.dayForwardOffset(25, 25))
    }

    @Test
    fun dayForwardOffset_matchesWorkedExample_today25() {
        // 오늘 25일: 25, 26, ..., 31, 1, 2, ..., 24 — ascending offsets, 24 (yesterday) last.
        assertEquals(0, DateFormatUtils.dayForwardOffset(25, 25))
        assertEquals(1, DateFormatUtils.dayForwardOffset(25, 26))
        assertEquals(6, DateFormatUtils.dayForwardOffset(25, 31))
        assertEquals(7, DateFormatUtils.dayForwardOffset(25, 1))
        assertEquals(8, DateFormatUtils.dayForwardOffset(25, 2))
        assertEquals(30, DateFormatUtils.dayForwardOffset(25, 24))
    }

    @Test
    fun dayForwardOffset_sortingProducesTodayFirstYesterdayLast() {
        val today = 25
        val days = listOf(24, 1, 31, 25, 10, 26)
        val sorted = days.sortedBy { DateFormatUtils.dayForwardOffset(today, it) }
        assertEquals(listOf(25, 26, 31, 1, 10, 24), sorted)
    }

    @Test
    fun dayForwardOffset_isNotSymmetric() {
        // Forward-only: day-before-today is far (near 31), day-after-today is close (near 0) —
        // unlike dayDiffWrap, this deliberately differs by direction.
        assertEquals(1, DateFormatUtils.dayForwardOffset(10, 11))
        assertEquals(30, DateFormatUtils.dayForwardOffset(10, 9))
    }

    @Test
    fun dayDiffWrap_stillSymmetric_forWindowMatching() {
        assertEquals(1, DateFormatUtils.dayDiffWrap(31, 1))
        assertEquals(1, DateFormatUtils.dayDiffWrap(1, 31))
        assertEquals(0, DateFormatUtils.dayDiffWrap(15, 15))
    }

    // -------------------- dayNextOccurrenceOffset --------------------

    @Test
    fun dayNextOccurrenceOffset_matchesUserWorkedExample_today28() {
        val today = 28
        // 2일 > 1일 > 31일 > 30일 > 28일 > 27일 > 26일 — descending signed offset.
        assertEquals(5, DateFormatUtils.dayNextOccurrenceOffset(today, 2))
        assertEquals(4, DateFormatUtils.dayNextOccurrenceOffset(today, 1))
        assertEquals(3, DateFormatUtils.dayNextOccurrenceOffset(today, 31))
        assertEquals(2, DateFormatUtils.dayNextOccurrenceOffset(today, 30))
        assertEquals(0, DateFormatUtils.dayNextOccurrenceOffset(today, 28))
        assertEquals(-1, DateFormatUtils.dayNextOccurrenceOffset(today, 27))
        assertEquals(-2, DateFormatUtils.dayNextOccurrenceOffset(today, 26))
    }

    @Test
    fun dayNextOccurrenceOffset_sortingDescendingReproducesWorkedExample() {
        val today = 28
        val dueDays = listOf(26, 27, 28, 30, 31, 1, 2)
        val sorted = dueDays.sortedByDescending { DateFormatUtils.dayNextOccurrenceOffset(today, it) }
        assertEquals(listOf(2, 1, 31, 30, 28, 27, 26), sorted)
    }

    @Test
    fun dayNextOccurrenceOffset_staysNegativeWithinGraceWindow() {
        // Overdue by exactly the grace cutoff (3 days) still reads as "-3", not wrapped.
        assertEquals(-3, DateFormatUtils.dayNextOccurrenceOffset(28, 25))
    }

    @Test
    fun dayNextOccurrenceOffset_beyondGraceWindow_rollsOverToNextMonth() {
        // Overdue by 4+ days is read as "next month's occurrence" instead — still a positive
        // offset (3 days to month-end + 24 days into next month = 27), not a large negative one.
        assertEquals(27, DateFormatUtils.dayNextOccurrenceOffset(28, 24))
    }
}
