package kr.blogspot.charlie0301.wimple.impl.db

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExtractionExampleDBHandlerTest {

    private lateinit var context: Context
    private lateinit var handler: ExtractionExampleDBHandler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        handler = ExtractionExampleDBHandler(context)
        handler.clear()
    }

    @After
    fun tearDown() {
        handler.clear()
    }

    @Test
    fun upsert_insertsNewExampleWithHitCountOne() {
        handler.upsert("[KB카드] GS25 강남점 12,000원 출금", "expense", "GS25 강남점", 12000L, now = 1000L)

        val all = handler.findAll()
        assertEquals(1, all.size)
        val ex = all[0]
        assertEquals("expense", ex.kind)
        assertEquals("GS25 강남점", ex.merchant)
        assertEquals(12000L, ex.amount)
        assertEquals(1000L, ex.lastUsed)
        assertEquals(1, ex.hitCount)
    }

    @Test
    fun upsert_sameBodyAndKind_bumpsHitCountAndOverwritesFields() {
        handler.upsert("[KB카드] GS25 강남점 12,000원 출금", "expense", "GS25 강남점", 12000L, now = 1000L)
        // Same body re-confirmed but with corrected merchant/amount — must replace fields,
        // not duplicate the row.
        handler.upsert("[KB카드] GS25 강남점 12,000원 출금", "expense", "GS25", 12500L, now = 2000L)

        val all = handler.findAll()
        assertEquals(1, all.size)
        val ex = all[0]
        assertEquals("GS25", ex.merchant)
        assertEquals(12500L, ex.amount)
        assertEquals(2000L, ex.lastUsed)
        assertEquals(2, ex.hitCount)
    }

    @Test
    fun upsert_normalizesBodyForDedup() {
        handler.upsert(" [kb카드]  GS25 강남점 12,000원 출금 ", "expense", "GS25 강남점", 12000L)
        handler.upsert("[KB카드] gs25 강남점 12,000원 출금", "expense", "GS25 강남점", 12000L)

        // Whitespace + casing-only differences must collide on the same row.
        assertEquals(1, handler.findAll().size)
        assertEquals(2, handler.findAll()[0].hitCount)
    }

    @Test
    fun upsert_distinctKindsAtSameBodyStoredSeparately() {
        // Contrived but defends the kind portion of the PK — a refund-style notification
        // with the same body skeleton shouldn't overwrite the expense row.
        handler.upsert("body", "expense", "Foo", 100L)
        handler.upsert("body", "income", "Foo", 100L)

        assertEquals(2, handler.findAll().size)
    }

    @Test
    fun upsert_blankInputsAreIgnored() {
        handler.upsert("   ", "expense", "Foo", 100L)
        handler.upsert("body", "", "Foo", 100L)
        handler.upsert("body", "expense", "", 100L)
        handler.upsert("body", "expense", "Foo", 0L)
        handler.upsert("body", "expense", "Foo", -1L)

        assertEquals(0, handler.count())
    }

    @Test
    fun upsert_storedTextIsCappedToMaxLen() {
        val longBody = "X".repeat(ExtractionExampleDBHandler.MAX_TEXT_LEN + 50)
        handler.upsert(longBody, "expense", "Foo", 100L)

        val stored = handler.findAll()[0].notificationText
        assertEquals(ExtractionExampleDBHandler.MAX_TEXT_LEN, stored.length)
    }

    @Test
    fun pickShots_emptyDb_returnsEmpty() {
        assertEquals(0, handler.pickShots(3).size)
    }

    @Test
    fun pickShots_prefersDiverseKinds() {
        // Heavily skewed expense pool with one income — diversity pass must surface income
        // even though every expense row outranks it on hit_count.
        handler.upsert("e1", "expense", "M1", 100L, now = 1000L)
        handler.upsert("e1", "expense", "M1", 100L, now = 2000L) // hit_count 2
        handler.upsert("e2", "expense", "M2", 200L, now = 3000L)
        handler.upsert("e3", "expense", "M3", 300L, now = 4000L)
        handler.upsert("i1", "income", "M4", 400L, now = 5000L)

        val shots = handler.pickShots(3)
        assertEquals(3, shots.size)
        // First shot is the highest hit_count expense, second pass picks income (new kind),
        // third tops up by overall hit_count.
        val kinds = shots.map { it.kind }.toSet()
        assertTrue("expected income to be surfaced; kinds=$kinds", "income" in kinds)
        assertTrue("expected expense; kinds=$kinds", "expense" in kinds)
    }

    @Test
    fun pickShots_capsAtRequestedSize() {
        handler.upsert("a", "expense", "A", 100L)
        handler.upsert("b", "expense", "B", 100L)
        handler.upsert("c", "expense", "C", 100L)
        handler.upsert("d", "expense", "D", 100L)
        handler.upsert("e", "expense", "E", 100L)

        assertEquals(3, handler.pickShots(3).size)
        assertEquals(1, handler.pickShots(1).size)
        assertEquals(0, handler.pickShots(0).size)
    }

    @Test
    fun pickShots_singleKindPool_topsUpByRanking() {
        // Diversity pass picks one expense; second pass must keep filling with expense
        // since that's all there is, instead of returning a short list.
        handler.upsert("a", "expense", "A", 100L, now = 1000L)
        handler.upsert("b", "expense", "B", 100L, now = 2000L)
        handler.upsert("c", "expense", "C", 100L, now = 3000L)

        val shots = handler.pickShots(3)
        assertEquals(3, shots.size)
        // All three distinct rows.
        assertEquals(3, shots.map { it.notificationText }.toSet().size)
    }

    @Test
    fun upsert_evictsOldestPastCap() {
        // Use an injected small cap so the eviction path is exercised without inserting
        // DEFAULT_MAX_EXAMPLES+1 rows. Both handlers operate on the same DB file, so the
        // setUp/tearDown clears still apply.
        val cap = 3
        val small = ExtractionExampleDBHandler(context, maxRows = cap)
        for (i in 0..cap) {
            small.upsert("body$i", "expense", "M$i", 100L, now = (1000 + i).toLong())
        }
        assertEquals(cap, small.count())
        // The oldest row (body0, lastUsed=1000) is gone; the newest (body3) is present.
        val texts = small.findAll().map { it.notificationText }.toSet()
        assertTrue("body$cap should still be present", "body$cap" in texts)
        assertTrue("body0 should have been evicted", "body0" !in texts)
    }

    @Test
    fun upsert_existingRowDoesNotTriggerEviction() {
        // Bumping an existing row's hit_count must not push the row count over the cap or
        // evict anything; only fresh inserts should trip eviction.
        val cap = 3
        val small = ExtractionExampleDBHandler(context, maxRows = cap)
        for (i in 0 until cap) {
            small.upsert("body$i", "expense", "M$i", 100L, now = (1000 + i).toLong())
        }
        assertEquals(cap, small.count())

        // Re-upsert the oldest. Should bump it to most-recent without changing row count.
        small.upsert("body0", "expense", "M0", 100L, now = 9999L)

        assertEquals(cap, small.count())
        val all = small.findAll()
        // findAll is ordered by last_used DESC, so body0 is now first.
        assertEquals("body0", all[0].notificationText)
        assertEquals(2, all[0].hitCount)
    }

    @Test
    fun defaultCap_isAtLeastOneThousand() {
        // Guards against an accidental cap regression — the production cap should comfortably
        // hold a deep, diverse pool of bank-notification formats.
        assertTrue(
            "DEFAULT_MAX_EXAMPLES should be at least 1000; was ${ExtractionExampleDBHandler.DEFAULT_MAX_EXAMPLES}",
            ExtractionExampleDBHandler.DEFAULT_MAX_EXAMPLES >= 1000
        )
    }

    @Test
    fun clear_emptiesTable() {
        handler.upsert("a", "expense", "A", 100L)
        handler.upsert("b", "income", "B", 200L)

        handler.clear()

        assertEquals(0, handler.count())
        assertEquals(0, handler.pickShots(3).size)
    }

    @Test
    fun normalizeBody_capsAt150Chars() {
        val long1 = "a".repeat(150) + "X"
        val long2 = "a".repeat(150) + "Y"
        // Bodies that diverge only past the 150-char cap dedup to the same row — by design
        // (trailing 안내 text is variable; the leading structure is what we want to dedup
        // on for prompt-shot purposes).
        assertEquals(
            ExtractionExampleDBHandler.normalizeBody(long1),
            ExtractionExampleDBHandler.normalizeBody(long2)
        )
    }

    @Test
    fun normalizeBody_distinguishesShortBodies() {
        assertNotEquals(
            ExtractionExampleDBHandler.normalizeBody("foo"),
            ExtractionExampleDBHandler.normalizeBody("bar")
        )
    }

    @Test
    fun normalizeBody_unifiesFullwidthAndStripsZeroWidth() {
        val raw = "ＧＳ２５ 강남점​ 12,000원"
        val canonical = "GS25 강남점 12,000원"
        // NFKC fullwidth-to-halfwidth + ZWSP strip should produce the same key as the
        // canonical form. Verifies the same encoding-artifact resilience that
        // MerchantMappingDBHandler.normalize provides, applied to body dedup.
        assertEquals(
            ExtractionExampleDBHandler.normalizeBody(canonical),
            ExtractionExampleDBHandler.normalizeBody(raw)
        )
    }

    @Test
    fun normalizeBody_preservesInteriorPunctuation() {
        // Interior punctuation must survive — distinct bodies should remain distinct after
        // normalize, otherwise the dedup pool would merge unrelated examples.
        assertNotEquals(
            ExtractionExampleDBHandler.normalizeBody("이마트.com 결제 12000"),
            ExtractionExampleDBHandler.normalizeBody("이마트 com 결제 12000")
        )
    }
}
