package me.blog.imhallower.wimple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.blog.imhallower.wimple.WimpleActivity.CommandID;
import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.model.Account;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;

public class TransactionInsertFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionFragment";
	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;

	private static View view = null;
	private static Context context = null;

	ExpandableListAdapter leftAccountListAdapter;
	ExpandableListAdapter rightAccountListAdapter;

	ExpandableListView leftAccountListView;
	ExpandableListView rightAccountListView;	

	private List<String> listDataHeader = new ArrayList<String>();
	private Map<String, List<Account>> listDataChild = new HashMap<String, List<Account>>();

	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */


	@Override
	public void onResume() {
		context = WimpleActivity.context;
		initWimple();

		super.onResume();
	}

	private void initWimple() {
		wimple.getAllAccounts(wimple.firstSectionID);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context;
		view = (LinearLayout)inflater.inflate(R.layout.fragment_transaction_insert_tab, container, false);

		listDataHeader.add("자산");
		listDataHeader.add("부채");
		listDataHeader.add("자본");
		listDataHeader.add("수입");
		listDataHeader.add("지출");

		leftAccountListAdapter = new ExpandableListAdapter(context, listDataHeader, listDataChild);
		leftAccountListView = (ExpandableListView) view.findViewById(R.id.insert_category_left);
		leftAccountListView.setAdapter(leftAccountListAdapter);

		rightAccountListAdapter = new ExpandableListAdapter(context, listDataHeader, listDataChild);
		rightAccountListView = (ExpandableListView) view.findViewById(R.id.insert_category_right);
		rightAccountListView.setAdapter(rightAccountListAdapter);

		return view;
	}


	@SuppressWarnings("unchecked")
	public void handleMessage(Message msg) {

		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		switch(command){

		//case CommandID.WIMPLE_LOGGIN_SUCCESS :
		case CommandID.GET_ALL_SECTION_RECEIVED :{
			initWimple();
			break;
		}

		case CommandID.GET_ALL_ACCOUNT_RECEIVED :{
			Collection<Account> accountList = (Collection<Account>) obj;

			List<Account> assets = new ArrayList<Account>();
			List<Account> liabilities = new ArrayList<Account>();
			List<Account> capital = new ArrayList<Account>();
			List<Account> income = new ArrayList<Account>();
			List<Account> expenses = new ArrayList<Account>();
			/*
			 * 
			 */
			for(Account item : accountList){

				switch(item.getWhat().charAt(0)){
				case 'a' :	// assets
					assets.add(item);
					break;
				case 'l' :	// liabilities
					liabilities.add(item);
					break;
				case 'c' :	// capital
					capital.add(item);
					break;
				case 'i' :	// income
					income.add(item);
					break;
				case 'e' :	// expenses
					expenses.add(item);
					break;
				default :
					Log.e(LOG_TAG, "Invalid accout item !!!!");
				}
			}

			listDataChild.clear();
			listDataChild.put(listDataHeader.get(0), assets);
			listDataChild.put(listDataHeader.get(1), liabilities);
			listDataChild.put(listDataHeader.get(2), capital);
			listDataChild.put(listDataHeader.get(3), income);
			listDataChild.put(listDataHeader.get(4), expenses);

			leftAccountListAdapter.setData(listDataHeader, listDataChild);
			leftAccountListAdapter.notifyDataSetChanged();
			rightAccountListAdapter.setData(listDataHeader, listDataChild);
			rightAccountListAdapter.notifyDataSetChanged();

			break;			
		}


		}
	}

	@Override
	public void onDetach() {

		context = null;

		/*
		for(int i=0; i < feedAdapter.get().getCount() ; i++){
			View v = feedAdapter.get().getView(i, null, feedList.get());
			if(v instanceof FeedItemView){
				((FeedItemView) v).clear();
			}
		}
		 */

		//WidgetItem.recycleRecursive(view);

		view = null;
		super.onDetach();
	}

	@Override
	public void refreshView() {

	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
		mainActivity = instance;
	}
}
