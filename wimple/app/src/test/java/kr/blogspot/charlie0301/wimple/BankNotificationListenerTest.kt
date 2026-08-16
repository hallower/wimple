package kr.blogspot.charlie0301.wimple

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression fixtures for [BankNotificationListener.looksLikeTransaction], the pre-filter
 * gating whether a captured notification ever enters the local review queue / AI classifier.
 */
@RunWith(RobolectricTestRunner::class)
class BankNotificationListenerTest {

    @Test
    fun looksLikeTransaction_hanaCardOverseasPayment_krwPrefixedNoWon_isRecognized() {
        // Real Hana Card overseas-payment SMS format: amount stated as "KRW83,300" (no
        // trailing "원") and the only transaction marker is "신용" ("거래종류 신용") — neither
        // of which the old regex pair covered, so this notification was silently dropped
        // before reaching the review queue or AI classifier at all.
        val text = "금액 KRW83,300\n카드 하나2*0*\n손님명 김*승\n거래종류 신용\n" +
            "사용처 MICROSOFT*STORE\n거래시간 07/12 20:24"
        assertTrue(BankNotificationListener.looksLikeTransaction("", text))
    }

    @Test
    fun looksLikeTransaction_domesticWonSuffixedPayment_isRecognized() {
        val title = "(결제) 16,892원"
        val text = "예스이십사주식회사 / 신용(일시불,2*0*) / 07.03 13:06 / 누적이용금액 2,399,130원"
        assertTrue(BankNotificationListener.looksLikeTransaction(title, text))
    }

    @Test
    fun looksLikeTransaction_exchangeRateAlert_isFiltered() {
        val text = "오늘의 원/달러 환율은 1,392.50원 입니다."
        assertFalse(BankNotificationListener.looksLikeTransaction("환율 안내", text))
    }

    @Test
    fun looksLikeTransaction_noAmount_isFiltered() {
        assertFalse(BankNotificationListener.looksLikeTransaction("", "결제가 완료되었습니다. 감사합니다."))
    }

    @Test
    fun looksLikeTransaction_adLabeledPromoWithPriceAndSavingsWord_isFiltered() {
        // Real dev-log misfire: a legally "(광고)"-labeled marketing push quoting a promo
        // price plus "적립식" (incidentally matching the "적립" keyword) used to pass both
        // checks and reach the review queue as a fake expense.
        val title = "(광고)원데이20 멀티비타민 미네랄 2,900원 (선착순 100명)"
        val text = "하루 한 정으로 건강도 적립식 투자 시작\n☞  지금 바로 우리WON마켓 특가 확인"
        assertFalse(BankNotificationListener.looksLikeTransaction(title, text))
    }
}
