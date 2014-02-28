package kr.blogspot.charlie0301;

import java.lang.ref.WeakReference;
import java.util.Collection;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.widget.ItemListView;
import kr.blogspot.charlie0301.widget.accountstate.AccountStateItemListAdapter;
import kr.blogspot.charlie0301.widget.entry.EntryItemListAdapter;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

public class FinancialStateSummaryFragment  extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionInsertFragment";

	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;
	private static View view = null;
	private static Context context = null;



	// GUI

	private WeakReference<ItemListView> asList;
	private WeakReference<AccountStateItemListAdapter> asAdapter;
	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context;		

		view = (RelativeLayout)inflater.inflate(R.layout.fragment_finalcial_state_summary_tab, container, false);

		RelativeLayout.LayoutParams sessionParams = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
		asList = new WeakReference<ItemListView>((ItemListView)view.findViewById(R.id.as_list_view));
		asAdapter = new WeakReference<AccountStateItemListAdapter>(new AccountStateItemListAdapter(context));

		asList.get().setAdapter(asAdapter.get());
		asList.get().setLayoutParams(sessionParams);

		registerForContextMenu(asList.get());

		
		
		wimple.getFinancialState(DateFormatUtils.getServerDateString(""), false);
		return view;
	}
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
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

			Collection<AccountState> accountStates = (Collection<AccountState>)obj;
			for(AccountState as : accountStates){
				asAdapter.get().addAccountState(as);
			}
			asAdapter.get().notifyDataSetChanged();
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
