package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import android.os.Message
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.core.content.ContextCompat
import kr.blogspot.charlie0301.wimple.databinding.FragmentFinalcialStateSummaryTabBinding
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.AccountState

class FinancialStateSummaryFragment : androidx.fragment.app.Fragment(), IWimpleFragment {

    private val wimple = WimpleImpl.getInstance()
    private var _binding: FragmentFinalcialStateSummaryTabBinding? = null
    private val binding get() = _binding!!

    // Data
    private var firstUpdate: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _binding = FragmentFinalcialStateSummaryTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.firstUpdate = true
        this.wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false)

        binding.asRefresh.setOnClickListener {
            this.wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true)
            binding.asUpdateNotification.visibility = View.VISIBLE
        }
    }

    private fun setShowingNotification(show: Boolean) {
        _binding?.let {
            it.asUpdateNotification.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        this.wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false)
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
        if (!this.isAdded || _binding == null) {
            return
        }

        if (null == this.context) {
            return
        }

        when (command) {

            CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED -> {

                setShowingNotification(false)

                if (this.firstUpdate) {
                    this.firstUpdate = false
                    this.wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true)
                    setShowingNotification(true)
                }

                if (!booleanStatus) {
                    return
                }

                var saving = 0.0
                var debt = 0.0

                @Suppress("UNCHECKED_CAST") val accountStates = obj as Collection<AccountState>
                for (acs in accountStates) {

                    if (!acs.group) {
                        if (acs.category.startsWith("li")) {
                            debt += acs.amount
                        }
                        if (acs.category.startsWith("as")) {
                            saving += acs.amount
                        }
                    }/*else{
					//asAdapter.get().addAccountState(as);
				}*/
                }
                //asAdapter.get().notifyDataSetChanged();

                binding.asSavingValue.text = DateFormatUtils.getNoPointDecimalFormat().format(saving)
                binding.asDebtValue.text = DateFormatUtils.getNoPointDecimalFormat().format(-1 * debt)

                val sum = saving - debt

                binding.asSumValue.text = DateFormatUtils.getNoPointDecimalFormat().format(sum)
                if (sum >= 0) {
                    binding.asSumValue.setTextColor(ContextCompat.getColor(this.requireContext(), R.color.text_blue))
                } else {
                    binding.asSumValue.setTextColor(ContextCompat.getColor(this.requireContext(), R.color.text_red))
                }

                val pcv = ChartUtils.makeChart(this.context,
                        doubleArrayOf(Math.abs(saving), Math.abs(debt)),
                        arrayOf(this.resources.getString(R.string.title_saving), this.resources.getString(R.string.title_debt)),
                        if (Math.abs(saving) > Math.abs(debt)) Math.abs(saving) else Math.abs(debt))

                binding.chart.removeAllViews()
                binding.chart.addView(pcv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {}
}