package kr.blogspot.charlie0301.wimple

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.blogspot.charlie0301.wimple.impl.BankNotificationClassifier
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue

/**
 * Phase 2 review queue screen, extended in Phase 4 to drive the AI cascade. On entry every
 * row that doesn't already carry a cached [BankNotificationClassifier.Result] gets queued
 * for classification — sequential, foreground-only because ML Kit Prompt API rejects
 * background calls. Each completion persists back into [LocalReviewQueue.setClassification]
 * so re-entering the screen never repeats inference for the same row.
 *
 * Confirm flow ([확정] one-tap, [선택] picker) is Phase 5; for now only [Dismiss] and
 * [Manual entry] are wired.
 */
class BankNotificationReviewActivity : AppCompatActivity() {

    private lateinit var adapter: ReviewAdapter
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var toolbar: Toolbar

    /**
     * Tracks the currently-running classify job so a second resume (or finish()) cancels in-
     * flight inference instead of leaking a coroutine into the next screen. Single job is
     * fine because we classify rows serially.
     */
    private var classifyJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_notification_review)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.bank_noti_review_title)
        }

        listView = findViewById(R.id.review_list)
        emptyView = findViewById(R.id.empty_view)
        adapter = ReviewAdapter()
        listView.adapter = adapter

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) as? LocalReviewQueue.ReviewItem
                ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setMessage(R.string.bank_noti_review_delete_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    LocalReviewQueue.removeById(this, item.id)
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        startClassificationPass()
    }

    override fun onPause() {
        super.onPause()
        // Cancel in-flight classification when leaving the screen — the model's checkStatus
        // path is foreground-only and we don't want a half-finished call to update the queue
        // after the user has moved on. The coroutine itself ends once it sees the cancel.
        classifyJob?.cancel()
        classifyJob = null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bank_noti_review, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish(); true
            }
            R.id.menu_clear_all -> {
                confirmClearAll(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmClearAll() {
        if (LocalReviewQueue.count(this) == 0) return
        AlertDialog.Builder(this)
            .setMessage(R.string.bank_noti_review_clear_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                LocalReviewQueue.clear(this)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Hand off to manual entry, prefilling the title and amount from the cached classifier
     * output when the row produced extracted fields. UNPARSED rows (no merchant/amount in
     * the cache) just open an empty form like before. Phase 5 will also auto-remove the
     * queue row when the form is successfully submitted; for now the user [Dismiss]es it
     * after entry.
     */
    private fun openManualEntry(item: LocalReviewQueue.ReviewItem) {
        val intent = Intent(this, WimpleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WimpleActivity.EXTRA_OPEN_MENU, R.id.menu_transaction_insert)

        val result = BankNotificationClassifier.Result.fromJson(item.classificationJson)
        if (result != null) {
            result.merchant?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(WimpleActivity.EXTRA_PREFILL_TITLE, it)
            }
            result.amount?.takeIf { it > 0.0 }?.let {
                intent.putExtra(WimpleActivity.EXTRA_PREFILL_AMOUNT, it)
            }
        }
        startActivity(intent)
    }

    private fun refresh() {
        adapter.reload()
        val empty = adapter.count == 0
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        // Empty queue → clear the toolbar progress subtitle so it doesn't linger from a
        // prior session.
        if (empty) supportActionBar?.subtitle = null
    }

    /**
     * Walk the queue, find rows without a cached classification, and run them serially.
     * Each completed row is persisted, then the adapter's matching position is rebound so
     * the badge / extracted line update without flickering the whole list.
     */
    private fun startClassificationPass() {
        classifyJob?.cancel()
        val pending = adapter.itemsSnapshot().filter { it.classificationJson == null }
        if (pending.isEmpty()) {
            supportActionBar?.subtitle = null
            return
        }
        val total = pending.size
        classifyJob = lifecycleScope.launch {
            pending.forEachIndexed { index, item ->
                supportActionBar?.subtitle =
                    getString(R.string.bank_noti_review_classification_progress, index + 1, total)
                val result = BankNotificationClassifier.classify(this@BankNotificationReviewActivity, item)
                LocalReviewQueue.setClassification(
                    this@BankNotificationReviewActivity,
                    item.id,
                    result.toJson()
                )
                // Reload from disk so the row picks up its persisted classification on next
                // render. Cheap because we only re-parse SharedPreferences JSON.
                adapter.reload()
            }
            supportActionBar?.subtitle = null
        }
    }

    private inner class ReviewAdapter : BaseAdapter() {
        private var items: List<LocalReviewQueue.ReviewItem> = emptyList()

        fun reload() {
            items = LocalReviewQueue.getAll(this@BankNotificationReviewActivity)
            notifyDataSetChanged()
        }

        fun itemsSnapshot(): List<LocalReviewQueue.ReviewItem> = items

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@BankNotificationReviewActivity)
                .inflate(R.layout.item_bank_notification_review, parent, false)
            val item = items[position]
            bindRow(view, item)
            return view
        }

        private fun bindRow(view: View, item: LocalReviewQueue.ReviewItem) {
            val source = item.appLabel.ifBlank { item.packageName }
            view.findViewById<TextView>(R.id.noti_title).text =
                if (item.title.isNotBlank()) "[$source] ${item.title}" else "[$source]"
            view.findViewById<TextView>(R.id.noti_text).text = item.text
            view.findViewById<TextView>(R.id.noti_time).text =
                DateFormat.format("yyyy-MM-dd HH:mm:ss", item.time)

            view.findViewById<Button>(R.id.btn_dismiss).setOnClickListener {
                LocalReviewQueue.removeById(this@BankNotificationReviewActivity, item.id)
                refresh()
            }
            view.findViewById<Button>(R.id.btn_manual_entry).setOnClickListener {
                openManualEntry(item)
            }

            applyClassificationToCard(view, item)
        }

        private fun applyClassificationToCard(view: View, item: LocalReviewQueue.ReviewItem) {
            val badge = view.findViewById<TextView>(R.id.state_badge)
            val extracted = view.findViewById<TextView>(R.id.extracted_line)
            val accountPair = view.findViewById<TextView>(R.id.account_pair_line)

            val result = BankNotificationClassifier.Result.fromJson(item.classificationJson)

            // Three display modes:
            //  - Pending (no cached JSON yet): "Checking…" badge, extracted/pair hidden.
            //  - Result with extracted fields: show badge + merchant·amount line + suggested
            //    account pair (if the cascade resolved one).
            //  - Result with no extracted fields: show state badge only (e.g., UNPARSED on
            //    novel notification with no candidates).
            if (item.classificationJson == null) {
                badge.text = getString(R.string.bank_noti_review_state_classifying)
                badge.setBackgroundColor(STATE_COLOR_NEUTRAL)
                extracted.visibility = View.GONE
                accountPair.visibility = View.GONE
                return
            }

            val (label, color) = when (result?.state) {
                BankNotificationClassifier.State.READY ->
                    getString(R.string.bank_noti_review_state_ready) to STATE_COLOR_READY
                BankNotificationClassifier.State.AMBIGUOUS ->
                    getString(R.string.bank_noti_review_state_ambiguous) to STATE_COLOR_AMBIGUOUS
                BankNotificationClassifier.State.UNPARSED ->
                    getString(R.string.bank_noti_review_state_unparsed) to STATE_COLOR_UNPARSED
                BankNotificationClassifier.State.ERROR, null ->
                    getString(R.string.bank_noti_review_state_error) to STATE_COLOR_ERROR
            }
            badge.text = label
            badge.setBackgroundColor(color)

            if (result != null && result.merchant != null && result.amount != null) {
                val amountStr = getString(
                    R.string.bank_noti_review_amount_format,
                    result.amount.toLong()
                )
                extracted.text = "${result.merchant}    $amountStr"
                extracted.visibility = View.VISIBLE
            } else {
                extracted.visibility = View.GONE
            }

            if (result != null && result.leftAccountTitle != null && result.rightAccountTitle != null) {
                val pair = getString(
                    R.string.bank_noti_review_account_pair,
                    result.leftAccountTitle, result.rightAccountTitle
                )
                val sourceTag = when (result.source) {
                    BankNotificationClassifier.Source.MAPPING ->
                        " · ${getString(R.string.bank_noti_review_source_mapping)}"
                    BankNotificationClassifier.Source.AI_SIMILARITY ->
                        " · ${getString(R.string.bank_noti_review_source_ai)}"
                    BankNotificationClassifier.Source.NONE -> ""
                }
                accountPair.text = pair + sourceTag
                accountPair.visibility = View.VISIBLE
            } else {
                accountPair.visibility = View.GONE
            }
        }
    }

    companion object {
        // Inline ARGB constants — Material chip colors would be cleaner but pulling in the
        // chip library for four badges isn't worth the dependency cost. These match the
        // semantic palette in values/colors.xml roughly.
        private const val STATE_COLOR_NEUTRAL = 0xFF757575.toInt()  // grey
        private const val STATE_COLOR_READY = 0xFF2E7D32.toInt()    // green 800
        private const val STATE_COLOR_AMBIGUOUS = 0xFFEF6C00.toInt() // orange 800
        private const val STATE_COLOR_UNPARSED = 0xFF6A1B9A.toInt()  // purple 800
        private const val STATE_COLOR_ERROR = 0xFFC62828.toInt()     // red 800
    }
}
