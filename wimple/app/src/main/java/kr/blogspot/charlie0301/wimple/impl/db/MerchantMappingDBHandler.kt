package kr.blogspot.charlie0301.wimple.impl.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Normalizer

/**
 * Persisted "this notification merchant maps to these accounts" decisions, used by the local
 * review queue to skip the AI similarity step on the second-and-later occurrences of a known
 * merchant. Lives in its own SQLite file (`wimple_review.db`) rather than the existing
 * `wimple.db` because the [DatabaseHandler] singleton pattern there is single-table-per-
 * handler and bumping its DB_VERSION drops only the first registered table — keeping the
 * mapping store independent avoids that footgun.
 *
 * Primary key is (merchant_norm, kind). [normalize] strips whitespace and lowercases so
 * "GS25 강남점" and "gs25  강남점" collide. `kind` is included in the key because a refund
 * notification at the same merchant flips the left/right account choice; sharing one row
 * for both directions would force one to win and silently overwrite the other.
 *
 * `INSERT OR REPLACE` would reset hit_count, so [upsert] does an UPDATE-then-INSERT inside a
 * transaction. SQLite's `INSERT ... ON CONFLICT DO UPDATE` is API 28+ only and minSdk is 26.
 */
class MerchantMappingDBHandler(context: Context) {

    data class Mapping(
        val merchantNorm: String,
        val kind: String,
        val lAccountType: String,
        val lAccountId: String,
        val rAccountType: String,
        val rAccountId: String,
        val lastUsed: Long,
        val hitCount: Int
    )

    private val helper = Helper(context.applicationContext)

    /**
     * Insert a new mapping or, if (merchantNorm, kind) already exists, overwrite the account
     * fields and bump hit_count + last_used. The increment is what makes "auto-learn the
     * correction" cleanly re-target an existing entry instead of leaving stale data behind.
     */
    @Synchronized
    fun upsert(
        merchantNorm: String,
        kind: String,
        lAccountType: String,
        lAccountId: String,
        rAccountType: String,
        rAccountId: String,
        now: Long = System.currentTimeMillis()
    ) {
        val key = normalize(merchantNorm)
        if (key.isBlank() || kind.isBlank()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val updated = db.update(
                TABLE_NAME,
                ContentValues().apply {
                    put(COL_L_TYPE, lAccountType)
                    put(COL_L_ID, lAccountId)
                    put(COL_R_TYPE, rAccountType)
                    put(COL_R_ID, rAccountId)
                    put(COL_LAST_USED, now)
                },
                "$COL_MERCHANT_NORM = ? AND $COL_KIND = ?",
                arrayOf(key, kind)
            )
            if (updated > 0) {
                // Bump hit_count separately so we keep the existing value before adding 1.
                db.execSQL(
                    "UPDATE $TABLE_NAME SET $COL_HIT_COUNT = $COL_HIT_COUNT + 1 " +
                        "WHERE $COL_MERCHANT_NORM = ? AND $COL_KIND = ?",
                    arrayOf(key, kind)
                )
            } else {
                db.insert(TABLE_NAME, null, ContentValues().apply {
                    put(COL_MERCHANT_NORM, key)
                    put(COL_KIND, kind)
                    put(COL_L_TYPE, lAccountType)
                    put(COL_L_ID, lAccountId)
                    put(COL_R_TYPE, rAccountType)
                    put(COL_R_ID, rAccountId)
                    put(COL_LAST_USED, now)
                    put(COL_HIT_COUNT, 1)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun find(merchantNorm: String, kind: String): Mapping? {
        val key = normalize(merchantNorm)
        if (key.isBlank() || kind.isBlank()) return null
        helper.readableDatabase.query(
            TABLE_NAME, null,
            "$COL_MERCHANT_NORM = ? AND $COL_KIND = ?",
            arrayOf(key, kind),
            null, null, null, "1"
        ).use { c ->
            return if (c.moveToFirst()) cursorToMapping(c) else null
        }
    }

    /**
     * Restore-only write used by [kr.blogspot.charlie0301.wimple.impl.ClassificationBackupManager]:
     * writes every field exactly as given (including hit_count/last_used) via INSERT OR REPLACE,
     * unlike [upsert] which always bumps hit_count by 1. Merges rather than clobbers — a row
     * already present locally with an equal-or-higher hit_count (i.e. learned more recently
     * than the backup) is left alone, so restoring an older backup can't erase newer learning.
     */
    @Synchronized
    fun restore(mapping: Mapping) {
        val key = normalize(mapping.merchantNorm)
        if (key.isBlank() || mapping.kind.isBlank()) return
        val existing = find(key, mapping.kind)
        if (existing != null && existing.hitCount >= mapping.hitCount) return
        helper.writableDatabase.insertWithOnConflict(
            TABLE_NAME, null,
            ContentValues().apply {
                put(COL_MERCHANT_NORM, key)
                put(COL_KIND, mapping.kind)
                put(COL_L_TYPE, mapping.lAccountType)
                put(COL_L_ID, mapping.lAccountId)
                put(COL_R_TYPE, mapping.rAccountType)
                put(COL_R_ID, mapping.rAccountId)
                put(COL_LAST_USED, mapping.lastUsed)
                put(COL_HIT_COUNT, mapping.hitCount)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun findAll(): List<Mapping> {
        val out = ArrayList<Mapping>()
        helper.readableDatabase.query(
            TABLE_NAME, null, null, null, null, null,
            "$COL_LAST_USED DESC"
        ).use { c ->
            while (c.moveToNext()) out.add(cursorToMapping(c))
        }
        return out
    }

    @Synchronized
    fun delete(merchantNorm: String, kind: String): Boolean {
        val key = normalize(merchantNorm)
        if (key.isBlank() || kind.isBlank()) return false
        val db = helper.writableDatabase
        return db.delete(
            TABLE_NAME,
            "$COL_MERCHANT_NORM = ? AND $COL_KIND = ?",
            arrayOf(key, kind)
        ) > 0
    }

    @Synchronized
    fun clear() {
        helper.writableDatabase.delete(TABLE_NAME, null, null)
    }

    private fun cursorToMapping(c: android.database.Cursor) = Mapping(
        merchantNorm = c.getString(c.getColumnIndexOrThrow(COL_MERCHANT_NORM)),
        kind = c.getString(c.getColumnIndexOrThrow(COL_KIND)),
        lAccountType = c.getString(c.getColumnIndexOrThrow(COL_L_TYPE)),
        lAccountId = c.getString(c.getColumnIndexOrThrow(COL_L_ID)),
        rAccountType = c.getString(c.getColumnIndexOrThrow(COL_R_TYPE)),
        rAccountId = c.getString(c.getColumnIndexOrThrow(COL_R_ID)),
        lastUsed = c.getLong(c.getColumnIndexOrThrow(COL_LAST_USED)),
        hitCount = c.getInt(c.getColumnIndexOrThrow(COL_HIT_COUNT))
    )

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "wimple_review.db"
        private const val DB_VERSION = 1

        const val TABLE_NAME = "merchant_mapping"

        const val COL_MERCHANT_NORM = "merchant_norm"
        const val COL_KIND = "kind"
        const val COL_L_TYPE = "l_account_type"
        const val COL_L_ID = "l_account_id"
        const val COL_R_TYPE = "r_account_type"
        const val COL_R_ID = "r_account_id"
        const val COL_LAST_USED = "last_used"
        const val COL_HIT_COUNT = "hit_count"

        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_MERCHANT_NORM TEXT NOT NULL,
                $COL_KIND TEXT NOT NULL,
                $COL_L_TYPE TEXT NOT NULL,
                $COL_L_ID TEXT NOT NULL,
                $COL_R_TYPE TEXT NOT NULL,
                $COL_R_ID TEXT NOT NULL,
                $COL_LAST_USED INTEGER NOT NULL DEFAULT 0,
                $COL_HIT_COUNT INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY ($COL_MERCHANT_NORM, $COL_KIND)
            )
        """

        /**
         * Collapse superficial encoding/format variants of the same merchant string into a
         * single key, while keeping genuinely-different stores distinct. Each step is layered
         * on the prior so a merchant typed with a fullwidth IME, copy-pasted with stray BOM,
         * or wrapped in a bracket ends up at the same key as the canonical form:
         *
         * 1. NFKC unifies fullwidth/halfwidth and compatibility forms (ＧＳ２５ → GS25,
         *    ① → 1) and composed/decomposed Korean — these are encoding artifacts the user
         *    never sees, but they would otherwise produce phantom-distinct rows.
         * 2. Zero-width chars (BOM, ZWSP, ZWNJ, ZWJ) sometimes ride along on copy-paste from
         *    web-rendered notifications and would create invisible-but-distinct keys.
         * 3. Brackets become spaces — keeps the content inside (so "GS25(강남점)" and
         *    "GS25(역삼점)" stay distinct after collapse) while letting "GS25(강남점)" and
         *    "GS25 강남점" collide once whitespace is collapsed below.
         * 4. Leading/trailing punctuation+space is trimmed so "·이마트" and "이마트 " collide
         *    on "이마트". Interior punctuation (`이마트.com`, `4·19기념관`) survives by design.
         * 5. Existing baseline: collapse interior whitespace runs, lowercase.
         *
         * Does NOT strip the corporate marker `(주)` or store-suffixes like `점/매장/지점`
         * — that would merge actually-different stores; see #2-B in the design doc.
         */
        fun normalize(raw: String): String {
            if (raw.isBlank()) return ""
            val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            val noZeroWidth = nfkc.replace(ZERO_WIDTH_REGEX, "")
            val bracketsAsSpaces = noZeroWidth.replace(BRACKET_REGEX, " ")
            val edgesTrimmed = bracketsAsSpaces.replace(EDGE_PUNCT_REGEX, "")
            return edgesTrimmed.replace(WHITESPACE_RUN_REGEX, " ").lowercase()
        }

        // ZWSP (U+200B), ZWNJ (U+200C), ZWJ (U+200D), BOM (U+FEFF) — the four invisibles
        // that ride along on copy-paste from web-rendered notifications. Listed via escapes
        // so the source stays grep-able rather than embedding the literal invisibles inline.
        private val ZERO_WIDTH_REGEX = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")
        // ASCII + halfwidth + fullwidth bracket pairs. NFKC above already maps fullwidth
        // brackets to ASCII, but listing both is harmless and avoids depending on that
        // implementation detail across platforms.
        private val BRACKET_REGEX = Regex("[()\\[\\]{}\\uFF08\\uFF09\\uFF3B\\uFF3D\\uFF5B\\uFF5D]")
        // Use Unicode property \p{P} (all punctuation categories Pc/Pd/Pe/Pf/Pi/Po/Ps), not
        // POSIX \p{Punct} which is ASCII-only and would miss the Korean middle dot `·`,
        // CJK punctuation, fullwidth dashes, and similar.
        private val EDGE_PUNCT_REGEX = Regex("^[\\p{P}\\s]+|[\\p{P}\\s]+$")
        private val WHITESPACE_RUN_REGEX = Regex("\\s+")
    }
}
