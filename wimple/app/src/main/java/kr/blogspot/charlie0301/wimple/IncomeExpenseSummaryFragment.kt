package kr.blogspot.charlie0301.wimple


import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.databinding.FragmentIncomeExpenseSummaryTabBinding // <-- Add this import
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.impl.util.ImageUtils
import kr.blogspot.charlie0301.wimple.model.AccountState
import kr.blogspot.charlie0301.wimple.model.Budget
import java.util.*

class IncomeExpenseSummaryFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()

    private var _binding: FragmentIncomeExpenseSummaryTabBinding? = null
    private val binding get() = _binding!!

    // Data
    private var firstUpdate: Boolean = false
    private var isUsingBudgetInformation: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _binding = FragmentIncomeExpenseSummaryTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.firstUpdate = true

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
        this.isUsingBudgetInformation = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_ENABLE_BUDGET, true)

        if (!this.isUsingBudgetInformation) {
            binding.ineBudgetStatusTitle.visibility = View.GONE
            binding.ineBudgetStatusIncome.visibility = View.GONE
            binding.ineBudgetStatusExpense.visibility = View.GONE

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

            binding.ineUpdateNotification.visibility = View.VISIBLE
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

                binding.ineUpdateNotification.visibility = View.GONE

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

                        binding.ineUpdateNotification.visibility = View.VISIBLE
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

                binding.ineIncomeValue.text = DateFormatUtils.getNoPointDecimalFormat().format(income)
                binding.ineExpenseValue.text = DateFormatUtils.getNoPointDecimalFormat().format((-1 * expense))

                val sum = income - expense

                binding.ineSumValue.text = DateFormatUtils.getNoPointDecimalFormat().format(sum)
                if (sum >= 0) {
                    binding.ineSumValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_blue))
                } else {
                    binding.ineSumValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_red))
                }

                val wm = this.requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = wm.defaultDisplay
                val size = Point()
                display.getSize(size)
                var width = size.x - ImageUtils.getDPSize(100, this.requireContext())

                Log.d(LOG_TAG, "width = $width, expense = $expense, income = $income")

                var params: FrameLayout.LayoutParams

                if (income > expense) {
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "expense / income = " + expense / income)

                    params = binding.ineBarIncome.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    binding.ineBarIncome.layoutParams = params

                    width = (width * (expense / income)).toInt()

                    params = binding.ineBarExpense.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    binding.ineBarExpense.layoutParams = params

                } else {
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "income / expense = " + income / expense)

                    params = binding.ineBarExpense.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    binding.ineBarExpense.layoutParams = params

                    width = (width * (income / expense)).toInt()

                    params = binding.ineBarIncome.layoutParams as FrameLayout.LayoutParams
                    params.width = width
                    binding.ineBarIncome.layoutParams = params
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

                val wm = this.requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = wm.defaultDisplay
                val size = Point()
                display.getSize(size)
                var width = size.x - ImageUtils.getDPSize(130, this.requireContext())

                var params: FrameLayout.LayoutParams

                if (0.0 == budget) {

                    if (isIncome) {
                        params = binding.ineBarBudgetBaseIncome.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetBaseIncome.layoutParams = params

                        params = binding.ineBarBudgetCurrentIncome.layoutParams as FrameLayout.LayoutParams
                        params.width = 0
                        binding.ineBarBudgetCurrentIncome.layoutParams = params

                        binding.ineBarBudgetCurrentIncomePercentage.text = this.resources.getString(R.string.budget_not_yet)

                    } else {
                        params = binding.ineBarBudgetBaseExpense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetBaseExpense.layoutParams = params

                        params = binding.ineBarBudgetCurrentExpense.layoutParams as FrameLayout.LayoutParams
                        params.width = 0
                        binding.ineBarBudgetCurrentExpense.layoutParams = params

                        binding.ineBarBudgetCurrentExpensePercentage.text = this.resources.getString(R.string.budget_not_yet)
                    }

                } else if (budget > current) {
                    if (isIncome) {
                        params = binding.ineBarBudgetBaseIncome.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetBaseIncome.layoutParams = params
                    } else {
                        params = binding.ineBarBudgetBaseExpense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetBaseExpense.layoutParams = params
                    }

                    val percentage = (current!! / budget as Double * 100).toInt()
                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "current / budget = " + current / budget * 100)
                    width = (width * current / budget).toInt()

                    if (isIncome) {
                        params = binding.ineBarBudgetCurrentIncome.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetCurrentIncome.layoutParams = params
                        binding.ineBarBudgetCurrentIncomePercentage.text = "$percentage%"
                    } else {
                        params = binding.ineBarBudgetCurrentExpense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetCurrentExpense.layoutParams = params
                        binding.ineBarBudgetCurrentExpensePercentage.text = "$percentage%"
                    }
                } else {

                    if (isIncome) {
                        params = binding.ineBarBudgetCurrentIncome.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetCurrentIncome.layoutParams = params
                    } else {
                        params = binding.ineBarBudgetCurrentExpense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetCurrentExpense.layoutParams = params
                    }

                    Log.d(kr.blogspot.charlie0301.wimple.IncomeExpenseSummaryFragment.Companion.LOG_TAG, "current / budget = " + current!! / budget as Double)
                    val percentage = (current / budget * 100).toInt()
                    //width = (int)(width * ((double)budget/ (double)current));

                    if (isIncome) {
                        params = binding.ineBarBudgetBaseIncome.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetBaseIncome.layoutParams = params
                        binding.ineBarBudgetCurrentIncomePercentage.text = "$percentage%"
                    } else {
                        params = binding.ineBarBudgetBaseExpense.layoutParams as FrameLayout.LayoutParams
                        params.width = width
                        binding.ineBarBudgetBaseExpense.layoutParams = params
                        binding.ineBarBudgetCurrentExpensePercentage.text = "$percentage%"
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
