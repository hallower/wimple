package kr.blogspot.charlie0301.wimple.impl

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BankNotificationPayloadBuilderTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun buildFromArray_dropsDuplicateBankTitleAndNormalizesWhitespace() {
        val rows = listOf(notification(
            label = "MG새마을금고",
            title = "MG새마을금고",
            text = "[입금]\n김김김         잔액 1,000원",
            time = millisInSeoul(2024, Calendar.JANUARY, 2, 3, 4)
        ))

        assertEquals(
            "MG새마을금고 [입금] 김김김 잔액 1,000원 01/02 03:04\n",
            BankNotificationPayloadBuilder.build(rows)
        )
    }

    @Test
    fun buildFromArray_keepsExistingDateAndCombinesDifferentTitle() {
        val rows = listOf(notification(
            label = "하나카드",
            title = "승인",
            text = "01/02 03:04 커피 4,500원",
            time = millisInSeoul(2024, Calendar.JANUARY, 2, 3, 4)
        ))

        assertEquals(
            "하나카드 승인 01/02 03:04 커피 4,500원\n",
            BankNotificationPayloadBuilder.build(rows)
        )
    }

    private fun notification(
        label: String,
        title: String,
        text: String,
        time: Long
    ) = BankNotificationPayloadBuilder.PayloadNotification(
        label = label,
        title = title,
        text = text,
        time = time
    )

    private fun millisInSeoul(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
