package kr.blogspot.charlie0301;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.Calculator;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.widget.AccountExpandableListAdapter;
import kr.blogspot.charlie0301.widget.DatePickerFragment;
import kr.blogspot.charlie0301.widget.DatePickerFragment.OnDateSetListener;
import android.app.Fragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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

	private final static String LOG_TAG = "TransactionInsertFragment";

	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;
	private static View view = null;
	private static Context context = null;

	private final static Calculator cal = new Calculator();
	private static int[] padRIDs = null;

	// Widget
	private AccountExpandableListAdapter leftAccountListAdapter;
	private AccountExpandableListAdapter rightAccountListAdapter;

	private TextView tvLeftAccountTitle;
	private TextView tvRightAccountTitle;
	private ExpandableListView leftAccountListView;
	private ExpandableListView rightAccountListView;

	private TextView[] buttons;
	private TextView txtAmount;
	private EditText txtTitle;
	private TextView txtItemDate; 
	private TextView txtInsertMode;

	private DatePickerFragment datePicker;

	// Data
	private ListView listViewLatestItems;
	private ArrayAdapter<Item> adapterLatestItems;
	private boolean isEditing = false;
	private Item editingItem = null;
	//private boolean isFirstTimeForUniqueFiltering = true;

	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */

	@Override
	public void onResume() {
		//Log.e(LOG_TAG, "Insert - onResume");
		context = WimpleActivity.context;
		initWimple();

		super.onResume();
	}

	@Override
	public void onPause() {
		//Log.e(LOG_TAG, "Insert - onPause");

		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	public void onStart() {
		//Log.e(LOG_TAG, "Insert - onStart");

		// TODO Auto-generated method stub
		super.onStart();
	}

	@Override
	public void onStop() {
		//Log.e(LOG_TAG, "Insert - onStop");

		// TODO Auto-generated method stub
		super.onStop();
	}

	private void initWimple() {
		wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(datePicker.getSelectedDate()));
		wimple.getLatestItems();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		//Log.e(LOG_TAG, "Insert - onCreateView");
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

		setupDate();

		setupAccountLists();

		setupTitlenSubmit();

		setupLatestItems();

		setupButtons();

		setAmountText(0.0);

		//initWimple();

		return view;
	}

	private void setupTitlenSubmit() {
		txtAmount = (TextView) view.findViewById(R.id.insert_amount);
		txtInsertMode  = (TextView) view.findViewById(R.id.btn_submit);
		txtInsertMode.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				setAmountText(cal.eq());

				if(false == validateForms()){
					return;
				}

				Double amount = getAmountValue();
				if(amount < 0){
					Log.e(LOG_TAG, "Amount parsing error : " + txtAmount.getText());
					return;
				}


				if(isEditing){
					isEditing = false;

					/*
					 * server doesn't receive yyyyMMdd.xxxx format
					String date = editingItem.getDateValue();
					if(datePicker.isDateChanged()){
						date = DateFormatUtils.getServerDateString(datePicker.getSelectedDate());
					}
					 */

					boolean res = wimple.modifyEntry(editingItem.getId(), DateFormatUtils.getServerDateString(datePicker.getSelectedDate()), 
							leftAccountListAdapter.getSelected(), rightAccountListAdapter.getSelected(), 
							txtTitle.getText().toString(), amount, "");

					if(false == res){
						Toast.makeText(context, getResources().getString(R.string.modify_failed), Toast.LENGTH_LONG).show();
					}

					editingItem = null;

				}else{
					boolean res = wimple.makeEntry(datePicker.getSelectedDate(), 
							leftAccountListAdapter.getSelected(), rightAccountListAdapter.getSelected(), 
							txtTitle.getText().toString(), amount, "");

					if(false == res){
						Toast.makeText(context, getResources().getString(R.string.insert_failed), Toast.LENGTH_LONG).show();
					}
				}
			}
		});
		setSubmitButton(true);

		txtTitle = (EditText) view.findViewById(R.id.insert_entry_title);
		txtTitle.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String changed = s.toString();
				if(changed.contains("(")){
					changed = changed.substring(0, changed.indexOf("(") - 1);
				}
				adapterLatestItems.getFilter().filter(changed);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

	}

	private void setupLatestItems() {
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

		ImageView titleClear = (ImageView) view.findViewById(R.id.insert_title_clear);
		titleClear.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				clearForms();
			}
		});
	}

	private void setupButtons() {
		buttons = new TextView[padRIDs.length];
		for(int i = 0; i < padRIDs.length ; i++){
			buttons[i] = (TextView) view.findViewById(padRIDs[i]);
			buttons[i].setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {

					// remove virtual keyboard
					txtTitle.clearFocus();
					((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
							txtTitle.getWindowToken(), 0);

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
	}

	private void setupDate() {
		txtItemDate = (TextView) view.findViewById(R.id.insert_date);
		datePicker = new DatePickerFragment();
		datePicker.setTextViewWidget(txtItemDate);
		datePicker.setOnDateSetListener(new OnDateSetListener(){

			@Override
			public void onDateSet(Long date) {				
				setupItemDate(date);
			}

		});
		txtItemDate.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				datePicker.show(mainActivity.getFragmentManager(), "itemDate");
			}
		});
		setupItemDate(Calendar.getInstance().getTimeInMillis());

		ImageView ivYesterday = (ImageView) view.findViewById(R.id.insert_yesterday);
		ivYesterday.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Long newDate = datePicker.getSelectedDate() - 24 * 60 * 60 * 1000;
				setupItemDate(newDate);
			}

		});
		ImageView ivTomorrow = (ImageView) view.findViewById(R.id.insert_tomorrow);
		ivTomorrow.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Long newDate = datePicker.getSelectedDate() + 24 * 60 * 60 * 1000;
				setupItemDate(newDate);
			}

		});
	}

	private void setupAccountLists() {
		tvLeftAccountTitle = (TextView) view.findViewById(R.id.insert_category_left_title);		
		tvLeftAccountTitle.getBackground().setAlpha(128);

		leftAccountListAdapter = new AccountExpandableListAdapter(context);
		leftAccountListView = (ExpandableListView) view.findViewById(R.id.insert_category_left);
		leftAccountListView.setAdapter(leftAccountListAdapter);

		leftAccountListView.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				leftAccountListAdapter.setSelected(groupPosition, childPosition, id);
				tvLeftAccountTitle.setText(((Account)leftAccountListAdapter.getChild(groupPosition, childPosition)).getTitle());
				return false;
			}
		});

		tvRightAccountTitle = (TextView) view.findViewById(R.id.insert_category_right_title);
		tvRightAccountTitle.getBackground().setAlpha(128);

		rightAccountListAdapter = new AccountExpandableListAdapter(context);
		rightAccountListView = (ExpandableListView) view.findViewById(R.id.insert_category_right);
		rightAccountListView.setAdapter(rightAccountListAdapter);

		rightAccountListView.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				rightAccountListAdapter.setSelected(groupPosition, childPosition, id);
				tvRightAccountTitle.setText(((Account)rightAccountListAdapter.getChild(groupPosition, childPosition)).getTitle());
				return false;
			}
		});
	}

	private void setupItemDate(Long date) {
		datePicker.setDate(date);
		wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(datePicker.getSelectedDate()));
	}

	private void setAmountText(Double amount){
		//cal.setValue(selected.getAmount());
		txtAmount.setText(DateFormatUtils.getDecimalFormat().format(amount));
	}

	private Double getAmountValue(){
		Double amount = 0.0; 
		try{
			amount = DateFormatUtils.getNumberFormat().parse(txtAmount.getText().toString()).doubleValue();
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
		//cal.setValue(selected.getAmount());
		//setAmountText(selected.getAmount());

		selectCategory(selected);
	}

	private void setEntry(Item entry) {
		txtTitle.setText(entry.getItem());
		cal.setValue(entry.getAmount());
		setAmountText(entry.getAmount());
		datePicker.setDate(entry.getDate());

		selectCategory(entry);
	}

	private void selectCategory(Item entry) {
		int selectedLeftGroup = leftAccountListAdapter.setSelected(entry.getLeftAccountID());
		if(selectedLeftGroup > -1){

			/*
			for(int i = 0; i < leftAccountListView.getChildCount() ; i++){
				leftAccountListView.collapseGroup(i);
			}
			leftAccountListView.expandGroup(selectedLeftGroup);
			 */
			leftAccountListView.setSelection(selectedLeftGroup);
			leftAccountListView.setSelectedChild(selectedLeftGroup, leftAccountListAdapter.getSelectedChildPosition(), true);
			tvLeftAccountTitle.setText(((Account)leftAccountListAdapter.getChild(selectedLeftGroup, leftAccountListAdapter.getSelectedChildPosition())).getTitle());
		}

		int selectedRightGroup = rightAccountListAdapter.setSelected(entry.getRightAccountID());
		if(selectedRightGroup > -1){

			/*
			for(int i = 0; i < rightAccountListView.getChildCount() ; i++){
				rightAccountListView.collapseGroup(i);
			}
			rightAccountListView.expandGroup(selectedRightGroup);
			 */
			rightAccountListView.setSelection(selectedRightGroup);
			rightAccountListView.setSelectedChild(selectedRightGroup, rightAccountListAdapter.getSelectedChildPosition(), true);
			tvRightAccountTitle.setText(((Account)rightAccountListAdapter.getChild(selectedRightGroup, rightAccountListAdapter.getSelectedChildPosition())).getTitle());
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

	private void clearForms(){
		txtTitle.setText("");
		setAmountText(0.0);
		datePicker.setDate(Calendar.getInstance().getTimeInMillis());

		tvLeftAccountTitle.setText(getResources().getString(R.string.insert_left_accounts));
		tvRightAccountTitle.setText(getResources().getString(R.string.insert_right_accounts));;
		leftAccountListAdapter.clearSelection();
		rightAccountListAdapter.clearSelection();

		if(this.isEditing){
			this.isEditing = false;
			editingItem = null;
		}
		setSubmitButton(true);
	}

	@SuppressWarnings("unchecked")
	public void handleMessage(Message msg) {

		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		// if fragment is added or not to the activity
		if(false == isAdded()){
			return;
		}

		switch(command){

		//case CommandID.WIMPLE_LOGGIN_SUCCESS :
		case CommandID.GET_ALL_SECTION_RECEIVED :{
			initWimple();
		}
		break;

		case CommandID.GET_ALL_ACCOUNT_RECEIVED :{

			if(false == booleanStatus){
				return;
			}

			Collection<Account> accountList = (Collection<Account>) obj;

			if(accountList.isEmpty()){
				return;
			}

			List<Account> assets = new ArrayList<Account>();
			List<Account> liabilities = new ArrayList<Account>();
			List<Account> capital = new ArrayList<Account>();
			List<Account> income = new ArrayList<Account>();
			List<Account> expenses = new ArrayList<Account>();
			/*
			 * 
			 */
			for(Account item : accountList){

				if(0 == item.getType().compareTo("group")){
					continue;
				}

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
				lHeader.add(getResources().getString(R.string.entry_header_asset_p));
				lHeader.add(getResources().getString(R.string.entry_header_debt_m));
				lHeader.add(getResources().getString(R.string.entry_header_capital_m));
				lHeader.add(getResources().getString(R.string.entry_header_expenses));

				Map<String, List<Account>> lChild = new HashMap<String, List<Account>>();
				lChild.put(lHeader.get(0), assets);
				lChild.put(lHeader.get(1), liabilities);
				lChild.put(lHeader.get(2), capital);
				lChild.put(lHeader.get(3), expenses);

				leftAccountListAdapter.clear();
				leftAccountListAdapter.setData(lHeader, lChild);
				leftAccountListAdapter.notifyDataSetChanged();

				for(int i = 0; i < leftAccountListAdapter.getGroupCount() ; i++){
					leftAccountListView.expandGroup(i);
				}
			}

			{
				List<String> rHeader = new ArrayList<String>();
				rHeader.add(getResources().getString(R.string.entry_header_asset_m));
				rHeader.add(getResources().getString(R.string.entry_header_debt_p));
				rHeader.add(getResources().getString(R.string.entry_header_capital_p));
				rHeader.add(getResources().getString(R.string.entry_header_income));

				Map<String, List<Account>> rChild = new HashMap<String, List<Account>>();
				rChild.put(rHeader.get(0), assets);
				rChild.put(rHeader.get(1), liabilities);
				rChild.put(rHeader.get(2), capital);
				rChild.put(rHeader.get(3), income);

				rightAccountListAdapter.clear();
				rightAccountListAdapter.setData(rHeader, rChild);
				rightAccountListAdapter.notifyDataSetChanged();	

				for(int i = 0; i < rightAccountListAdapter.getGroupCount() ; i++){
					rightAccountListView.expandGroup(i);
				}
			}			

			break;			
		}

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
				adapterLatestItems.sort(new Item.DateDescCompare());
				adapterLatestItems.notifyDataSetChanged();
			}	
		}
		break;

		case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED :
		{	
			String entryDate = (String)obj;

			Log.e(LOG_TAG, "GET_MAKE_ENTRY_RESPONSE_RECEIVED entryDate=" + entryDate);
			if(booleanStatus){
				Toast.makeText(context, getResources().getString(R.string.insert_success), Toast.LENGTH_SHORT).show();
				clearForms();
				wimple.getLatestItems(true);
				//mainActivity.moveTabOfPager(1);
			}else{
				Toast.makeText(context, getResources().getString(R.string.insert_failed), Toast.LENGTH_LONG).show();
			}
		}	
		break;

		case CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM : {

			String itemID = obj.toString();

			// Modifying 
			Item item = wimple.getEntry(itemID);
			isEditing = true;
			setSubmitButton(false);

			if(null == item){
				// Add Monthly Item
				isEditing = false;
				item = wimple.getMonthlyItem(itemID);
				setSubmitButton(true);
			}			

			if(null == item){
				isEditing = false;
				editingItem = null;
				Toast.makeText(context, "oops", Toast.LENGTH_SHORT).show();
				setSubmitButton(true);
				return;
			}

			editingItem = item;

			setEntry(item);

			if(isEditing){
				Toast.makeText(context, getResources().getString(R.string.entry_modify_notice), Toast.LENGTH_LONG).show();	
			}else{
				Toast.makeText(context, getResources().getString(R.string.month_item_modify_notice), Toast.LENGTH_LONG).show();
			}			
		}
		break;

		case CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED : {

			//Entry entry = (Entry)obj;
			if(booleanStatus){
				Toast.makeText(context, getResources().getString(R.string.modify_success), Toast.LENGTH_SHORT).show();
				clearForms();
				mainActivity.moveTabOfPager(1);
			}else{
				Toast.makeText(context, getResources().getString(R.string.modify_failed), Toast.LENGTH_LONG).show();
			}
		}
		break;

		}
	}

	public void setSubmitButton(boolean isInserting){

		if(isInserting){
			txtInsertMode.setText(getResources().getString(R.string.mode_monthly_insert));
			txtInsertMode.setBackgroundResource(R.drawable.input_color_box_2);
		}else{
			txtInsertMode.setText(getResources().getString(R.string.mode_entry_modify));
			txtInsertMode.setBackgroundResource(R.drawable.input_color_box_6);	
		}
		txtInsertMode.getBackground().setAlpha(192);
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
