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

    @Test
    fun clear_emptiesTable() {
        handler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201")
        handler.upsert("CU 신촌점", "expense", "expenses", "x102", "assets", "x202")

        handler.clear()

        assertEquals(0, handler.findAll().size)
    }
}
