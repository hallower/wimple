package kr.blogspot.charlie0301.wimple

import android.app.AlertDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kr.blogspot.charlie0301.wimple.impl.AiClassificationLog

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
                    "extract" -> getString(R.string.dev_ai_log_stage_extract)
                    "similarity" -> getString(R.string.dev_ai_log_stage_similarity)
                    else -> stage.label
                }
                append("── Stage ").append(idx + 1).append(" / ").append(label).append(" ──\n")
                append("Prompt:\n").append(stage.prompt).append("\n\n")
                append("Response:\n").append(stage.response ?: "<null>").append("\n\n")
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
}
