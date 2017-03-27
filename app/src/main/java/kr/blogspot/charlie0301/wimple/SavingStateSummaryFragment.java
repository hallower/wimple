package kr.blogspot.charlie0301.wimple;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

import com.github.mikephil.charting.charts.PieChart;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.widget.ItemListView;
import kr.blogspot.charlie0301.wimple.widget.accountstate.AccountStateItemListAdapter;

public class SavingStateSummaryFragment  extends Fragment implements IWimpleFragment{

	//private final static String LOG_TAG = "TransactionInsertFragment";

	private final WimpleImpl wimple = WimpleImpl.getInstance();
	private View view = null;
	private Context context = null;

	// GUI
	private WeakReference<ItemListView> asList;
	private WeakReference<AccountStateItemListAdapter> asAdapter;
	private LinearLayout llChart;

	// Data
	private boolean firstUpdate;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context.get();

		view = inflater.inflate(R.layout.fragment_saving_state_summary_tab, container, false);

		LinearLayout.LayoutParams sessionParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		asList = new WeakReference<>((ItemListView) view.findViewById(R.id.saving_list_view));
		asAdapter = new WeakReference<>(new AccountStateItemListAdapter(context));

		asList.get().setAdapter(asAdapter.get());
		asList.get().setLayoutParams(sessionParams);

		registerForContextMenu(asList.get());

		firstUpdate = true;
		WimpleImpl.getInstance().getFinancialState(DateFormatUtils.getServerDateString(""), false);
		return view;
	}
	@Override
	public void onDestroy() {

		asList.clear();
		asList = null;
		asAdapter.clear();
		asAdapter = null;
		llChart = null;
		
		super.onDestroy();
	}
	@Override
	public void onDetach() {

		super.onDetach();
	}
	@Override
	public void onResume() {

		context = WimpleActivity.context.get();
		super.onResume();
	}
	@SuppressWarnings("unchecked")
	@Override
	public void handleMessage(Message msg) {
		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		// if fragment is added or not to the activity
		if(!isAdded()){
			return;
		}

		switch(command){

		case CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED :{

			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

			if(firstUpdate) {
				firstUpdate = false;
				boolean autoRefresh = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_AUTO_REFRESH, true);
				if (autoRefresh) {
					wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true);
				}
			}

			if(!booleanStatus){
				return;
			}

			if(null == context){
				context = WimpleActivity.context.get();
				if(null == context){
					return;
				}
			}

			boolean showGroup = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_SHOW_GROUP, false);
			
			ArrayList<Double> values = new ArrayList<>();
			ArrayList<String> names = new ArrayList<>();

			Collection<AccountState> accountStates = (Collection<AccountState>)obj;
			for(AccountState as : accountStates){
				//Log.d(LOG_TAG, "[" + as.getAccountID() + "], " + as.getAccountName() + 
				//		" = " + as.getCategory() + ", " + as.getGroup());
				if(!as.getCategory().startsWith("as")){
					continue;
				}
				
				if(showGroup == as.getGroup() &&
						as.getAmount() != 0){
					values.add(as.getAmount());
					names.add(as.getAccountName());	
				}
				asAdapter.get().addAccountState(as);
			}
			asAdapter.get().notifyDataSetChanged();
			
			if(null == llChart){
				llChart = (LinearLayout)view.findViewById(R.id.chart);	
			}

			if(0 < values.size()){
				double maxValue = -99999999;
				double[] doubleValues = new double[values.size()];
				for(int i = 0; i < doubleValues.length; i++){
					doubleValues[i] = values.get(i);
					if(maxValue < doubleValues[i])
						maxValue = doubleValues[i];
				}
				String[] stringValues = new String[names.size()];
				for(int i = 0; i < stringValues.length; i++){
					stringValues[i] = names.get(i);
				}

				PieChart pcv = ChartUtils.makeChart(context, doubleValues, stringValues, maxValue);

				llChart.removeAllViews();
				llChart.addView(pcv, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
			}
		}
		break;
		}
	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
	}

}
