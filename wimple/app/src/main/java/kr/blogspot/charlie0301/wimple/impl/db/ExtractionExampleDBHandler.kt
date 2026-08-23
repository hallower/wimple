package kr.blogspot.charlie0301.wimple.impl.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Normalizer

/**
 * Persisted (notification body → ground-truth {kind, merchant, amount}) examples that seed
 * multi-shot prompts in `BankNotificationClassifier.extractFields()`. Each row is a real
 * notification body the user has confirmed (one-tap path) or corrected through the manual
 * form, so the example pool is by construction labeled correctly.
 *
 * Stored in its own SQLite file `wimple_extraction.db` for the same single-table-per-handler
 * reason as [MerchantMappingDBHandler] — bumping DB_VERSION drops only the first registered
 * table on the shared handler pattern in `wimple.db`, and we want this concern isolated.
 *
 * Capped at [maxRows] rows; oldest by `last_used` is evicted on insert overflow so the shot
 * pool stays fresh without unbounded growth. Default cap is [DEFAULT_MAX_EXAMPLES] (≈250 KB
 * at saturation). The cap is constructor-injected so tests can exercise eviction without
 * inserting a thousand rows.
 */
class ExtractionExampleDBHandler @JvmOverloads constructor(
    context: Context,
    private val maxRows: Int = DEFAULT_MAX_EXAMPLES
) {

    data class Example(
        val notificationText: String,
        val kind: String,
        val merchant: String,
        val amount: Long,
        val lastUsed: Long,
        val hitCount: Int,
        // Notification title (e.g., "[KB카드] 출금"). Stored alongside the body so the extract
        // prompt's few-shot examples present exactly the same Title+Body shape the live
        // notification has. Older rows from before the v2 migration carry an empty string
        // here — those shots fall back to body-only formatting in the prompt.
        val notificationTitle: String = ""
    )

    private val helper = Helper(context.applicationContext)

    /**
     * Insert a confirmed extraction example. Dedup key is `(normalize(text), kind)` — same
     * notification body re-confirmed bumps hit_count instead of duplicating, so the prompt
     * doesn't end up with several near-identical shots crowding out diversity.
     */
    @Synchronized
    fun upsert(
        notificationText: String,
        kind: String,
        merchant: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
        notificationTitle: String = ""
    ) {
        val norm = normalizeBody(notificationText)
        if (norm.isBlank() || kind.isBlank() || merchant.isBlank() || amount <= 0L) return
        val storedText = notificationText.trim().take(MAX_TEXT_LEN)
        val storedTitle = notificationTitle.trim().take(MAX_TITLE_LEN)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val updated = db.update(
                TABLE_NAME,
                ContentValues().apply {
                    put(COL_TEXT, storedText)
                    put(COL_TITLE, storedTitle)
                    put(COL_MERCHANT, merchant)
                    put(COL_AMOUNT, amount)
                    put(COL_LAST_USED, now)
                },
                "$COL_TEXT_NORM = ? AND $COL_KIND = ?",
                arrayOf(norm, kind)
            )
            if (updated > 0) {
                db.execSQL(
                    "UPDATE $TABLE_NAME SET $COL_HIT_COUNT = $COL_HIT_COUNT + 1 " +
                        "WHERE $COL_TEXT_NORM = ? AND $COL_KIND = ?",
                    arrayOf(norm, kind)
                )
            } else {
                db.insert(TABLE_NAME, null, ContentValues().apply {
                    put(COL_TEXT_NORM, norm)
                    put(COL_KIND, kind)
                    put(COL_TEXT, storedText)
                    put(COL_TITLE, storedTitle)
                    put(COL_MERCHANT, merchant)
                    put(COL_AMOUNT, amount)
                    put(COL_LAST_USED, now)
                    put(COL_HIT_COUNT, 1)
                })
                evictOverflow(db)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Pick up to [k] examples for the multi-shot extract prompt. We don't know the live
     * notification's `kind` yet (extracting it is the whole point), so the ranking prefers
     * DIVERSITY across kinds first — one shot per kind by hit_count — then tops up by
     * overall hit_count + recency. Even when the pool is heavily skewed (e.g., 90% expense)
     * this guarantees the model sees an income/transfer example if one exists.
     */
    fun pickShots(k: Int = 3): List<Example> {
        if (k <= 0) return emptyList()
        val all = ArrayList<Example>()
        helper.readableDatabase.query(
            TABLE_NAME, null, null, null, null, null,
            "$COL_HIT_COUNT DESC, $COL_LAST_USED DESC"
        ).use { c ->
            while (c.moveToNext()) all.add(cursorToExample(c))
        }
        if (all.isEmpty()) return emptyList()

        val out = ArrayList<Example>(k)
        val seenKinds = HashSet<String>()
        for (ex in all) {
            if (out.size >= k) break
            if (seenKinds.add(ex.kind)) out.add(ex)
        }
        if (out.size < k) {
            val seenIds = out.toHashSet()
            for (ex in all) {
                if (out.size >= k) break
                if (ex !in seenIds) out.add(ex)
            }
        }
        return out
    }

    /**
     * Restore-only write used by [kr.blogspot.charlie0301.wimple.impl.ClassificationBackupManager]:
     * writes every field exactly as given (including hit_count/last_used) via INSERT OR REPLACE,
     * unlike [upsert] which always bumps hit_count by 1. Merges rather than clobbers — a row
     * already present locally with an equal-or-higher hit_count is left alone, so restoring an
     * older backup can't erase examples confirmed more recently than the backup.
     */
    @Synchronized
    fun restore(example: Example) {
        val norm = normalizeBody(example.notificationText)
        if (norm.isBlank() || example.kind.isBlank() || example.merchant.isBlank() || example.amount <= 0L) return
        val existing = findByNormAndKind(norm, example.kind)
        if (existing != null && existing.hitCount >= example.hitCount) return
        val db = helper.writableDatabase
        db.insertWithOnConflict(
            TABLE_NAME, null,
            ContentValues().apply {
                put(COL_TEXT_NORM, norm)
                put(COL_KIND, example.kind)
                put(COL_TEXT, example.notificationText.trim().take(MAX_TEXT_LEN))
                put(COL_TITLE, example.notificationTitle.trim().take(MAX_TITLE_LEN))
                put(COL_MERCHANT, example.merchant)
                put(COL_AMOUNT, example.amount)
                put(COL_LAST_USED, example.lastUsed)
                put(COL_HIT_COUNT, example.hitCount)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        if (existing == null) evictOverflow(db)
    }

    private fun findByNormAndKind(norm: String, kind: String): Example? {
        if (norm.isBlank() || kind.isBlank()) return null
        helper.readableDatabase.query(
            TABLE_NAME, null,
            "$COL_TEXT_NORM = ? AND $COL_KIND = ?",
            arrayOf(norm, kind), null, null, null, "1"
        ).use { c -> return if (c.moveToFirst()) cursorToExample(c) else null }
    }

    fun count(): Int {
        helper.readableDatabase.query(
            TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun findAll(): List<Example> {
        val out = ArrayList<Example>()
        helper.readableDatabase.query(
            TABLE_NAME, null, null, null, null, null,
            "$COL_LAST_USED DESC"
        ).use { c ->
            while (c.moveToNext()) out.add(cursorToExample(c))
        }
        return out
    }

    @Synchronized
    fun clear() {
        helper.writableDatabase.delete(TABLE_NAME, null, null)
    }

    // -------------------- internals --------------------

    private fun evictOverflow(db: SQLiteDatabase) {
        val total = db.query(
            TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val excess = total - maxRows
        if (excess <= 0) return
        // Single-statement deletion of the N oldest rows; SQLite supports rowid IN (subquery).
        // The ORDER BY is index-backed by idx_extraction_examples_last_used, so this stays
        // fast even when the cap is bumped to thousands.
        db.execSQL(
            "DELETE FROM $TABLE_NAME WHERE rowid IN (" +
                "SELECT rowid FROM $TABLE_NAME ORDER BY $COL_LAST_USED ASC LIMIT $excess" +
                ")"
        )
    }

    private fun cursorToExample(c: android.database.Cursor) = Example(
        notificationText = c.getString(c.getColumnIndexOrThrow(COL_TEXT)),
        kind = c.getString(c.getColumnIndexOrThrow(COL_KIND)),
        merchant = c.getString(c.getColumnIndexOrThrow(COL_MERCHANT)),
        amount = c.getLong(c.getColumnIndexOrThrow(COL_AMOUNT)),
        lastUsed = c.getLong(c.getColumnIndexOrThrow(COL_LAST_USED)),
        hitCount = c.getInt(c.getColumnIndexOrThrow(COL_HIT_COUNT)),
        notificationTitle = c.getColumnIndex(COL_TITLE).let { idx ->
            if (idx >= 0) c.getString(idx).orEmpty() else ""
        }
    )

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE)
            db.execSQL(CREATE_INDEX_LAST_USED)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v1 → v2: add notification_title column. Use ALTER TABLE rather than the
            // earlier drop-and-recreate so user-confirmed shots survive the upgrade — losing
            // them would force the model back to zero-shot until the user re-confirms a
            // representative pool of bank-app formats. New rows post-upgrade fill the title;
            // pre-existing rows carry an empty title and the prompt formatter falls back to
            // body-only for those shots.
            if (oldVersion < 2) {
                db.execSQL(
                    "ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TITLE TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }

    companion object {
        private const val DB_NAME = "wimple_extraction.db"
        private const val DB_VERSION = 2

        /**
         * Default cap on stored examples; oldest evicted past this. With ~250 B/row the cap
         * lands around ~250 KB on disk — still trivial vs. a typical app's local data
         * footprint, while giving the prompt-shot picker a deep enough pool that diverse
         * bank-app formats are well represented.
         */
        const val DEFAULT_MAX_EXAMPLES = 1000

        /** Stored body is capped — leading lines carry the structure the model needs. */
        const val MAX_TEXT_LEN = 300

        /** Notification titles are short (e.g., "[KB카드] 출금"); 100 covers the bank-app
         *  formats observed in practice without bloating the row. */
        const val MAX_TITLE_LEN = 100

        const val TABLE_NAME = "extraction_examples"

        const val COL_TEXT_NORM = "notification_text_norm"
        const val COL_KIND = "kind"
        const val COL_TEXT = "notification_text"
        const val COL_TITLE = "notification_title"
        const val COL_MERCHANT = "merchant"
        const val COL_AMOUNT = "amount"
        const val COL_LAST_USED = "last_used"
        const val COL_HIT_COUNT = "hit_count"

        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_TEXT_NORM TEXT NOT NULL,
                $COL_KIND TEXT NOT NULL,
                $COL_TEXT TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL DEFAULT '',
                $COL_MERCHANT TEXT NOT NULL,
                $COL_AMOUNT INTEGER NOT NULL,
                $COL_LAST_USED INTEGER NOT NULL DEFAULT 0,
                $COL_HIT_COUNT INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY ($COL_TEXT_NORM, $COL_KIND)
            )
        """

        // Index backs eviction's ORDER BY last_used and pickShots' last_used tiebreaker.
        // Without it, eviction at the higher cap would degrade to a full table scan + sort.
        private const val CREATE_INDEX_LAST_USED =
            "CREATE INDEX IF NOT EXISTS idx_${TABLE_NAME}_last_used " +
                "ON $TABLE_NAME($COL_LAST_USED)"

        /**
         * Dedup key for example bodies. Steps mirror [MerchantMappingDBHandler.normalize]
         * (NFKC unification + zero-width strip + bracket-to-space + edge punct trim +
         * whitespace collapse + lowercase) and add a 150-char cap on top.
         *
         * Cap at 150 chars: the leading lines of a Korean bank notification carry the
         * deterministic structure (merchant, amount, kind keywords) we want to dedup on;
         * trailing 안내/광고 strings vary across the same logical event and would otherwise
         * produce phantom duplicates.
         */
        fun normalizeBody(raw: String): String {
            if (raw.isBlank()) return ""
            val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            val noZeroWidth = nfkc.replace(ZERO_WIDTH_REGEX, "")
            val bracketsAsSpaces = noZeroWidth.replace(BRACKET_REGEX, " ")
            val edgesTrimmed = bracketsAsSpaces.replace(EDGE_PUNCT_REGEX, "")
            return edgesTrimmed.replace(WHITESPACE_RUN_REGEX, " ").lowercase().take(150)
        }

        // Same regexes as [MerchantMappingDBHandler]; duplicated here rather than shared so
        // the two normalizers can diverge cleanly later if domain-specific rules emerge.
        private val ZERO_WIDTH_REGEX = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")
        private val BRACKET_REGEX = Regex("[()\\[\\]{}\\uFF08\\uFF09\\uFF3B\\uFF3D\\uFF5B\\uFF5D]")
        private val EDGE_PUNCT_REGEX = Regex("^[\\p{P}\\s]+|[\\p{P}\\s]+$")
        private val WHITESPACE_RUN_REGEX = Regex("\\s+")
    }
}
