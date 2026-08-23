package kr.blogspot.charlie0301.wimple.impl.db

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MerchantMappingDBHandlerTest {

    private lateinit var context: Context
    private lateinit var handler: MerchantMappingDBHandler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        handler = MerchantMappingDBHandler(context)
        handler.clear()
    }

    @After
    fun tearDown() {
        handler.clear()
    }

    @Test
    fun upsert_insertsNewMappingWithHitCountOne() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201", now = 1000L)

        val found = handler.find("GS25 강남점", "expense")
        assertNotNull(found)
        assertEquals("gs25 강남점", found!!.merchantNorm)
        assertEquals("expense", found.kind)
        assertEquals("x101", found.lAccountId)
        assertEquals("x201", found.rAccountId)
        assertEquals(1000L, found.lastUsed)
        assertEquals(1, found.hitCount)
    }

    @Test
    fun upsert_secondCallOverwritesAccountsAndIncrementsHitCount() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201", now = 1000L)
        handler.upsert("GS25 강남점", "expense", "expenses", "x102", "assets", "x202", now = 2000L)

        val found = handler.find("GS25 강남점", "expense")
        assertNotNull(found)
        assertEquals("x102", found!!.lAccountId)
        assertEquals("x202", found.rAccountId)
        assertEquals(2000L, found.lastUsed)
        assertEquals(2, found.hitCount)
    }

    @Test
    fun upsert_distinctKindsAtSameMerchantStoredSeparately() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201")
        handler.upsert("GS25 강남점", "income", "assets", "x201", "income", "x301")

        val expense = handler.find("GS25 강남점", "expense")
        val income = handler.find("GS25 강남점", "income")
        assertNotNull(expense)
        assertNotNull(income)
        assertEquals("x101", expense!!.lAccountId)
        assertEquals("x301", income!!.rAccountId)
    }

    @Test
    fun find_returnsNullWhenMissing() {
        assertNull(handler.find("Unknown Merchant", "expense"))
    }

    @Test
    fun normalize_collapsesWhitespaceAndLowercases() {
        handler.upsert("GS25  강남점 ", "expense", "expenses", "x101", "assets", "x201")

        // Lookup with different spacing / case should hit the same row.
        val found = handler.find("gs25 강남점", "expense")
        assertNotNull(found)
        assertEquals(1, found!!.hitCount)

        // Sanity: a different store does NOT collide.
        assertNull(handler.find("GS25 역삼점", "expense"))
    }

    @Test
    fun normalize_unifiesFullwidthAndHalfwidth() {
        // Fullwidth ASCII (FF21..FF5A range) sometimes appears via Korean IME composition or
        // copy-paste from receipt printers. NFKC collapses them.
        handler.upsert("ＧＳ２５ 강남점", "expense", "expenses", "x101", "assets", "x201")

        val found = handler.find("GS25 강남점", "expense")
        assertNotNull("fullwidth → halfwidth should collide", found)
        assertEquals(1, found!!.hitCount)
    }

    @Test
    fun normalize_stripsZeroWidthCharacters() {
        // Build with Kotlin Unicode escapes so the source stays grep-able rather than
        // embedding the actual invisible characters. Cover all four invisibles we strip:
        // ZWSP (U+200B), ZWNJ (U+200C), ZWJ (U+200D), BOM (U+FEFF) — exactly what rides
        // along on copy-paste from web-rendered notifications.
        val withInvisibles = "GS25​‌‍﻿ 강남점"
        handler.upsert(withInvisibles, "expense", "expenses", "x101", "assets", "x201")

        val found = handler.find("GS25 강남점", "expense")
        assertNotNull("invisibles should be stripped", found)
        assertEquals(1, found!!.hitCount)
    }

    @Test
    fun normalize_bracketsBecomeSpaces() {
        // Parens around the branch name should still collide with space-separated form,
        // since brackets get rewritten to whitespace before the collapse step.
        handler.upsert("GS25(강남점)", "expense", "expenses", "x101", "assets", "x201")

        val found = handler.find("GS25 강남점", "expense")
        assertNotNull("bracketed branch should collide with spaced form", found)
        assertEquals(1, found!!.hitCount)

        // But different branches inside brackets stay distinct — content survives, only
        // the bracket characters become spaces.
        assertNull(handler.find("GS25(역삼점)", "expense"))
    }

    @Test
    fun normalize_trimsLeadingAndTrailingPunctuation() {
        // "·이마트" / "이마트 " / "이마트." all collide once edge punctuation is trimmed,
        // while interior punctuation is preserved.
        handler.upsert("·이마트.", "expense", "expenses", "x101", "assets", "x201")

        assertNotNull(handler.find("이마트", "expense"))
        assertNotNull(handler.find(" 이마트 ", "expense"))
        // Interior dot survives — different merchants stay distinct.
        assertNull(handler.find("이.마.트", "expense"))
    }

    @Test
    fun normalize_doesNotMergeStoreSuffixes() {
        // Conservative normalize must NOT collapse "이마트" and "이마트 강남점" — that's a
        // policy change tracked separately as #2-B (brand/branch split).
        handler.upsert("이마트", "expense", "expenses", "x101", "assets", "x201")

        assertNull(handler.find("이마트 강남점", "expense"))
        assertNull(handler.find("이마트(주)", "expense"))
    }

    @Test
    fun upsert_blankInputsAreIgnored() {
        handler.upsert("   ", "expense", "expenses", "x101", "assets", "x201")
        handler.upsert("GS25 강남점", "", "expenses", "x101", "assets", "x201")

        assertEquals(0, handler.findAll().size)
    }

    @Test
    fun delete_removesMatchingRow() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201")
        handler.upsert("CU 신촌점", "expense", "expenses", "x102", "assets", "x202")

        val removed = handler.delete("GS25 강남점", "expense")

        assertTrue(removed)
        assertNull(handler.find("GS25 강남점", "expense"))
        assertNotNull(handler.find("CU 신촌점", "expense"))
    }

    @Test
    fun delete_returnsFalseForMissing() {
        assertFalse(handler.delete("Unknown", "expense"))
    }

    @Test
    fun findAll_orderedByLastUsedDescending() {
        handler.upsert("A", "expense", "expenses", "x101", "assets", "x201", now = 1000L)
        handler.upsert("B", "expense", "expenses", "x101", "assets", "x201", now = 3000L)
        handler.upsert("C", "expense", "expenses", "x101", "assets", "x201", now = 2000L)

        val all = handler.findAll()
        assertEquals(listOf("b", "c", "a"), all.map { it.merchantNorm })
    }

    // -------------------- restore (Task 5: 백업/복원) --------------------

    @Test
    fun restore_insertsNewRowFaithfullyWithoutBumpingHitCount() {
        handler.restore(
            MerchantMappingDBHandler.Mapping(
                merchantNorm = "gs25 강남점", kind = "expense",
                lAccountType = "expenses", lAccountId = "x101",
                rAccountType = "assets", rAccountId = "x201",
                lastUsed = 5000L, hitCount = 7
            )
        )

        val found = handler.find("GS25 강남점", "expense")
        assertNotNull(found)
        assertEquals(5000L, found!!.lastUsed)
        assertEquals(7, found.hitCount)
    }

    @Test
    fun restore_doesNotOverwriteWhenLocalHitCountIsHigherOrEqual() {
        // Locally learned more (hit_count 5) since the backup (hit_count 2) was taken.
        handler.upsert("GS25 강남점", "expense", "expenses", "x999", "assets", "x888", now = 9000L)
        repeat(4) { handler.upsert("GS25 강남점", "expense", "expenses", "x999", "assets", "x888", now = 9000L) }
        assertEquals(5, handler.find("GS25 강남점", "expense")!!.hitCount)

        handler.restore(
            MerchantMappingDBHandler.Mapping(
                merchantNorm = "gs25 강남점", kind = "expense",
                lAccountType = "expenses", lAccountId = "x101",
                rAccountType = "assets", rAccountId = "x201",
                lastUsed = 1000L, hitCount = 2
            )
        )

        val found = handler.find("GS25 강남점", "expense")!!
        assertEquals("x999", found.lAccountId)
        assertEquals(5, found.hitCount)
    }

    @Test
    fun restore_overwritesWhenBackupHitCountIsHigher() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x999", "assets", "x888", now = 1000L)
        assertEquals(1, handler.find("GS25 강남점", "expense")!!.hitCount)

        handler.restore(
            MerchantMappingDBHandler.Mapping(
                merchantNorm = "gs25 강남점", kind = "expense",
                lAccountType = "expenses", lAccountId = "x101",
                rAccountType = "assets", rAccountId = "x201",
                lastUsed = 5000L, hitCount = 7
            )
        )

        val found = handler.find("GS25 강남점", "expense")!!
        assertEquals("x101", found.lAccountId)
        assertEquals(7, found.hitCount)
    }

    @Test
    fun clear_emptiesTable() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201")
        handler.upsert("CU 신촌점", "expense", "expenses", "x102", "assets", "x202")

        handler.clear()

        assertEquals(0, handler.findAll().size)
    }
}
