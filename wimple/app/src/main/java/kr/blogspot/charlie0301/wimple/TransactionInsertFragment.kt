package kr.blogspot.charlie0301.wimple


import android.content.Context
import android.os.Bundle
import android.os.Message
import androidx.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.TextView
import kr.blogspot.charlie0301.wimple.databinding.FragmentTransactionInsertTabBinding
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.db.ExtractionExampleDBHandler
import kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler
import kr.blogspot.charlie0301.wimple.impl.util.Calculator
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.impl.util.KoreanWordSearch
import kr.blogspot.charlie0301.wimple.model.Account
import kr.blogspot.charlie0301.wimple.model.Entry
import kr.blogspot.charlie0301.wimple.model.Item
import kr.blogspot.charlie0301.wimple.widget.AccountExpandableListAdapter
import kr.blogspot.charlie0301.wimple.widget.DatePickerFragment
import java.util.*
import kotlin.collections.ArrayList


class TransactionInsertFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()
    private var _binding: FragmentTransactionInsertTabBinding? = null
    private val binding get() = _binding!!

    // Widget
    private lateinit var leftAccountListAdapter: AccountExpandableListAdapter
    private lateinit var rightAccountListAdapter: AccountExpandableListAdapter

    private var datePicker: DatePickerFragment = DatePickerFragment()

    private lateinit var adapterLatestItems: ArrayAdapter<Item>
    private var latestItems : ArrayList<Item> = ArrayList()
    private var editingItem: Item? = null
    private var toolMode = CurrentToolMode.INSERT

    private var selected: Item? = null

    // Review-queue prefill state. Account ids are deferred until after GET_ALL_ACCOUNT_RECEIVED
    // populates the adapters (initial value during onViewCreated is empty); review-session
    // ids are kept until the user submits the form so we can close the loop on success.
    private var pendingLeftAccountId: String? = null
    private var pendingRightAccountId: String? = null
    private var activeReviewItemId: String? = null
    private var activeReviewMerchant: String? = null
    private var activeReviewKind: String? = null
    // Original bank-notification body kept around for the success path so we can persist a
    // (body → user-confirmed extraction) shot to ExtractionExampleDBHandler. Only retained
    // for the duration of the review session — wiped in closeReviewSessionOnSuccess().
    private var activeReviewNotificationText: String? = null
    // Notification title (e.g., "[KB카드] 출금"). Persisted alongside the body in the same
    // few-shot row so the extract prompt can show Title+Body in shots — matching the live
    // notification format and removing the prior format inconsistency. Optional: many bank
    // notifications carry a meaningful title, but UNPARSED rows can lack one and that's
    // fine (empty title is a valid stored value).
    private var activeReviewNotificationTitle: String? = null

    // Inside a review session ([activeReviewItemId] != null) the frequent-item pick
    // refines the item label only — amount, date, and both accounts stay whatever they
    // currently are. This is stricter than the earlier "AI prefilled this" per-field
    // locks: those only protected fields the AI happened to fill, so a UNPARSED row
    // followed by a frequent-item pick would still see accounts/amount cascade. The
    // user-visible contract is now "in review mode, frequent-item is title-only" with
    // no per-field nuance. Outside a review session the cascade runs in full as before.

    private val amountValue: Double
        get() {
            val amount: Double
            try {
                amount =
                    DateFormatUtils.getNumberFormat().parse(binding.insertAmount.text.toString())
                        ?.toDouble() ?: -1.0
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Amount parsing error : " + binding.insertAmount.text)
                return -1.0
            }

            return amount
        }

    // Data
    private enum class CurrentToolMode {
        INSERT, EDITING, MONTHLY_INSERT
    }
    //private boolean isFirstTimeForUniqueFiltering = true;

    /**
     * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
     * onPause() > onStop() > onDestroyView() > onDestroy() > onDetach()
     */

    override fun onResume() {
        Log.i(LOG_TAG, "onResume()")
        this.initWimple()
        super.onResume()
    }

    private fun initWimple() {
        Log.i(LOG_TAG, "initWimple()")

        binding.tiUpdateNotification.visibility = View.VISIBLE
        binding.tiListNotificationText.text = this.resources.getString(R.string.update_latest_items)

        this.wimple.setApplicationContext(context)
        this.wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(this.datePicker.selectedDate), false)
        this.wimple.latestItems
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        Log.i(LOG_TAG, "onCreateView()")
        _binding = FragmentTransactionInsertTabBinding.inflate(inflater, container, false)
        val view = binding.root

        //synchronized(TransactionInsertFragment.class){
        if (padRIDs.isEmpty()) {
            val ar = this.requireContext().resources.obtainTypedArray(R.array.number_buttons)
            for (cnt in 0 until ar.length()) padRIDs.add(ar.getResourceId(cnt, 0))
            ar.recycle()
        }
        //}

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.i(LOG_TAG, "onViewCreated()")
        super.onViewCreated(view, savedInstanceState)

        // To show previous data during new data dispatching without any GUI display delay.
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.requireContext())
        val isNeedDisableMemo = sharedPref.getBoolean(SettingsFragment.KEY_DISABLE_MEMO, true)
        if (isNeedDisableMemo) {
            binding.insertMemoWindow.visibility = View.GONE
        }

        binding.tiUpdateNotification.visibility = View.GONE

        this.setupDate()

        this.setupAccountLists()

        this.setupTitleAndSubmit()

        this.setupLatestItems()

        this.setupButtons()

        cal.setListener { amount -> binding.insertAmount.setText(DateFormatUtils.getDecimalFormat().format(amount)) }

        // Apply review-queue prefill (title + amount from the cached classification) if the
        // fragment was opened via BankNotificationReviewActivity → "수동 입력". Args are
        // cleared after read so a config change rebuild doesn't re-stomp user edits.
        consumePrefillArguments()

        //initWimple();
    }

    private fun consumePrefillArguments() {
        val args = arguments ?: return
        val title = args.getString(WimpleActivity.EXTRA_PREFILL_TITLE)
        val amount = if (args.containsKey(WimpleActivity.EXTRA_PREFILL_AMOUNT))
            args.getDouble(WimpleActivity.EXTRA_PREFILL_AMOUNT)
        else null
        val leftId = args.getString(WimpleActivity.EXTRA_PREFILL_LEFT_ACCOUNT_ID)
        val rightId = args.getString(WimpleActivity.EXTRA_PREFILL_RIGHT_ACCOUNT_ID)
        val reviewItemId = args.getString(WimpleActivity.EXTRA_REVIEW_ITEM_ID)
        val reviewMerchant = args.getString(WimpleActivity.EXTRA_REVIEW_MERCHANT)
        val reviewKind = args.getString(WimpleActivity.EXTRA_REVIEW_KIND)
        val notificationText = args.getString(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_TEXT)
        val notificationSource = args.getString(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_SOURCE)
        val notificationTitle = args.getString(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_TITLE)
        applyPrefill(
            title, amount, leftId, rightId,
            reviewItemId, reviewMerchant, reviewKind,
            notificationText, notificationSource, notificationTitle
        )
        // Single-shot — clear so a config-change rebuild doesn't re-prefill over edits.
        args.remove(WimpleActivity.EXTRA_PREFILL_TITLE)
        args.remove(WimpleActivity.EXTRA_PREFILL_AMOUNT)
        args.remove(WimpleActivity.EXTRA_PREFILL_LEFT_ACCOUNT_ID)
        args.remove(WimpleActivity.EXTRA_PREFILL_RIGHT_ACCOUNT_ID)
        args.remove(WimpleActivity.EXTRA_REVIEW_ITEM_ID)
        args.remove(WimpleActivity.EXTRA_REVIEW_MERCHANT)
        args.remove(WimpleActivity.EXTRA_REVIEW_KIND)
        args.remove(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_TEXT)
        args.remove(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_SOURCE)
        args.remove(WimpleActivity.EXTRA_REVIEW_NOTIFICATION_TITLE)
    }

    /**
     * Public hook so [WimpleActivity] can re-prefill an already-displayed instance of this
     * fragment when the review-queue activity returns via singleTask + CLEAR_TOP. The
     * onViewCreated arguments path covers fresh creation; this covers the same-instance case
     * where replaceWimpleFragment early-returns without re-applying arguments.
     *
     * Title/amount apply to views immediately (calculator value, EditText). Account ids may
     * arrive before [GET_ALL_ACCOUNT_RECEIVED][CommandID.GET_ALL_ACCOUNT_RECEIVED] populates
     * the adapters, so they're stashed in pending fields and applied either from the message
     * handler when the cache lands, or right now if the cache is already in.
     *
     * Review-session ids tag the form's next submit as belonging to a queue row, so the
     * success branch of [CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED] can remove the row and
     * upsert the user-confirmed account pair into [MerchantMappingDBHandler].
     */
    fun applyPrefill(
        title: String?,
        amount: Double?,
        leftAccountId: String? = null,
        rightAccountId: String? = null,
        reviewItemId: String? = null,
        reviewMerchant: String? = null,
        reviewKind: String? = null,
        notificationText: String? = null,
        notificationSource: String? = null,
        notificationTitle: String? = null
    ) {
        if (_binding == null) return
        if (!title.isNullOrBlank()) {
            binding.insertEntryTitle.setText(title)
            binding.insertEntryTitle.setSelection(binding.insertEntryTitle.text.length)
        }
        if (amount != null && amount > 0.0) {
            cal.setValue(amount)
        }
        pendingLeftAccountId = leftAccountId?.takeIf { it.isNotBlank() }
        pendingRightAccountId = rightAccountId?.takeIf { it.isNotBlank() }
        activeReviewItemId = reviewItemId?.takeIf { it.isNotBlank() }
        activeReviewMerchant = reviewMerchant?.takeIf { it.isNotBlank() }
        activeReviewKind = reviewKind?.takeIf { it.isNotBlank() }
        activeReviewNotificationText = notificationText?.takeIf { it.isNotBlank() }
        activeReviewNotificationTitle = notificationTitle?.takeIf { it.isNotBlank() }
        applyNotificationPanel(notificationText, notificationSource)
        tryApplyPendingAccountSelection()
    }

    /**
     * Show or hide the original-notification panel above the form. We surface it whenever a
     * non-blank text arrives, even on UNPARSED rows that have no field prefill — seeing the
     * source body is exactly what a user needs to fill the form by hand.
     */
    private fun applyNotificationPanel(text: String?, source: String?) {
        if (_binding == null) return
        if (text.isNullOrBlank()) {
            binding.reviewNotificationPanel.visibility = View.GONE
            return
        }
        val labelSource = source?.takeIf { it.isNotBlank() } ?: ""
        binding.reviewNotificationLabel.text = getString(
            R.string.review_notification_panel_label, labelSource
        )
        binding.reviewNotificationText.text = text
        binding.reviewNotificationPanel.visibility = View.VISIBLE
    }

    /**
     * (Re-)apply the AI-prefilled account ids whenever the adapters carry data. The pending
     * fields are NOT cleared on success — that's intentional. GET_ALL_ACCOUNT_RECEIVED fires
     * multiple times during a single review session (initial setupDate, onResume's
     * initWimple, any date-picker change), and each fire calls `adapter.clear()` which
     * wipes the visual selection. If we cleared pending after the first apply, subsequent
     * adapter reloads would leave the AI's choice as an empty selection in the list while
     * the title text might still hint at it — exactly the "AI account doesn't seem
     * selected" symptom. Holding pending across reloads lets us re-stamp the selection
     * each time, until the user explicitly overrides (account list tap clears pending) or
     * the review session ends (closeReviewSessionOnSuccess / clearForms reset pending).
     */
    private fun tryApplyPendingAccountSelection() {
        if (_binding == null) return
        if (!::leftAccountListAdapter.isInitialized || !::rightAccountListAdapter.isInitialized) return
        val left = pendingLeftAccountId
        if (left != null && leftAccountListAdapter.groupCount > 0) {
            selectLeftCategory(left)
        }
        val right = pendingRightAccountId
        if (right != null && rightAccountListAdapter.groupCount > 0) {
            selectRightCategory(right)
        }
    }

    /**
     * Called from the makeEntry success branch BEFORE the form is cleared. If this submit
     * came out of the review queue ([activeReviewItemId] is set), close the loop:
     *   1. Upsert the merchant→accounts mapping using the user's currently-selected
     *      accounts (which may differ from the AI suggestion if they corrected it — that's
     *      the "auto-learn the correction" path).
     *   2. Remove the row from [LocalReviewQueue].
     *   3. Clear the review fields so the next non-review submit doesn't re-fire this.
     */
    private fun closeReviewSessionOnSuccess() {
        val itemId = activeReviewItemId ?: return
        val ctx = context
        if (ctx != null) {
            val kind = activeReviewKind
            if (!activeReviewMerchant.isNullOrBlank() && !kind.isNullOrBlank()
                && ::leftAccountListAdapter.isInitialized
                && ::rightAccountListAdapter.isInitialized) {
                val left = leftAccountListAdapter.selected
                val right = rightAccountListAdapter.selected
                if (left != null && right != null) {
                    MerchantMappingDBHandler(ctx).upsert(
                        activeReviewMerchant!!, kind,
                        left.what, left.id,
                        right.what, right.id
                    )
                }
            }
            // Persist a (notification body → ground-truth extraction) shot using the form's
            // final values, since the user may have corrected merchant or amount. The shot
            // table is separate from the merchant mapping above — that one keys on merchant
            // for AI bypass, this one is keyed on body for prompt grounding.
            val notiText = activeReviewNotificationText
            val finalMerchant = binding.insertEntryTitle.text?.toString()?.trim().orEmpty()
            val finalAmount = amountValue.takeIf { it > 0.0 }?.toLong() ?: 0L
            if (!notiText.isNullOrBlank()
                && !kind.isNullOrBlank()
                && finalMerchant.isNotBlank()
                && finalAmount > 0L) {
                ExtractionExampleDBHandler(ctx).upsert(
                    notiText, kind, finalMerchant, finalAmount,
                    notificationTitle = activeReviewNotificationTitle.orEmpty()
                )
            }
            LocalReviewQueue.removeById(ctx, itemId)
        }
        activeReviewItemId = null
        activeReviewMerchant = null
        activeReviewKind = null
        activeReviewNotificationText = null
        activeReviewNotificationTitle = null
        pendingLeftAccountId = null
        pendingRightAccountId = null
        // Hide the source-notification panel so a follow-up non-review submit (re-using the
        // same fragment instance) doesn't show stale context above the empty form.
        if (_binding != null) {
            binding.reviewNotificationPanel.visibility = View.GONE
        }
    }

    private fun setupTitleAndSubmit() {

        binding.insertAmount.setOnEditorActionListener(TextView.OnEditorActionListener { textView, id, _ ->
            when (id) {
                EditorInfo.IME_ACTION_DONE -> {
                    this.setAmount(textView.text.toString())

                    val imm = this.requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(this.requireView().windowToken, 0)

                    return@OnEditorActionListener true
                }
            }
            false
        })


        binding.btnSubmit.setOnClickListener(OnClickListener {
            binding.btnSubmit.isEnabled = false

            // To handle typed amount by IME
            this.setAmount(binding.insertAmount.text.toString())
            binding.insertAmount.setText(cal.eq().toString())

            if (!this.validateForms()) {
                binding.btnSubmit.isEnabled = true
                return@OnClickListener
            }

            val amount = this.amountValue
            /*
				if(amount < 0){
					btn_submit.setEnabled(true);
					Log.e(LOG_TAG, "Amount parsing error : " + insert_amount.getText());
					return;
				}*/

            if (this.toolMode == CurrentToolMode.EDITING) {
                this.toolMode = CurrentToolMode.INSERT

                /*
					 * server doesn't receive yyyyMMdd.xxxx format
					String date = editingItem.getDateValue();
					if(datePicker.isDateChanged()){
						date = DateFormatUtils.getServerDateString(datePicker.getSelectedDate());
					}
					 */

                val res = this.wimple.modifyEntry(this.editingItem!!.id, DateFormatUtils.getServerDateString(this.datePicker.selectedDate),
                        this.leftAccountListAdapter.selected, this.rightAccountListAdapter.selected,
                        binding.insertEntryTitle.text.toString(), amount, binding.insertMemo.text.toString())
                if (!res) {
                    binding.btnSubmit.isEnabled = true
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.modify_failed))
                } else {
                    binding.tiUpdateNotification.visibility = View.VISIBLE
                    binding.tiListNotificationText.text = this.resources.getString(R.string.modify_exist_item)
                }

                this.editingItem = null

            } else {
                val res = this.wimple.makeEntry(this.datePicker.selectedDate,
                        this.leftAccountListAdapter.selected, this.rightAccountListAdapter.selected,
                        binding.insertEntryTitle.text.toString(), amount, binding.insertMemo.text.toString())

                if (!res) {
                    binding.btnSubmit.isEnabled = true
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.insert_failed))
                } else {
                    binding.tiUpdateNotification.visibility = View.VISIBLE
                    binding.tiListNotificationText.text = this.resources.getString(R.string.insert_new_item)
                }
            }
        })
        this.setSubmitButton(this.toolMode)

        binding.insertEntryTitle.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filterLatestItems(s)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun filterLatestItems(s: CharSequence) {
        var changed = s.toString().trim { it <= ' ' }
        if (changed.contains("(") && changed.indexOf("(") > 0) {
            changed = changed.substring(0, changed.indexOf("(") - 1)
            changed = changed.trim { it <= ' ' }
        }

        if (changed.isEmpty()) {
            resetLatestItems(latestItems)
        } else {
            val foundItems: ArrayList<Item> = ArrayList()
            for (item in latestItems) {
                if (KoreanWordSearch.matchString(item.item, changed)) {
                    foundItems.add(item)
                }
            }

            foundItems.sortWith { item1, item2 -> item1.item.length.compareTo(item2.item.length) }
            resetLatestItems(foundItems)
        }
    }

    private fun setupLatestItems() {

        this.adapterLatestItems = ArrayAdapter(this.requireContext(), R.layout.list_frequent_entries, R.id.list_frequent_entry_name, latestItems)
        binding.insertFrequentItems.adapter = this.adapterLatestItems
        binding.insertFrequentItems.onItemClickListener = OnItemClickListener { _, _, position, _ -> this.selectLatestItem(position) }

        binding.insertTitleClear.setOnClickListener {
            // In a review session, the user is correcting the AI-extracted title against the
            // notification panel above; the amount, date, and selected accounts are still
            // valid context. Clearing the entire form would force them to redo prefill that
            // the cascade already resolved. Clear just the title — clearForms remains the
            // X-button behavior in normal entry mode.
            if (activeReviewItemId != null) {
                binding.insertEntryTitle.setText("")
            } else {
                this.clearForms()
            }
        }
    }

    private fun setupButtons() {
        val buttons = arrayOfNulls<TextView>(padRIDs.size)
        for (i in padRIDs.indices) {
            buttons[i] = this.requireView().findViewById<View>(padRIDs[i]) as TextView
            buttons[i]!!.setOnClickListener { v ->
                // remove virtual keyboard
                binding.insertEntryTitle.clearFocus()
                binding.insertMemo.clearFocus()

                when (v.id) {

                    // I don't know why numbersRIDS[] is not suitable for this.
                    R.id.insert_pad_10 -> cal.zero()
                    R.id.insert_pad_1 -> cal.shift(1)
                    R.id.insert_pad_2 -> cal.shift(2)
                    R.id.insert_pad_3 -> cal.shift(3)
                    R.id.insert_pad_4 -> cal.shift(4)
                    R.id.insert_pad_5 -> cal.shift(5)
                    R.id.insert_pad_6 -> cal.shift(6)
                    R.id.insert_pad_7 -> cal.shift(7)
                    R.id.insert_pad_8 -> cal.shift(8)
                    R.id.insert_pad_9 -> cal.shift(9)
                    R.id.insert_pad_100 -> cal.zeroTwice()

                    R.id.insert_pad_point -> cal.point()
                    R.id.insert_pad_plus -> cal.plus()
                    R.id.insert_pad_minus -> cal.minus()
                    R.id.insert_pad_multiply -> cal.multiply()
                    R.id.insert_pad_divide -> cal.divide()
                    R.id.insert_pad_eq -> cal.eq()
                    R.id.insert_pad_clear -> cal.clear()
                    R.id.insert_pad_back -> cal.shiftBack()
                }
            }
        }
    }

    private fun setupDate() {
        this.datePicker.setTextViewWidget(binding.insertDate)
        this.datePicker.setOnDateSetListener(object : DatePickerFragment.OnDateSetListener {
            override fun onDateSet(date: Long?) {
                this@TransactionInsertFragment.setupItemDate(date)
            }
        })
        binding.insertDate.setOnClickListener {
            this.datePicker.show(this.requireFragmentManager(), "itemDate")
        }
        this.setupItemDate(Calendar.getInstance().timeInMillis)

        binding.insertYesterday.setOnClickListener {
            val newDate = this.datePicker.selectedDate - 24 * 60 * 60 * 1000
            this.setupItemDate(newDate)
        }

        binding.insertTomorrow.setOnClickListener {
            val newDate = this.datePicker.selectedDate + 24 * 60 * 60 * 1000
            this.setupItemDate(newDate)
        }
    }

    private fun setupAccountLists() {
        binding.insertCategoryLeftTitle.background.alpha = 200
        this.leftAccountListAdapter = AccountExpandableListAdapter(this.context)
        binding.insertCategoryLeft.setAdapter(this.leftAccountListAdapter)

        binding.insertCategoryLeft.setOnChildClickListener { _, _, groupPosition, childPosition, id ->
            this.leftAccountListAdapter.setSelected(groupPosition, childPosition, id)
            binding.insertCategoryLeftTitle.text = (this.leftAccountListAdapter.getChild(groupPosition, childPosition) as Account).title
            // Clear pending so the next GET_ALL_ACCOUNT_RECEIVED adapter reload doesn't
            // re-stamp the AI's earlier suggestion back into the visual selection. The
            // frequent-item cascade is already gated off in review-session mode, so no
            // separate lock state is needed here.
            pendingLeftAccountId = null
            false
        }
//        binding.insertCategoryLeftTitle.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
//            val selectedID = this.leftAccountListAdapter.selected.id
//            if (selectedID.isNotEmpty()) {
//                for (idx in 0 until this.leftAccountListAdapter.groupCount)
//                    binding.insertCategoryLeftTitle.collapseGroup(idx)
//
//                if (!this.selectLeftCategory(selectedID)) {
//                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_account_update_retry))
//                }
//            }
//        }

        binding.insertCategoryRightTitle.background.alpha = 200
        this.rightAccountListAdapter = AccountExpandableListAdapter(this.context)
        binding.insertCategoryRight.setAdapter(this.rightAccountListAdapter)

        binding.insertCategoryRight.setOnChildClickListener { _, _, groupPosition, childPosition, id ->
            this.rightAccountListAdapter.setSelected(groupPosition, childPosition, id)
            binding.insertCategoryRightTitle.text = (this.rightAccountListAdapter.getChild(groupPosition, childPosition) as Account).title
            // Mirror the left-side handler — clear pending so an adapter reload doesn't
            // undo the user's pick.
            pendingRightAccountId = null
            false
        }
//        binding.insertCategoryRight.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
//            val selectedID = this.rightAccountListAdapter.selected.id
//            if (selectedID.isNotEmpty()) {
//                for (idx in 0 until this.rightAccountListAdapter.groupCount)
//                    binding.insertCategoryRight.collapseGroup(idx)
//
//                if (!this.selectRightCategory(selectedID)) {
//                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_account_update_retry))
//                }
//            }
//        }
    }

    private fun setupItemDate(date: Long?) {
        this.datePicker.setDate(date)
        this.wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(this.datePicker.selectedDate), false)
    }

    private fun setAmount(amount: String) {
        if (amount.isEmpty()) {
            cal.setValue(0.0)
            return
        }

        val amountValue: Double = try {
            java.lang.Double.parseDouble(amount.replace(",", ""))
        }catch (e:Exception){
            0.0
        }
        cal.setValue(amountValue)
    }

    private fun setAmount(amount: Double) {
        cal.setValue(amount)
    }

    private fun selectLatestItem(position: Int) {

        try {
            this.selected = this.adapterLatestItems.getItem(position)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(LOG_TAG, "Failed to select latest Item!!!, position=$position")
            return
        }

        if (this.selected == null)
            return

        var title = binding.insertEntryTitle.text.toString()
        var inlineMemo = ""

        val pos = title.indexOf("(")
        if (pos > 0) {
            inlineMemo = title.substring(pos)
            title = title.substring(0, pos)
        }

        if (0 != title.compareTo(this.selected!!.item)) {
            binding.insertEntryTitle.setText("${this.selected!!.item}$inlineMemo")
            binding.insertEntryTitle.setSelection(binding.insertEntryTitle.text.length)
        }
        // Inside a review session the cascade is title-only: amount, date, and both
        // accounts are either AI-recognized ground truth or the user's deliberate
        // corrections within this session, and picking a frequent-item to refine the
        // item label must not overwrite either. Outside a review session the cascade
        // runs in full so picking a past entry completes the form from past behavior.
        if (activeReviewItemId == null) {
            this.setAmount(this.selected!!.amount)
            this.selectLeftCategory(this.selected!!.leftAccountID)
            this.selectRightCategory(this.selected!!.rightAccountID)
        }
    }

    private fun setEntry(entry: Item) {
        selected = entry
        binding.insertEntryTitle.setText(entry.item)
        if (entry is Entry) {
            binding.insertMemo.setText(entry.memo)
        }
        this.setAmount(entry.amount)

        if (this.toolMode == CurrentToolMode.EDITING) {
            this.datePicker.setDate(entry.date)
        } else {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            this.datePicker.setDate(today.timeInMillis)
        }

        this.selectCategory(entry)
    }

    private fun selectLeftCategory(leftAccountID: String): Boolean {
        val selectedLeftGroup = this.leftAccountListAdapter.setSelected(leftAccountID)
        if (selectedLeftGroup == -1) {
            Log.e(LOG_TAG, "Can't select left category!!!, $leftAccountID")
            return false
        }

        //insert_category_right.requestFocusFromTouch()
        binding.insertCategoryLeft.expandGroup(selectedLeftGroup)
        binding.insertCategoryLeft.setSelection(selectedLeftGroup)
        binding.insertCategoryLeft.setSelectedChild(selectedLeftGroup, this.leftAccountListAdapter.selectedChildPosition, true)
        binding.insertCategoryLeftTitle.text = (this.leftAccountListAdapter.getChild(selectedLeftGroup, this.leftAccountListAdapter.selectedChildPosition) as Account).title
        return true
    }

    private fun selectRightCategory(rightAccountID: String): Boolean {
        val selectedRightGroup = this.rightAccountListAdapter.setSelected(rightAccountID)
        if (selectedRightGroup == -1) {
            Log.e(LOG_TAG, "Can't select right category!!!, $rightAccountID")
            return false
        }

        //insert_category_right.requestFocusFromTouch()
        binding.insertCategoryRight.expandGroup(selectedRightGroup)
        binding.insertCategoryRight.setSelection(selectedRightGroup)
        binding.insertCategoryRight.setSelectedChild(selectedRightGroup, this.rightAccountListAdapter.selectedChildPosition, true)
        binding.insertCategoryRightTitle.text = (this.rightAccountListAdapter.getChild(selectedRightGroup, this.rightAccountListAdapter.selectedChildPosition) as Account).title
        return true
    }

    private fun selectCategory(entry: Item) {
        this.selectLeftCategory(entry.leftAccountID)
        this.selectRightCategory(entry.rightAccountID)
    }

    private fun validateForms(): Boolean {
        if (binding.insertEntryTitle.text.toString().isEmpty()) {
            Log.e(LOG_TAG, "Invalid entry title.")
            WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_invalid_title))
            return false
        }

        if (binding.insertAmount.text.toString().isEmpty()) {
            Log.e(LOG_TAG, "Invalid entry amount.")
            WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_invalid_amount))
            return false
        }

        /*
		Double amount = getAmountValue();
		if(amount <= 0){
			Log.e(LOG_TAG, "Invalid entry amount.");
			Toast.makeText(context, context.getResources().getString(R.string.insert_invalid_amount),
					Toast.LENGTH_SHORT).show();
			return false;
		}
		 */

        if (!this.leftAccountListAdapter.isSelected) {
            Log.e(LOG_TAG, "left side account is not selected!!!")
            WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_invalid_left_accounts))
            return false
        }

        if (!this.rightAccountListAdapter.isSelected) {
            Log.e(LOG_TAG, "right side account is not selected!!!")
            WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_invalid_right_accounts))
            return false
        }
        return true
    }

    private fun clearForms() {
        binding.insertEntryTitle.setText("")
        binding.insertMemo.setText("")
        this.setAmount(0.0)
        this.datePicker.setDate(Calendar.getInstance().timeInMillis)
        this.selected = null

        binding.insertCategoryLeftTitle.text = this.resources.getString(R.string.insert_left_accounts)
        binding.insertCategoryRightTitle.text = this.resources.getString(R.string.insert_right_accounts)
        this.leftAccountListAdapter.clearSelection()
        this.rightAccountListAdapter.clearSelection()
        // pending-id fields would normally already be null via closeReviewSessionOnSuccess
        // but reset here too so a clearForms reached from any path leaves no stale prefill
        // that the next account-reload could re-stamp into the form.
        pendingLeftAccountId = null
        pendingRightAccountId = null

        if (CurrentToolMode.EDITING == this.toolMode) {
            this.editingItem = null
        }
        this.toolMode = CurrentToolMode.INSERT
        this.setSubmitButton(this.toolMode)
    }

    private fun resetLatestItems(items : ArrayList<Item>) {
        this.adapterLatestItems.clear()
        this.adapterLatestItems.addAll(items)
        this.adapterLatestItems.notifyDataSetChanged()
    }

    override fun handleMessage(msg: Message) {

        val command = msg.what
        val booleanStatus = msg.arg1 == 1
        val obj = msg.obj

        // if fragment is added or not to the activity
        if (!this.isAdded) {
            return
        }

        when (command) {

            CommandID.WIMPLE_LOGGIN_SUCCESS ->
                //case CommandID.GET_ALL_SECTION_RECEIVED :
            {
                this.initWimple()
            }

            CommandID.GET_ALL_ACCOUNT_RECEIVED -> {

                binding.tiUpdateNotification.visibility = View.GONE
                if (!booleanStatus) {
                    return
                }

                val accountList = arrayListOf<Account>()
                if (obj is Collection<*>) {
                    for (given_item in obj) {
                        //if (given_item is Account) {
                            accountList.add(given_item as Account)
                        //}
                    }
                }

                if (accountList.isEmpty()) {
                    return
                }

                val assets = ArrayList<Account>()
                val liabilities = ArrayList<Account>()
                val capital = ArrayList<Account>()
                val income = ArrayList<Account>()
                val expenses = ArrayList<Account>()

                for (item in accountList) {

                    if (0 == item.type.compareTo("group"))
                        continue

                    when (item.what[0]) {
                        'a'    // assets
                        -> assets.add(item)
                        'l'    // liabilities
                        -> liabilities.add(item)
                        'c'    // capital
                        -> capital.add(item)
                        'i'    // income
                        -> income.add(item)
                        'e'    // expenses
                        -> expenses.add(item)
                        else -> Log.e(LOG_TAG, "Invalid account item !!!!")
                    }
                }

                this.run {
                    val lHeader = ArrayList<String>()
                    lHeader.add(this.resources.getString(R.string.entry_header_asset_p))
                    lHeader.add(this.resources.getString(R.string.entry_header_debt_m))
                    lHeader.add(this.resources.getString(R.string.entry_header_capital_m))
                    lHeader.add(this.resources.getString(R.string.entry_header_expenses))

                    val lChild = HashMap<String, List<Account>>()
                    lChild[lHeader[0]] = assets
                    lChild[lHeader[1]] = liabilities
                    lChild[lHeader[2]] = capital
                    lChild[lHeader[3]] = expenses

                    this.leftAccountListAdapter.clear()
                    this.leftAccountListAdapter.setData(lHeader, lChild)
                    this.leftAccountListAdapter.notifyDataSetChanged()


                    for (idx in 0 until this.leftAccountListAdapter.groupCount)
                        binding.insertCategoryLeft.expandGroup(idx)

                    if (this.selected != null) {
                        val selectedID = this.selected!!.leftAccountID
                        if (selectedID.isNotEmpty())
                            this.selectLeftCategory(selectedID)
                    }
                }

                this.run {
                    val rHeader = ArrayList<String>()
                    rHeader.add(this.resources.getString(R.string.entry_header_asset_m))
                    rHeader.add(this.resources.getString(R.string.entry_header_debt_p))
                    rHeader.add(this.resources.getString(R.string.entry_header_capital_p))
                    rHeader.add(this.resources.getString(R.string.entry_header_income))

                    val rChild = HashMap<String, List<Account>>()
                    rChild[rHeader[0]] = assets
                    rChild[rHeader[1]] = liabilities
                    rChild[rHeader[2]] = capital
                    rChild[rHeader[3]] = income

                    this.rightAccountListAdapter.clear()
                    this.rightAccountListAdapter.setData(rHeader, rChild)
                    this.rightAccountListAdapter.notifyDataSetChanged()

                    for (idx in 0 until this.rightAccountListAdapter.groupCount)
                        binding.insertCategoryRight.expandGroup(idx)

                    if (this.selected != null) {
                        val selectedID = this.selected!!.rightAccountID
                        if (selectedID.isNotEmpty())
                            this.selectRightCategory(selectedID)
                    }

                    // Apply any review-queue prefill that was waiting for the adapters to
                    // hydrate. Idempotent — clears its own pending fields on success.
                    tryApplyPendingAccountSelection()
                }
            }

            CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED -> {
                // do nothing
            }

            CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED -> {
                binding.tiUpdateNotification.visibility = View.GONE

                if (booleanStatus) {
                    this.latestItems = obj as ArrayList<Item>
                    Collections.sort(latestItems, Item.DateDescCompare())
                    resetLatestItems(this.latestItems)
                    filterLatestItems(binding.insertEntryTitle.text)
                }
            }

            CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED -> {
                val entryDate = obj as String

                binding.tiUpdateNotification.visibility = View.GONE

                Log.e(LOG_TAG, "GET_MAKE_ENTRY_RESPONSE_RECEIVED entryDate=$entryDate")
                if (booleanStatus) {
                    // Must run BEFORE clearForms — it reads the user-confirmed account
                    // selection (which may differ from the AI suggestion) and clears the
                    // review-session ids; clearForms then drops the form widgets' state.
                    closeReviewSessionOnSuccess()
                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_success))
                    this.clearForms()
                    this.wimple.getLatestItems(true)
                    this.wimple.getMonthlyItems(true)
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.insert_failed))
                }

                binding.btnSubmit.isEnabled = true
            }

            CommandID.MODIFY_ENTRY -> {

                if (null == obj || obj !is Item)
                    return

                this.toolMode = CurrentToolMode.EDITING
                this.setSubmitButton(this.toolMode)

                this.editingItem = obj
                this.setEntry(obj)

                WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.entry_modify_notice))
            }

            CommandID.ADD_MONTHLY_ITEM -> {

                if (null == obj || obj !is Item)
                    return

                CurrentToolMode.MONTHLY_INSERT
                this.setSubmitButton(this.toolMode)

                this.editingItem = obj
                this.setEntry(obj)

                WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.month_item_modify_notice))
            }

            CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED -> {

                binding.tiUpdateNotification.visibility = View.GONE

                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.modify_success))
                    this.clearForms()
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.modify_failed))
                }
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    private fun setSubmitButton(mode: CurrentToolMode) {

        when (mode) {

            CurrentToolMode.INSERT -> {
                binding.btnSubmit.text = this.resources.getString(R.string.mode_entry_insert)
                binding.btnSubmit.setBackgroundResource(R.color.md_theme_primary)
                binding.btnSubmit.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary))
            }

            CurrentToolMode.EDITING -> {
                binding.btnSubmit.text = this.resources.getString(R.string.mode_entry_modify)
                binding.btnSubmit.setBackgroundResource(R.color.md_theme_secondary)
                binding.btnSubmit.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_on_secondary))
            }

            CurrentToolMode.MONTHLY_INSERT -> {
                binding.btnSubmit.text = this.resources.getString(R.string.mode_monthly_insert)
                binding.btnSubmit.setBackgroundResource(R.color.md_theme_primary)
                binding.btnSubmit.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_on_primary))
            }
        }
        binding.btnSubmit.background.alpha = 192
    }

    override fun setActivityInstance(instance: WimpleActivity) {
        //mainActivity = instance
    }

    companion object {

        private const val LOG_TAG = "TransactionInsertFrag"

        private val cal = Calculator()
        private var padRIDs: MutableList<Int> = arrayListOf()
    }
}
