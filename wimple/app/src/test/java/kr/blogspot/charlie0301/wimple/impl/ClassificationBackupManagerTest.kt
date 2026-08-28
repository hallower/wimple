package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import kotlinx.coroutines.runBlocking
import kr.blogspot.charlie0301.wimple.impl.db.ExtractionExampleDBHandler
import kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ClassificationBackupManagerTest {

    private lateinit var context: Context
    private lateinit var mappingHandler: MerchantMappingDBHandler
    private lateinit var exampleHandler: ExtractionExampleDBHandler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mappingHandler = MerchantMappingDBHandler(context)
        exampleHandler = ExtractionExampleDBHandler(context)
        mappingHandler.clear()
        exampleHandler.clear()
    }

    @After
    fun tearDown() {
        mappingHandler.clear()
        exampleHandler.clear()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(ClassificationBackupManager.KEY_BACKUP_ENABLED)
            .apply()
    }

    // -------------------- explicit opt-in gate --------------------

    @Test
    fun isEnabled_defaultsToFalse() {
        assertEquals(false, ClassificationBackupManager.isEnabled(context))
    }

    @Test
    fun isEnabled_reflectsPreferenceValue() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(ClassificationBackupManager.KEY_BACKUP_ENABLED, true)
            .apply()
        assertEquals(true, ClassificationBackupManager.isEnabled(context))
    }

    @Test
    fun backupNow_noOpsWhenNotExplicitlyEnabled() = runBlocking {
        mappingHandler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201")

        val result = ClassificationBackupManager.backupNow(context)

        assertEquals(false, result)
    }

    @Test
    fun backupIfDue_doesNotThrowWhenNotEnabled() = runBlocking {
        // Silent no-op is the whole point — must not throw regardless of storage support on
        // whatever SDK level Robolectric simulates.
        ClassificationBackupManager.backupIfDue(context)
    }

    @Test
    fun buildBackupJson_includesAllRowsFromBothStores() {
        mappingHandler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201", now = 1000L)
        exampleHandler.upsert("[KB카드] GS25 강남점 12,000원 출금", "expense", "GS25 강남점", 12000L, now = 1000L)

        val json = ClassificationBackupManager.buildBackupJson(context)

        assertTrue(json.contains("gs25 강남점"))
        assertTrue(json.contains("merchantMappings"))
        assertTrue(json.contains("extractionExamples"))
    }

    @Test
    fun backupThenRestore_roundTripsOnAFreshInstall() = runBlocking {
        mappingHandler.upsert("GS25 강남점", "expense", "expenses", "x101", "assets", "x201", now = 1000L)
        exampleHandler.upsert("[KB카드] GS25 강남점 12,000원 출금", "expense", "GS25 강남점", 12000L, now = 2000L)
        val json = ClassificationBackupManager.buildBackupJson(context)

        // Simulate a fresh install: the local stores are empty when restore runs.
        mappingHandler.clear()
        exampleHandler.clear()

        val result = ClassificationBackupManager.restore(context, json)

        assertNotNull(result)
        assertEquals(1, result!!.mappingsRestored)
        assertEquals(1, result.examplesRestored)

        val mapping = mappingHandler.find("GS25 강남점", "expense")
        assertNotNull(mapping)
        assertEquals("x101", mapping!!.lAccountId)

        val examples = exampleHandler.findAll()
        assertEquals(1, examples.size)
        assertEquals("GS25 강남점", examples[0].merchant)
    }

    @Test
    fun restore_mergesWithoutErasingLocallyLearnedRows() = runBlocking {
        // Locally learned more (hit_count 2) than the backup being restored (hit_count 1) —
        // the local row must survive untouched.
        mappingHandler.upsert("GS25 강남점", "expense", "expenses", "x999", "assets", "x888", now = 9000L)
        mappingHandler.upsert("GS25 강남점", "expense", "expenses", "x999", "assets", "x888", now = 9000L)

        val staleBackup = """
            {"version":1,"exportedAt":0,"merchantMappings":[
              {"merchantNorm":"gs25 강남점","kind":"expense","lAccountType":"expenses",
               "lAccountId":"x101","rAccountType":"assets","rAccountId":"x201",
               "lastUsed":1000,"hitCount":1}
            ],"extractionExamples":[]}
        """.trimIndent()

        ClassificationBackupManager.restore(context, staleBackup)

        val mapping = mappingHandler.find("GS25 강남점", "expense")
        assertNotNull(mapping)
        assertEquals("x999", mapping!!.lAccountId)
    }

    @Test
    fun restore_malformedJson_returnsNull() = runBlocking {
        val result = ClassificationBackupManager.restore(context, "not json")
        assertNull(result)
    }
}
