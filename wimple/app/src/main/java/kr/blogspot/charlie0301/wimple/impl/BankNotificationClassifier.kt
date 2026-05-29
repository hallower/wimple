package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.blogspot.charlie0301.wimple.impl.db.ExtractionExampleDBHandler
import kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler
import kr.blogspot.charlie0301.wimple.model.Account
import kr.blogspot.charlie0301.wimple.model.Entry
import kr.blogspot.charlie0301.wimple.model.Item
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

/**
 * Two-step classifier that turns a captured bank notification into a suggested transaction.
 *
 * 1. **Extract**: ML Kit Prompt API call asks Gemini Nano to parse the Korean notification
 *    body into `(kind, merchant, amount)` as JSON.
 * 2. **Mapping cascade**: look up `(merchant_norm, kind)` in [MerchantMappingDBHandler]; on
 *    hit and the referenced accounts still exist, return [State.READY] with confidence 1.0.
 * 3. **Similarity cascade**: on miss, gather candidates from [WimpleImpl.getCachedEntries],
 *    feed a JSON list of them to Gemini Nano, and ask which is the closest analogue. Map
 *    the model's confidence into READY / AMBIGUOUS / UNPARSED bands and use the matched
 *    entry's left/right accounts as the suggestion.
 *
 * All AI failures (parser errors, AICore unavailable, candidate id not in EntryDB,
 * suggested account no longer in AccountDB) collapse to a `Result` with what we know so
 * far — extraction usually still succeeds even if the similarity step doesn't, so the user
 * sees the merchant and amount even on UNPARSED rows.
 */
object BankNotificationClassifier {

    private const val LOG_TAG = "BankNotiClassifier"
    /**
     * Cap on candidates fed to the AI similarity step. Dropped from an earlier 30 because
     * Gemini Nano's effective attention budget can't reliably reason over a long candidate
     * list — past ~10 entries lost-in-the-middle starts to dominate and confidence
     * calibration drifts. The pre-filter in [pickCandidates] is now responsible for getting
     * the right merchant/amount neighbors into the top 10.
     */
    private const val MAX_CANDIDATES = 10
    /**
     * Cap on multi-shot examples injected into the extract prompt. Three is enough for
     * format diversity (typically expense + income + transfer) without crowding the small
     * Gemini Nano context window — adding more risks pushing the live notification or
     * schema/rules out of the model's effective attention.
     */
    private const val MAX_EXTRACT_SHOTS = 3

    // Confidence thresholds, mirrored from the design doc. Above READY we offer one-tap
    // confirmation; in the AMBIGUOUS band the user gets a candidate picker; below
    // AMBIGUOUS we treat similarity as too weak to seed the form and fall back to manual.
    //
    // THRESHOLD_AMBIGUOUS was raised from 0.5 → 0.7 because "medium" confidence
    // (CONFIDENCE_MEDIUM = 0.65) dominated the dev-log output and contained both
    // genuine close-but-not-exact matches AND post-hallucination near-misses. After
    // [grounding_check] removes the obvious hallucinations, the residual medium
    // results were still wrong often enough that surfacing them as a candidate
    // picker was lower-utility than asking the user to enter the row manually.
    // Net effect: similarity now produces only READY (high) or UNPARSED (medium/low);
    // AMBIGUOUS remains in the enum and is reachable only if the model regresses to
    // a numeric confidence in [0.7, 0.8).
    private const val THRESHOLD_READY = 0.8
    private const val THRESHOLD_AMBIGUOUS = 0.7

    // Discrete confidence mapping. We ask the model for "high"/"medium"/"low" instead of a
    // continuous 0.0–1.0 score because small on-device models calibrate ordinal labels much
    // more reliably than continuous values. The numeric scores below land each label inside
    // its target band (READY ≥ 0.8, AMBIGUOUS ≥ 0.5, otherwise UNPARSED) without changing
    // downstream consumers that reason about a Double.
    private const val CONFIDENCE_HIGH = 0.9
    private const val CONFIDENCE_MEDIUM = 0.65
    private const val CONFIDENCE_LOW = 0.3

    // English ↔ Korean kind label tables. Wire format (DB / Result.kind / merchant mapping
    // key) stays English so existing rows in MerchantMappingDBHandler / Entry.kind survive
    // unchanged. The Korean variants are used ONLY to talk to the model — small models
    // cope better when the schema and live notification share a language, so we display
    // shots and ask for responses in Korean and convert back at the parser boundary.
    private val KIND_EN_TO_KO = mapOf(
        "expense" to "지출",
        "income" to "수입",
        "transfer" to "이체"
    )
    private val KIND_KO_TO_EN = KIND_EN_TO_KO.entries.associate { (en, ko) -> ko to en }

    private fun kindToKorean(en: String): String = KIND_EN_TO_KO[en] ?: en

    /**
     * Pulls the first KRW-suffixed integer from a bank-notification body. Used as a
     * regex fallback by [BankNotificationReviewActivity.openManualEntry] when the AI
     * couldn't return a structured amount — the user shouldn't have to retype an amount
     * that's already sitting in the original text.
     *
     * Heuristic: take the FIRST match. Korean bank notifications conventionally lead
     * with the transaction amount, then state the account balance afterward (e.g.,
     * "결제 12,000원 ... 잔액 234,567원"). Picking the first occurrence usually
     * surfaces the transaction; the user can override on the form. Picking the largest
     * would systematically prefer balance over transaction.
     *
     * Pattern allows comma-grouped (12,000) or plain (12000) integers immediately
     * before "원", with optional whitespace between. Returns null if no match or the
     * parsed value isn't positive.
     */
    fun extractAmountFromText(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        // Skip "...원" figures labelled as a running balance / cumulative total / credit
        // limit rather than the transaction amount. Hana card bodies end with
        // "누적이용금액 3,774,850원" and most bank bodies carry "잔액 ...원"; taking the first
        // raw "원" match would surface those instead of the real amount — which for card
        // apps lives in the title ("(결제) 19,900원"). Callers pass title + body so the
        // title's transaction amount is reached before the body's cumulative/balance line.
        for (m in AMOUNT_REGEX.findAll(text)) {
            if (precededByBalanceLabel(text, m.range.first)) continue
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (v != null && v > 0.0) return v
        }
        return null
    }

    private val AMOUNT_REGEX = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")

    // Labels marking a "원" figure as a balance / cumulative total / limit rather than the
    // transaction amount. Checked in the text immediately preceding the matched number.
    private val BALANCE_LABEL_REGEX = Regex("누적|잔액|한도|이용가능|사용가능|가용")
    private const val BALANCE_LABEL_LOOKBEHIND = 10

    private fun precededByBalanceLabel(haystack: String, numStart: Int): Boolean {
        val from = maxOf(0, numStart - BALANCE_LABEL_LOOKBEHIND)
        return BALANCE_LABEL_REGEX.containsMatchIn(haystack.substring(from, numStart))
    }

    // KRW-suffixed pattern above doesn't cover JPY/USD-prefixed bodies that hana
    // card issues for overseas transactions. Used only by the prefilter to detect
    // the presence of *any* monetary amount; the regex group set is intentionally
    // limited to the currency codes seen on this device's bank notifications.
    private val FOREIGN_AMOUNT_REGEX = Regex("""(?:USD|JPY|EUR|CNY|GBP)\s*\d[\d,]*""")

    // Plain integer groups (with optional comma grouping). Used by the grounding
    // check to verify the AI's extracted amount actually appears somewhere in the
    // notification text — independent of currency suffix, since the model may
    // return either the KRW number or the foreign-currency number depending on
    // which one shows up in the body.
    private val NUMBER_GROUP_REGEX = Regex("""\d{1,3}(?:,\d{3})+|\d+""")

    // Phrases that have appeared in misfiring noise notifications on real device
    // dev-log captures. Containing any of these is treated as a strong signal that
    // the notification is marketing/news/UI rather than a transaction, even when
    // an amount-looking number is present elsewhere in the body (e.g. "환율통지"
    // bodies quote the FX rate as "1500원"). Extend cautiously: each entry here
    // permanently suppresses notifications containing it.
    private val NON_TX_PHRASES = listOf(
        "증시리뷰",
        "모닝브리핑",
        "채팅상담",
        "럭키볼",
        "환율통지",
        "기준환율 안내",
        "카드 청구서가 도착",
        "청구서를 지금 바로 확인",
        "결제 금액 할인",
        "매출취소"
    )

    /**
     * Cheap structural check applied before any AI call. Returns a short reason
     * string when the notification looks like marketing/news/UI rather than a
     * transaction, or null when the row should proceed to the AI cascade.
     *
     * Two signals:
     *   1. **No monetary amount anywhere.** Korean bank transaction notifications
     *      always include the amount as "...원" or as a currency-prefixed group
     *      like "JPY 1,800". Absent → not a transaction.
     *   2. **Known non-transaction phrases.** See [NON_TX_PHRASES].
     */
    private fun nonTransactionReason(title: String, text: String): String? {
        val combined = "$title $text"
        if (!AMOUNT_REGEX.containsMatchIn(combined)
            && !FOREIGN_AMOUNT_REGEX.containsMatchIn(combined)
        ) {
            return "no monetary amount in body"
        }
        val phrase = NON_TX_PHRASES.firstOrNull { combined.contains(it) }
        if (phrase != null) return "matches non-transaction phrase \"$phrase\""
        // A real transaction always names the movement (입금/출금/결제/승인/이체/충전/취소…) or
        // shows an account→account arrow. Price/FX/news alerts carry a "...원" figure but none
        // of these and would otherwise reach extract + mapping and become a confident fake
        // transaction (e.g. "삼성전자 -5.05% 현재 291,750원" → fake 291,750 income).
        if (!TX_KEYWORD_REGEX.containsMatchIn(combined)) {
            return "no transaction keyword (입금/출금/결제/승인/이체…)"
        }
        return null
    }

    // Movement keywords that mark a notification as an actual transaction, built from the
    // bank/card formats on this device. The arrow covers transfer notifications phrased as
    // "입출금통장(1596) → 김철승" with no explicit 입금/출금 word.
    private val TX_KEYWORD_REGEX = Regex(
        "입금|출금|결제|승인|이체|송금|환급|적립|충전|인출|환전|캐시백|취소|신용|일시불|할부|→|->"
    )

    // Explicit direction markers. Outflow is compatible with expense OR transfer; inflow with
    // income OR transfer — so [directionConflicts] only flags a hard contradiction
    // (outflow⇒income, inflow⇒expense), never the expense-vs-transfer ambiguity.
    private val OUTFLOW_MARKER_REGEX = Regex("출금|결제|승인|인출")
    private val INFLOW_MARKER_REGEX = Regex("입금|환급|적립|캐시백")

    /**
     * True when the extracted [kind] hard-contradicts an explicit direction marker in the
     * notification: an outflow ("[출금]"/결제/승인) extracted as 수입, or an inflow ("[입금]")
     * extracted as 지출. False for transfer (valid either way) and when both or neither marker
     * is present. Callers use this to refuse a confidence-1.0 mapping short-circuit and to keep
     * a direction-flipped row out of READY — the user's own ledger treats 출금 as expense or
     * transfer (never income), so an 출금→income extraction is always wrong.
     */
    private fun directionConflicts(title: String, text: String, kind: String): Boolean {
        val combined = "$title $text"
        val outflow = OUTFLOW_MARKER_REGEX.containsMatchIn(combined)
        val inflow = INFLOW_MARKER_REGEX.containsMatchIn(combined)
        return when (kind) {
            "income" -> outflow && !inflow
            "expense" -> inflow && !outflow
            else -> false
        }
    }

    // --- Inter-account transfer detection (방안 D) --------------------------------------
    // Korean bank notifications name the *notifying* account by a number fragment that also
    // shows up in the user's Whooing account title (새마을금고**3827**, 우리은행주**060**,
    // 하나카드_하나**7*6***). When the counterparty is ANOTHER own account — named by its bank
    // token ("하나카드", "우리은행") or its own number in the body — the row is a transfer
    // between the user's accounts, not an expense/income. Surfaced as a suggestion only
    // (State.AMBIGUOUS): the left/right convention below is a best guess the user confirms on
    // the prefilled manual form, so a swap is harmless. Name-only counterparties ("김철승")
    // don't resolve and fall through to the existing cascade unchanged.

    private val DIGIT_RUN_REGEX = Regex("""\d+""")
    private val MASKED_CARD_REGEX = Regex("""\d\*\d\*""")
    private val LEADING_HANGUL_REGEX = Regex("""^[가-힣]+""")

    private fun accountNumberFragments(title: String): List<String> {
        val out = ArrayList<String>()
        DIGIT_RUN_REGEX.findAll(title).forEach { if (it.value.length >= 3) out.add(it.value) }
        MASKED_CARD_REGEX.findAll(title).forEach { out.add(it.value) }
        return out
    }

    // A 4+ digit / masked fragment matches anywhere; a 3-digit fragment must sit in an
    // account-number context (next to '-' or '*') so it doesn't collide with an amount.
    private fun numberFragmentAppears(text: String, frag: String): Boolean {
        var idx = text.indexOf(frag)
        while (idx >= 0) {
            val left = if (idx > 0) text[idx - 1] else ' '
            val right = if (idx + frag.length < text.length) text[idx + frag.length] else ' '
            if (frag.length >= 4 || frag.contains('*') ||
                left == '-' || left == '*' || right == '-' || right == '*') return true
            idx = text.indexOf(frag, idx + 1)
        }
        return false
    }

    // Leading-Hangul bank tokens of a title, used to spot the account named as a transfer
    // counterparty. "하나카드_하나7*6*" → ["하나카드"]. This user suffixes bank accounts with
    // "주" before the number ("우리은행주060", "하나은행주228"), which the notification body
    // doesn't carry, so we also emit the 주-stripped form ("우리은행", "하나은행").
    private fun accountBankTokens(title: String): List<String> {
        val lead = LEADING_HANGUL_REGEX.find(title)?.value ?: return emptyList()
        val out = ArrayList<String>()
        if (lead.length >= 3) out.add(lead)
        val stripped = lead.removeSuffix("주")
        if (stripped.length >= 3 && stripped != lead) out.add(stripped)
        return out
    }

    private fun resolveAccountByNumber(text: String, pool: List<Account>): Account? =
        pool.firstOrNull { acc -> accountNumberFragments(acc.title).any { numberFragmentAppears(text, it) } }

    /**
     * Detect a transfer between two of the user's own asset/liability accounts. Returns a
     * suggestion-only [Result] (State.AMBIGUOUS, never READY) or null to fall through to the AI
     * similarity step. Fires only when the notifying account resolves by number AND a DIFFERENT
     * own account is named (by bank token or a second account number) — the high-precision
     * subset.
     */
    private fun detectTransfer(
        item: LocalReviewQueue.ReviewItem,
        extracted: ExtractedFields,
        accounts: Collection<Account>,
        stages: MutableList<AiClassificationLog.Stage>
    ): Result? {
        val pool = accounts.filter { it.type == "assets" || it.type == "liabilities" }
        if (pool.size < 2) return null
        val source = resolveAccountByNumber(item.text, pool) ?: return null
        // The extract prompt strips bank/card names from `merchant`, so look for the
        // counterparty's bank token across title + body + merchant, not merchant alone.
        val combined = "${item.title} ${item.text} ${extracted.merchant}"
        val counterparty = pool.firstOrNull { acc ->
            acc.id != source.id && accountBankTokens(acc.title).any { combined.contains(it) }
        } ?: pool.firstOrNull { acc ->
            acc.id != source.id && accountNumberFragments(acc.title).any { numberFragmentAppears(item.text, it) }
        } ?: return null

        val markerSrc = "${item.title} ${item.text}"
        val outflow = OUTFLOW_MARKER_REGEX.containsMatchIn(markerSrc)
        val inflow = INFLOW_MARKER_REGEX.containsMatchIn(markerSrc)
        // Outflow: money leaves the source → counterparty on the left (차변), source on the
        // right (대변). Inflow: the reverse. Default to the outflow shape when ambiguous.
        val (left, right) = if (inflow && !outflow) source to counterparty else counterparty to source

        stages.add(
            AiClassificationLog.Stage(
                "transfer_detect",
                "source=${source.title} counterparty=${counterparty.title} merchant=\"${extracted.merchant}\"",
                "TRANSFER between own accounts — suggestion only (state=AMBIGUOUS)"
            )
        )
        return Result(
            state = State.AMBIGUOUS,
            kind = "transfer",
            merchant = extracted.merchant,
            amount = extracted.amount,
            leftAccountId = left.id,
            leftAccountTitle = left.title,
            rightAccountId = right.id,
            rightAccountTitle = right.title,
            source = Source.TRANSFER,
            confidence = THRESHOLD_AMBIGUOUS
        )
    }

    // --- Monthly (recurring) transaction matching (방안 F) ------------------------------
    // The user pre-plans recurring transactions (관리비, 구독, 대출이자, 보험…) as Whooing
    // "monthly items", each carrying a due date, amount and a canonical left/right account
    // pair. When a notification lands on/near a monthly item's due day for a matching amount,
    // it's that month's occurrence — book it with the monthly item's own accounts (ground
    // truth the user set), using the notification's actual amount.

    private const val MONTHLY_DATE_WINDOW_DAYS = 2
    private const val MONTHLY_AMOUNT_TOLERANCE = 0.05

    private fun dayOfMonth(epochMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.DAY_OF_MONTH)

    // Day-of-month distance with month wraparound so due-day 1 vs notification-day 30 is near.
    private fun dayDiffWrap(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 31 - d)
    }

    private fun kindFromAccountTypes(leftType: String?, rightType: String?): String {
        val l = leftType.orEmpty(); val r = rightType.orEmpty()
        return when {
            l == "income" || r == "income" -> "income"
            l == "expenses" || r == "expenses" -> "expense"
            else -> "transfer"
        }
    }

    /**
     * Match the notification against the user's cached monthly items by due-day (±window) and
     * amount (±tolerance). On a single confident match return a READY result built from the
     * monthly item's canonical account pair + the notification's actual amount. Multiple
     * candidates or none → null (fall through). The matched item's accounts must still be live.
     */
    private fun detectMonthly(
        item: LocalReviewQueue.ReviewItem,
        extracted: ExtractedFields,
        accounts: Collection<Account>,
        stages: MutableList<AiClassificationLog.Stage>
    ): Result? {
        val monthlies: Collection<Item> =
            WimpleImpl.getInstance()?.monthlyItemDBHandler?.allItems.orEmpty()
        if (monthlies.isEmpty()) return null
        val notiDay = dayOfMonth(item.time)
        val matches = monthlies.filter { mi ->
            val amt = mi.amount ?: 0.0
            val due = mi.date ?: 0L
            amt > 0.0 && due > 0L &&
                dayDiffWrap(notiDay, dayOfMonth(due)) <= MONTHLY_DATE_WINDOW_DAYS &&
                abs(amt - extracted.amount) <= max(amt, extracted.amount) * MONTHLY_AMOUNT_TOLERANCE
        }
        if (matches.isEmpty()) return null
        if (matches.size > 1) {
            stages.add(
                AiClassificationLog.Stage(
                    "monthly_match",
                    "notiDay=$notiDay amount=${extracted.amount.toLong()}",
                    "AMBIGUOUS — ${matches.size} monthly candidates; skipping auto-match"
                )
            )
            return null
        }
        val mi = matches[0]
        val lAcc = accounts.firstOrNull { it.id == mi.leftAccountID } ?: return null
        val rAcc = accounts.firstOrNull { it.id == mi.rightAccountID } ?: return null
        stages.add(
            AiClassificationLog.Stage(
                "monthly_match",
                "notiDay=$notiDay amount=${extracted.amount.toLong()}",
                "MATCH '${mi.item}' (due day ${dayOfMonth(mi.date ?: 0L)}, plan ${mi.amount?.toLong()}) → READY"
            )
        )
        return Result(
            state = State.READY,
            kind = kindFromAccountTypes(mi.leftAccount, mi.rightAccount),
            merchant = mi.item.ifBlank { extracted.merchant },
            amount = extracted.amount,
            leftAccountId = lAcc.id,
            leftAccountTitle = lAcc.title,
            rightAccountId = rAcc.id,
            rightAccountTitle = rAcc.title,
            source = Source.MONTHLY,
            confidence = 1.0
        )
    }

    /**
     * Strict numeric check: the AI's extracted amount must appear as an integer
     * group somewhere in title+body. Comma grouping is normalized away on both
     * sides so "21,000" in the body matches an extracted 21000.
     */
    private fun amountAppearsInSource(amount: Double, haystack: String): Boolean {
        val target = amount.toLong()
        // Require at least one occurrence that ISN'T a balance/cumulative figure, so a model
        // that returns 누적이용금액/잔액 instead of the transaction amount fails grounding and
        // collapses to UNPARSED rather than auto-confirming the wrong number.
        return NUMBER_GROUP_REGEX.findAll(haystack).any { m ->
            m.value.replace(",", "").toLongOrNull() == target &&
                !precededByBalanceLabel(haystack, m.range.first)
        }
    }

    /**
     * Lenient string check: direct substring first (covers the common case), then
     * token-overlap fallback so legitimate AI normalization like "GS25강남점" →
     * "GS25 강남점" still grounds. Returns true if any 2+ char token from the
     * extracted merchant string appears in the source.
     */
    private fun merchantAppearsInSource(merchant: String, haystack: String): Boolean {
        val low = haystack.lowercase()
        if (low.contains(merchant.lowercase())) return true
        val tokens = tokenize(merchant)
        return tokens.isNotEmpty() && tokens.any { low.contains(it) }
    }

    /** Accept either Korean (preferred — what the prompt asks for) or English (defensive
     *  — small models occasionally regress to the schema literal). Returns the canonical
     *  English form, or null if the value isn't a recognized kind. */
    private fun parseKindLabel(raw: String): String? {
        val trimmed = raw.trim()
        KIND_KO_TO_EN[trimmed]?.let { return it }
        val lower = trimmed.lowercase()
        return if (lower == "expense" || lower == "income" || lower == "transfer") lower else null
    }

    enum class State { READY, AMBIGUOUS, UNPARSED, ERROR }
    enum class Source { MAPPING, AI_SIMILARITY, TRANSFER, MONTHLY, NONE }

    data class Result(
        val state: State,
        val kind: String? = null,
        val merchant: String? = null,
        val amount: Double? = null,
        val leftAccountId: String? = null,
        val leftAccountTitle: String? = null,
        val rightAccountId: String? = null,
        val rightAccountTitle: String? = null,
        val source: Source = Source.NONE,
        val confidence: Double = 0.0,
        val bestMatchEntryId: String? = null
    ) {
        fun toJson(): String = JSONObject().apply {
            put("state", state.name)
            kind?.let { put("kind", it) }
            merchant?.let { put("merchant", it) }
            amount?.let { put("amount", it) }
            leftAccountId?.let { put("left_account_id", it) }
            leftAccountTitle?.let { put("left_account_title", it) }
            rightAccountId?.let { put("right_account_id", it) }
            rightAccountTitle?.let { put("right_account_title", it) }
            put("source", source.name)
            put("confidence", confidence)
            bestMatchEntryId?.let { put("best_match_entry_id", it) }
        }.toString()

        companion object {
            fun fromJson(raw: String?): Result? {
                if (raw.isNullOrBlank()) return null
                return try {
                    val o = JSONObject(raw)
                    Result(
                        state = State.valueOf(o.optString("state", "ERROR")),
                        kind = o.optStringOrNull("kind"),
                        merchant = o.optStringOrNull("merchant"),
                        amount = if (o.has("amount")) o.optDouble("amount") else null,
                        leftAccountId = o.optStringOrNull("left_account_id"),
                        leftAccountTitle = o.optStringOrNull("left_account_title"),
                        rightAccountId = o.optStringOrNull("right_account_id"),
                        rightAccountTitle = o.optStringOrNull("right_account_title"),
                        source = runCatching {
                            Source.valueOf(o.optString("source", "NONE"))
                        }.getOrDefault(Source.NONE),
                        confidence = o.optDouble("confidence", 0.0),
                        bestMatchEntryId = o.optStringOrNull("best_match_entry_id")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Run the full cascade for one notification. Suspend; safe to call from a foreground
     * Activity coroutine. Background callers will hit `BACKGROUND_USE_BLOCKED` from ML Kit
     * and end up with [State.ERROR].
     */
    suspend fun classify(ctx: Context, item: LocalReviewQueue.ReviewItem): Result {
        val started = System.currentTimeMillis()
        val stages = mutableListOf<AiClassificationLog.Stage>()
        val result = try {
            doClassify(ctx, item, stages)
        } catch (c: CancellationException) {
            // The review screen cancels the in-flight pass on pause / retry. A cancelled run
            // is NOT a failure — rethrow so it isn't recorded as a State.ERROR log entry. (This
            // is what produced the "same notification ×6 ERROR" storm: a foldable config-change
            // recreated the activity, cancelling and restarting the pass repeatedly, and each
            // cancellation was being caught below and logged as ERROR.)
            throw c
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "classify failed for ${item.id}", t)
            Result(state = State.ERROR)
        }
        // Persist the run for the dev log regardless of outcome — the failure cases
        // (UNPARSED / ERROR) are exactly what we'd want to inspect to tune prompts.
        AiClassificationLog.record(
            ctx,
            AiClassificationLog.Entry(
                timestamp = started,
                packageName = item.packageName,
                appLabel = item.appLabel,
                notiTitle = item.title,
                notiText = item.text,
                stages = stages,
                resultJson = result.toJson(),
                durationMs = System.currentTimeMillis() - started
            )
        )
        return result
    }

    private suspend fun doClassify(
        ctx: Context,
        item: LocalReviewQueue.ReviewItem,
        stages: MutableList<AiClassificationLog.Stage>
    ): Result = withContext(Dispatchers.IO) {
        // Pre-filter: skip notifications that aren't transactions before any AI call.
        // The monitored bank apps interleave real transaction alerts with marketing,
        // morning-brief news, chat replies, FX-rate notices, and bill-due reminders.
        // Running Gemini Nano on these wastes battery and — more dangerously — invites
        // fully-hallucinated (merchant, amount, kind) tuples that downstream mapping
        // gladly turns into a confident fake transaction. See the on-device dev log
        // for the misfire patterns that seeded the phrase list below.
        val skipReason = nonTransactionReason(item.title, item.text)
        if (skipReason != null) {
            stages.add(
                AiClassificationLog.Stage(
                    "prefilter",
                    "title=\"${item.title}\" text=\"${item.text.take(120)}\"",
                    "SKIP — $skipReason; classification not attempted"
                )
            )
            return@withContext Result(state = State.UNPARSED)
        }

        val extracted = extractFields(ctx, item, stages)
            ?: return@withContext Result(state = State.UNPARSED)

        // The extracted kind sometimes flips against an explicit 입금/출금 marker (the small
        // model mislabels, or copies a shot's kind). The user's ledger never books an 출금 as
        // income, so a conflicting kind must not reach a confidence-1.0 mapping short-circuit
        // or a READY similarity row — route it to manual review instead.
        val dirConflict = directionConflicts(item.title, item.text, extracted.kind)
        if (dirConflict) {
            stages.add(
                AiClassificationLog.Stage(
                    "direction_check",
                    "kind=\"${extracted.kind}\" vs 입금/출금 markers in source",
                    "CONFLICT — extracted kind contradicts the direction marker; blocking auto-confirm"
                )
            )
        }

        val accounts = WimpleImpl.getInstance()?.cachedAccounts.orEmpty()

        // Step 2: mapping lookup.
        val mappingHandler = MerchantMappingDBHandler(ctx)
        val mapping = mappingHandler.find(extracted.merchant, extracted.kind)
        // Resolve account liveness once so the dev log and the decision branch agree on the
        // same view of the cache (cachedAccounts is a snapshot already by .orEmpty() above).
        val mappingLAcc = mapping?.let { m -> accounts.firstOrNull { it.id == m.lAccountId } }
        val mappingRAcc = mapping?.let { m -> accounts.firstOrNull { it.id == m.rAccountId } }
        stages.add(
            AiClassificationLog.Stage(
                "mapping_lookup",
                "find(merchant=\"${extracted.merchant}\", kind=\"${extracted.kind}\")",
                describeMappingLookup(extracted, mapping, mappingLAcc, mappingRAcc)
            )
        )
        if (mapping != null && mappingLAcc != null && mappingRAcc != null && !dirConflict) {
            return@withContext mappingResult(extracted, mappingLAcc, mappingRAcc)
        }
        if (mapping != null) {
            Log.d(LOG_TAG, "mapping accounts no longer in cache; falling back to similarity")
        }

        // Step 2.4: monthly (recurring) match (방안 F). The user's monthly items carry the
        // canonical account pair, so a due-day + amount match is authoritative — checked before
        // the transfer heuristic and AI similarity.
        detectMonthly(item, extracted, accounts, stages)?.let { return@withContext it }

        // Step 2.5: inter-account transfer detection (방안 D). Suggestion-only; runs only when
        // no confident mapping auto-confirmed above, so it never downgrades a good mapping.
        detectTransfer(item, extracted, accounts, stages)?.let { return@withContext it }

        // Step 3: AI similarity.
        val entries = WimpleImpl.getInstance()?.cachedEntries.orEmpty()
        val candidates = pickCandidates(entries, extracted, MAX_CANDIDATES)
        stages.add(
            AiClassificationLog.Stage(
                "candidates",
                "pickCandidates(pool=${entries.size}, max=$MAX_CANDIDATES) " +
                    "filter: token-overlap(merchant=\"${extracted.merchant}\") + amount near ${extracted.amount.toLong()}",
                describeCandidates(entries.size, candidates)
            )
        )
        if (candidates.isEmpty()) return@withContext extractedOnly(extracted, State.UNPARSED)

        val match = aiSimilarity(item, extracted, candidates, stages)
            ?: return@withContext extractedOnly(extracted, State.UNPARSED)

        val candidateEntry = candidates.firstOrNull { it.id == match.bestMatchEntryId }
            ?: return@withContext extractedOnly(extracted, State.UNPARSED)

        val lAcc = accounts.firstOrNull { it.id == candidateEntry.leftAccountID }
        val rAcc = accounts.firstOrNull { it.id == candidateEntry.rightAccountID }
        if (lAcc == null || rAcc == null) return@withContext extractedOnly(extracted, State.UNPARSED)

        var state = when {
            match.confidence >= THRESHOLD_READY -> State.READY
            match.confidence >= THRESHOLD_AMBIGUOUS -> State.AMBIGUOUS
            else -> State.UNPARSED
        }
        // A direction-conflicting row never auto-confirms — demote READY so the user reviews
        // it manually even if the similarity model was confident. Leaves AMBIGUOUS/UNPARSED.
        if (dirConflict && state == State.READY) state = State.AMBIGUOUS

        Result(
            state = state,
            kind = extracted.kind,
            merchant = extracted.merchant,
            amount = extracted.amount,
            leftAccountId = lAcc.id,
            leftAccountTitle = lAcc.title,
            rightAccountId = rAcc.id,
            rightAccountTitle = rAcc.title,
            source = Source.AI_SIMILARITY,
            confidence = match.confidence,
            bestMatchEntryId = candidateEntry.id
        )
    }

    private fun mappingResult(
        extracted: ExtractedFields,
        lAcc: Account,
        rAcc: Account
    ) = Result(
        state = State.READY,
        kind = extracted.kind,
        merchant = extracted.merchant,
        amount = extracted.amount,
        leftAccountId = lAcc.id,
        leftAccountTitle = lAcc.title,
        rightAccountId = rAcc.id,
        rightAccountTitle = rAcc.title,
        source = Source.MAPPING,
        confidence = 1.0
    )

    private fun extractedOnly(extracted: ExtractedFields, state: State) = Result(
        state = state,
        kind = extracted.kind,
        merchant = extracted.merchant,
        amount = extracted.amount,
        source = Source.NONE,
        confidence = 0.0
    )

    // -------------------- AI: extract --------------------

    private data class ExtractedFields(val kind: String, val merchant: String, val amount: Double)

    private suspend fun extractFields(
        ctx: Context,
        item: LocalReviewQueue.ReviewItem,
        stages: MutableList<AiClassificationLog.Stage>
    ): ExtractedFields? {
        // User-confirmed shots ground the model in the bank-app formats this device actually
        // sees. Failure to load (DB miss, schema migration mid-flight) collapses to zero-shot
        // — the original prompt still works, we just lose grounding.
        val shots = try {
            ExtractionExampleDBHandler(ctx).pickShots(MAX_EXTRACT_SHOTS)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "pickShots failed; falling back to zero-shot", t)
            emptyList()
        }
        // Record what we pulled out of the example DB so the dev log shows whether a poor
        // extraction is "model failed despite good shots" or "shot pool empty / unhelpful".
        stages.add(
            AiClassificationLog.Stage(
                "shots",
                "ExtractionExampleDBHandler.pickShots(k=$MAX_EXTRACT_SHOTS)",
                describeShots(shots)
            )
        )
        // Prompt structure rationale:
        //   1. Task line in Korean — matches the live notification language and reduces the
        //      cognitive translation step the model would otherwise perform.
        //   2. Schema + rules placed BEFORE examples and the live notification. Small models
        //      anchor on the earliest instructions; putting schema last (the prior shape)
        //      meant ad-hoc parsing of the body could drift away from the required JSON.
        //   3. Examples include both Title and Body and use Korean kind labels — matching
        //      exactly the format of the live "새 알림" block below. Earlier shots only had
        //      Body, which left the model to guess whether the live Title was data or noise.
        //   4. Trailing "JSON:" cues the model to produce JSON next, similar to the
        //      few-shot pattern.
        val prompt = buildString {
            append("한국어 은행 알림에서 (분류, 가맹점, 금액)을 추출해 JSON 객체 하나만 반환하세요. ")
            append("마크다운 코드 펜스나 추가 설명 없이 JSON 한 줄만 출력합니다.\n\n")
            append("스키마:\n")
            append("{\"kind\":\"지출\"|\"수입\"|\"이체\",\"merchant\":\"<상호 문자열>\",\"amount\":<정수 KRW>}\n\n")
            append("규칙:\n")
            append("- 지출: 출금/결제/카드 사용. 수입: 입금/적립/환급. 이체: 본인 계좌 사이 이동.\n")
            append("- merchant: **현재 알림 본문에 실제로 등장하는** 가맹점 또는 거래상대방 문자열 ")
            append("(예: \"GS25 강남점\"). 은행명·카드사명·예금주명은 제거. ")
            append("본문에 가맹점/거래상대방이 명시되지 않았으면 merchant 키 자체를 생략 — 추측 금지.\n")
            append("- amount: 정수 KRW. \"12,000원\" → 12000. 본문에 명시된 숫자만 사용.\n")
            append("- 누적이용금액·잔액·이용한도는 거래금액이 아님. ")
            append("결제/승인/입금/출금 금액(흔히 제목의 \"(결제) 19,900원\")을 사용.\n")
            append("- 예시는 출력 형식만 참고할 것. 예시의 merchant/amount 값을 그대로 베끼지 말고, ")
            append("실제 값은 항상 새 알림에서만 추출한다.\n")
            append("- 정말 알 수 없는 필드만 키 자체를 생략.\n\n")
            if (shots.isNotEmpty()) {
                append("예시 — 사용자가 확정한 과거 알림과 정답 JSON:\n\n")
                for (s in shots) {
                    append("알림:\n")
                    if (s.notificationTitle.isNotBlank()) {
                        append("Title: ").append(s.notificationTitle).append('\n')
                    }
                    append("Body: ").append(s.notificationText).append('\n')
                    append("JSON: ").append(
                        JSONObject().apply {
                            put("kind", kindToKorean(s.kind))
                            put("merchant", s.merchant)
                            put("amount", s.amount)
                        }.toString()
                    ).append("\n\n")
                }
            }
            append("새 알림:\n")
            if (item.title.isNotBlank()) append("Title: ").append(item.title).append('\n')
            append("Body: ").append(item.text).append('\n')
            append("JSON:")
        }
        val response = generate(prompt)
        stages.add(AiClassificationLog.Stage("extract", prompt, response))
        if (response == null) return null
        val json = parseJson(response) ?: return null

        // Prefer Korean (what we asked for) but accept English too — the model occasionally
        // regresses to the schema literal even when the rest of the prompt is Korean.
        val kind = json.optStringOrNull("kind")?.let { parseKindLabel(it) } ?: return null
        val merchant = json.optStringOrNull("merchant")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val amount = if (json.has("amount")) json.optDouble("amount", -1.0) else -1.0
        if (amount <= 0.0) return null

        // Grounding check: every field the model returned must trace back to the
        // source text. Without this, near-empty bodies (e.g. "바로 확인해보세요.")
        // produce fully-fabricated tuples that mapping_lookup then turns into
        // confidence-1.0 fake transactions. Amount is checked strictly (exact
        // numeric match anywhere in title+body); merchant is lenient (any 2+ char
        // token of the extracted name appears in the source) so legitimate AI
        // normalization like "GS25강남점" → "GS25 강남점" still passes.
        val haystack = "${item.title} ${item.text}"
        val groundedAmount = amountAppearsInSource(amount, haystack)
        val groundedMerchant = merchantAppearsInSource(merchant, haystack)
        if (!groundedAmount || !groundedMerchant) {
            val missing = buildList {
                if (!groundedAmount) add("amount=${amount.toLong()}")
                if (!groundedMerchant) add("merchant=\"$merchant\"")
            }
            stages.add(
                AiClassificationLog.Stage(
                    "grounding_check",
                    "amount=${amount.toLong()} merchant=\"$merchant\"",
                    "FAIL — ${missing.joinToString(", ")} not found in source; treating as UNPARSED"
                )
            )
            return null
        }

        return ExtractedFields(kind, merchant, amount)
    }

    // -------------------- AI: similarity --------------------

    private data class MatchResult(val bestMatchEntryId: String, val confidence: Double)

    private suspend fun aiSimilarity(
        item: LocalReviewQueue.ReviewItem,
        extracted: ExtractedFields,
        candidates: List<Entry>,
        stages: MutableList<AiClassificationLog.Stage>
    ): MatchResult? {
        val candidateBlock = JSONArray().apply {
            candidates.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("title", e.item.orEmpty())
                    put("memo", (e.memo ?: "").take(80))
                    put("amount", e.amount ?: 0.0)
                    put("left_account", e.leftAccount.orEmpty())
                    put("right_account", e.rightAccount.orEmpty())
                })
            }
        }.toString()

        // Same restructuring rationale as the extract prompt:
        //   1. Task + schema + rules first so the model anchors on the output format before
        //      it sees any data. Small models that read instruction-then-data follow the
        //      schema more reliably than those given data first.
        //   2. Discrete confidence (high/medium/low) instead of a 0.0–1.0 score. Small
        //      on-device models calibrate ordinal labels far better than continuous values;
        //      we map back to the existing numeric thresholds at the parser boundary.
        //   3. Amount tolerance is now spelled out — earlier the candidates were pre-filtered
        //      on amount proximity but the model couldn't see that context.
        //   4. Kind label is shown in Korean to match the rest of the cascade's prompt
        //      language; candidate JSON keys stay English (id/title/amount/...) so the
        //      response's id field round-trips cleanly back into our ascii ids.
        val prompt = buildString {
            append("새 한국어 은행 알림을 가장 비슷한 과거 거래와 매칭합니다. ")
            append("매칭된 거래의 좌·우 계정을 재사용하기 위함입니다.\n\n")
            append("응답 스키마 (마크다운 코드 펜스나 설명 없이 JSON만):\n")
            append("{\"best_match_entry_id\":\"<후보 id 또는 빈 문자열>\",\"confidence\":\"high\"|\"medium\"|\"low\"}\n\n")
            append("판정 규칙 (보수적으로 — 확실하지 않으면 low 를 선택):\n")
            append("- high: 가맹점 문자열이 정확히 일치(공백/대소문자 무시 가능)하고 금액이 ±20% 이내.\n")
            append("- medium: 가맹점 토큰이 부분 일치하거나 같은 체인/브랜드이고 금액이 ±50% 이내.\n")
            append("  · 단순히 '같은 카테고리'(예: 둘 다 식비)만으로는 medium 도 부족 — low 로 답할 것.\n")
            append("- low: 위 조건을 모두 충족하지 못하거나 확신이 없는 모든 경우. ")
            append("best_match_entry_id 는 빈 문자열로.\n")
            append("- 금액이 3배 이상 다르면 high → medium, medium → low 로 한 단계 낮춤.\n\n")
            append("새 알림:\n")
            append("- 분류: ").append(kindToKorean(extracted.kind)).append('\n')
            append("- 가맹점: ").append(extracted.merchant).append('\n')
            append("- 금액: ").append(extracted.amount.toLong()).append('\n')
            append("- 원문: ").append(item.text).append("\n\n")
            append("후보 과거 거래 (JSON 배열, 같은 형식의 id 를 그대로 응답에 사용):\n")
            append(candidateBlock).append('\n')
            append("JSON:")
        }
        val response = generate(prompt)
        stages.add(AiClassificationLog.Stage("similarity", prompt, response))
        if (response == null) return null
        val json = parseJson(response) ?: return null

        val rawId = json.optStringOrNull("best_match_entry_id") ?: return null
        // Empty string is the canonical "no match" signal we asked for; "null" / "none"
        // are common model regressions when discrete answers are pushed.
        if (rawId.isBlank()
            || rawId.equals("null", ignoreCase = true)
            || rawId.equals("none", ignoreCase = true)) return null
        // Prefer the discrete label we asked for, but fall back to a continuous score if
        // the model regressed to numeric. coerceIn keeps downstream thresholds well-defined
        // either way.
        val confidence = parseConfidenceLevel(json) ?: return null
        return MatchResult(rawId, confidence)
    }

    // -------------------- AI plumbing --------------------

    private suspend fun generate(prompt: String): String? = try {
        val model = Generation.getClient()
        val response = model.generateContent(prompt)
        response.candidates.firstOrNull()?.text
    } catch (c: CancellationException) {
        // Don't swallow cancellation as a null extract — let it propagate so the pass ends
        // cleanly instead of continuing and recording a misleading result.
        throw c
    } catch (t: Throwable) {
        Log.w(LOG_TAG, "generate failed", t)
        null
    }

    /**
     * Strip code fences and surrounding prose, then carve out the first balanced JSON object
     * substring. Models occasionally wrap output in ```json ...``` even when told not to, or
     * preface with "Here is the result:" — be defensive without trying to fix arbitrary
     * malformations.
     */
    private fun parseJson(raw: String): JSONObject? {
        val cleaned = raw
            .replace(Regex("(?i)```(?:json)?"), "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    // -------------------- Candidate selection --------------------

    /**
     * Score entries by token overlap with the extracted merchant string and amount proximity,
     * keep the top [max] for AI similarity. The goal isn't to be the matcher — it's to keep
     * the prompt short while still including everything plausibly relevant. A merchant the
     * user has seen before will rank near the top; a totally novel merchant will surface
     * weakly-related entries that the AI can still down-weight to <0.5 confidence.
     */
    private fun pickCandidates(
        entries: Collection<Entry>,
        extracted: ExtractedFields,
        max: Int
    ): List<Entry> {
        if (entries.isEmpty()) return emptyList()
        val merchantTokens = tokenize(extracted.merchant)
        val target = extracted.amount

        return entries.asSequence()
            .map { e ->
                val tokens = tokenize("${e.item.orEmpty()} ${e.memo.orEmpty()}")
                val overlap = if (merchantTokens.isEmpty()) 0.0
                    else merchantTokens.intersect(tokens).size.toDouble() / merchantTokens.size
                val amount = e.amount ?: 0.0
                val amountScore = if (amount > 0.0 && target > 0.0)
                    1.0 - (abs(amount - target) / max(amount, target)).coerceIn(0.0, 1.0)
                else 0.0
                e to (overlap * 0.7 + amountScore * 0.3)
            }
            .sortedByDescending { it.second }
            .take(max)
            .map { it.first }
            .toList()
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[\\s,.()/\\-]+")).filter { it.length >= 2 }.toSet()

    // -------------------- Stage logging helpers --------------------
    //
    // The dev AI log surfaces every stage of the cascade. AI stages already self-record
    // (extract / similarity); these helpers format the DB-side stages (shots pulled from
    // ExtractionExampleDBHandler, mapping_lookup against MerchantMappingDBHandler, candidate
    // selection from cached entries) so the on-device log reads as a complete trace.

    private fun describeShots(shots: List<ExtractionExampleDBHandler.Example>): String {
        if (shots.isEmpty()) return "0 shots — zero-shot fallback (DB empty or pickShots failed)"
        return buildString {
            append(shots.size).append(" shots:\n")
            for (s in shots) {
                append("- [").append(s.kind).append("] hit=").append(s.hitCount)
                    .append(" merchant=").append(s.merchant)
                    .append(" amount=").append(s.amount).append('\n')
                if (s.notificationTitle.isNotBlank()) {
                    append("  title=").append(s.notificationTitle.take(60).replace('\n', ' '))
                    append('\n')
                }
                append("  body=").append(s.notificationText.take(80).replace('\n', ' '))
                if (s.notificationText.length > 80) append('…')
                append('\n')
            }
        }
    }

    private fun describeMappingLookup(
        extracted: ExtractedFields,
        mapping: MerchantMappingDBHandler.Mapping?,
        lAcc: Account?,
        rAcc: Account?
    ): String {
        if (mapping == null) return "MISS — falling back to AI similarity"
        val outcome = when {
            lAcc != null && rAcc != null -> "BOTH ACCOUNTS LIVE → READY (state short-circuits AI)"
            lAcc == null && rAcc == null -> "BOTH ACCOUNTS STALE → falling back to AI similarity"
            lAcc == null -> "LEFT ACCOUNT STALE → falling back to AI similarity"
            else -> "RIGHT ACCOUNT STALE → falling back to AI similarity"
        }
        return buildString {
            append("HIT (hit_count=").append(mapping.hitCount)
                .append(", last_used=").append(mapping.lastUsed).append(")\n")
            append("  l=").append(mapping.lAccountType).append('/').append(mapping.lAccountId).append('\n')
            append("  r=").append(mapping.rAccountType).append('/').append(mapping.rAccountId).append('\n')
            append(outcome)
        }
    }

    private fun describeCandidates(
        poolSize: Int,
        candidates: List<Entry>
    ): String {
        if (candidates.isEmpty()) {
            return "0 candidates from pool of $poolSize — UNPARSED (similarity skipped)"
        }
        return buildString {
            append(candidates.size).append(" candidates from pool of ").append(poolSize).append(":\n")
            val previewLimit = 5
            for ((idx, e) in candidates.withIndex()) {
                if (idx >= previewLimit) {
                    append("  … (+").append(candidates.size - previewLimit).append(" more)\n")
                    break
                }
                append("- ").append(e.id).append(" | ")
                    .append((e.item.orEmpty()).take(40)).append(" | ")
                    .append(e.amount?.toLong() ?: 0L).append(" | ")
                    .append(e.leftAccount.orEmpty()).append(" → ").append(e.rightAccount.orEmpty())
                    .append('\n')
            }
        }
    }

    // optString returns "" for missing keys, which collides with legitimately empty values;
    // use this to disambiguate.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    /**
     * Resolve the similarity prompt's confidence field to a Double. The prompt asks for
     * "high" / "medium" / "low" — those are the well-calibrated path. If the model regressed
     * and returned a number we accept that too (clamped to 0..1) so a noisy run still
     * produces a usable result rather than UNPARSED. Returns null when the field is missing
     * or unrecognized so the caller can treat the row as a parse failure.
     */
    private fun parseConfidenceLevel(json: JSONObject): Double? {
        val raw = json.opt("confidence") ?: return null
        if (raw is Number) return raw.toDouble().coerceIn(0.0, 1.0)
        val label = raw.toString().trim().lowercase()
        return when (label) {
            "high" -> CONFIDENCE_HIGH
            "medium", "med" -> CONFIDENCE_MEDIUM
            "low" -> CONFIDENCE_LOW
            else -> label.toDoubleOrNull()?.coerceIn(0.0, 1.0)
        }
    }
}
