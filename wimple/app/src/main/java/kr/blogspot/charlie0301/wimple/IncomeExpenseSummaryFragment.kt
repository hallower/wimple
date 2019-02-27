package kr.blogspot.charlie0301.wimple


import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_income_expense_summary_tab.*
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.impl.util.ImageUtils
import kr.blogspot.charlie0301.wimple.model.AccountState
import kr.blogspot.charlie0301.wimple.model.Budget
import java.util.*

class IncomeExpenseSummaryFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()

    // Data
    private var firstUpdate: Boolean = false
    private var isUsingBudgetInformation: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_income_expense_summary_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.firstUpdate = true

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
        this.isUsingBudgetInformation = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_ENABLE_BUDGET, true)

        if (!this.isUsingBudgetInformation) {
            this.ine_budget_status_title.visibility = View.GONE
            this.ine_budget_status_income.visibility = View.GONE
            this.ine_budget_status_expense.visibility = View.GONE

        }

        val c = Calendar.getInstance()
        c.time = Date()
        c.set(Calendar.DATE, 1)

        this.wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
        if (this.isUsingBudgetInformation) {
            this.wimple.getBudget(true, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
            this.wimple.getBudget(false, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
        }


        view.findViewById<View>(R.id.ine_refresh).setOnClickListener {
            val _cal = Calendar.getInstance()
            _cal.time = Date()
            _cal.set(Calendar.DATE, 1)

            this.wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
            if (this.isUsingBudgetInformation) {
                this.wimple.getBudget(true, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                this.wimple.getBudget(false, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
            }

            this.ine_update_notification.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        val c = Calendar.getInstance()
        c.time = Date()
        c.set(Calendar.DATE, 1)

        this.wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
        if (this.isUsingBudgetInformation) {
            this.wimple.getBudget(true, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
            this.wimple.getBudget(false, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
        }

        super.onResume()
    }

    override fun handleMessage(msg: Message) {
        val command = msg.what
        val booleanStatus = msg.arg1 == 1
        val obj = msg.obj

        // if fragment is added or not to the activity
        if (!this.isAdded) {
            return
        }

        if (null == this.context) {
            return
        }

        when (command) {

            CommandID.GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED -> {

                this.ine_update_notification.visibility = View.GONE

                if (this.firstUpdate) {
                    this.firstUpdate = false
                    // To show previous data during new data dispatching without any GUI display delay.
                    val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
                    val autoRefresh = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH, true)
                    if (autoRefresh) {
                        val c = Calendar.getInstance()
                        c.time = Date()
                        c.set(Calendar.DATE, 1)
                        this.wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                        if (this.isUsingBudgetInformation) {
                            this.wimple.getBudget(true, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                            this.wimple.getBudget(false, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                        }

                        this.ine_update_notification.visibility = View.VISIBLE
                    }
                }

                if (!booleanStatus) {
                    return
                }

                var income: Double = 0.0
                var expense: Double = 0.0

                @Suppress("UNCHECKED_CAST") val accountStates = obj as Collection<AccountState>
                for (acs in accountStates) {

                    if (!acs.group) {
                        if (acs.category.startsWith("ex")) {
                            expense += acs.amount
                        }
                        if (acs.category.startsWith("in")) {
                            income += acs.amount
                        }
                    }
                }
                //asAdapter.get().notifyDataSetChanged();

                this.ine_income_value.text = DateFormatUtils.getNoPointDecimalFormat().format(income)
                this.ine_expense_value.text = DateFormatUtils.getNoPointDecimalFormat().format((-1 * expense))

                val sum = income - expense

                this.ine_sum_value.text = DateFormatUtils.getNoPointDecimalFormat().format(sum)
                if (sum >= 0) {
                    this.ine_sum_value.setTextColor(ContextCompat.getColor(context!!, R.color.text_blue))
                } else {
                    this.ine_sum_value.setTextColor(ContextCompat.getColor(context!!, R.color.text_red))
                }

                val wm = this.context!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = wm.defaultDisplay
                val size = Point()
                display.getSize(size)
                var width = size.x - ImageUtils.getDPSize(100, this.context!!)

                Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "width = $width, expense = $expense, income = $income")

                var params: FrameLayout.LayoutParams

                if (income > expense) {
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "expense / income = " + expense / income)

                    params = this.ine_bar_income.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    this.ine_bar_income.layoutParams = params

                    width = (width * (expense / income)).toInt()

                    params = this.ine_bar_expense.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    this.ine_bar_expense.layoutParams = params

                } else {
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "income / expense = " + income / expense)

                    params = this.ine_bar_expense.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    this.ine_bar_expense.layoutParams = params

                    width = (width * (income / expense)).toInt()

                    params = this.ine_bar_income.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    this.ine_bar_income.layoutParams = params
                }


            }

            CommandID.GET_BUDGET_RESPONSE_RECEIVED -> {

                if (!booleanStatus) {
                    return
                }

                if (!this.isUsingBudgetInformation) {
                    return
                }

                val isIncome = msg.arg2 == 1

                @Suppress("UNCHECKED_CAST") val map = obj as Map<String, Budget>
                val budgetStatus: Budget?
                val current: Double?
                val budget: Double?

                try {
                    budgetStatus = map[Budget.SUMMARYACCOUNTID]
                } catch (e: Exception) {
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "oops no budget summary!!!")
                    return
                }

                if (null == budgetStatus) {
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "oops no budget summary!!!")
                    return
                }

                current = budgetStatus.current
                budget = budgetStatus.budget

                Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "current=$current, budget=$budget")

                val wm = this.context!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = wm.defaultDisplay
                val size = Point()
                display.getSize(size)
                var width = size.x - ImageUtils.getDPSize(130, this.context!!)

                var params: FrameLayout.LayoutParams

                if (0.0 == budget) {

                    if (isIncome) {
                        params = this.ine_bar_budget_base_income.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_base_income.layoutParams = params

                        params = this.ine_bar_budget_current_income.layoutParams as FrameLayout.LayoutParams
                        params.width = 0
                        this.ine_bar_budget_current_income.layoutParams = params

                        this.ine_bar_budget_current_income_percentage.text = this.resources.getString(R.string.budget_not_yet)

                    } else {
                        params = this.ine_bar_budget_base_expense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_base_expense.layoutParams = params

                        params = this.ine_bar_budget_current_expense.layoutParams as FrameLayout.LayoutParams
                        params.width = 0
                        this.ine_bar_budget_current_expense.layoutParams = params

                        this.ine_bar_budget_current_expense_percentage.text = this.resources.getString(R.string.budget_not_yet)
                    }

                } else if (budget > current) {
                    if (isIncome) {
                        params = this.ine_bar_budget_base_income.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_base_income.layoutParams = params
                    } else {
                        params = this.ine_bar_budget_base_expense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_base_expense.layoutParams = params
                    }

                    val percentage = (current!! / budget as Double * 100).toInt()
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "current / budget = " + current / budget * 100)
                    width = (width * current / budget).toInt()

                    if (isIncome) {
                        params = this.ine_bar_budget_current_income.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_current_income.layoutParams = params
                        this.ine_bar_budget_current_income_percentage.text = "$percentage%"
                    } else {
                        params = this.ine_bar_budget_current_expense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_current_expense.layoutParams = params
                        this.ine_bar_budget_current_expense_percentage.text = "$percentage%"
                    }
                } else {

                    if (isIncome) {
                        params = this.ine_bar_budget_current_income.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_current_income.layoutParams = params
                    } else {
                        params = this.ine_bar_budget_current_expense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_current_expense.layoutParams = params
                    }

                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "current / budget = " + current!! / budget as Double)
                    val percentage = (current / budget * 100).toInt()
                    //width = (int)(width * ((double)budget/ (double)current));

                    if (isIncome) {
                        params = this.ine_bar_budget_base_income.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_base_income.layoutParams = params
                        this.ine_bar_budget_current_income_percentage.text = "$percentage%"
                    } else {
                        params = this.ine_bar_budget_base_expense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        this.ine_bar_budget_base_expense.layoutParams = params
                        this.ine_bar_budget_current_expense_percentage.text = "$percentage%"
                    }
                }
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {}

    companion object {

        private const val LOG_TAG = "IncomeExpenseSumFrag"
    }

}
