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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kr.blogspot.charlie0301.wimple.impl.BankNotifications

class BankNotificationListActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_notification_list)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.bank_noti_list_title)
        }

        listView = findViewById(R.id.notification_list)
        emptyView = findViewById(R.id.empty_view)
        adapter = NotificationAdapter()
        listView.adapter = adapter

        listView.setOnItemLongClickListener { _, _, position, _ ->
            AlertDialog.Builder(this)
                .setMessage(R.string.bank_noti_list_delete_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    BankNotifications.removeAt(this, position)
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
        menuInflater.inflate(R.menu.bank_noti_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish(); true
            }
            R.id.menu_send_now -> {
                sendNow(); true
            }
            R.id.menu_clear_all -> {
                confirmClearAll(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sendNow() {
        if (!BankNotifications.hasAnyUnsent(this)) {
            Toast.makeText(this, R.string.bank_noti_send_none, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.bank_noti_sending, Toast.LENGTH_SHORT).show()
        BankNotifications.forwardToWhooing(this) { success ->
            val msg = if (success) R.string.bank_noti_toast_sent else R.string.bank_noti_toast_send_failed
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    private fun confirmClearAll() {
        if (BankNotifications.count(this) == 0) return
        AlertDialog.Builder(this)
            .setMessage(R.string.bank_noti_clear_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                BankNotifications.clear(this)
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
    }

    private inner class NotificationAdapter : BaseAdapter() {
        private var items: List<BankNotifications.StoredNotification> = emptyList()

        fun reload() {
            items = BankNotifications.getAll(this@BankNotificationListActivity)
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@BankNotificationListActivity)
                .inflate(R.layout.item_bank_notification, parent, false)
            val item = items[position]
            val source = item.appLabel.ifBlank { item.packageName }
            view.findViewById<TextView>(R.id.noti_title).text =
                if (item.title.isNotBlank()) "[$source] ${item.title}" else "[$source]"
            view.findViewById<TextView>(R.id.noti_text).text = item.text
            view.findViewById<TextView>(R.id.noti_time).text =
                DateFormat.format("yyyy-MM-dd HH:mm:ss", item.time)
            return view
        }
    }
}
