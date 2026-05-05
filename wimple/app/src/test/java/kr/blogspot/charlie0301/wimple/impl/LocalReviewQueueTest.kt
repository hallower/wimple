package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalReviewQueueTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LocalReviewQueue.clear(context)
    }

    @Test
    fun add_skipsImmediateDuplicateNotification() {
        val first = LocalReviewQueue.add(
            context,
            pkg = "com.bank.app",
            appLabel = "테스트은행",
            title = "입금",
            text = "1,000원",
            time = 1000L
        )
        val duplicate = LocalReviewQueue.add(
            context,
            pkg = "com.bank.app",
            appLabel = "테스트은행",
            title = "입금",
            text = "1,000원",
            time = 2000L
        )

        assertTrue(first.added)
        assertFalse(duplicate.added)
        assertEquals(1, LocalReviewQueue.count(context))
    }

    @Test
    fun add_assignsStableUniqueIdsPerItem() {
        LocalReviewQueue.add(context, "com.bank.one", "은행1", "입금", "1,000원", 1000L)
        LocalReviewQueue.add(context, "com.bank.two", "은행2", "출금", "2,000원", 2000L)

        val items = LocalReviewQueue.getAll(context)
        assertEquals(2, items.size)
        assertNotEquals(items[0].id, items[1].id)
        assertTrue(items[0].id.isNotEmpty())
    }

    @Test
    fun removeById_removesOnlyTargetedItem() {
        LocalReviewQueue.add(context, "com.bank.one", "은행1", "입금", "1,000원", 1000L)
        LocalReviewQueue.add(context, "com.bank.two", "은행2", "출금", "2,000원", 2000L)

        val targetId = LocalReviewQueue.getAll(context)[0].id
        val removed = LocalReviewQueue.removeById(context, targetId)

        assertTrue(removed)
        val remaining = LocalReviewQueue.getAll(context)
        assertEquals(1, remaining.size)
        assertEquals("com.bank.two", remaining[0].packageName)
    }

    @Test
    fun removeById_returnsFalseForUnknownId() {
        LocalReviewQueue.add(context, "com.bank.one", "은행1", "입금", "1,000원", 1000L)

        val removed = LocalReviewQueue.removeById(context, "nonexistent-id")

        assertFalse(removed)
        assertEquals(1, LocalReviewQueue.count(context))
    }

    @Test
    fun clear_emptiesQueue() {
        LocalReviewQueue.add(context, "com.bank.one", "은행1", "입금", "1,000원", 1000L)
        LocalReviewQueue.add(context, "com.bank.two", "은행2", "출금", "2,000원", 2000L)

        LocalReviewQueue.clear(context)

        assertEquals(0, LocalReviewQueue.count(context))
        assertTrue(LocalReviewQueue.getAll(context).isEmpty())
    }
}
