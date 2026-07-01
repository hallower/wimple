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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.blogspot.charlie0301.wimple.impl.BankNotificationClassifier
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.model.Item
import java.util.Calendar

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
    private lateinit var dualUseBanner: TextView
    private lateinit var classificationNotice: TextView

    /**
     * Tracks the currently-running classify job so a second resume (or finish()) cancels in-
     * flight inference instead of leaking a coroutine into the next screen. Single job is
     * fine because we classify rows serially.
     */
    private var classifyJob: Job? = null

    private lateinit var confirmController: BankNotificationConfirmController

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
        dualUseBanner = findViewById(R.id.dual_use_banner)
        classificationNotice = findViewById(R.id.classification_notice)
        adapter = ReviewAdapter()
        listView.adapter = adapter

        confirmController = BankNotificationConfirmController(this) { refresh() }

        dualUseBanner.setOnClickListener {
            // Settings is a fragment hosted by WimpleActivity, not its own activity. Send
            // the user there via the same EXTRA_OPEN_MENU plumbing used for manual entry.
            val intent = Intent(this, WimpleActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(WimpleActivity.EXTRA_OPEN_MENU, R.id.menu_preference)
            startActivity(intent)
        }

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
        refreshDualUseBanner()
        refresh()
        startClassificationPass()
        maybeShowTutorial()
        maybeShowLogConsentDialog()
    }

    private fun maybeShowLogConsentDialog() {
        val mgr = kr.blogspot.charlie0301.wimple.impl.AiLogConsentManager
        if (!mgr.shouldPrompt(this)) return
        mgr.markShown(this)

        // Preview: show the first 300 chars of the log to the user before asking
        val logPreview = kr.blogspot.charlie0301.wimple.impl.AiClassificationLog
            .exportJson(this).take(300) + "\n…"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_log_consent_title))
            .setMessage(getString(R.string.ai_log_consent_message, logPreview))
            .setPositiveButton(R.string.ai_log_consent_agree) { _, _ ->
                val intent = mgr.buildEmailIntent(this)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.ai_log_consent_no_email_app, Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton(R.string.ai_log_consent_skip_once, null)
            .setNegativeButton(R.string.ai_log_consent_never) { _, _ ->
                mgr.neverAsk(this)
            }
            .show()
    }

    private fun maybeShowTutorial() {
        if (!intent.getBooleanExtra(EXTRA_SHOW_TUTORIAL, false)) return
        // Consume the extra so rotation / back-stack re-entry doesn't re-trigger.
        intent.removeExtra(EXTRA_SHOW_TUTORIAL)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(KEY_AI_REVIEW_TUTORIAL_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_AI_REVIEW_TUTORIAL_SHOWN, true).apply()
        showTutorialDialog()
    }

    private fun showTutorialDialog() {
        data class TutorialPage(val icon: String, val title: String, val desc: String)

        val pages = listOf(
            TutorialPage(
                getString(R.string.tutorial_page1_icon),
                getString(R.string.tutorial_page1_title),
                getString(R.string.tutorial_page1_desc)
            ),
            TutorialPage(
                getString(R.string.tutorial_page2_icon),
                getString(R.string.tutorial_page2_title),
                getString(R.string.tutorial_page2_desc)
            ),
            TutorialPage(
                getString(R.string.tutorial_page3_icon),
                getString(R.string.tutorial_page3_title),
                getString(R.string.tutorial_page3_desc)
            ),
            TutorialPage(
                getString(R.string.tutorial_page4_icon),
                getString(R.string.tutorial_page4_title),
                getString(R.string.tutorial_page4_desc)
            ),
            TutorialPage(
                getString(R.string.tutorial_page5_icon),
                getString(R.string.tutorial_page5_title),
                getString(R.string.tutorial_page5_desc)
            )
        )

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_tutorial, null)
        val pager = dialogView.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.tutorial_pager)
        val indicatorLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.page_indicator_layout)
        val btnPrev = dialogView.findViewById<Button>(R.id.btn_tutorial_prev)
        val btnNext = dialogView.findViewById<Button>(R.id.btn_tutorial_next)

        // Build dot indicators
        val density = resources.displayMetrics.density
        val dotSize = (8 * density).toInt()
        val dotMargin = (4 * density).toInt()
        val dots = pages.indices.map { i ->
            android.view.View(this).also { dot ->
                val params = android.widget.LinearLayout.LayoutParams(dotSize, dotSize)
                params.setMargins(dotMargin, 0, dotMargin, 0)
                dot.layoutParams = params
                dot.background = android.graphics.drawable.GradientDrawable().also { d ->
                    d.shape = android.graphics.drawable.GradientDrawable.OVAL
                    d.setColor(
                        if (i == 0) getColor(R.color.md_theme_primary)
                        else getColor(R.color.md_theme_outline_variant)
                    )
                }
                indicatorLayout.addView(dot)
            }
        }

        fun updateDots(current: Int) {
            dots.forEachIndexed { i, dot ->
                (dot.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                    if (i == current) getColor(R.color.md_theme_primary)
                    else getColor(R.color.md_theme_outline_variant)
                )
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.tutorial_title)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                btnPrev.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
                btnNext.text = getString(
                    if (position == pages.lastIndex) R.string.tutorial_btn_start else R.string.tutorial_btn_next
                )
            }
        })

        btnPrev.setOnClickListener { pager.currentItem = pager.currentItem - 1 }
        btnNext.setOnClickListener {
            if (pager.currentItem < pages.lastIndex) {
                pager.currentItem = pager.currentItem + 1
            } else {
                dialog.dismiss()
                finish()
            }
        }

        // Show the dialog first so ViewPager2's internal RecyclerView is fully measured
        // before the adapter creates its first ViewHolder. Setting the adapter before show()
        // causes onCreateViewHolder to run while the RecyclerView still has 0 dimensions,
        // making all items 0px wide — they all appear stacked on the first "page" until
        // the user swipes and forces a re-layout.
        dialog.show()

        pager.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun getItemCount() = pages.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_tutorial_page, parent, false)
                v.layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val page = pages[position]
                holder.itemView.findViewById<TextView>(R.id.tutorial_icon).text = page.icon
                holder.itemView.findViewById<TextView>(R.id.tutorial_title).text = page.title
                holder.itemView.findViewById<TextView>(R.id.tutorial_desc).text = page.desc
            }
        }
    }

    /**
     * Show the dual-use warning whenever the user has both outside-input forwarding
     * (KEY_BANK_NOTI_FORWARD) and local review (KEY_BANK_NOTI_LOCAL_REVIEW) on. Re-evaluated
     * every onResume so toggling either setting reflects on next entry.
     */
    private fun refreshDualUseBanner() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val outsideOn = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_FORWARD, false)
        val localOn = prefs.getBoolean(BankNotificationListener.KEY_BANK_NOTI_LOCAL_REVIEW, false)
        dualUseBanner.visibility = if (outsideOn && localOn) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        // Cancel in-flight classification when leaving the screen — the model's checkStatus
        // path is foreground-only and we don't want a half-finished call to update the queue
        // after the user has moved on. The coroutine itself ends once it sees the cancel.
        classifyJob?.cancel()
        classifyJob = null
        // Drop our listener proxy so the next foreground surface (likely WimpleActivity) sees
        // its original response routing. release() is a no-op if no confirm round was active.
        if (::confirmController.isInitialized) confirmController.release()
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
            R.id.menu_confirm_all_ready -> {
                confirmAllReady(); true
            }
            R.id.menu_show_transfers -> {
                showTransferHistoryDialog(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmAllReady() {
        val readyItems = adapter.itemsSnapshot().mapNotNull { item ->
            val r = BankNotificationClassifier.Result.fromJson(item.classificationJson)
            if (r?.state == BankNotificationClassifier.State.READY) item to r else null
        }
        if (readyItems.isEmpty()) return
        confirmController.confirmBatch(readyItems)
    }

    private fun confirmRow(item: LocalReviewQueue.ReviewItem, result: BankNotificationClassifier.Result) {
        confirmController.confirmOne(item, result)
    }

    /**
     * User-triggered retry on an UNPARSED / ERROR row. Drop the cached result so the row
     * goes back to "pending", refresh the list (badge flips to "확인 중…"), then kick the
     * classification pass — which now picks the row up again because its classificationJson
     * is null. Cancels any in-flight pass first so the resumed pass doesn't double-classify.
     */
    private fun retryClassification(item: LocalReviewQueue.ReviewItem) {
        classifyJob?.cancel()
        classifyJob = null
        LocalReviewQueue.resetClassification(this, item.id)
        refresh()
        startClassificationPass()
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

    // ── Task 1: 수동 월별거래 연계 ──────────────────────────────────────────────

    /**
     * 월별 거래 목록을 다이얼로그로 표시하고 사용자가 선택한 항목의 계좌 정보를
     * 해당 알림 행에 적용한다. 금액은 기존 분류 결과 또는 본문 추출 값을 우선 사용한다.
     * 선택 후 행은 READY(금액 있음) 또는 AMBIGUOUS(금액 없음) 상태로 갱신된다.
     */
    private fun showMonthlyLinkPicker(item: LocalReviewQueue.ReviewItem) {
        val monthlies = WimpleImpl.getInstance()?.monthlyItemDBHandler?.allItems.orEmpty()
            .sortedBy { mi -> monthlyDayOfMonth(mi.date ?: 0L) }
        if (monthlies.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.bank_noti_review_monthly_link_dialog_title)
                .setMessage(R.string.bank_noti_review_monthly_link_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val accounts = WimpleImpl.getInstance()?.cachedAccounts.orEmpty()
        val labels = monthlies.map { mi ->
            val lTitle = accounts.firstOrNull { it.id == mi.leftAccountID }?.title ?: mi.leftAccountID
            val rTitle = accounts.firstOrNull { it.id == mi.rightAccountID }?.title ?: mi.rightAccountID
            val dueDay = monthlyDayOfMonth(mi.date ?: 0L)
            val amt = mi.amount?.toLong() ?: 0L
            getString(R.string.bank_noti_review_monthly_link_item_format, mi.item, amt, dueDay) +
                "\n  $lTitle ← $rTitle"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.bank_noti_review_monthly_link_dialog_title)
            .setItems(labels) { _, which -> applyMonthlyLink(item, monthlies[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyMonthlyLink(item: LocalReviewQueue.ReviewItem, mi: Item) {
        val accounts = WimpleImpl.getInstance()?.cachedAccounts.orEmpty()
        val lAcc = accounts.firstOrNull { it.id == mi.leftAccountID }
        val rAcc = accounts.firstOrNull { it.id == mi.rightAccountID }
        if (lAcc == null || rAcc == null) {
            Toast.makeText(this, R.string.bank_noti_review_confirm_skip_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        // 금액: 기존 분류 결과 → 본문 추출 순으로 시도
        val existingAmt = BankNotificationClassifier.Result.fromJson(item.classificationJson)?.amount
        val amount = existingAmt?.takeIf { it > 0.0 }
            ?: BankNotificationClassifier.extractAmountFromText("${item.title}\n${item.text}")

        val kind = monthlyKindFromTypes(mi.leftAccount, mi.rightAccount)
        val state = if (amount != null && amount > 0.0) BankNotificationClassifier.State.READY
                    else BankNotificationClassifier.State.AMBIGUOUS

        val result = BankNotificationClassifier.Result(
            state = state,
            kind = kind,
            merchant = mi.item.ifBlank { null },
            amount = amount,
            leftAccountId = lAcc.id,
            leftAccountTitle = lAcc.title,
            rightAccountId = rAcc.id,
            rightAccountTitle = rAcc.title,
            source = BankNotificationClassifier.Source.MONTHLY,
            confidence = 1.0
        )
        LocalReviewQueue.setClassification(this, item.id, result.toJson())
        if (amount == null || amount <= 0.0) {
            Toast.makeText(this, R.string.bank_noti_review_monthly_link_no_amount, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.bank_noti_review_monthly_link_applied, Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    /** Item의 좌·우 계정 타입으로 kind 결정 (분류기와 동일 로직) */
    private fun monthlyKindFromTypes(leftType: String?, rightType: String?): String {
        val l = leftType.orEmpty(); val r = rightType.orEmpty()
        return when {
            l == "income" || r == "income" -> "income"
            l == "expenses" || r == "expenses" -> "expense"
            else -> "transfer"
        }
    }

    private fun monthlyDayOfMonth(epochMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.DAY_OF_MONTH)

    // ── Task 3b: 기존 이체 내역 보기 ────────────────────────────────────────────

    /**
     * 캐시된 거래 중 좌·우 계정이 모두 자산/부채 타입인 항목(= 계좌이체)을 다이얼로그로
     * 표시한다. 사용자가 이체 패턴을 파악해 수동 분류 시 참고용으로 활용한다.
     */
    private fun showTransferHistoryDialog() {
        val entries = WimpleImpl.getInstance()?.cachedEntries.orEmpty()
        val transfers = entries.filter { e ->
            val l = e.leftAccount.orEmpty()
            val r = e.rightAccount.orEmpty()
            (l == "assets" || l == "liabilities") && (r == "assets" || r == "liabilities")
        }.sortedByDescending { it.date }

        if (transfers.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.bank_noti_review_transfer_history_title)
                .setMessage(R.string.bank_noti_review_transfer_history_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val accounts = WimpleImpl.getInstance()?.cachedAccounts.orEmpty()
        val labels = transfers.take(50).map { e ->
            val lTitle = accounts.firstOrNull { it.id == e.leftAccountID }?.title ?: e.leftAccountID
            val rTitle = accounts.firstOrNull { it.id == e.rightAccountID }?.title ?: e.rightAccountID
            getString(
                R.string.bank_noti_review_transfer_history_item_format,
                e.item.orEmpty(), e.amount?.toLong() ?: 0L, lTitle, rTitle
            )
        }.toTypedArray()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.bank_noti_review_transfer_history_title)} (${transfers.size}건)")
            .setAdapter(adapter, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hand off to manual entry. Attaches everything the cached classifier output knows so
     * the form arrives prefilled (title/amount) and pre-selected (left/right accounts) when
     * those are available, and tagged with the review session ids so the form's success
     * branch can remove the queue row plus learn the user's confirmed account pair into
     * [MerchantMappingDBHandler].
     *
     * UNPARSED rows or those missing fields fall back gracefully: only the keys we know go
     * onto the intent, and the form ignores any subset that's not present.
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
            result.leftAccountId?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(WimpleActivity.EXTRA_PREFILL_LEFT_ACCOUNT_ID, it)
            }
            result.rightAccountId?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(WimpleActivity.EXTRA_PREFILL_RIGHT_ACCOUNT_ID, it)
            }
            // Tag the launch as a review session so the form's success branch can close the
            // loop. Only attach when we have enough to learn from — merchant + kind drive
            // the mapping key; without those there's nothing to upsert (we still attach the
            // item id so the row gets removed on success).
            intent.putExtra(WimpleActivity.EXTRA_REVIEW_ITEM_ID, item.id)
            result.merchant?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(WimpleActivity.EXTRA_REVIEW_MERCHANT, it)
            }
            result.kind?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(WimpleActivity.EXTRA_REVIEW_KIND, it)
            }
        } else {
            // Even with no classification (UNPARSED, ERROR, or pre-classify dismiss), still
            // tag the item id so a manual submit removes the row.
            intent.putExtra(WimpleActivity.EXTRA_REVIEW_ITEM_ID, item.id)
        }

        // Amount handled separately so the regex fallback covers BOTH the no-Result case
        // (UNPARSED with no classification yet, or ERROR) AND the Result-without-amount
        // case (extract returned a kind/merchant but no amount). Without this fallback the
        // user lands on a blank amount field while the original notification text — visible
        // in the panel above — already contains the digits they need to retype.
        val prefillAmount = result?.amount?.takeIf { it > 0.0 }
            ?: BankNotificationClassifier.extractAmountFromText("${item.title}\n${item.text}")
        if (prefillAmount != null) {
            intent.putExtra(WimpleActivity.EXTRA_PREFILL_AMOUNT, prefillAmount)
        }

        // Always include the source notification body for the form's review panel — even
        // UNPARSED rows benefit, since the user is filling the form by hand against it.
        if (item.text.isNotBlank()) {
            intent.putExtra(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_TEXT, item.text)
        }
        // Title is propagated separately from the panel-source label so the form's success
        // branch can persist (Title + Body → extracted JSON) as a few-shot training row.
        if (item.title.isNotBlank()) {
            intent.putExtra(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_TITLE, item.title)
        }
        val source = item.appLabel.ifBlank { item.packageName }
        if (source.isNotBlank()) {
            intent.putExtra(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_SOURCE, source)
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
            classificationNotice.visibility = View.GONE
            return
        }
        val total = pending.size
        classificationNotice.visibility = View.VISIBLE
        classifyJob = lifecycleScope.launch {
            try {
                pending.forEachIndexed { index, item ->
                    // Re-check the queue before each row — the user may have dismissed it
                    // since we took the snapshot. Skipping early avoids spending model time
                    // on a row that's already gone, and keeps the progress badge
                    // (1/N … N/N) honest. setClassification would no-op anyway, but the
                    // inference itself is the expensive part.
                    if (!LocalReviewQueue.exists(
                            this@BankNotificationReviewActivity, item.id
                        )) {
                        return@forEachIndexed
                    }
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
            } finally {
                classificationNotice.visibility = View.GONE
            }
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
                kr.blogspot.charlie0301.wimple.impl.AiLogConsentManager
                    .recordDismissal(this@BankNotificationReviewActivity)
                LocalReviewQueue.removeById(this@BankNotificationReviewActivity, item.id)
                refresh()
            }
            view.findViewById<Button>(R.id.btn_manual_entry).setOnClickListener {
                openManualEntry(item)
            }
            // Task 1: 월별연결 버튼
            view.findViewById<Button>(R.id.btn_monthly_link).setOnClickListener {
                showMonthlyLinkPicker(item)
            }

            applyClassificationToCard(view, item)
            applyConfirmButton(view, item)
            applyRetryButton(view, item)
            applyPairedTransferHint(view, item)
        }

        /**
         * Task 3a: 큐 내에 이 알림과 금액 거의 동일하고 시각 차이 10분 이내인 다른 알림이
         * 있으면 이체 가능성 힌트 TextView를 표시한다.
         */
        private fun applyPairedTransferHint(view: View, item: LocalReviewQueue.ReviewItem) {
            val hintView = view.findViewById<TextView>(R.id.tv_paired_transfer_hint)
            val itemAmt = BankNotificationClassifier.extractAmountFromText(
                "${item.title}\n${item.text}"
            ) ?: run { hintView.visibility = View.GONE; return }

            val queue = LocalReviewQueue.getAll(this@BankNotificationReviewActivity)
            val hasPair = queue.any { other ->
                if (other.id == item.id) return@any false
                val diff = kotlin.math.abs(other.time - item.time)
                if (diff > 10 * 60 * 1000L) return@any false
                val otherAmt = BankNotificationClassifier.extractAmountFromText(
                    "${other.title}\n${other.text}"
                ) ?: return@any false
                val max = maxOf(itemAmt, otherAmt)
                max > 0.0 && kotlin.math.abs(itemAmt - otherAmt) / max <= 0.01
            }
            hintView.visibility = if (hasPair) View.VISIBLE else View.GONE
        }

        /**
         * Show the [재시도] button only when the cached result was UNPARSED or ERROR. READY
         * and AMBIGUOUS rows already have a usable suggestion — re-running inference there
         * would just churn the model. Pre-classify rows hide it too (the running pass will
         * land a result on its own).
         */
        private fun applyRetryButton(view: View, item: LocalReviewQueue.ReviewItem) {
            val retryBtn = view.findViewById<Button>(R.id.btn_retry)
            val result = BankNotificationClassifier.Result.fromJson(item.classificationJson)
            val canRetry = result?.state == BankNotificationClassifier.State.UNPARSED
                || result?.state == BankNotificationClassifier.State.ERROR
            if (canRetry) {
                retryBtn.visibility = View.VISIBLE
                retryBtn.setOnClickListener { retryClassification(item) }
            } else {
                retryBtn.visibility = View.GONE
                retryBtn.setOnClickListener(null)
            }
        }

        /**
         * Show the [확정] one-tap button only on READY rows that carry a fully-resolved
         * suggestion (merchant + amount + both account ids). Lower confidence states need a
         * picker / manual review and so route through [수동 입력] instead.
         */
        private fun applyConfirmButton(view: View, item: LocalReviewQueue.ReviewItem) {
            val confirmBtn = view.findViewById<Button>(R.id.btn_confirm)
            val result = BankNotificationClassifier.Result.fromJson(item.classificationJson)
            val ready = result != null
                && result.state == BankNotificationClassifier.State.READY
                && !result.merchant.isNullOrBlank()
                && result.amount != null && result.amount > 0.0
                && !result.leftAccountId.isNullOrBlank()
                && !result.rightAccountId.isNullOrBlank()
            if (ready) {
                confirmBtn.visibility = View.VISIBLE
                confirmBtn.setOnClickListener { confirmRow(item, result!!) }
            } else {
                confirmBtn.visibility = View.GONE
                confirmBtn.setOnClickListener(null)
            }
        }

        private fun applyClassificationToCard(view: View, item: LocalReviewQueue.ReviewItem) {
            val badge = view.findViewById<TextView>(R.id.state_badge)
            val extractedSection = view.findViewById<View>(R.id.extracted_section)
            val extracted = view.findViewById<TextView>(R.id.extracted_line)
            val accountPair = view.findViewById<TextView>(R.id.account_pair_line)

            val result = BankNotificationClassifier.Result.fromJson(item.classificationJson)

            // Three display modes:
            //  - Pending (no cached JSON yet): "Checking…" badge, extracted/pair hidden.
            //  - Result with extracted fields: show badge + merchant·amount line + suggested
            //    account pair (if the cascade resolved one or both accounts).
            //  - Result with no extracted fields: show state badge only (e.g., UNPARSED on
            //    novel notification with no candidates).
            if (item.classificationJson == null) {
                badge.text = getString(R.string.bank_noti_review_state_classifying)
                setBadgePill(badge, STATE_COLOR_NEUTRAL)
                extractedSection?.visibility = View.GONE
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
            setBadgePill(badge, color)

            if (result != null && result.merchant != null && result.amount != null) {
                val amountStr = getString(
                    R.string.bank_noti_review_amount_format,
                    result.amount.toLong()
                )
                extracted.text = "${result.merchant}  $amountStr"
                extractedSection?.visibility = View.VISIBLE
            } else {
                extractedSection?.visibility = View.GONE
            }

            // Show account pair when at least one side is resolved.
            // "?" is shown on the unknown side so the user knows one side was identified.
            val hasAnyAccount = result != null &&
                (result.leftAccountTitle != null || result.rightAccountTitle != null)
            if (hasAnyAccount) {
                val left = result!!.leftAccountTitle ?: "?"
                val right = result.rightAccountTitle ?: "?"
                val pair = getString(R.string.bank_noti_review_account_pair, left, right)
                val sourceTag = when (result.source) {
                    BankNotificationClassifier.Source.MAPPING ->
                        " · ${getString(R.string.bank_noti_review_source_mapping)}"
                    BankNotificationClassifier.Source.AI_SIMILARITY ->
                        " · ${getString(R.string.bank_noti_review_source_ai)}"
                    BankNotificationClassifier.Source.TRANSFER ->
                        " · ${getString(R.string.bank_noti_review_source_transfer)}"
                    BankNotificationClassifier.Source.MONTHLY ->
                        " · ${getString(R.string.bank_noti_review_source_monthly)}"
                    BankNotificationClassifier.Source.ACCOUNT_DIRECT ->
                        " · 직접매칭"
                    BankNotificationClassifier.Source.NONE -> ""
                }
                accountPair.text = pair + sourceTag
                accountPair.visibility = View.VISIBLE
            } else {
                accountPair.visibility = View.GONE
            }
        }

        private fun setBadgePill(badge: TextView, color: Int) {
            val density = badge.context.resources.displayMetrics.density
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.cornerRadius = density * 20f
            drawable.setColor(color)
            badge.background = drawable
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

        private const val KEY_AI_REVIEW_TUTORIAL_SHOWN = "pref_aiReviewTutorialShown"
        const val EXTRA_SHOW_TUTORIAL = "show_tutorial"
    }
}
