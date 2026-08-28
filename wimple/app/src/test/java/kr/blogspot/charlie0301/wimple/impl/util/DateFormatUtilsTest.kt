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
}
