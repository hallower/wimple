package kr.blogspot.charlie0301.wimple.impl.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
         * Collapse whitespace runs and lowercase. Doesn't strip punctuation aggressively —
         * "GS25 강남점" should collide with "gs25  강남점" but stay distinct from "GS25
         * 역삼점" (different store), so periods/commas inside the merchant string survive.
         */
        fun normalize(raw: String): String {
            return raw.trim().replace(Regex("\\s+"), " ").lowercase()
        }
    }
}
