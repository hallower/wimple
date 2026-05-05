package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BankNotificationsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        BankNotifications.clear(context)
    }

    @Test
    fun add_skipsImmediateDuplicateNotification() {
        val first = BankNotifications.add(
            context,
            pkg = "com.bank.app",
            appLabel = "테스트은행",
            title = "입금",
            text = "1,000원",
            time = 1000L
        )
        val duplicate = BankNotifications.add(
            context,
            pkg = "com.bank.app",
            appLabel = "테스트은행",
            title = "입금",
            text = "1,000원",
            time = 2000L
        )

        assertTrue(first.added)
        assertFalse(duplicate.added)
        assertEquals(1, BankNotifications.count(context))
    }

    @Test
    fun removeAt_removesOnlyStoredItemAtIndex() {
        BankNotifications.add(context, "com.bank.one", "은행1", "입금", "1,000원", 1000L)
        BankNotifications.add(context, "com.bank.two", "은행2", "출금", "2,000원", 2000L)

        BankNotifications.removeAt(context, 0)

        val remaining = BankNotifications.getAll(context)
        assertEquals(1, remaining.size)
        assertEquals("com.bank.two", remaining[0].packageName)
        assertTrue(BankNotifications.hasAnyUnsent(context))
    }
}
