package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler
import kr.blogspot.charlie0301.wimple.model.Account
import kr.blogspot.charlie0301.wimple.model.Entry
import org.json.JSONArray
import org.json.JSONObject
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
    private const val MAX_CANDIDATES = 30

    // Confidence thresholds, mirrored from the design doc. Above READY we offer one-tap
    // confirmation; in the AMBIGUOUS band the user gets a candidate picker; below
    // AMBIGUOUS we treat similarity as too weak to seed the form and fall back to manual.
    private const val THRESHOLD_READY = 0.8
    private const val THRESHOLD_AMBIGUOUS = 0.5

    enum class State { READY, AMBIGUOUS, UNPARSED, ERROR }
    enum class Source { MAPPING, AI_SIMILARITY, NONE }

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
        return try {
            doClassify(ctx, item)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "classify failed for ${item.id}", t)
            Result(state = State.ERROR)
        }
    }

    private suspend fun doClassify(
        ctx: Context,
        item: LocalReviewQueue.ReviewItem
    ): Result = withContext(Dispatchers.IO) {
        val extracted = extractFields(item)
            ?: return@withContext Result(state = State.UNPARSED)

        val accounts = WimpleImpl.getInstance()?.cachedAccounts.orEmpty()

        // Step 2: mapping lookup.
        val mappingHandler = MerchantMappingDBHandler(ctx)
        val mapping = mappingHandler.find(extracted.merchant, extracted.kind)
        if (mapping != null) {
            val lAcc = accounts.firstOrNull { it.id == mapping.lAccountId }
            val rAcc = accounts.firstOrNull { it.id == mapping.rAccountId }
            if (lAcc != null && rAcc != null) {
                return@withContext mappingResult(extracted, lAcc, rAcc)
            }
            // Mapping references stale accounts (since-deleted) — fall through to similarity.
            Log.d(LOG_TAG, "mapping accounts no longer in cache; falling back to similarity")
        }

        // Step 3: AI similarity.
        val entries = WimpleImpl.getInstance()?.cachedEntries.orEmpty()
        val candidates = pickCandidates(entries, extracted, MAX_CANDIDATES)
        if (candidates.isEmpty()) return@withContext extractedOnly(extracted, State.UNPARSED)

        val match = aiSimilarity(item, extracted, candidates)
            ?: return@withContext extractedOnly(extracted, State.UNPARSED)

        val candidateEntry = candidates.firstOrNull { it.id == match.bestMatchEntryId }
            ?: return@withContext extractedOnly(extracted, State.UNPARSED)

        val lAcc = accounts.firstOrNull { it.id == candidateEntry.leftAccountID }
        val rAcc = accounts.firstOrNull { it.id == candidateEntry.rightAccountID }
        if (lAcc == null || rAcc == null) return@withContext extractedOnly(extracted, State.UNPARSED)

        val state = when {
            match.confidence >= THRESHOLD_READY -> State.READY
            match.confidence >= THRESHOLD_AMBIGUOUS -> State.AMBIGUOUS
            else -> State.UNPARSED
        }

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

    private suspend fun extractFields(item: LocalReviewQueue.ReviewItem): ExtractedFields? {
        val prompt = buildString {
            append("You will receive a Korean bank notification. ")
            append("Extract the merchant, transaction type, and amount. ")
            append("Respond ONLY with a single JSON object — no markdown fences, no commentary.\n\n")
            append("Notification:\n")
            if (item.title.isNotBlank()) append("Title: ").append(item.title).append('\n')
            append("Body: ").append(item.text).append("\n\n")
            append("Schema:\n")
            append("{\"kind\":\"expense\"|\"income\"|\"transfer\",\"merchant\":\"...\",\"amount\":<integer KRW>}\n\n")
            append("Rules:\n")
            append("- expense: withdrawal/payment. income: deposit/credit. transfer: between own accounts.\n")
            append("- merchant: place or counterparty (e.g., 'GS25 강남점'). Strip bank name and cardholder name.\n")
            append("- amount: integer KRW; '12,000원' → 12000.\n")
            append("- If a field is unclear, omit its key.")
        }
        val response = generate(prompt) ?: return null
        val json = parseJson(response) ?: return null

        val kind = json.optStringOrNull("kind")?.lowercase()?.takeIf {
            it == "expense" || it == "income" || it == "transfer"
        } ?: return null
        val merchant = json.optStringOrNull("merchant")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val amount = if (json.has("amount")) json.optDouble("amount", -1.0) else -1.0
        if (amount <= 0.0) return null

        return ExtractedFields(kind, merchant, amount)
    }

    // -------------------- AI: similarity --------------------

    private data class MatchResult(val bestMatchEntryId: String, val confidence: Double)

    private suspend fun aiSimilarity(
        item: LocalReviewQueue.ReviewItem,
        extracted: ExtractedFields,
        candidates: List<Entry>
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

        val prompt = buildString {
            append("You are matching a new Korean bank notification to the most similar past transaction so we can reuse its account categorization.\n\n")
            append("New notification:\n")
            append("- merchant: ").append(extracted.merchant).append('\n')
            append("- kind: ").append(extracted.kind).append('\n')
            append("- amount: ").append(extracted.amount.toLong()).append('\n')
            append("- raw text: ").append(item.text).append("\n\n")
            append("Candidate past transactions:\n").append(candidateBlock).append("\n\n")
            append("Respond ONLY with this JSON — no markdown fences, no commentary:\n")
            append("{\"best_match_entry_id\":\"<id from list, or null>\",\"confidence\":<0.0 to 1.0>}\n\n")
            append("Rules:\n")
            append("- Same merchant exact: 0.9–1.0.\n")
            append("- Same chain or topical (e.g., another convenience store at similar amount): 0.5–0.8.\n")
            append("- Weak/no link: <0.5 with id null.\n")
        }
        val response = generate(prompt) ?: return null
        val json = parseJson(response) ?: return null

        val rawId = json.optStringOrNull("best_match_entry_id") ?: return null
        if (rawId.equals("null", ignoreCase = true)) return null
        val confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
        return MatchResult(rawId, confidence)
    }

    // -------------------- AI plumbing --------------------

    private suspend fun generate(prompt: String): String? = try {
        val model = Generation.getClient()
        val response = model.generateContent(prompt)
        response.candidates.firstOrNull()?.text
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

    // optString returns "" for missing keys, which collides with legitimately empty values;
    // use this to disambiguate.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
