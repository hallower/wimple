package kr.blogspot.charlie0301.wimple


import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.fragment_saving_state_summary_tab.*
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.AccountState
import kr.blogspot.charlie0301.wimple.widget.accountstate.AccountStateItemListAdapter
import java.util.*

class SavingStateSummaryFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    //private final static String LOG_TAG = "TransactionInsertFragment";

    private val wimple = WimpleImpl.getInstance()

    // GUI
    private lateinit var asAdapter: AccountStateItemListAdapter

    // Data
    private var firstUpdate: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_saving_state_summary_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        this.asAdapter = AccountStateItemListAdapter(this.context)
        this.saving_list_view.setAdapter(this.asAdapter)
        this.saving_list_view.setLayoutParams(sessionParams)

        this.registerForContextMenu(this.saving_list_view)

        this.firstUpdate = true
        WimpleImpl.getInstance().getFinancialState(DateFormatUtils.getServerDateString(""), false)
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

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)

                if (this.firstUpdate) {
                    this.firstUpdate = false
                    val autoRefresh = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_AUTO_REFRESH, true)
                    if (autoRefresh) {
                        this.wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true)
                    }
                }

                if (!booleanStatus) {
                    return
                }

                if (null == this.context) {
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

                    chart.removeAllViews()
                    chart.addView(pcv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {}

}
