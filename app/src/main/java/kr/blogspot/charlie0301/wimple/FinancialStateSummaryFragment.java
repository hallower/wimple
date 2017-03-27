package kr.blogspot.charlie0301.wimple;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.PieChart;

import java.util.Collection;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.AccountState;

public class FinancialStateSummaryFragment  extends Fragment implements IWimpleFragment{

	//private final static String LOG_TAG = "TransactionInsertFragment";

	private final WimpleImpl wimple = WimpleImpl.getInstance();
	private View view = null;
	private Context context = null;

	// GUI
	private LinearLayout llChart;
	
	private LinearLayout llUpdateNotice;
	private TextView tvSavingValue;
	private TextView tvDebtValue;
	private TextView tvSumValue;

	// Data
	private boolean firstUpdate;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context.get();

		view = inflater.inflate(R.layout.fragment_finalcial_state_summary_tab, container, false);
		
		tvSumValue = (TextView)view.findViewById(R.id.as_sum_value);
		tvSavingValue = (TextView)view.findViewById(R.id.as_saving_value);
		tvDebtValue = (TextView)view.findViewById(R.id.as_debt_value);

		llUpdateNotice = (LinearLayout)view.findViewById(R.id.as_update_notification);
		
		firstUpdate = true;
		wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false);
		
		view.findViewById(R.id.as_refresh).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true);
				llUpdateNotice.setVisibility(View.VISIBLE);
			}
		});
			
		return view;
	}
	@Override
	public void onDestroy() {

		/*
		asList.clear();
		asList = null;
		asAdapter.clear();
		asAdapter = null;
		 */

		llChart = null;
		tvSavingValue = null;
		tvDebtValue = null;
		tvSumValue = null;

		super.onDestroy();
	}
	@Override
	public void onDetach() {

		super.onDetach();
	}
	@Override
	public void onResume() {
		context = WimpleActivity.context.get();
		wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false);
		super.onResume();
	}
	@SuppressWarnings({"unchecked", "deprecation"})
	@Override
	public void handleMessage(Message msg) {
		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		// if fragment is added or not to the activity
		if(!isAdded()){
			return;
		}
		
		if(null == context){
			context = WimpleActivity.context.get();
			if(null == context){
				return;
			}
		}

		switch(command){

		case CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED :{

			llUpdateNotice.setVisibility(View.GONE);
			
			if(firstUpdate){
				firstUpdate = false;
				// To show previous data during new data dispatching without any GUI display delay.
				SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
				boolean autoRefresh = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_AUTO_REFRESH, true);
				if(autoRefresh){
					wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true);	
					llUpdateNotice.setVisibility(View.VISIBLE);
				}
			}

			if(!booleanStatus){
				return;
			}
			
			Double saving = 0.0;
			Double debt = 0.0;

			Collection<AccountState> accountStates = (Collection<AccountState>)obj;
			for(AccountState as : accountStates){

				if(!as.getGroup()){
					if(as.getCategory().startsWith("li")){
						debt += as.getAmount();
					}if(as.getCategory().startsWith("as")){
						saving += as.getAmount();
					}
				}/*else{
					//asAdapter.get().addAccountState(as);
				}*/
			}
			//asAdapter.get().notifyDataSetChanged();

			if(null == llChart){
				llChart = (LinearLayout) view.findViewById(R.id.chart);
			}

			tvSavingValue.setText(DateFormatUtils.getNoPointDecimalFormat().format(saving));
			tvDebtValue.setText(DateFormatUtils.getNoPointDecimalFormat().format(-debt));

			Double sum = saving - debt;

			tvSumValue.setText(DateFormatUtils.getNoPointDecimalFormat().format(sum));
			if(sum >= 0){
				tvSumValue.setTextColor(getResources().getColor(R.color.text_blue));
			}else{
				tvSumValue.setTextColor(getResources().getColor(R.color.text_red));
			}

			PieChart pcv = ChartUtils.makeChart(context,
					new double[] {
							Math.abs(saving),
							Math.abs(debt)
					},
					new String[] {
							getResources().getString(R.string.title_saving),
							getResources().getString(R.string.title_debt)
					},
					Math.abs(saving) > Math.abs(debt)? Math.abs(saving):Math.abs(debt));

			llChart.removeAllViews();
			llChart.addView(pcv, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
		}
		break;
		}
	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
	}
}