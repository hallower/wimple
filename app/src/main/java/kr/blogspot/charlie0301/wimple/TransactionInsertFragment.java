package kr.blogspot.charlie0301.wimple;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.Calculator;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.Account;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.widget.AccountExpandableListAdapter;
import kr.blogspot.charlie0301.wimple.widget.DatePickerFragment;
import kr.blogspot.charlie0301.wimple.widget.DatePickerFragment.OnDateSetListener;

public class TransactionInsertFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionInsertFrag";

	private final WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;
	private View view = null;
	private Context context = null;

	private final static Calculator cal = new Calculator();
	private static int[] padRIDs = null;

	// Widget
	private AccountExpandableListAdapter leftAccountListAdapter;
	private AccountExpandableListAdapter rightAccountListAdapter;

	private TextView tvLeftAccountTitle;
	private TextView tvRightAccountTitle;
	private ExpandableListView leftAccountListView;
	private ExpandableListView rightAccountListView;

	private TextView txtAmount;
	private EditText txtTitle;
	private TextView txtInsertMode;
	private EditText txtMemo;

	private DatePickerFragment datePicker;
	private LinearLayout llInsertNotice;
	private TextView tvNoticeMessage;

	// Data
	private enum CurrentToolMode { INSERT, EDITING, MONTHLY_INSERT }

	private ArrayAdapter<Item> adapterLatestItems;
	private Item editingItem = null;
	private CurrentToolMode toolMode = CurrentToolMode.INSERT;
	//private boolean isFirstTimeForUniqueFiltering = true;

	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */

	@Override
	public void onResume() {
		context = WimpleActivity.context.get();
		initWimple();

		super.onResume();
	}

	private void initWimple() {
		Log.e(LOG_TAG, "initWimple()");

		llInsertNotice.setVisibility(View.VISIBLE);
		tvNoticeMessage.setText(getResources().getString(R.string.update_latest_items));

		wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(datePicker.getSelectedDate()), false);
		wimple.getLatestItems();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context.get();

		// Data 
		view = inflater.inflate(R.layout.fragment_transaction_insert_tab, container, false);
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

		// To show previous data during new data dispatching without any GUI display delay.
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		boolean isNeedDisableMemo = sharedPref.getBoolean(SettingsFragment.KEY_DISABLE_MEMO, false);
		if(isNeedDisableMemo){
			view.findViewById(R.id.insert_memo_window).setVisibility(View.GONE);
		}

		llInsertNotice = (LinearLayout)view.findViewById(R.id.ti_update_notification);
		llInsertNotice.setVisibility(View.INVISIBLE);
		tvNoticeMessage = (TextView)view.findViewById(R.id.ti_list_notification_text);

		setupDate();

		setupAccountLists();

		setupTitlenSubmit();

		setupLatestItems();

		setupButtons();

		cal.setListener(new Calculator.CalculatorResultListener() {
            @Override
            public void OnResultUpdate(double amount) {
                Log.e(LOG_TAG, "txt set : " + amount);
                txtAmount.setText(DateFormatUtils.getDecimalFormat().format(amount));
            }
        });

		//initWimple();

		return view;
	}

	private void setupTitlenSubmit() {
		txtAmount = (TextView) view.findViewById(R.id.insert_amount);
		txtAmount.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                switch (id) {
                    case EditorInfo.IME_ACTION_DONE:
                        setAmount(textView.getText().toString());

                        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);

                        return true;
                }
                return false;
            }
        });
		txtInsertMode  = (TextView) view.findViewById(R.id.btn_submit);
		txtInsertMode.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				txtInsertMode.setEnabled(false);

				txtAmount.setText(cal.eq().toString());

				if(!validateForms()){
					txtInsertMode.setEnabled(true);
					return;
				}

				Double amount = getAmountValue();
				/*
				if(amount < 0){
					txtInsertMode.setEnabled(true);
					Log.e(LOG_TAG, "Amount parsing error : " + txtAmount.getText());
					return;
				}*/

				if(toolMode == CurrentToolMode.EDITING){
					toolMode = CurrentToolMode.INSERT;

					/*
					 * server doesn't receive yyyyMMdd.xxxx format
					String date = editingItem.getDateValue();
					if(datePicker.isDateChanged()){
						date = DateFormatUtils.getServerDateString(datePicker.getSelectedDate());
					}
					 */

					boolean res = wimple.modifyEntry(editingItem.getId(), DateFormatUtils.getServerDateString(datePicker.getSelectedDate()), 
							leftAccountListAdapter.getSelected(), rightAccountListAdapter.getSelected(), 
							txtTitle.getText().toString(), amount, txtMemo.getText().toString());

					if(!res){
						txtInsertMode.setEnabled(true);
						WimpleActivity.sm(CommandID.TOAST_LONG, getResources().getString(R.string.modify_failed));
					}else{
						llInsertNotice.setVisibility(View.VISIBLE);
						tvNoticeMessage.setText(getResources().getString(R.string.modify_exist_item));
					}

					editingItem = null;

				}else{
					boolean res = wimple.makeEntry(datePicker.getSelectedDate(), 
							leftAccountListAdapter.getSelected(), rightAccountListAdapter.getSelected(), 
							txtTitle.getText().toString(), amount, txtMemo.getText().toString());

					if(!res){
						txtInsertMode.setEnabled(true);
						WimpleActivity.sm(CommandID.TOAST_LONG, getResources().getString(R.string.insert_failed));
					}else{
						llInsertNotice.setVisibility(View.VISIBLE);
						tvNoticeMessage.setText(getResources().getString(R.string.insert_new_item));
					}
				}
			}
		});
		setSubmitButton(toolMode);

		txtMemo = (EditText)view.findViewById(R.id.insert_memo);

		txtTitle = (EditText) view.findViewById(R.id.insert_entry_title);
		txtTitle.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String changed = s.toString().trim();
				if(changed.contains("(") &&
						changed.indexOf("(") > 0){
					changed = changed.substring(0, changed.indexOf("(") - 1);
					changed = changed.trim();
				}
				adapterLatestItems.getFilter().filter(changed);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
	}

	private void setupLatestItems() {
		List<Item> latestItems = new ArrayList<>();
		ListView listViewLatestItems = (ListView) view.findViewById(R.id.insert_frequent_items);
		adapterLatestItems = new ArrayAdapter<>(context, R.layout.list_frequent_entries, R.id.list_frequent_entry_name, latestItems);
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
		TextView[] buttons = new TextView[padRIDs.length];
		for(int i = 0; i < padRIDs.length ; i++){
			buttons[i] = (TextView) view.findViewById(padRIDs[i]);
			buttons[i].setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {

					// remove virtual keyboard
					txtTitle.clearFocus();
					txtMemo.clearFocus();

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
				}

			});
		}
	}

	private void setupDate() {
		TextView txtItemDate = (TextView) view.findViewById(R.id.insert_date);
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
		wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(datePicker.getSelectedDate()), false);
	}

    private void setAmount(String amount){
	    double amountValue;
	    amountValue = Double.parseDouble(amount.replace(",",""));
        Log.e(LOG_TAG, "setAmount : " + amountValue);
        cal.setValue(amountValue);
    }

    private void setAmount(Double amount){
        cal.setValue(amount);
    }

	private Double getAmountValue(){
		Double amount;
		try{
			amount = DateFormatUtils.getNumberFormat().parse(txtAmount.getText().toString()).doubleValue();
		}catch(Exception e){
			Log.e(LOG_TAG, "Amount parsing error : " + txtAmount.getText());
			return -1.0;
		}
		return amount;
	}

	private void selectLatestItem(int position) {
		Item selected;

		try{
			selected = adapterLatestItems.getItem(position);
			if(selected == null)
				return;
		}catch(Exception e){
			e.printStackTrace();
			Log.e(LOG_TAG, "Failed to select latest Item!!!, position=" + position);
			return;
		}

		String title = txtTitle.getText().toString();
		String inlineMemo = "";

		int pos = title.indexOf("(");
		if( pos > 0 ){
			inlineMemo = title.substring(pos);
			title = title.substring(0, pos);
		}

		if(	0 != title.compareTo(selected.getItem())){
			txtTitle.setText(selected.getItem() + inlineMemo);
			txtTitle.setSelection(txtTitle.getText().length());
		}
		setAmount(selected.getAmount());

		selectCategory(selected);
	}

	private void setEntry(Item entry) {
		txtTitle.setText(entry.getItem());
		if(entry instanceof Entry){
			Entry entryItem = (Entry) entry;
			txtMemo.setText(entryItem.getMemo());	
		}
        setAmount(entry.getAmount());
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
		if(txtTitle.getText().toString().isEmpty()){
			Log.e(LOG_TAG, "Invalid entry title.");
			WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.insert_invalid_title));
			return false;
		}

		if(txtAmount.getText().toString().isEmpty()){
			Log.e(LOG_TAG, "Invalid entry amount.");
			WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.insert_invalid_amount));
			return false;
		}

		/*
		Double amount = getAmountValue();
		if(amount <= 0){
			Log.e(LOG_TAG, "Invalid entry amount.");
			Toast.makeText(context, context.getResources().getString(R.string.insert_invalid_amount), 
					Toast.LENGTH_SHORT).show();
			return false;
		}
		 */

		if(!this.leftAccountListAdapter.isSelected()){
			Log.e(LOG_TAG, "left side account is not selected!!!");
			WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.insert_invalid_left_accounts));
			return false;
		}

		if(!this.rightAccountListAdapter.isSelected()){
			Log.e(LOG_TAG, "right side account is not selected!!!");
			WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.insert_invalid_right_accounts));
			return false;
		}
		return true;
	}

	private void clearForms(){
		txtTitle.setText("");
		txtMemo.setText("");
        setAmount(0.0);
		datePicker.setDate(Calendar.getInstance().getTimeInMillis());

		tvLeftAccountTitle.setText(getResources().getString(R.string.insert_left_accounts));
		tvRightAccountTitle.setText(getResources().getString(R.string.insert_right_accounts));
		leftAccountListAdapter.clearSelection();
		rightAccountListAdapter.clearSelection();

		if(CurrentToolMode.EDITING == toolMode){
			editingItem = null;
		}
		toolMode = CurrentToolMode.INSERT;
		setSubmitButton(toolMode);
	}

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

		case CommandID.WIMPLE_LOGGIN_SUCCESS :
			//case CommandID.GET_ALL_SECTION_RECEIVED :
		{
			initWimple();
		}
		break;

		case CommandID.GET_ALL_ACCOUNT_RECEIVED :{

			llInsertNotice.setVisibility(View.INVISIBLE);
			if(!booleanStatus){
				return;
			}

			Collection<Account> accountList = (Collection<Account>) obj;

			if(accountList.isEmpty()){
				return;
			}

			List<Account> assets = new ArrayList<>();
			List<Account> liabilities = new ArrayList<>();
			List<Account> capital = new ArrayList<>();
			List<Account> income = new ArrayList<>();
			List<Account> expenses = new ArrayList<>();

			for(Account item : accountList){

				if(0 == item.getType().compareTo("group"))
					continue;

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
				List<String> lHeader = new ArrayList<>();
				lHeader.add(getResources().getString(R.string.entry_header_asset_p));
				lHeader.add(getResources().getString(R.string.entry_header_debt_m));
				lHeader.add(getResources().getString(R.string.entry_header_capital_m));
				lHeader.add(getResources().getString(R.string.entry_header_expenses));

				Map<String, List<Account>> lChild = new HashMap<>();
				lChild.put(lHeader.get(0), assets);
				lChild.put(lHeader.get(1), liabilities);
				lChild.put(lHeader.get(2), capital);
				lChild.put(lHeader.get(3), expenses);

				String selectedID = leftAccountListAdapter.getSelected().getId();

				leftAccountListAdapter.clear();
				leftAccountListAdapter.setData(lHeader, lChild);
				leftAccountListAdapter.notifyDataSetChanged();

				if(!selectedID.isEmpty())
					leftAccountListAdapter.setSelected(selectedID);

				for(int i = 0; i < leftAccountListAdapter.getGroupCount() ; i++)
					leftAccountListView.expandGroup(i);
			}

			{
				List<String> rHeader = new ArrayList<>();
				rHeader.add(getResources().getString(R.string.entry_header_asset_m));
				rHeader.add(getResources().getString(R.string.entry_header_debt_p));
				rHeader.add(getResources().getString(R.string.entry_header_capital_p));
				rHeader.add(getResources().getString(R.string.entry_header_income));

				Map<String, List<Account>> rChild = new HashMap<>();
				rChild.put(rHeader.get(0), assets);
				rChild.put(rHeader.get(1), liabilities);
				rChild.put(rHeader.get(2), capital);
				rChild.put(rHeader.get(3), income);

				String selectedID = rightAccountListAdapter.getSelected().getId();

				rightAccountListAdapter.clear();
				rightAccountListAdapter.setData(rHeader, rChild);
				rightAccountListAdapter.notifyDataSetChanged();	

				if(!selectedID.isEmpty())
					rightAccountListAdapter.setSelected(selectedID);

				for(int i = 0; i < rightAccountListAdapter.getGroupCount() ; i++)
					rightAccountListView.expandGroup(i);
			}
			break;			
		}

		case CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED :
		case CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED :
		{
			// do nothing
		}
		break;

		case CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED :
		{
			llInsertNotice.setVisibility(View.INVISIBLE);

			if(booleanStatus){
				adapterLatestItems.clear();
                adapterLatestItems.getFilter().filter("");
				adapterLatestItems.addAll((List<Item>) obj);
				adapterLatestItems.notifyDataSetChanged();
			}
		}
		break;

		case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED :
		{	
			String entryDate = (String)obj;

			llInsertNotice.setVisibility(View.INVISIBLE);

			Log.e(LOG_TAG, "GET_MAKE_ENTRY_RESPONSE_RECEIVED entryDate=" + entryDate);
			if(booleanStatus){
				WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.insert_success));
				clearForms();
				wimple.getLatestItems(true);
				wimple.getMonthlyItems(true);
			}else{
				WimpleActivity.sm(CommandID.TOAST_LONG, getResources().getString(R.string.insert_failed));
			}

			txtInsertMode.setEnabled(true);
		}	
		break;

		case CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM : {

			if(null == obj ||
					!(obj instanceof Item))
				return;

			// Modifying 
			Item item = (Item)obj;

			if(item.getId().isEmpty()){
				toolMode = CurrentToolMode.MONTHLY_INSERT;
			}else{
				toolMode = CurrentToolMode.EDITING;
			}
			setSubmitButton(toolMode);

			editingItem = item;
			setEntry(item);

			if(CurrentToolMode.EDITING == toolMode){
				WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.entry_modify_notice));
			}else{
				WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.month_item_modify_notice));
			}			
		}
		break;

		case CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED : {

			llInsertNotice.setVisibility(View.INVISIBLE);

			if(booleanStatus){
				WimpleActivity.sm(CommandID.TOAST_SHORT, getResources().getString(R.string.modify_success));
				clearForms();
			}else{
				WimpleActivity.sm(CommandID.TOAST_LONG, getResources().getString(R.string.modify_failed));
			}
			txtInsertMode.setEnabled(true);
		}
		break;

		}
	}

	public void setSubmitButton(CurrentToolMode mode){

		switch(mode){

		case INSERT :
			txtInsertMode.setText(getResources().getString(R.string.mode_entry_insert));
			txtInsertMode.setBackgroundResource(R.drawable.input_color_box_2);	
			break;

		case EDITING :
			txtInsertMode.setText(getResources().getString(R.string.mode_entry_modify));
			txtInsertMode.setBackgroundResource(R.drawable.input_color_box_6);	
			break;

		case MONTHLY_INSERT :
			txtInsertMode.setText(getResources().getString(R.string.mode_monthly_insert));
			txtInsertMode.setBackgroundResource(R.drawable.input_color_box_2);
			break;
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
	public void setActivityInstance(WimpleActivity instance) {
		mainActivity = instance;
	}
}
