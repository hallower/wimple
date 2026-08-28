package kr.blogspot.charlie0301.wimple.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.blogspot.charlie0301.wimple.impl.db.ExtractionExampleDBHandler
import kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs up the two on-device AI classification learning stores —
 * [MerchantMappingDBHandler] (merchant→account mappings) and [ExtractionExampleDBHandler]
 * (confirmed few-shot examples) — to a JSON file outside the app's own storage, and restores
 * from it. The ledger itself is NOT backed up here: Whooing's server already holds it.
 *
 * Storage: the public `Documents/Wimple/` MediaStore collection under a fixed display name.
 * Files an app creates there are implicitly readable/writable by that same app (by package
 * name) with no runtime permission on API 29+, and they persist across an uninstall — so a
 * fresh install can silently rediscover a prior backup via [findExistingBackupUri], which is
 * exactly what the first-run restore prompt (SplashScreenActivity) needs. Devices below API 29
 * (pre-scoped-storage) are reported as [StorageSupport.UNSUPPORTED_OS_VERSION] rather than
 * falling back to a legacy permission-gated path — negligible install base for a minSdk-26 app
 * in this era, and half-working storage code is worse than an honest "unavailable".
 */
object ClassificationBackupManager {

    private const val LOG_TAG = "ClassificationBackup"

    const val BACKUP_DISPLAY_NAME = "wimple_classification_backup.json"
    private const val BACKUP_RELATIVE_PATH = "Documents/Wimple/"
    private const val BACKUP_MIME_TYPE = "application/json"
    private const val SCHEMA_VERSION = 1

    private const val KEY_LAST_BACKUP_CHECK = "pref_classificationBackupLastCheck"
    private const val BACKUP_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** Explicit opt-in gate — saving (manual or periodic) never runs unless the user has
     *  turned this on in settings. Defaults off: no silent saving before the user asks for
     *  it. Restore is NOT gated by this — reading a backup back in is a one-off explicit
     *  action in its own right, on the settings screen or the first-install prompt. */
    const val KEY_BACKUP_ENABLED = "pref_classificationBackupEnabled"

    fun isEnabled(ctx: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(KEY_BACKUP_ENABLED, false)

    enum class StorageSupport { SUPPORTED, UNSUPPORTED_OS_VERSION }

    fun storageSupport(): StorageSupport =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) StorageSupport.SUPPORTED
        else StorageSupport.UNSUPPORTED_OS_VERSION

    // -------------------- export --------------------

    /** Explicit "지금 백업" trigger from settings. Always writes, regardless of the throttle —
     *  but still requires [isEnabled], since the button itself is dependency-disabled in the
     *  UI until the user opts in; this is the backend-side half of that same rule. */
    suspend fun backupNow(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled(ctx)) return@withContext false
        if (storageSupport() != StorageSupport.SUPPORTED) return@withContext false
        val ok = writeBackup(ctx, buildBackupJson(ctx))
        if (ok) markBackedUpNow(ctx)
        ok
    }

    /**
     * Opportunistic periodic backup: call from a low-frequency app-open hook. No-ops unless
     * the user has opted in ([isEnabled]), more than [BACKUP_CHECK_INTERVAL_MS] has passed
     * since the last check, AND there's learned data to save, so a fresh/empty install
     * doesn't write an empty file every day.
     */
    suspend fun backupIfDue(ctx: Context) {
        if (!isEnabled(ctx)) return
        if (storageSupport() != StorageSupport.SUPPORTED) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val last = prefs.getLong(KEY_LAST_BACKUP_CHECK, 0L)
        if (System.currentTimeMillis() - last < BACKUP_CHECK_INTERVAL_MS) return
        withContext(Dispatchers.IO) {
            val hasData = MerchantMappingDBHandler(ctx).findAll().isNotEmpty() ||
                ExtractionExampleDBHandler(ctx).findAll().isNotEmpty()
            if (hasData) {
                if (writeBackup(ctx, buildBackupJson(ctx))) markBackedUpNow(ctx)
            } else {
                markBackedUpNow(ctx)
            }
        }
    }

    private fun markBackedUpNow(ctx: Context) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putLong(KEY_LAST_BACKUP_CHECK, System.currentTimeMillis())
            .apply()
    }

    @VisibleForTesting
    internal fun buildBackupJson(ctx: Context): String {
        val mappings = MerchantMappingDBHandler(ctx).findAll()
        val examples = ExtractionExampleDBHandler(ctx).findAll()
        return JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("merchantMappings", JSONArray().apply {
                mappings.forEach { m ->
                    put(JSONObject().apply {
                        put("merchantNorm", m.merchantNorm)
                        put("kind", m.kind)
                        put("lAccountType", m.lAccountType)
                        put("lAccountId", m.lAccountId)
                        put("rAccountType", m.rAccountType)
                        put("rAccountId", m.rAccountId)
                        put("lastUsed", m.lastUsed)
                        put("hitCount", m.hitCount)
                    })
                }
            })
            put("extractionExamples", JSONArray().apply {
                examples.forEach { e ->
                    put(JSONObject().apply {
                        put("notificationText", e.notificationText)
                        put("notificationTitle", e.notificationTitle)
                        put("kind", e.kind)
                        put("merchant", e.merchant)
                        put("amount", e.amount)
                        put("lastUsed", e.lastUsed)
                        put("hitCount", e.hitCount)
                    })
                }
            })
        }.toString()
    }

    private fun writeBackup(ctx: Context, json: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val uri = findExistingBackupUri(ctx) ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_DISPLAY_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, BACKUP_MIME_TYPE)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, BACKUP_RELATIVE_PATH)
                }
            ) ?: return false
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "backup write failed", t)
            false
        }
    }

    // -------------------- discovery + restore --------------------

    /** Fixed-name lookup — no persisted URI/permission required, so this also finds a backup
     *  left behind by a previous install of this app (see class doc). */
    fun findExistingBackupUri(ctx: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val collection = MediaStore.Files.getContentUri("external")
            ctx.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(BACKUP_DISPLAY_NAME, BACKUP_RELATIVE_PATH),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(collection, id)
                }
                null
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "backup lookup failed", t)
            null
        }
    }

    fun hasExistingBackup(ctx: Context): Boolean = findExistingBackupUri(ctx) != null

    /** Epoch-millis the backup file was last written, or null if none exists / lookup failed.
     *  MediaStore.MediaColumns.DATE_MODIFIED is stored in whole seconds, hence the *1000. */
    fun lastBackupTimestamp(ctx: Context): Long? {
        val uri = findExistingBackupUri(ctx) ?: return null
        return try {
            ctx.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) * 1000L else null }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "backup timestamp lookup failed", t)
            null
        }
    }

    suspend fun readBackupJson(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "backup read failed", t)
            null
        }
    }

    data class RestoreResult(val mappingsRestored: Int, val examplesRestored: Int)

    /**
     * Parses [json] and merges every row into the two handlers via their `restore()` writers
     * (see those classes for the merge-not-clobber rule). Malformed rows are skipped
     * individually rather than aborting the whole restore.
     */
    suspend fun restore(ctx: Context, json: String): RestoreResult? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)
            val mappingHandler = MerchantMappingDBHandler(ctx)
            val exampleHandler = ExtractionExampleDBHandler(ctx)

            var mappingsRestored = 0
            root.optJSONArray("merchantMappings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val o = arr.getJSONObject(i)
                        mappingHandler.restore(
                            MerchantMappingDBHandler.Mapping(
                                merchantNorm = o.getString("merchantNorm"),
                                kind = o.getString("kind"),
                                lAccountType = o.getString("lAccountType"),
                                lAccountId = o.getString("lAccountId"),
                                rAccountType = o.getString("rAccountType"),
                                rAccountId = o.getString("rAccountId"),
                                lastUsed = o.optLong("lastUsed", 0L),
                                hitCount = o.optInt("hitCount", 1)
                            )
                        )
                    }.onSuccess { mappingsRestored++ }
                }
            }

            var examplesRestored = 0
            root.optJSONArray("extractionExamples")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val o = arr.getJSONObject(i)
                        exampleHandler.restore(
                            ExtractionExampleDBHandler.Example(
                                notificationText = o.getString("notificationText"),
                                kind = o.getString("kind"),
                                merchant = o.getString("merchant"),
                                amount = o.getLong("amount"),
                                lastUsed = o.optLong("lastUsed", 0L),
                                hitCount = o.optInt("hitCount", 1),
                                notificationTitle = o.optString("notificationTitle", "")
                            )
                        )
                    }.onSuccess { examplesRestored++ }
                }
            }

            RestoreResult(mappingsRestored, examplesRestored)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "restore failed", t)
            null
        }
    }

    suspend fun deleteBackup(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val uri = findExistingBackupUri(ctx) ?: return@withContext false
        try {
            ctx.contentResolver.delete(uri, null, null) > 0
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "backup delete failed", t)
            false
        }
    }
}
