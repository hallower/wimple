package kr.blogspot.charlie0301.wimple

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * App picker for letting the user choose which installed apps to monitor for bank/card
 * notifications. There are no longer any preset apps — every monitored package flows
 * through this picker.
 *
 * Launch modes:
 *  - Default: lists all launcher-visible apps so the user can pick freely.
 *  - With [EXTRA_FINANCE_FILTER] = true: initially filters to apps whose label or package
 *    contains finance-related keywords (은행, 증권, 카드, 페이, bank, invest, card, pay).
 *    As soon as the user types in the search box, the finance filter turns off and the
 *    search query takes over, so nothing is permanently hidden.
 */
class BankAppPickerActivity : AppCompatActivity() {

    companion object {
        /** Intent extra: if true, open with the finance-app pre-filter active. */
        const val EXTRA_FINANCE_FILTER = "extra_finance_filter"

        // Keywords used by the optional finance-app pre-filter. Matched case-insensitively against
        // both the app's user-facing label AND the package name (label is primary — package names
        // are usually English even for Korean banks, so they rarely contain the Korean keywords).
        //
        // Explicitly covers user-reported misses: 저축은행 (via 은행), 새마을금고 (via 새마을 OR 금고),
        // 농협·축협·수협 (each added by full name since "은행" alone misses 농협중앙회·축협 등),
        // plus common fintech like 토스·뱅크·네이버페이·카카오페이.
        private val FINANCE_KEYWORDS = listOf(
            "은행", "뱅크", "증권", "카드", "페이", "금융", "저축",
            "농협", "축협", "수협", "새마을", "금고", "토스",
            "bank", "invest", "card", "pay", "finance", "wallet", "credit", "toss"
        )
    }

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
        val searchBox = findViewById<EditText>(R.id.search_box)
        val loadingContainer = findViewById<View>(R.id.loading_container)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        initialSelection.addAll(prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet())
        currentSelection.addAll(initialSelection)

        val financeFilterInitial = intent.getBooleanExtra(EXTRA_FINANCE_FILTER, false)
        adapter = AppAdapter()
        if (financeFilterInitial) {
            adapter.setFinanceFilterActive(true)
            supportActionBar?.subtitle = getString(R.string.bank_noti_picker_finance_subtitle)
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position)
            if (currentSelection.contains(entry.pkg)) currentSelection.remove(entry.pkg)
            else currentSelection.add(entry.pkg)
            adapter.notifyDataSetChanged()
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty()
                adapter.setQuery(q)
                // Once the user starts searching manually, drop the finance pre-filter so nothing
                // they might want is permanently hidden.
                if (q.isNotBlank() && adapter.financeFilterActive) {
                    adapter.setFinanceFilterActive(false)
                    supportActionBar?.subtitle = null
                }
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

        // Load installed apps on a background thread so the activity opens instantly
        // with a visible progress indicator. PackageManager.queryIntentActivities +
        // loadIcon() for 100+ launcher apps can take several hundred ms — blocking the
        // main thread there is exactly why users couldn't tell anything was happening
        // after dismissing the data-handling dialog.
        Thread {
            val loaded = enumerateLauncherApps()
            Handler(Looper.getMainLooper()).post {
                if (isFinishing || isDestroyed) return@post
                allEntries = loaded
                adapter.onDataLoaded()
                loadingContainer.visibility = View.GONE
                searchBox.visibility = View.VISIBLE
                listView.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun enumerateLauncherApps(): List<AppEntry> {
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
        return list
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { save(); finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Persist the current selection. Called on every exit path — back gesture, toolbar home,
     * and as a safety net in [onStop] if the activity is being finished. No explicit save button.
     *
     * All ticked packages become monitored AND get appended (insertion-order preserving) to
     * the ordered custom-app list. Unticked packages are removed from both sets.
     */
    private fun save() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Merge picked selection into the monitored set.
        val monitored = HashSet(prefs.getStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, emptySet()) ?: emptySet())
        for (pkg in initialSelection) {
            if (!currentSelection.contains(pkg)) monitored.remove(pkg)
        }
        monitored.addAll(currentSelection)

        // Maintain the ordered custom-app list: drop unticked, append newly-ticked.
        val deselected = initialSelection - currentSelection
        val existingOrder = BankNotifications.getCustomApps(this).toMutableList()
        for (pkg in deselected) existingOrder.remove(pkg)
        for (pkg in currentSelection) {
            if (pkg !in existingOrder) existingOrder.add(pkg)
        }

        prefs.edit()
            .putStringSet(BankNotificationListener.KEY_BANK_NOTI_APPS, monitored)
            .apply()
        BankNotifications.setCustomApps(this, existingOrder)
    }

    override fun onStop() {
        if (isFinishing) save()
        super.onStop()
    }

    private inner class AppAdapter : BaseAdapter() {
        private var filtered: List<AppEntry> = emptyList()
        private var query: String = ""
        var financeFilterActive: Boolean = false
            private set

        /** Called once [allEntries] finishes loading on a background thread. */
        fun onDataLoaded() {
            applyFilters()
        }

        fun setQuery(q: String) {
            query = q.trim()
            applyFilters()
        }

        fun setFinanceFilterActive(active: Boolean) {
            financeFilterActive = active
            applyFilters()
        }

        private fun applyFilters() {
            filtered = allEntries.filter { entry ->
                val matchesFinance = !financeFilterActive || FINANCE_KEYWORDS.any {
                    entry.label.contains(it, ignoreCase = true) ||
                    entry.pkg.contains(it, ignoreCase = true)
                }
                val matchesQuery = query.isEmpty() ||
                    entry.label.contains(query, ignoreCase = true) ||
                    entry.pkg.contains(query, ignoreCase = true)
                matchesFinance && matchesQuery
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
