package kr.blogspot.charlie0301.wimple

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import kr.blogspot.charlie0301.wimple.impl.BankNotifications

/**
 * App picker for letting the user add banks/cards not in the preset list.
 *
 * Lists all launcher-visible apps installed on the device. The user ticks any
 * they want monitored; the selection is merged into the MultiSelectListPreference
 * set used by [BankNotificationListener]. The full custom-package list is also
 * kept in [KEY_BANK_NOTI_CUSTOM_APPS] so the main settings screen can render
 * those entries with their labels (looked up via PackageManager).
 */
class BankAppPickerActivity : AppCompatActivity() {

    private data class AppEntry(val label: String, val pkg: String, val icon: Drawable)

    private lateinit var adapter: AppAdapter
    private lateinit var listView: ListView
    private val initialSelection = HashSet<String>()
    private val currentSelection = HashSet<String>()
    private var allEntries: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_app_picker)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.bank_noti_picker_title)
        }

        listView = findViewById(R.id.app_list)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        initialSelection.addAll(prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet())
        currentSelection.addAll(initialSelection)

        loadInstalledApps()

        adapter = AppAdapter()
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position)
            if (currentSelection.contains(entry.pkg)) currentSelection.remove(entry.pkg)
            else currentSelection.add(entry.pkg)
            adapter.notifyDataSetChanged()
        }

        findViewById<EditText>(R.id.search_box).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.setQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Auto-save on system back (gesture or button).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                save()
                finish()
            }
        })
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(launcher, 0)
        val seen = HashSet<String>()
        val list = ArrayList<AppEntry>(resolveInfos.size)
        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName ?: continue
            if (!seen.add(pkg)) continue
            if (pkg == packageName) continue
            val label = try {
                ri.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) { pkg }
            val icon = try {
                ri.loadIcon(pm) ?: pm.defaultActivityIcon
            } catch (_: Exception) { pm.defaultActivityIcon }
            list.add(AppEntry(label, pkg, icon))
        }
        list.sortBy { it.label.lowercase() }
        allEntries = list
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { save(); finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Persists the current pick list. Called from every exit path — back gesture,
     * toolbar-home button, and as a safety net in [onStop] if the activity is being
     * finished. No explicit save button is shown.
     */
    private fun save() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Merge picked selection into the monitored set.
        val monitored = HashSet(prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet())
        // Remove packages that were in initialSelection but not in currentSelection (user unticked).
        for (pkg in initialSelection) {
            if (!currentSelection.contains(pkg)) monitored.remove(pkg)
        }
        // Add newly-ticked ones.
        monitored.addAll(currentSelection)

        // Update the ordered custom-app list. Insertion order is preserved so the settings UI
        // can render "추가된 순" sorting correctly.
        val presetValues = resources.getStringArray(R.array.bank_app_values).toHashSet()
        val deselected = initialSelection - currentSelection
        val existingOrder = BankNotifications.getCustomApps(this).toMutableList()
        // Drop custom packages that were unticked in this picker session.
        for (pkg in deselected) {
            if (pkg !in presetValues) existingOrder.remove(pkg)
        }
        // Append newly-added custom packages (preserve first-seen order).
        for (pkg in currentSelection) {
            if (pkg !in presetValues && pkg !in existingOrder) existingOrder.add(pkg)
        }

        prefs.edit()
            .putStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, monitored)
            .apply()
        BankNotifications.setCustomApps(this, existingOrder)
    }

    override fun onStop() {
        // Safety net: guarantees a save if the activity is being finished via some path that
        // bypassed back/home (e.g. task removal, system kill with finish). No-op if already saved.
        if (isFinishing) save()
        super.onStop()
    }

    private inner class AppAdapter : BaseAdapter() {
        private var filtered: List<AppEntry> = allEntries
        private var query: String = ""

        fun setQuery(q: String) {
            query = q.trim()
            filtered = if (query.isEmpty()) allEntries
            else allEntries.filter {
                it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true)
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = filtered.size
        override fun getItem(position: Int): AppEntry = filtered[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@BankAppPickerActivity)
                .inflate(R.layout.item_bank_app_picker, parent, false)
            val item = filtered[position]
            view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
            view.findViewById<TextView>(R.id.app_name).text = item.label
            view.findViewById<TextView>(R.id.app_package).text = item.pkg
            view.findViewById<CheckBox>(R.id.app_check).isChecked = currentSelection.contains(item.pkg)
            return view
        }
    }
}
