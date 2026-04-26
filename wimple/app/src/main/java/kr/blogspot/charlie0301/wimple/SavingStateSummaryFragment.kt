package kr.blogspot.charlie0301.wimple


import android.os.Bundle
import android.os.Message
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import kr.blogspot.charlie0301.wimple.databinding.FragmentSavingStateSummaryTabBinding
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.AccountState
import kr.blogspot.charlie0301.wimple.widget.accountstate.AccountStateItemListAdapter
import java.util.*

class SavingStateSummaryFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    //private final static String LOG_TAG = "TransactionInsertFragment";

    private val wimple = WimpleImpl.getInstance()
    private var _binding: FragmentSavingStateSummaryTabBinding? = null // ADD THIS
    private val binding get() = _binding!! // ADD THIS

    // GUI
    private lateinit var asAdapter: AccountStateItemListAdapter

    // Data
    private var firstUpdate: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _binding = FragmentSavingStateSummaryTabBinding.inflate(inflater, container, false) // MODIFY THIS
        return binding.root // MODIFY THIS
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        this.asAdapter = AccountStateItemListAdapter(this.context)
        binding.savingListView.setAdapter(this.asAdapter)
        binding.savingListView.setLayoutParams(sessionParams)

        this.registerForContextMenu(binding.savingListView)

        this.firstUpdate = true
        WimpleImpl.getInstance().getFinancialState(DateFormatUtils.getServerDateString(""), false)
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

        when (command) {

            CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED -> {

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.requireContext())

                if (this.firstUpdate) {
                    this.firstUpdate = false
                    this.wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true)
                }

                if (!booleanStatus) {
                    return
                }

                if (null == this.context || _binding == null) {
                    return
                }

                val showGroup = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_SHOW_GROUP, false)

                val values = ArrayList<Double>()
                val names = ArrayList<String>()

                @Suppress("UNCHECKED_CAST") val accountStates = obj as Collection<AccountState>
                for (acs in accountStates) {
                    //Log.d(LOG_TAG, "[" + as.getAccountID() + "], " + as.getAccountName() +
                    //		" = " + as.getCategory() + ", " + as.getGroup());
                    if (!acs.category.startsWith("as")) {
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
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {}

}
