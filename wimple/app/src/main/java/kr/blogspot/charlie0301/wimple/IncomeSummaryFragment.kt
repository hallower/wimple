package kr.blogspot.charlie0301.wimple


import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.databinding.FragmentIncomeSummaryTabBinding
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.AccountState
import kr.blogspot.charlie0301.wimple.model.Budget
import kr.blogspot.charlie0301.wimple.widget.budgetstate.BudgetStateItemListAdapter
import java.util.*

class IncomeSummaryFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()
    // Add these two lines for View Binding
    private var _binding: FragmentIncomeSummaryTabBinding? = null
    private val binding get() = _binding!!

    // GUI
    private lateinit var asAdapter: BudgetStateItemListAdapter

    // Data
    private var firstUpdate: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _binding = FragmentIncomeSummaryTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        this.asAdapter = BudgetStateItemListAdapter(this.context)
        binding.savingListView.setAdapter(this.asAdapter)
        binding.savingListView.layoutParams = sessionParams

        this.registerForContextMenu(binding.savingListView)

        val c = Calendar.getInstance()
        c.time = Date()
        c.set(Calendar.DATE, 1)
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
        val isUsingBudgetInformation = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_ENABLE_BUDGET, true)

        this.firstUpdate = true
        this.wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
        if (isUsingBudgetInformation) {
            this.wimple.getBudget(true, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
            this.wimple.getBudget(false, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), false)
        }

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

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
                if (this.firstUpdate) {
                    this.firstUpdate = false
                    val autoRefresh = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH, true)
                    val isUsingBudgetInformation = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_ENABLE_BUDGET, true)

                    if (autoRefresh) {
                        val c = Calendar.getInstance()
                        c.time = Date()
                        c.set(Calendar.DATE, 1)
                        this.wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                        if (isUsingBudgetInformation) {
                            this.wimple.getBudget(true, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                            this.wimple.getBudget(false, DateFormatUtils.getServerDateString(c.timeInMillis), DateFormatUtils.getServerDateString(""), true)
                        }
                    }
                }

                if (!booleanStatus) {
                    return
                }

                val showGroup = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_SHOW_GROUP, false)

                val values = ArrayList<Double>()
                val names = ArrayList<String>()

                @Suppress("UNCHECKED_CAST") val accountStates = obj as Collection<AccountState>
                for (acs in accountStates) {
                    //Log.d(LOG_TAG, "[" + as.getAccountID() + "], " + as.getAccountName() +
                    //		" = " + as.getCategory() + ", " + as.getGroup());
                    if (!acs.category.startsWith("in")) {
                        continue
                    }

                    if (0.0 == acs.amount) {
                        continue
                    }

                    if (showGroup == acs.group && acs.amount != 0.0) {
                        values.add(acs.amount)
                        names.add(acs.accountName)
                    }
                    this.asAdapter.addAccountState(acs)
                }
                this.asAdapter.notifyDataSetChanged()

                if (0 < values.size) {
                    var maxValue = -99999999.0
                    val doubleValues = DoubleArray(values.size)
                    for (i in doubleValues.indices) {
                        doubleValues[i] = values[i]
                        if (maxValue < doubleValues[i])
                            maxValue = doubleValues[i]
                    }
                    val stringValues = arrayOfNulls<String>(names.size)
                    for (i in stringValues.indices) {
                        stringValues[i] = names[i]
                    }

                    val pcv = ChartUtils.makeChart(this.context, doubleValues, stringValues, maxValue)

                    binding.chart.removeAllViews()
                    binding.chart.addView(pcv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            }

            CommandID.GET_BUDGET_RESPONSE_RECEIVED -> {

                if (!booleanStatus) {
                    return
                }

                val isIncome = msg.arg2 == 1

                if (!isIncome) {
                    return
                }

                @Suppress("UNCHECKED_CAST") val map = obj as Map<String, Budget>
                this.asAdapter.setBudgets(map)
                this.asAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {

    }

}
