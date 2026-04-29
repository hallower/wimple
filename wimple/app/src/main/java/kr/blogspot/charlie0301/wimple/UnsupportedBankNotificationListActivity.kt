package kr.blogspot.charlie0301.wimple

import android.app.AlertDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kr.blogspot.charlie0301.wimple.impl.BankNotifications

/**
 * Lists notifications that the Whooing parser rejected with HTTP 400. The user can long-press
 * to enter multi-select mode, then re-send to Whooing or delete the selection. Items are also
 * subject to TTL/max-count pruning enforced inside [BankNotifications.getRejected].
 */
class UnsupportedBankNotificationListActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var headerHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unsupported_bank_notification_list)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.bank_noti_unsupported_title)
        }

        listView = findViewById(R.id.notification_list)
        emptyView = findViewById(R.id.empty_view)
        headerHint = findViewById(R.id.header_hint)
        adapter = NotificationAdapter()
        listView.adapter = adapter

        listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE_MODAL
        listView.setMultiChoiceModeListener(MultiChoiceListener())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.unsupported_bank_noti_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.menu_clear_all -> { confirmClearAll(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmClearAll() {
        if (adapter.count == 0) return
        AlertDialog.Builder(this)
            .setMessage(R.string.bank_noti_unsupported_clear_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                BankNotifications.clearRejected(this)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        adapter.reload()
        val empty = adapter.count == 0
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        headerHint.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun selectedIds(): Set<String> {
        val checked: SparseBooleanArray = listView.checkedItemPositions
        val ids = HashSet<String>()
        for (i in 0 until checked.size()) {
            if (checked.valueAt(i)) {
                val pos = checked.keyAt(i)
                if (pos in 0 until adapter.count) {
                    ids.add(adapter.itemAt(pos).id)
                }
            }
        }
        return ids
    }

    private inner class MultiChoiceListener : AbsListView.MultiChoiceModeListener {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.unsupported_bank_noti_action_mode, menu)
            updateTitle(mode)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val ids = selectedIds()
            if (ids.isEmpty()) {
                mode.finish()
                return true
            }
            return when (item.itemId) {
                R.id.menu_send_selected -> {
                    sendSelected(ids)
                    mode.finish()
                    true
                }
                R.id.menu_delete_selected -> {
                    AlertDialog.Builder(this@UnsupportedBankNotificationListActivity)
                        .setMessage(getString(R.string.bank_noti_unsupported_delete_confirm, ids.size))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            BankNotifications.removeRejectedByIds(
                                this@UnsupportedBankNotificationListActivity, ids)
                            refresh()
                            mode.finish()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) { /* no-op */ }

        override fun onItemCheckedStateChanged(
            mode: ActionMode, position: Int, id: Long, checked: Boolean
        ) {
            updateTitle(mode)
        }

        private fun updateTitle(mode: ActionMode) {
            val n = listView.checkedItemCount
            mode.title = getString(R.string.bank_noti_unsupported_selected_count, n)
        }
    }

    private fun sendSelected(ids: Set<String>) {
        val ctx = this
        Toast.makeText(ctx, R.string.bank_noti_sending, Toast.LENGTH_SHORT).show()
        // Per spec: items the user explicitly chose are removed from the rejected list as
        // soon as the send is initiated. If the server rejects them again, they re-enter
        // via the normal 400-handling path in BankNotifications.doSynchronousSend.
        val started = BankNotifications.resendRejectedByIds(ctx, ids) { success ->
            val msg = if (success) R.string.bank_noti_toast_sent else R.string.bank_noti_toast_send_failed
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            refresh()
        }
        if (!started) {
            // Either nothing matched or auth/in-progress check blocked us — refresh so the UI
            // reflects whatever state the store is now in.
            refresh()
        } else {
            refresh()
        }
    }

    private inner class NotificationAdapter : BaseAdapter() {
        private var items: List<BankNotifications.RejectedNotification> = emptyList()

        fun reload() {
            items = BankNotifications.getRejected(this@UnsupportedBankNotificationListActivity)
            notifyDataSetChanged()
        }

        fun itemAt(position: Int) = items[position]

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@UnsupportedBankNotificationListActivity)
                .inflate(R.layout.item_unsupported_bank_notification, parent, false)
            val item = items[position]
            val source = item.appLabel.ifBlank { item.packageName }
            view.findViewById<TextView>(R.id.noti_title).text =
                if (item.title.isNotBlank()) "[$source] ${item.title}" else "[$source]"
            view.findViewById<TextView>(R.id.noti_text).text = item.text
            view.findViewById<TextView>(R.id.noti_time).text =
                DateFormat.format("yyyy-MM-dd HH:mm:ss", item.time)
            view.findViewById<TextView>(R.id.noti_rejected_time).text = getString(
                R.string.bank_noti_unsupported_rejected_at,
                DateFormat.format("yyyy-MM-dd HH:mm", item.rejectedTime).toString()
            )
            return view
        }
    }
}
