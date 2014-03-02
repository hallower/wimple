package kr.blogspot.charlie0301;

import java.lang.ref.WeakReference;
import java.util.Collection;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.impl.util.WidgetItem;
import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.widget.ItemListView;
import kr.blogspot.charlie0301.widget.accountstate.AccountStateItemListAdapter;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class FinancialStateSummaryFragment  extends Fragment implements IWimpleFragment{

	//private final static String LOG_TAG = "TransactionInsertFragment";

	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	//private WimpleActivity mainActivity = null;
	private static View view = null;
	private static Context context = null;

	// GUI
	private WeakReference<ItemListView> asList;
	private WeakReference<AccountStateItemListAdapter> asAdapter;
	private DoughnutChartView cv;
	private LinearLayout llChart;
	private TextView tvSavingValue;
	private TextView tvDebtValue;
	private TextView tvSumValue;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context;		

		view = (RelativeLayout)inflater.inflate(R.layout.fragment_finalcial_state_summary_tab, container, false);

		LinearLayout.LayoutParams sessionParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		asList = new WeakReference<ItemListView>((ItemListView)view.findViewById(R.id.as_list_view));
		asAdapter = new WeakReference<AccountStateItemListAdapter>(new AccountStateItemListAdapter(context));

		asList.get().setAdapter(asAdapter.get());
		asList.get().setLayoutParams(sessionParams);

		registerForContextMenu(asList.get());

		tvSumValue = (TextView)view.findViewById(R.id.as_sum_value);
		tvSavingValue = (TextView)view.findViewById(R.id.as_saving_value);
		tvDebtValue = (TextView)view.findViewById(R.id.as_debt_value);

		/*
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		boolean prefetch = sharedPref.getBoolean(SettingsFragment.KEY_FINANCIAL_STATE_PREFETCH, true);
		if(prefetch){
			wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false);	
		}
		*/

		wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false);	
		
		((ImageView) view.findViewById(R.id.as_refresh)).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true);	
			}
		});
			
		return view;
	}
	@Override
	public void onDestroy() {

		asList.clear();
		asList = null;
		asAdapter.clear();
		asAdapter = null;

		cv = null;
		llChart = null;
		tvSavingValue = null;
		tvDebtValue = null;
		tvSumValue = null;

		super.onDestroy();
	}
	@Override
	public void onDetach() {
		// TODO Auto-generated method stub
		super.onDetach();
	}
	@Override
	public void onResume() {
		wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false);
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

		switch(command){

		case CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED :{

			if(false == booleanStatus){
				return;
			}
			
			Double saving = 0.0;
			Double debt = 0.0;

			Collection<AccountState> accountStates = (Collection<AccountState>)obj;
			for(AccountState as : accountStates){

				if(false == as.getGroup()){		
					if(as.getCategory().startsWith("li")){
						debt += as.getAmount();
					}if(as.getCategory().startsWith("as")){
						saving += as.getAmount();
					}
				}else{
					asAdapter.get().addAccountState(as);
				}
			}
			asAdapter.get().notifyDataSetChanged();

			if(null == cv){
				cv = new DoughnutChartView(context);
				llChart = (LinearLayout)view.findViewById(R.id.chart);	
			}

			tvSavingValue.setText(DateFormatUtils.getDecimalFormat().format(saving));
			tvDebtValue.setText(DateFormatUtils.getDecimalFormat().format(-debt));

			Double sum = saving - debt;

			tvSumValue.setText(DateFormatUtils.getDecimalFormat().format(sum));
			if(sum >= 0){
				tvSumValue.setTextColor(getResources().getColor(R.color.text_blue));
			}else{
				tvSumValue.setTextColor(getResources().getColor(R.color.text_red));
			}

			setGraphicChart(new double[] {Math.abs(saving), Math.abs(debt)}, 
					new String[] {getResources().getString(R.string.title_saving),
					getResources().getString(R.string.title_debt)});

			llChart.removeAllViews();
			llChart.addView(cv, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

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


	private void setGraphicChart(double[] values, String[] names){

		cv.setDataValues(values);
		cv.setLegendValues(names);		
		int[] colors = new int[values.length];
		for(int i = 0; i < values.length; i++){
			colors[i] = WidgetItem.predefinedColors[9-i];
		}
		cv.setBarColorValues(colors);
		cv.setDisplayLabels(true);
		cv.makeChart();

	}


}
