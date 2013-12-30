package me.blog.imhallower.wimple;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.blog.imhallower.wimple.WimpleActivity.CommandID;
import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.impl.util.Calculator;
import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Item;
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class TransactionInsertFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionFragment";

	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;
	private static View view = null;
	private static Context context = null;

	private static final Locale locale = new Locale("ko", "KR");
	private static final SimpleDateFormat sdfForGUI = new SimpleDateFormat("MM-dd", locale);
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", locale);
	private static final NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
	private static final DecimalFormat formatCalcNum = (DecimalFormat)nf;
	private static final String formatPattern = "###,###.####";	
	private static int[] padRIDs = null;
	
	// Widget
	private AccountExpandableListAdapter leftAccountListAdapter;
	private AccountExpandableListAdapter rightAccountListAdapter;

	private ExpandableListView leftAccountListView;
	private ExpandableListView rightAccountListView;

	private TextView[] buttons;
	private TextView txtAmount;
	private EditText txtTitle;
	private TextView txtItemDate; 

	// Data
	private List<String> listDataHeader = new ArrayList<String>();
	
	private ListView listViewLatestItems;
	private ArrayAdapter<Item> adapterLatestItems;
	
	private Long itemDate = Calendar.getInstance().getTimeInMillis();
	
	// 
	private Calculator cal = new Calculator();

	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */

	
	static {
		formatCalcNum.applyPattern("###,###.####");
	}

	@Override
	public void onResume() {
		context = WimpleActivity.context;
		initWimple();

		super.onResume();
	}

	private void initWimple() {
		wimple.getAllAccounts(sdf.format(this.itemDate));
		wimple.getLatestItems();
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


		listDataHeader.add("자산");
		listDataHeader.add("부채");
		listDataHeader.add("자본");
		listDataHeader.add("수입");
		listDataHeader.add("지출");		

		// View, Widget

		txtItemDate = (TextView) view.findViewById(R.id.insert_date);
		setupItemDate(Calendar.getInstance().getTimeInMillis());
		
		ImageView ivYesterday = (ImageView) view.findViewById(R.id.insert_yesterday);
		ivYesterday.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Long newDate = itemDate - 24 * 60 * 60 * 1000;
				setupItemDate(newDate);
			}
			
		});
		ImageView ivTomorrow = (ImageView) view.findViewById(R.id.insert_tomorrow);
		ivTomorrow.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Long newDate = itemDate + 24 * 60 * 60 * 1000;
				setupItemDate(newDate);
			}
			
		});
		
		// Account
		leftAccountListAdapter = new AccountExpandableListAdapter(context);
		leftAccountListView = (ExpandableListView) view.findViewById(R.id.insert_category_left);
		leftAccountListView.setAdapter(leftAccountListAdapter);

		leftAccountListView.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				leftAccountListAdapter.setSelected(groupPosition, childPosition, id);
				return false;
			}
		});

		rightAccountListAdapter = new AccountExpandableListAdapter(context);
		rightAccountListView = (ExpandableListView) view.findViewById(R.id.insert_category_right);
		rightAccountListView.setAdapter(rightAccountListAdapter);

		rightAccountListView.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				rightAccountListAdapter.setSelected(groupPosition, childPosition, id);
				return false;
			}
		});

		txtAmount = (TextView) view.findViewById(R.id.insert_amount);
		txtTitle = (EditText) view.findViewById(R.id.insert_entry_title);

		ImageView submit = (ImageView) view.findViewById(R.id.insert_submit);
		submit.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if(false == validateForms()){
					return;
				}

				Double amount = getAmountValue();
				if(amount < 0){
					Log.e(LOG_TAG, "Amount parsing error : " + txtAmount.getText());
					return;
				}

				wimple.makeEntry(Calendar.getInstance().getTimeInMillis(), 
						leftAccountListAdapter.getSelected(), rightAccountListAdapter.getSelected(), 
						txtTitle.getText().toString(), amount, "");
			}
		});

		// latest items
		List<Item> latestItems = new ArrayList<Item>();
		listViewLatestItems = (ListView) view.findViewById(R.id.insert_frequent_items);
		adapterLatestItems = new ArrayAdapter<Item>(context, R.layout.list_frequent_entries, R.id.list_frequent_entry_name, latestItems);
		listViewLatestItems.setAdapter(adapterLatestItems);
		listViewLatestItems.setOnItemClickListener(new OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				selectLatestItem(position);
			}
			
		});
		
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
					setAmountText(result);
				}

			});
		}



		return view;
	}

	private void setupItemDate(Long date) {
		this.itemDate = date;
		txtItemDate.setText(sdfForGUI.format(this.itemDate));
		wimple.getAllAccounts(sdf.format(this.itemDate));
	}
	
	private void setAmountText(Double amount){
		//cal.setValue(selected.getAmount());
		txtAmount.setText(formatCalcNum.format(amount));
	}
	
	private Double getAmountValue(){
		Double amount = 0.0; 
		try{
			amount = nf.parse(txtAmount.getText().toString()).doubleValue();
		}catch(Exception e){
			Log.e(LOG_TAG, "Amount parsing error : " + txtAmount.getText());
			return -1.0;
		}
		return amount;
	}
	
	private void selectLatestItem(int position) {
		Item selected = null;
		
		try{
			selected = adapterLatestItems.getItem(position);
		}catch(Exception e){
			e.printStackTrace();
			Log.e(LOG_TAG, "Failed to select latest Item!!!, position=" + position);
			return;
		}
				
		txtTitle.setText(selected.getItem());
		cal.setValue(selected.getAmount());
		setAmountText(selected.getAmount());
						
		int selectedLeftGroup = leftAccountListAdapter.setSelected(selected.getLeftAccountID());
		if(selectedLeftGroup > -1){
			leftAccountListView.expandGroup(selectedLeftGroup);
			leftAccountListView.setSelection(selectedLeftGroup);
		}
		
		int selectedRightGroup = rightAccountListAdapter.setSelected(selected.getRightAccountID());
		if(selectedRightGroup > -1){
			rightAccountListView.expandGroup(selectedRightGroup);
			rightAccountListView.setSelection(selectedRightGroup);
		}
	}

	private boolean validateForms() {
		if(null == txtTitle.getText().toString() ||
				txtTitle.getText().toString().isEmpty()){
			Log.e(LOG_TAG, "Invalid entry title.");
			Toast.makeText(context, context.getResources().getString(R.string.insert_invalid_title), 
					Toast.LENGTH_SHORT).show();
			return false;
		}

		if(null == txtAmount.getText().toString() ||
				txtAmount.getText().toString().isEmpty()){
			Log.e(LOG_TAG, "Invalid entry amount.");
			Toast.makeText(context, context.getResources().getString(R.string.insert_invalid_amount), 
					Toast.LENGTH_SHORT).show();
			return false;
		}

		if(false == this.leftAccountListAdapter.isSelected()){
			Log.e(LOG_TAG, "left side account is not selected!!!");
			Toast.makeText(context, context.getResources().getString(R.string.insert_invalid_left_accounts), 
					Toast.LENGTH_SHORT).show();
			return false;
		}
		
		if(false == this.rightAccountListAdapter.isSelected()){
			Log.e(LOG_TAG, "right side account is not selected!!!");
			Toast.makeText(context, context.getResources().getString(R.string.insert_invalid_right_accounts), 
					Toast.LENGTH_SHORT).show();
			return false;
		}
		return true;
	}

	private void cleanForms(){
		txtTitle.setText("");		
		setAmountText(0.0);
		// TODO : clear account selection
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

			{
				List<String> lHeader = new ArrayList<String>();
				lHeader.add(listDataHeader.get(0));
				lHeader.add(listDataHeader.get(1));
				lHeader.add(listDataHeader.get(2));
				lHeader.add(listDataHeader.get(4));

				Map<String, List<Account>> lChild = new HashMap<String, List<Account>>();
				lChild.put(lHeader.get(0), assets);
				lChild.put(lHeader.get(1), liabilities);
				lChild.put(lHeader.get(2), capital);
				lChild.put(lHeader.get(3), expenses);

				leftAccountListAdapter.clear();
				leftAccountListAdapter.setData(lHeader, lChild);
				leftAccountListAdapter.notifyDataSetChanged();	
			}

			{
				List<String> rHeader = new ArrayList<String>();
				rHeader.add(listDataHeader.get(0));
				rHeader.add(listDataHeader.get(1));
				rHeader.add(listDataHeader.get(2));
				rHeader.add(listDataHeader.get(3));

				Map<String, List<Account>> rChild = new HashMap<String, List<Account>>();
				rChild.put(rHeader.get(0), assets);
				rChild.put(rHeader.get(1), liabilities);
				rChild.put(rHeader.get(2), capital);
				rChild.put(rHeader.get(3), income);

				rightAccountListAdapter.clear();
				rightAccountListAdapter.setData(rHeader, rChild);
				rightAccountListAdapter.notifyDataSetChanged();	
			}			

			break;			
		}

		case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED :
		{			
			if(booleanStatus){
				Toast.makeText(context, getResources().getString(R.string.insert_success), Toast.LENGTH_SHORT).show();
				cleanForms();
				wimple.getLatestItems();
			}else{
				Toast.makeText(context, getResources().getString(R.string.insert_failed), Toast.LENGTH_LONG).show();
			}
		}	
		break;

		case CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED :
		{
			// TODO : test for frequent items
		}
		break;
		
		case CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED :
		{			
		}
		break;

		case CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED :
		{
			if(booleanStatus){
				adapterLatestItems.clear();
				adapterLatestItems.addAll((List<Item>) obj);
				adapterLatestItems.notifyDataSetChanged();
			}	
		}
		break;
		
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
