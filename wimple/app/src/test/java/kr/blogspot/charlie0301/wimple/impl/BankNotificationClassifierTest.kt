package kr.blogspot.charlie0301.wimple.impl

import kr.blogspot.charlie0301.wimple.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression fixtures pulled from a real device's AI classification dev log (2026-07-16),
 * with the account holder's name replaced by a placeholder. Each case documents a real
 * misclassification found by reading that log — see the fix commits for context.
 */
@RunWith(RobolectricTestRunner::class)
class BankNotificationClassifierTest {

    private fun account(what: String, id: String, title: String) =
        Account(what, id, "account", title, "", "", "", "")

    // -------------------- extractAmountFromText --------------------

    @Test
    fun extractAmountFromText_wonSuffixed_picksTransactionAmountNotCumulativeTotal() {
        val text = "예스이십사주식회사 / 신용(일시불,2*0*) / 07.03 13:06 / 누적이용금액 2,399,130원"
        val title = "(결제) 16,892원"
        // Callers pass title + body so the title's transaction amount is reached
        // before the body's cumulative-total line.
        assertEquals(16892.0, BankNotificationClassifier.extractAmountFromText("$title\n$text"))
    }

    @Test
    fun extractAmountFromText_krwPrefixed_overseasFormat_isRecognized() {
        // Real overseas-payment SMS format: amount stated as "KRW83,300" with no trailing
        // "원" — the old AMOUNT_REGEX-only implementation returned null for this body.
        val text = "금액 KRW83,300\n카드 하나2*0*\n손님명 김*승\n거래종류 신용\n" +
            "사용처 MICROSOFT*STORE\n거래시간 07/12 20:24"
        assertEquals(83300.0, BankNotificationClassifier.extractAmountFromText(text))
    }

    @Test
    fun extractAmountFromText_noAmount_returnsNull() {
        assertNull(BankNotificationClassifier.extractAmountFromText("결제가 완료되었습니다. 감사합니다."))
    }

    // -------------------- nonTransactionReason (prefilter) --------------------

    @Test
    fun nonTransactionReason_discountPreviewNotice_isFiltered() {
        val reason = BankNotificationClassifier.nonTransactionReason(
            title = "[원더카드2.0 FREE+] 결제 금액 할인(예정)",
            text = "- '전가맹점' 혜택으로 132원이 할인되어 결제될 예정이에요."
        )
        assertNotNull(reason)
    }

    @Test
    fun nonTransactionReason_krwPrefixedOverseasBody_isNotFiltered() {
        // The KRW-prefixed amount fix (above) only helps if the prefilter also lets the
        // notification through instead of reporting "no monetary amount in body".
        val reason = BankNotificationClassifier.nonTransactionReason(
            title = "",
            text = "금액 KRW83,300\n카드 하나2*0*\n손님명 김*승\n거래종류 신용\n" +
                "사용처 MICROSOFT*STORE\n거래시간 07/12 20:24"
        )
        assertNull(reason)
    }

    @Test
    fun nonTransactionReason_genuineRefundDepositNamed_maechulChwiso_isNotFiltered() {
        // Real device capture: a genuine refund deposit whose body happens to contain
        // "매출취소" ("sale cancelled"). This used to be blanket-filtered as marketing
        // noise even though it carries an explicit 입금 marker and a grounded amount.
        val reason = BankNotificationClassifier.nonTransactionReason(
            title = "",
            text = "입금 2,967원 하나매출취소  잔액 9,254원\n07/08 11:40 228-******-01407(구)440***75315"
        )
        assertNull(reason)
    }

    @Test
    fun nonTransactionReason_maechulChwisoWithoutDirectionMarker_isStillFiltered() {
        // Without an explicit 입금/출금 marker, "매출취소" still reads as a marketing-style
        // summary push rather than a single live transaction.
        val reason = BankNotificationClassifier.nonTransactionReason(
            title = "",
            text = "이번달 매출취소 합계 15,000원 안내"
        )
        assertNotNull(reason)
    }

    @Test
    fun nonTransactionReason_adLabeledPromoWithPriceAndSavingsWord_isFiltered() {
        // Real dev-log misfire: a card-app marketing push, legally labeled "(광고)" under
        // 정보통신망법, quoting a promo price ("2,900원" satisfies AMOUNT_REGEX) with the word
        // "적립식" ("savings-style investing") incidentally containing the TX_KEYWORD "적립".
        // Both signals passed before the "(광고)" phrase was added, producing a fully
        // hallucinated fake expense downstream.
        val reason = BankNotificationClassifier.nonTransactionReason(
            title = "(광고)원데이20 멀티비타민 미네랄 2,900원 (선착순 100명)",
            text = "하루 한 정으로 건강도 적립식 투자 시작\n☞  지금 바로 우리WON마켓 특가 확인"
        )
        assertNotNull(reason)
    }

    // -------------------- directionConflicts / correctedKind --------------------

    @Test
    fun correctedKind_shinhanCardApprovalMislabeledIncome_isCorrectedToExpense() {
        // Real dev-log pattern: the small on-device model mislabels 신한카드 "…승인" card
        // charges as "income" in roughly a third of real captures, even though "승인" is an
        // unambiguous outflow marker. The corrected kind must flow to every downstream
        // suggestion, not just block the mapping short-circuit.
        val title = "[신한카드]"
        val text = "[신한카드(0991)승인] 박*제\n- 승인금액: 58,450원(일시불)\n- 승인일시: 07/29 19:14\n" +
            "- 가맹점명: (주)서울에너지 서인 \n- 누적금액: 1,586,440원\n\n[신한카드 1544-7000]"
        assertEquals("expense", BankNotificationClassifier.correctedKind(title, text, "income"))
    }

    @Test
    fun correctedKind_noConflict_returnsKindUnchanged() {
        val text = "[신한카드(0991)승인] 박*제\n- 승인금액: 4,900원(일시불)\n- 가맹점명: 나이스인프라"
        assertEquals("expense", BankNotificationClassifier.correctedKind("", text, "expense"))
    }

    // -------------------- detectByAccountDirect --------------------

    @Test
    fun detectByAccountDirect_matchesCardByMaskedSuffix_evenWhenAccountTypeIsLiabilities() {
        // Regression test for the bug found via the on-device log: the pool filter used to
        // compare Account.type (which only ever holds "account"/"group") instead of
        // Account.what (which holds "assets"/"liabilities"/...), so this liabilities-type
        // card account was silently excluded from the pool and the masked-suffix match
        // below never fired — the row fell through to a weaker AI-similarity guess instead.
        val item = LocalReviewQueue.ReviewItem(
            id = "1", time = 0L, packageName = "com.hanaskcard.paycla", appLabel = "하나카드",
            title = "(결제) 16,892원",
            text = "예스이십사주식회사 / 신용(일시불,2*0*) / 07.03 13:06 / 누적이용금액 2,399,130원"
        )
        val extracted = extractedFields("expense", "예스이십사주식회사", 16892.0)
        val accounts = listOf(account("liabilities", "x398", "하나카드_MG새마을2*0*"))

        val result = BankNotificationClassifier.detectByAccountDirect(
            item, extracted, accounts, mutableListOf()
        )

        assertNotNull(result)
        assertEquals(BankNotificationClassifier.Source.ACCOUNT_DIRECT, result!!.source)
        assertEquals(BankNotificationClassifier.State.AMBIGUOUS, result.state)
        // Expense (outflow marker "결제"): the card is r_account (대변); l_account is the
        // expense category, left for the user to fill in on the review form.
        assertNull(result.leftAccountId)
        assertEquals("x398", result.rightAccountId)
    }

    @Test
    fun detectByAccountDirect_singleAccountIncome_placesAccountOnLeft() {
        // Income (inflow marker "입금"): the account is l_account (차변); r_account is the
        // income category, left for the user to fill in on the review form.
        val item = LocalReviewQueue.ReviewItem(
            id = "1", time = 0L, packageName = "com.smg.spbs", appLabel = "MG새마을금고",
            title = "",
            text = "[입금] 172,360원 3827-1012-****-6 잔액 172,360원 07/08 02:06 보너스"
        )
        val extracted = extractedFields("income", "3827-1012-****-6", 172360.0)
        val accounts = listOf(account("assets", "x4", "새마을금고3827"))

        val result = BankNotificationClassifier.detectByAccountDirect(
            item, extracted, accounts, mutableListOf()
        )

        assertNotNull(result)
        assertEquals(BankNotificationClassifier.Source.ACCOUNT_DIRECT, result!!.source)
        assertEquals(BankNotificationClassifier.State.AMBIGUOUS, result.state)
        assertEquals("x4", result.leftAccountId)
        assertNull(result.rightAccountId)
    }

    @Test
    fun detectByAccountDirect_noMatchingAccount_fallsThroughToNull() {
        val item = LocalReviewQueue.ReviewItem(
            id = "1", time = 0L, packageName = "com.hanaskcard.paycla", appLabel = "하나카드",
            title = "(결제) 100원", text = "Adobe / 신용(일시불,3*9*) / 07.14 19:09"
        )
        val extracted = extractedFields("expense", "Adobe", 100.0)
        val accounts = listOf(account("liabilities", "x398", "하나카드_MG새마을2*0*"))

        val result = BankNotificationClassifier.detectByAccountDirect(
            item, extracted, accounts, mutableListOf()
        )

        assertNull(result)
    }

    // -------------------- detectTransfer --------------------

    @Test
    fun detectTransfer_ownAccountToOwnAccount_suggestsBothSidesByNumberAndBankToken() {
        val item = LocalReviewQueue.ReviewItem(
            id = "1", time = 0L, packageName = "com.smg.spbs", appLabel = "MG새마을금고",
            title = "",
            text = "새마을금고 계좌(3827)에서 하나카드 결제대금 500,000원 자동이체 되었습니다"
        )
        val extracted = extractedFields("expense", null, 500000.0)
        val accounts = listOf(
            account("assets", "x4", "새마을금고3827"),
            account("liabilities", "x34", "하나카드_하나7*6*")
        )

        val result = BankNotificationClassifier.detectTransfer(item, extracted, accounts, mutableListOf())

        assertNotNull(result)
        assertEquals(BankNotificationClassifier.Source.TRANSFER, result!!.source)
        assertEquals("transfer", result.kind)
        // Outflow marker ("결제") present, no inflow marker → counterparty on the left,
        // the notifying (source) account on the right.
        assertEquals("x34", result.leftAccountId)
        assertEquals("x4", result.rightAccountId)
    }

    private fun extractedFields(kind: String, merchant: String?, amount: Double) =
        BankNotificationClassifier.ExtractedFields(kind, merchant, amount)
}
