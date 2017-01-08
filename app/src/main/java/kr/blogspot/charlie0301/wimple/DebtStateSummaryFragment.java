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
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.github.mikephil.charting.charts.PieChart;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.ChartUtils;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.impl.util.WidgetItem;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.widget.ItemListView;
import kr.blogspot.charlie0301.wimple.widget.accountstate.AccountStateItemListAdapter;

public class DebtStateSummaryFragment  extends Fragment implements IWimpleFragment{

	//private final static String LOG_TAG = "TransactionInsertFragment";

	//private final static WimpleImpl wimple = WimpleImpl.getInstance();
	//private WimpleActivity mainActivity = null;
	private static View view = null;
	private static Context context = null;


	// GUI
	private WeakReference<ItemListView> asList;
	private WeakReference<AccountStateItemListAdapter> asAdapter;
	private LinearLayout llChart;


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context;		

		view = (RelativeLayout)inflater.inflate(R.layout.fragment_debt_state_summary_tab, container, false);

		LinearLayout.LayoutParams sessionParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
		asList = new WeakReference<ItemListView>((ItemListView)view.findViewById(R.id.debt_list_view));
		asAdapter = new WeakReference<AccountStateItemListAdapter>(new AccountStateItemListAdapter(context));

		asList.get().setAdapter(asAdapter.get());
		asList.get().setLayoutParams(sessionParams);

		registerForContextMenu(asList.get());

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
		// TODO Auto-generated method stub
		super.onDetach();
	}
	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		context = WimpleActivity.context;
		super.onResume();
	}
	@SuppressWarnings("unchecked")
	@Override
	public void handleMessage(Message msg) {
		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		// if fragment is added or not to the activity
		if(false == isAdded()){
			return;
		}
		
		if(null == context){
			context = WimpleActivity.context;
			if(null == context){
				return;
			}
		}

		switch(command){

		case CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED :{

			if(false == booleanStatus){
				return;
			}
			
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
			boolean showGroup = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_SHOW_GROUP, false);
			
			ArrayList<Double> values = new ArrayList<Double>();
			ArrayList<String> names = new ArrayList<String>();
			
			Collection<AccountState> accountStates = (Collection<AccountState>)obj;
			for(AccountState as : accountStates){
				//Log.d(LOG_TAG, "[" + as.getAccountID() + "], " + as.getAccountName() + 
				//		" = " + as.getCategory() + ", " + as.getGroup());
				if(false == as.getCategory().startsWith("li")){
					continue;
				}
				
				if(showGroup == as.getGroup()  &&
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

			double[] doubleValues = new double[values.size()];
			for(int i = 0; i < doubleValues.length; i++){
				doubleValues[i] = values.get(i).doubleValue();
			}
			String[] stringValues = new String[names.size()];
			for(int i = 0; i < stringValues.length; i++){
				stringValues[i] = names.get(i);
			}

			PieChart pcv = ChartUtils.makeChart(context, doubleValues, stringValues);

			llChart.removeAllViews();
			llChart.addView(pcv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

		
		}
		break;
		}
	}
	@Override
	public void refreshView() {
		// TODO Auto-generated method stub

	}
	@Override
	public void setActivityInstance(WimpleActivity instance) {
		// TODO Auto-generated method stub
	}

}
