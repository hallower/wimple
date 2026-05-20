package kr.blogspot.charlie0301.wimple

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import kr.blogspot.charlie0301.wimple.impl.AiClassificationLog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Developer-only viewer for the [AiClassificationLog] ring buffer. Reached from the About
 * settings sub-screen after a 10-tap unlock on the OSS license entry.
 *
 * Each row summarises one classifier run; tapping opens an [AlertDialog] with the captured
 * prompt + response per stage and the parsed result. The detail dialog uses the same
 * AppCompat theme as the rest of the activity stack — no custom dialog layout for now since
 * this surface is gated behind the dev unlock.
 */
class AiClassificationLogActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_classification_log)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.dev_ai_log_activity_title)
        }

        listView = findViewById(R.id.log_list)
        emptyView = findViewById(R.id.empty_view)
        adapter = LogAdapter()
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            showDetail(adapter.entry(position))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.ai_classification_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish(); true
            }
            R.id.menu_export -> {
                exportLog(); true
            }
            R.id.menu_clear_all -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.dev_ai_log_clear_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        AiClassificationLog.clear(this)
                        refresh()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Pretty-print the log as JSON, zip it into the FileProvider-shared cache
     * subdirectory, and hand the resulting content:// URI to the system share
     * sheet pre-populated as an email to the developer recipient. The whole
     * flow runs on the main thread — the log is capped at 30 entries via the
     * ring buffer in [AiClassificationLog], so the JSON serialization and zip
     * write are both well under a frame's worth of work.
     */
    private fun exportLog() {
        val entries = AiClassificationLog.getAll(this)
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.dev_ai_log_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val generatedAt = Date()
        val fileTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(generatedAt)
        val displayTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(generatedAt)
        val baseName = "wimple-ailog-$fileTimestamp"

        val exportsDir = File(cacheDir, "ai_log_exports")
        if (!exportsDir.exists() && !exportsDir.mkdirs()) {
            Log.w(LOG_TAG, "exportLog: failed to create cache subdir ${exportsDir.absolutePath}")
            Toast.makeText(this, R.string.dev_ai_log_export_failed, Toast.LENGTH_SHORT).show()
            return
        }
        // Best-effort cleanup of prior exports — each export is a one-shot
        // attachment so leaving stale files around just consumes cache. The
        // system would clean them on cache pressure anyway, but this keeps the
        // FileProvider-visible directory tidy.
        exportsDir.listFiles()?.forEach { runCatching { it.delete() } }

        val zipFile = File(exportsDir, "$baseName.zip")
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("$baseName.json"))
                zos.write(AiClassificationLog.exportJson(this).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "exportLog: compression failed", t)
            Toast.makeText(this, R.string.dev_ai_log_export_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", zipFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.dev_ai_log_email_subject))
            putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.dev_ai_log_email_body, displayTimestamp, entries.size)
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(
                Intent.createChooser(intent, getString(R.string.dev_ai_log_export_chooser))
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.dev_ai_log_export_no_handler, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        adapter.reload()
        val empty = adapter.count == 0
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showDetail(entry: AiClassificationLog.Entry) {
        // Pre-formatted plaintext blob — easier to copy/paste during prompt tuning than a
        // bespoke layout, and the dialog scrolls if the content overflows.
        val body = buildString {
            append(DateFormat.format("yyyy-MM-dd HH:mm:ss", entry.timestamp))
            append(" · ").append(entry.durationMs).append("ms\n")
            append("[${entry.appLabel.ifBlank { entry.packageName }}]\n")
            if (entry.notiTitle.isNotBlank()) append("Title: ").append(entry.notiTitle).append('\n')
            append("Body: ").append(entry.notiText).append("\n\n")
            for ((idx, stage) in entry.stages.withIndex()) {
                val label = when (stage.label) {
                    "shots" -> getString(R.string.dev_ai_log_stage_shots)
                    "extract" -> getString(R.string.dev_ai_log_stage_extract)
                    "mapping_lookup" -> getString(R.string.dev_ai_log_stage_mapping)
                    "candidates" -> getString(R.string.dev_ai_log_stage_candidates)
                    "similarity" -> getString(R.string.dev_ai_log_stage_similarity)
                    else -> stage.label
                }
                append("── Stage ").append(idx + 1).append(" / ").append(label).append(" ──\n")
                // DB-side stages (shots / mapping_lookup / candidates) repurpose the same
                // (prompt, response) Stage shape: prompt = the query/inputs, response = the
                // result. Keep the rendered labels generic-enough that both readings work.
                append("Query:\n").append(stage.prompt).append("\n\n")
                append("Result:\n").append(stage.response ?: "<null>").append("\n\n")
            }
            entry.resultJson?.let {
                append("── Result ──\n").append(it)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dev_ai_log_detail_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class LogAdapter : BaseAdapter() {
        private var items: List<AiClassificationLog.Entry> = emptyList()

        fun reload() {
            // Most recent first — record() appends, so reverse on read.
            items = AiClassificationLog.getAll(this@AiClassificationLogActivity).asReversed()
            notifyDataSetChanged()
        }

        fun entry(position: Int): AiClassificationLog.Entry = items[position]

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@AiClassificationLogActivity)
                .inflate(R.layout.item_ai_classification_log, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.log_summary).text = item.summary()
            val time = DateFormat.format("yyyy-MM-dd HH:mm:ss", item.timestamp)
            val state = parseStateFromJson(item.resultJson)
            view.findViewById<TextView>(R.id.log_meta).text = "$time · $state · ${item.durationMs}ms"
            return view
        }

        private fun parseStateFromJson(json: String?): String {
            if (json.isNullOrBlank()) return "?"
            return try {
                org.json.JSONObject(json).optString("state", "?")
            } catch (_: Exception) {
                "?"
            }
        }
    }

    companion object {
        private const val LOG_TAG = "AiClassificationLogActivity"
        // Hard-coded developer mailbox — this activity is gated behind a dev-only
        // 10-tap unlock, so the recipient never needs to be user-configurable.
        private const val REPORT_RECIPIENT = "hallower@gmail.com"
    }
}
