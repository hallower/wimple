package kr.blogspot.charlie0301.wimple


import android.content.Context
import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
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
import kotlinx.android.synthetic.main.fragment_transaction_insert_tab.*
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
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

    // Widget
    private lateinit var leftAccountListAdapter: AccountExpandableListAdapter
    private lateinit var rightAccountListAdapter: AccountExpandableListAdapter

    private var datePicker: DatePickerFragment = DatePickerFragment()

    private lateinit var adapterLatestItems: ArrayAdapter<Item>
    private var latestItems : ArrayList<Item> = ArrayList()
    private var editingItem: Item? = null
    private var toolMode = CurrentToolMode.INSERT

    private var selected: Item? = null

    private val amountValue: Double?
        get() {
            val amount: Double?
            try {
                amount = DateFormatUtils.getNumberFormat().parse(this.insert_amount.text.toString()).toDouble()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Amount parsing error : " + this.insert_amount.text)
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
     * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
     */

    override fun onResume() {
        this.initWimple()
        super.onResume()
    }

    private fun initWimple() {
        Log.e(LOG_TAG, "initWimple()")

        this.ti_update_notification.visibility = View.VISIBLE
        this.ti_list_notification_text.text = this.resources.getString(R.string.update_latest_items)

        this.wimple.setApplicationContext(context);
        this.wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(this.datePicker.selectedDate), false)
        this.wimple.latestItems
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        //synchronized(TransactionInsertFragment.class){
        if (padRIDs.isEmpty()) {
            val ar = this.context!!.resources.obtainTypedArray(R.array.number_buttons)
            for (cnt in 0 until ar.length()) padRIDs.add(ar.getResourceId(cnt, 0))
            ar.recycle()
        }
        //}

        return inflater.inflate(R.layout.fragment_transaction_insert_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // To show previous data during new data dispatching without any GUI display delay.
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
        val isNeedDisableMemo = sharedPref.getBoolean(SettingsFragment.KEY_DISABLE_MEMO, true)
        if (isNeedDisableMemo) {
            this.insert_memo_window.visibility = View.GONE
        }

        this.ti_update_notification.visibility = View.INVISIBLE

        this.setupDate()

        this.setupAccountLists()

        this.setupTitleAndSubmit()

        this.setupLatestItems()

        this.setupButtons()

        cal.setListener { amount -> this.insert_amount.setText(DateFormatUtils.getDecimalFormat().format(amount)) }

        //initWimple();
    }


    private fun setupTitleAndSubmit() {

        this.insert_amount.setOnEditorActionListener(TextView.OnEditorActionListener { textView, id, _ ->
            when (id) {
                EditorInfo.IME_ACTION_DONE -> {
                    this.setAmount(textView.text.toString())

                    val imm = this.activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(this.view!!.windowToken, 0)

                    return@OnEditorActionListener true
                }
            }
            false
        })


        this.btn_submit.setOnClickListener(OnClickListener {
            this.btn_submit.isEnabled = false

            // To handle typed amount by IME
            this.setAmount(this.insert_amount.text.toString())
            this.insert_amount.setText(cal.eq().toString())

            if (!this.validateForms()) {
                this.btn_submit.isEnabled = true
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
                        this.insert_entry_title.text.toString(), amount, this.insert_memo.text.toString())
                if (!res) {
                    this.btn_submit!!.isEnabled = true
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.modify_failed))
                } else {
                    this.ti_update_notification.visibility = View.VISIBLE
                    this.ti_list_notification_text.text = this.resources.getString(R.string.modify_exist_item)
                }

                this.editingItem = null

            } else {
                val res = this.wimple.makeEntry(this.datePicker.selectedDate,
                        this.leftAccountListAdapter.selected, this.rightAccountListAdapter.selected,
                        this.insert_entry_title.text.toString(), amount, this.insert_memo.text.toString())

                if (!res) {
                    this.btn_submit!!.isEnabled = true
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.insert_failed))
                } else {
                    this.ti_update_notification.visibility = View.VISIBLE
                    this.ti_list_notification_text.text = this.resources.getString(R.string.insert_new_item)
                }
            }
        })
        this.setSubmitButton(this.toolMode)

        this.insert_entry_title.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                var changed = s.toString().trim { it <= ' ' }
                if (changed.contains("(") && changed.indexOf("(") > 0) {
                    changed = changed.substring(0, changed.indexOf("(") - 1)
                    changed = changed.trim { it <= ' ' }
                }

                val foundItems:ArrayList<Item> = ArrayList()
                for(item in latestItems){
                    if(KoreanWordSearch.matchString(item.item, s.toString())){
                        foundItems.add(item)
                    }
                }

                if(foundItems.isEmpty()){
                    resetLatestItems(latestItems)
                }else{
                    resetLatestItems(foundItems)
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun setupLatestItems() {

        this.adapterLatestItems = ArrayAdapter(this.context!!, R.layout.list_frequent_entries, R.id.list_frequent_entry_name, latestItems)
        this.insert_frequent_items.adapter = this.adapterLatestItems
        this.insert_frequent_items.onItemClickListener = OnItemClickListener { _, _, position, _ -> this.selectLatestItem(position) }

        this.insert_title_clear.setOnClickListener { this.clearForms() }
    }

    private fun setupButtons() {
        val buttons = arrayOfNulls<TextView>(padRIDs.size)
        for (i in padRIDs.indices) {
            buttons[i] = this.view!!.findViewById<View>(padRIDs[i]) as TextView
            buttons[i]!!.setOnClickListener { v ->
                // remove virtual keyboard
                this.insert_entry_title.clearFocus()
                this.insert_memo.clearFocus()

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
        this.datePicker.setTextViewWidget(this.insert_date)
        this.datePicker.setOnDateSetListener(object : DatePickerFragment.OnDateSetListener {
            override fun onDateSet(date: Long?) {
                this@TransactionInsertFragment.setupItemDate(date)
            }
        })
        this.insert_date.setOnClickListener {
            this.datePicker.show(this.fragmentManager!!, "itemDate")
        }
        this.setupItemDate(Calendar.getInstance().timeInMillis)

        this.insert_yesterday.setOnClickListener {
            val newDate = this.datePicker.selectedDate - 24 * 60 * 60 * 1000
            this.setupItemDate(newDate)
        }

        this.insert_tomorrow.setOnClickListener {
            val newDate = this.datePicker.selectedDate + 24 * 60 * 60 * 1000
            this.setupItemDate(newDate)
        }
    }

    private fun setupAccountLists() {
        this.insert_category_left_title.background.alpha = 128
        this.leftAccountListAdapter = AccountExpandableListAdapter(this.context)
        this.insert_category_left.setAdapter(this.leftAccountListAdapter)

        this.insert_category_left.setOnChildClickListener { _, _, groupPosition, childPosition, id ->
            this.leftAccountListAdapter.setSelected(groupPosition, childPosition, id)
            this.insert_category_left_title.text = (this.leftAccountListAdapter.getChild(groupPosition, childPosition) as Account).title
            false
        }
//        this.insert_category_left.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
//            val selectedID = this.leftAccountListAdapter.selected.id
//            if (selectedID.isNotEmpty()) {
//                for (idx in 0 until this.leftAccountListAdapter.groupCount)
//                    this.insert_category_left.collapseGroup(idx)
//
//                if (!this.selectLeftCategory(selectedID)) {
//                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_acount_update_retry))
//                }
//            }
//        }

        this.insert_category_right_title.background.alpha = 128
        this.rightAccountListAdapter = AccountExpandableListAdapter(this.context)
        this.insert_category_right.setAdapter(this.rightAccountListAdapter)

        this.insert_category_right.setOnChildClickListener { _, _, groupPosition, childPosition, id ->
            this.rightAccountListAdapter.setSelected(groupPosition, childPosition, id)
            this.insert_category_right_title.text = (this.rightAccountListAdapter.getChild(groupPosition, childPosition) as Account).title
            false
        }
//        this.insert_category_right.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
//            val selectedID = this.rightAccountListAdapter.selected.id
//            if (selectedID.isNotEmpty()) {
//                for (idx in 0 until this.rightAccountListAdapter.groupCount)
//                    this.insert_category_right.collapseGroup(idx)
//
//                if (!this.selectRightCategory(selectedID)) {
//                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_acount_update_retry))
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

        var title = this.insert_entry_title.text.toString()
        var inlineMemo = ""

        val pos = title.indexOf("(")
        if (pos > 0) {
            inlineMemo = title.substring(pos)
            title = title.substring(0, pos)
        }

        if (0 != title.compareTo(this.selected!!.item)) {
            this.insert_entry_title.setText("${this.selected!!.item}$inlineMemo")
            this.insert_entry_title.setSelection(this.insert_entry_title.text.length)
        }
        this.setAmount(this.selected!!.amount)

        this.selectCategory(this.selected!!)
    }

    private fun setEntry(entry: Item) {
        selected = entry
        this.insert_entry_title.setText(entry.item)
        if (entry is Entry) {
            this.insert_memo.setText(entry.memo)
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
        this.insert_category_left.expandGroup(selectedLeftGroup)
        this.insert_category_left.setSelection(selectedLeftGroup)
        this.insert_category_left.setSelectedChild(selectedLeftGroup, this.leftAccountListAdapter.selectedChildPosition, true)
        this.insert_category_left_title.text = (this.leftAccountListAdapter.getChild(selectedLeftGroup, this.leftAccountListAdapter.selectedChildPosition) as Account).title
        return true
    }

    private fun selectRightCategory(rightAccountID: String): Boolean {
        val selectedRightGroup = this.rightAccountListAdapter.setSelected(rightAccountID)
        if (selectedRightGroup == -1) {
            Log.e(LOG_TAG, "Can't select right category!!!, $rightAccountID")
            return false
        }

        //insert_category_right.requestFocusFromTouch()
        this.insert_category_right.expandGroup(selectedRightGroup)
        this.insert_category_right.setSelection(selectedRightGroup)
        this.insert_category_right.setSelectedChild(selectedRightGroup, this.rightAccountListAdapter.selectedChildPosition, true)
        this.insert_category_right_title.text = (this.rightAccountListAdapter.getChild(selectedRightGroup, this.rightAccountListAdapter.selectedChildPosition) as Account).title
        return true
    }

    private fun selectCategory(entry: Item) {
        this.selectLeftCategory(entry.leftAccountID)
        this.selectRightCategory(entry.rightAccountID)
    }

    private fun validateForms(): Boolean {
        if (this.insert_entry_title.text.toString().isEmpty()) {
            Log.e(LOG_TAG, "Invalid entry title.")
            WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_invalid_title))
            return false
        }

        if (this.insert_amount!!.text.toString().isEmpty()) {
            Log.e(LOG_TAG, "Invalid entry amount.")
            WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_invalid_amount))
            return false
        }

        /*
		Double amount = getAmountValue();
		if(amount <= 0){
			Log.e(LOG_TAG, "Invalid entry amount.");
			Toast.makeText(context, context!!.getResources().getString(R.string.insert_invalid_amount),
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
        this.insert_entry_title.setText("")
        this.insert_memo.setText("")
        this.setAmount(0.0)
        this.datePicker.setDate(Calendar.getInstance().timeInMillis)
        this.selected = null

        this.insert_category_left_title.text = this.resources.getString(R.string.insert_left_accounts)
        this.insert_category_right_title.text = this.resources.getString(R.string.insert_right_accounts)
        this.leftAccountListAdapter.clearSelection()
        this.rightAccountListAdapter.clearSelection()

        if (CurrentToolMode.EDITING == this.toolMode) {
            this.editingItem = null
        }
        this.toolMode = CurrentToolMode.INSERT
        this.setSubmitButton(this.toolMode)
    }

    private fun resetLatestItems(items : ArrayList<Item>) {
        this.adapterLatestItems.clear()
        //this.adapterLatestItems.filter.filter("")
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

                this.ti_update_notification.visibility = View.INVISIBLE
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
                        this.insert_category_left.expandGroup(idx)

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
                        this.insert_category_right.expandGroup(idx)

                    if (this.selected != null) {
                        val selectedID = this.selected!!.rightAccountID
                        if (selectedID.isNotEmpty())
                            this.selectRightCategory(selectedID)
                    }
                }
            }

            CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED -> {
                // do nothing
            }

            CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED -> {
                this.ti_update_notification.visibility = View.INVISIBLE

                if (booleanStatus) {
                    this.latestItems = obj as ArrayList<Item>
                    resetLatestItems(this.latestItems)
                    //WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.entry_latest_item_added))
                }
            }

            CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED -> {
                val entryDate = obj as String

                this.ti_update_notification.visibility = View.INVISIBLE

                Log.e(LOG_TAG, "GET_MAKE_ENTRY_RESPONSE_RECEIVED entryDate=$entryDate")
                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.insert_success))
                    this.clearForms()
                    this.wimple.getLatestItems(true)
                    this.wimple.getMonthlyItems(true)
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.insert_failed))
                }

                this.btn_submit!!.isEnabled = true
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

                this.ti_update_notification.visibility = View.INVISIBLE

                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, this.resources.getString(R.string.modify_success))
                    this.clearForms()
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, this.resources.getString(R.string.modify_failed))
                }
                this.btn_submit!!.isEnabled = true
            }
        }
    }

    private fun setSubmitButton(mode: CurrentToolMode) {

        when (mode) {

            CurrentToolMode.INSERT -> {
                this.btn_submit!!.text = this.resources.getString(R.string.mode_entry_insert)
                this.btn_submit!!.setBackgroundResource(R.drawable.input_color_box_2)
            }

            CurrentToolMode.EDITING -> {
                this.btn_submit!!.text = this.resources.getString(R.string.mode_entry_modify)
                this.btn_submit!!.setBackgroundResource(R.drawable.input_color_box_6)
            }

            CurrentToolMode.MONTHLY_INSERT -> {
                this.btn_submit!!.text = this.resources.getString(R.string.mode_monthly_insert)
                this.btn_submit!!.setBackgroundResource(R.drawable.input_color_box_2)
            }
        }
        this.btn_submit!!.background.alpha = 192
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
