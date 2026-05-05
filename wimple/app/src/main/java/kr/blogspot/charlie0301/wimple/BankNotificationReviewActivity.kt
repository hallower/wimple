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
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue

/**
 * Phase 2 review queue screen — displays raw notifications captured into [LocalReviewQueue]
 * and lets the user dismiss them or jump to manual entry. Classification, AI suggestions, and
 * inline confirm are added in later phases; this iteration validates the navigation flow only.
 *
 * Long-press on a row removes a single item (matches [BankNotificationListActivity] pattern so
 * users don't have to relearn the gesture).
 */
class BankNotificationReviewActivity : AppCompatActivity() {

    private lateinit var adapter: ReviewAdapter
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_notification_review)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
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
     * Hand off to the manual-entry flow. Phase 2 just routes to [TransactionInsertFragment] in
     * the existing [WimpleActivity] without prefilling — the user keys in the entry while the
     * notification stays in the review queue, and removes it via [Dismiss] afterwards. Phase 5
     * will prefill the form from the notification body and auto-remove on successful submit.
     */
    private fun openManualEntry(item: LocalReviewQueue.ReviewItem) {
        val intent = Intent(this, WimpleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WimpleActivity.EXTRA_OPEN_MENU, R.id.menu_transaction_insert)
        startActivity(intent)
        // Do NOT remove the queue item — Phase 2 has no prefill or post-submit hook, so the
        // user might want to come back if they cancel input. Explicit Dismiss removes it.
    }

    private fun refresh() {
        adapter.reload()
        val empty = adapter.count == 0
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private inner class ReviewAdapter : BaseAdapter() {
        private var items: List<LocalReviewQueue.ReviewItem> = emptyList()

        fun reload() {
            items = LocalReviewQueue.getAll(this@BankNotificationReviewActivity)
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@BankNotificationReviewActivity)
                .inflate(R.layout.item_bank_notification_review, parent, false)
            val item = items[position]
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
            return view
        }
    }
}
