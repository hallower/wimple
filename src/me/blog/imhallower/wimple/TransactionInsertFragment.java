package me.blog.imhallower.wimple;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.blog.imhallower.wimple.WimpleActivity.CommandID;
import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.impl.util.Calculator;
import me.blog.imhallower.wimple.model.Account;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TransactionInsertFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionFragment";
	
	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;
	private static View view = null;
	private static Context context = null;
	
	private DecimalFormat format = new DecimalFormat("#######.##########");
	private static int[] padRIDs = null;

	// Widget
	private ExpandableListAdapter leftAccountListAdapter;
	private ExpandableListAdapter rightAccountListAdapter;

	private ExpandableListView leftAccountListView;
	private ExpandableListView rightAccountListView;

	private TextView[] buttons;
	private TextView amount;

	private List<String> listDataHeader = new ArrayList<String>();
	private Map<String, List<Account>> listDataChild = new HashMap<String, List<Account>>();

	// 
	Calculator cal = new Calculator();

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

		// Data 
		view = (LinearLayout)inflater.inflate(R.layout.fragment_transaction_insert_tab, container, false);
		//synchronized(TransactionInsertFragment.class){
			if(null == padRIDs)
			{
				TypedArray ar = context.getResources().obtainTypedArray(R.array.number_buttons);

				int len = ar.length();
				padRIDs = new int[len];
				for(int cnt = 0; cnt < len ; cnt++){
					padRIDs[cnt] = ar.getResourceId(cnt, 0);
				}
				ar.recycle();        
			}
		//}
		
		
		// View, Widget
		
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

		amount = (TextView) view.findViewById(R.id.insert_amount);

		
		// post.. 
		
		buttons = new TextView[padRIDs.length];
		for(int i = 0; i < padRIDs.length ; i++){
			buttons[i] = (TextView) view.findViewById(padRIDs[i]);
			buttons[i].setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {

					//double right = Double.parseDouble(amount.getText().toString());
					double result = 0.0;
					switch(v.getId())
					{

					// I don't know why numbersRIDS[] is not suitable for this.
					case R.id.insert_pad_10 : result = cal.zero(); break;
					case R.id.insert_pad_1 : result = cal.shift(1); break;
					case R.id.insert_pad_2 : result = cal.shift(2); break;
					case R.id.insert_pad_3 : result = cal.shift(3); break;
					case R.id.insert_pad_4 : result = cal.shift(4); break;
					case R.id.insert_pad_5 : result = cal.shift(5); break;
					case R.id.insert_pad_6 : result = cal.shift(6); break;
					case R.id.insert_pad_7 : result = cal.shift(7); break;
					case R.id.insert_pad_8 : result = cal.shift(8); break;
					case R.id.insert_pad_9 : result = cal.shift(9); break;
					case R.id.insert_pad_100 : result = cal.zeroTwice(); break;

					case R.id.insert_pad_point : result = cal.point(); break;
					case R.id.insert_pad_plus : result = cal.plus(); break;
					case R.id.insert_pad_minus : result = cal.minus(); break;
					case R.id.insert_pad_multiply : result = cal.multiply(); break;
					case R.id.insert_pad_divide : result = cal.divide(); break;
					case R.id.insert_pad_eq : result = cal.eq(); break;
					case R.id.insert_pad_clear : result = cal.clear(); break;
					case R.id.insert_pad_back : result = cal.shiftBack(); break;

					}					
					amount.setText(format.format(result));
				}

			});
		}



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
