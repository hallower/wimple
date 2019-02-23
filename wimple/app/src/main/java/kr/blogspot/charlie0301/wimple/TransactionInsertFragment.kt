package kr.blogspot.charlie0301.wimple


import android.content.Context
import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
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
import kr.blogspot.charlie0301.wimple.model.Account
import kr.blogspot.charlie0301.wimple.model.Entry
import kr.blogspot.charlie0301.wimple.model.Item
import kr.blogspot.charlie0301.wimple.widget.AccountExpandableListAdapter
import kr.blogspot.charlie0301.wimple.widget.DatePickerFragment
import java.util.*


class TransactionInsertFragment : Fragment(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()

    // Widget
    private lateinit var leftAccountListAdapter: AccountExpandableListAdapter
    private lateinit var rightAccountListAdapter: AccountExpandableListAdapter

    private var datePicker: DatePickerFragment = DatePickerFragment()

    private lateinit var adapterLatestItems: ArrayAdapter<Item>
    private var editingItem: Item? = null
    private var toolMode = CurrentToolMode.INSERT

    private val amountValue: Double?
        get() {
            val amount: Double?
            try {
                amount = DateFormatUtils.getNumberFormat().parse(insert_amount.text.toString()).toDouble()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Amount parsing error : " + insert_amount.text)
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
        initWimple()

        super.onResume()
    }

    private fun initWimple() {
        Log.e(LOG_TAG, "initWimple()")

        ti_update_notification.visibility = View.VISIBLE
        ti_list_notification_text.text = resources.getString(R.string.update_latest_items)

        wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(datePicker.selectedDate), false)
        wimple.latestItems
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        //synchronized(TransactionInsertFragment.class){
        if (padRIDs.isEmpty()) {
            val ar = context!!.resources.obtainTypedArray(R.array.number_buttons)
            for (cnt in 0..(ar.length() - 1)) padRIDs.add(ar.getResourceId(cnt, 0))
            ar.recycle()
        }
        //}

        var view = inflater.inflate(R.layout.fragment_transaction_insert_tab, container, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // To show previous data during new data dispatching without any GUI display delay.
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val isNeedDisableMemo = sharedPref.getBoolean(SettingsFragment.KEY_DISABLE_MEMO, false)
        if (isNeedDisableMemo) {
            insert_memo_window.visibility = View.GONE
        }

        ti_update_notification.visibility = View.INVISIBLE

        setupDate()

        setupAccountLists()

        setupTitlenSubmit()

        setupLatestItems()

        setupButtons()

        cal.setListener { amount -> insert_amount.setText(DateFormatUtils.getDecimalFormat().format(amount)) }

        //initWimple();
    }


    private fun setupTitlenSubmit() {

        insert_amount.setOnEditorActionListener(TextView.OnEditorActionListener { textView, id, keyEvent ->
            when (id) {
                EditorInfo.IME_ACTION_DONE -> {
                    setAmount(textView.text.toString())

                    val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view!!.windowToken, 0)

                    return@OnEditorActionListener true
                }
            }
            false
        })


        btn_submit.setOnClickListener(OnClickListener {
            btn_submit.isEnabled = false

            // To handle typed amount by IME
            setAmount(insert_amount.text.toString())
            insert_amount.setText(cal.eq().toString())

            if (!validateForms()) {
                btn_submit.isEnabled = true
                return@OnClickListener
            }

            val amount = amountValue
            /*
				if(amount < 0){
					btn_submit.setEnabled(true);
					Log.e(LOG_TAG, "Amount parsing error : " + insert_amount.getText());
					return;
				}*/

            if (toolMode == CurrentToolMode.EDITING) {
                toolMode = CurrentToolMode.INSERT

                /*
					 * server doesn't receive yyyyMMdd.xxxx format
					String date = editingItem.getDateValue();
					if(datePicker.isDateChanged()){
						date = DateFormatUtils.getServerDateString(datePicker.getSelectedDate());
					}
					 */

                val res = wimple.modifyEntry(editingItem!!.id, DateFormatUtils.getServerDateString(datePicker.selectedDate),
                        leftAccountListAdapter.selected, rightAccountListAdapter.selected,
                        insert_entry_title.text.toString(), amount, insert_memo.text.toString())
                if (!res) {
                    btn_submit!!.isEnabled = true
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.modify_failed))
                } else {
                    ti_update_notification.visibility = View.VISIBLE
                    ti_list_notification_text.text = resources.getString(R.string.modify_exist_item)
                }

                editingItem = null

            } else {
                val res = wimple.makeEntry(datePicker.selectedDate,
                        leftAccountListAdapter.selected, rightAccountListAdapter.selected,
                        insert_entry_title.text.toString(), amount, insert_memo.text.toString())

                if (!res) {
                    btn_submit!!.isEnabled = true
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.insert_failed))
                } else {
                    ti_update_notification.visibility = View.VISIBLE
                    ti_list_notification_text.text = resources.getString(R.string.insert_new_item)
                }
            }
        })
        setSubmitButton(toolMode)

        insert_entry_title.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                var changed = s.toString().trim { it <= ' ' }
                if (changed.contains("(") && changed.indexOf("(") > 0) {
                    changed = changed.substring(0, changed.indexOf("(") - 1)
                    changed = changed.trim { it <= ' ' }
                }
                adapterLatestItems.filter.filter(changed)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun setupLatestItems() {
        val latestItems = ArrayList<Item>()
        adapterLatestItems = ArrayAdapter(context!!, R.layout.list_frequent_entries, R.id.list_frequent_entry_name, latestItems)
        insert_frequent_items.adapter = adapterLatestItems
        insert_frequent_items.onItemClickListener = OnItemClickListener { parent, view, position, id -> selectLatestItem(position) }

        insert_title_clear.setOnClickListener { clearForms() }
    }

    private fun setupButtons() {
        val buttons = arrayOfNulls<TextView>(padRIDs.size)
        for (i in padRIDs.indices) {
            buttons[i] = view!!.findViewById<View>(padRIDs[i]) as TextView
            buttons[i]!!.setOnClickListener(OnClickListener { v ->
                // remove virtual keyboard
                insert_entry_title.clearFocus()
                insert_memo.clearFocus()

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
            })
        }
    }

    private fun setupDate() {
        datePicker.setTextViewWidget(insert_date)
        datePicker.setOnDateSetListener { date -> setupItemDate(date) }
        insert_date.setOnClickListener {
            @Suppress("DEPRECATION")
            datePicker.show((activity as AppCompatActivity).fragmentManager, "itemDate")
        }
        setupItemDate(Calendar.getInstance().timeInMillis)

        insert_yesterday.setOnClickListener {
            val newDate = datePicker.selectedDate!! - 24 * 60 * 60 * 1000
            setupItemDate(newDate)
        }

        insert_tomorrow.setOnClickListener {
            val newDate = datePicker.selectedDate!! + 24 * 60 * 60 * 1000
            setupItemDate(newDate)
        }
    }

    private fun setupAccountLists() {
        insert_category_left_title.background.alpha = 128
        leftAccountListAdapter = AccountExpandableListAdapter(context)
        insert_category_left.setAdapter(leftAccountListAdapter)

        insert_category_left.setOnChildClickListener { parent, v, groupPosition, childPosition, id ->
            leftAccountListAdapter.setSelected(groupPosition, childPosition, id)
            insert_category_left_title.text = (leftAccountListAdapter.getChild(groupPosition, childPosition) as Account).title
            false
        }
        insert_category_left.addOnLayoutChangeListener { view: View, i: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int ->
            for (idx in 0 until leftAccountListAdapter.groupCount)
                insert_category_left.expandGroup(idx)

            val selectedID = leftAccountListAdapter.selected.id
            if (!selectedID.isEmpty()) {
                if (!selectLeftCategory(selectedID)) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_acount_update_retry))
                }
            }
        }

        insert_category_right_title.background.alpha = 128
        rightAccountListAdapter = AccountExpandableListAdapter(context)
        insert_category_right.setAdapter(rightAccountListAdapter)

        insert_category_right.setOnChildClickListener { parent, v, groupPosition, childPosition, id ->
            rightAccountListAdapter.setSelected(groupPosition, childPosition, id)
            insert_category_right_title.text = (rightAccountListAdapter.getChild(groupPosition, childPosition) as Account).title
            false
        }
        insert_category_right.addOnLayoutChangeListener { view: View, i: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int ->
            for (idx in 0 until rightAccountListAdapter.groupCount)
                insert_category_right.expandGroup(idx)

            val selectedID = rightAccountListAdapter.selected.id
            if (!selectedID.isEmpty()) {
                if (!selectRightCategory(selectedID)) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_acount_update_retry))
                }
            }
        }
    }

    private fun setupItemDate(date: Long?) {
        datePicker.setDate(date)
        wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(datePicker.selectedDate), false)
    }

    private fun setAmount(amount: String) {
        if (amount.isEmpty()) {
            cal.setValue(0.0);
        } else {
            var amountValue = java.lang.Double.parseDouble(amount.replace(",", ""))
            cal.setValue(amountValue)
        }
    }

    private fun setAmount(amount: Double?) {
        cal.setValue(amount)
    }

    private fun selectLatestItem(position: Int) {
        val selected: Item?

        try {
            selected = adapterLatestItems.getItem(position)
            if (selected == null)
                return
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(LOG_TAG, "Failed to select latest Item!!!, position=$position")
            return
        }

        var title = insert_entry_title.text.toString()
        var inlineMemo = ""

        val pos = title.indexOf("(")
        if (pos > 0) {
            inlineMemo = title.substring(pos)
            title = title.substring(0, pos)
        }

        if (0 != title.compareTo(selected.item)) {
            insert_entry_title.setText(selected.item + inlineMemo)
            insert_entry_title.setSelection(insert_entry_title.text.length)
        }
        setAmount(selected.amount)

        selectCategory(selected)
    }

    private fun setEntry(entry: Item) {
        insert_entry_title.setText(entry.item)
        if (entry is Entry) {
            insert_memo.setText(entry.memo)
        }
        setAmount(entry.amount)

        if (toolMode == CurrentToolMode.EDITING) {
            datePicker.setDate(entry.date)
        } else {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            datePicker.setDate(today.timeInMillis)
        }

        selectCategory(entry)
    }

    private fun selectLeftCategory(leftAccountID: String): Boolean {
        val selectedLeftGroup = leftAccountListAdapter.setSelected(leftAccountID)
        if (selectedLeftGroup == -1) {
            Log.e(LOG_TAG, "Can't select left category!!!, $leftAccountID")
            return false
        }

        //insert_category_right.requestFocusFromTouch()
        insert_category_left.setSelection(selectedLeftGroup)
        insert_category_left.setSelectedChild(selectedLeftGroup, leftAccountListAdapter.selectedChildPosition, true)
        insert_category_left_title.text = (leftAccountListAdapter.getChild(selectedLeftGroup, leftAccountListAdapter.selectedChildPosition) as Account).title
        return true
    }

    private fun selectRightCategory(rightAccountID: String): Boolean {
        val selectedRightGroup = rightAccountListAdapter.setSelected(rightAccountID)
        if (selectedRightGroup == -1) {
            Log.e(LOG_TAG, "Can't select right category!!!, $rightAccountID")
            return false
        }

        //insert_category_right.requestFocusFromTouch()
        insert_category_right.setSelection(selectedRightGroup)
        insert_category_right.setSelectedChild(selectedRightGroup, rightAccountListAdapter.selectedChildPosition, true)
        insert_category_right_title.text = (rightAccountListAdapter.getChild(selectedRightGroup, rightAccountListAdapter.selectedChildPosition) as Account).title
        return true
    }

    private fun selectCategory(entry: Item) {
        selectLeftCategory(entry.leftAccountID)
        selectRightCategory(entry.rightAccountID)
    }

    private fun validateForms(): Boolean {
        if (insert_entry_title.text.toString().isEmpty()) {
            Log.e(LOG_TAG, "Invalid entry title.")
            WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_invalid_title))
            return false
        }

        if (insert_amount!!.text.toString().isEmpty()) {
            Log.e(LOG_TAG, "Invalid entry amount.")
            WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_invalid_amount))
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
            WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_invalid_left_accounts))
            return false
        }

        if (!this.rightAccountListAdapter.isSelected) {
            Log.e(LOG_TAG, "right side account is not selected!!!")
            WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_invalid_right_accounts))
            return false
        }
        return true
    }

    private fun clearForms() {
        insert_entry_title.setText("")
        insert_memo.setText("")
        setAmount(0.0)
        datePicker.setDate(Calendar.getInstance().timeInMillis)

        insert_category_left_title.text = resources.getString(R.string.insert_left_accounts)
        insert_category_right_title.text = resources.getString(R.string.insert_right_accounts)
        leftAccountListAdapter.clearSelection()
        rightAccountListAdapter.clearSelection()

        if (CurrentToolMode.EDITING == toolMode) {
            editingItem = null
        }
        toolMode = CurrentToolMode.INSERT
        setSubmitButton(toolMode)
    }

    override fun handleMessage(msg: Message) {

        val command = msg.what
        val booleanStatus = msg.arg1 == 1
        val obj = msg.obj

        // if fragment is added or not to the activity
        if (!isAdded) {
            return
        }

        when (command) {

            CommandID.WIMPLE_LOGGIN_SUCCESS ->
                //case CommandID.GET_ALL_SECTION_RECEIVED :
            {
                initWimple()
            }

            CommandID.GET_ALL_ACCOUNT_RECEIVED -> {

                ti_update_notification.visibility = View.INVISIBLE
                if (!booleanStatus) {
                    return
                }

                val accountList = arrayListOf<Account>()
                if (obj is Collection<*>) {
                    for (given_item in obj) {
                        if (given_item is Account) {
                            accountList.add(given_item)
                        }
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
                        else -> Log.e(LOG_TAG, "Invalid accout item !!!!")
                    }
                }

                run {
                    val lHeader = ArrayList<String>()
                    lHeader.add(resources.getString(R.string.entry_header_asset_p))
                    lHeader.add(resources.getString(R.string.entry_header_debt_m))
                    lHeader.add(resources.getString(R.string.entry_header_capital_m))
                    lHeader.add(resources.getString(R.string.entry_header_expenses))

                    val lChild = HashMap<String, List<Account>>()
                    lChild[lHeader[0]] = assets
                    lChild[lHeader[1]] = liabilities
                    lChild[lHeader[2]] = capital
                    lChild[lHeader[3]] = expenses

                    leftAccountListAdapter.clear()
                    leftAccountListAdapter.setData(lHeader, lChild)
                    leftAccountListAdapter.notifyDataSetChanged()


                    for (idx in 0 until leftAccountListAdapter.groupCount)
                        insert_category_left.expandGroup(idx)

                    val selectedID = leftAccountListAdapter.selected.id
                    if (!selectedID.isEmpty())
                        selectLeftCategory(selectedID)
                }

                run {
                    val rHeader = ArrayList<String>()
                    rHeader.add(resources.getString(R.string.entry_header_asset_m))
                    rHeader.add(resources.getString(R.string.entry_header_debt_p))
                    rHeader.add(resources.getString(R.string.entry_header_capital_p))
                    rHeader.add(resources.getString(R.string.entry_header_income))

                    val rChild = HashMap<String, List<Account>>()
                    rChild[rHeader[0]] = assets
                    rChild[rHeader[1]] = liabilities
                    rChild[rHeader[2]] = capital
                    rChild[rHeader[3]] = income

                    rightAccountListAdapter.clear()
                    rightAccountListAdapter.setData(rHeader, rChild)
                    rightAccountListAdapter.notifyDataSetChanged()

                    for (idx in 0 until rightAccountListAdapter.groupCount)
                        insert_category_right.expandGroup(idx)

                    val selectedID = rightAccountListAdapter.selected.id
                    if (!selectedID.isEmpty())
                        selectRightCategory(selectedID)
                }
            }

            CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED -> {
                // do nothing
            }

            CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED -> {
                ti_update_notification.visibility = View.INVISIBLE

                if (booleanStatus) {
                    adapterLatestItems.clear()
                    adapterLatestItems.filter.filter("")
                    @Suppress("UNCHECKED_CAST")
                    adapterLatestItems.addAll(obj as List<Item>)
                    adapterLatestItems.notifyDataSetChanged()
                    //WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.entry_lastest_item_added))
                }
            }

            CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED -> {
                val entryDate = obj as String

                ti_update_notification.visibility = View.INVISIBLE

                Log.e(LOG_TAG, "GET_MAKE_ENTRY_RESPONSE_RECEIVED entryDate=$entryDate")
                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.insert_success))
                    clearForms()
                    wimple.getLatestItems(true)
                    wimple.getMonthlyItems(true)
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.insert_failed))
                }

                btn_submit!!.isEnabled = true
            }

            CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM -> {

                if (null == obj || obj !is Item)
                    return

                // Modifying

                toolMode = if (obj.id.isEmpty()) {
                    CurrentToolMode.MONTHLY_INSERT
                } else {
                    CurrentToolMode.EDITING
                }
                setSubmitButton(toolMode)

                editingItem = obj
                setEntry(obj)

                if (CurrentToolMode.EDITING == toolMode) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.entry_modify_notice))
                } else {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.month_item_modify_notice))
                }
            }

            CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED -> {

                ti_update_notification.visibility = View.INVISIBLE

                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_SHORT, resources.getString(R.string.modify_success))
                    clearForms()
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.modify_failed))
                }
                btn_submit!!.isEnabled = true
            }
        }
    }

    private fun setSubmitButton(mode: CurrentToolMode) {

        when (mode) {

            TransactionInsertFragment.CurrentToolMode.INSERT -> {
                btn_submit!!.text = resources.getString(R.string.mode_entry_insert)
                btn_submit!!.setBackgroundResource(R.drawable.input_color_box_2)
            }

            TransactionInsertFragment.CurrentToolMode.EDITING -> {
                btn_submit!!.text = resources.getString(R.string.mode_entry_modify)
                btn_submit!!.setBackgroundResource(R.drawable.input_color_box_6)
            }

            TransactionInsertFragment.CurrentToolMode.MONTHLY_INSERT -> {
                btn_submit!!.text = resources.getString(R.string.mode_monthly_insert)
                btn_submit!!.setBackgroundResource(R.drawable.input_color_box_2)
            }
        }
        btn_submit!!.background.alpha = 192
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
