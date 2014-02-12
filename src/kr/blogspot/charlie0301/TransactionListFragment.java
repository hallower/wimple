package kr.blogspot.charlie0301;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Semaphore;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.entry.EntryItemListAdapter;
import kr.blogspot.charlie0301.entry.EntryItemListView;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class TransactionListFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionFragment";
	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;

	private static View view = null;
	private static Context context = null;

	// Static reference
	private static Long monthlyDisplayAllowanceDays = 10L;	// 10 days

	// GUI
	private WeakReference<EntryItemListView> entryList;
	private WeakReference<EntryItemListAdapter> entryAdapter;

	private boolean isAllOldActivityFechted = false;
	private static final Semaphore available = new Semaphore(1);

	private int maxHeight = 0;
	private float touchBaseY = 0;
	private boolean isShowingFirstItem = false;
	private boolean isSwipeDowned = false;
	
	private String prevLastDate = "";
	private int countLastDateRequest = 0;


	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */


	@Override
	public void onResume() {
		context = WimpleActivity.context;

		// TODO : what is better? below line is duplicated running when activity restarting and after log in. 
		wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);		
		super.onResume();
	}

	public void updateItems(){
		// TODO : what is best? performance
		Log.e(LOG_TAG, "Force Refresh!!!");
		entryAdapter.get().removeAllMonthlyItem();
		wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);
		wimple.getMonthlyItems(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context;
		view = (LinearLayout)inflater.inflate(R.layout.fragment_transaction_list_tab, container, false);

		LinearLayout.LayoutParams sessionParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
		entryList = new WeakReference<EntryItemListView>((EntryItemListView)view.findViewById(R.id.entry_list_view));
		entryAdapter = new WeakReference<EntryItemListAdapter>(new EntryItemListAdapter(context));

		entryList.get().setAdapter(entryAdapter.get());
		entryList.get().setLayoutParams(sessionParams);

		entryList.get().setOnScrollListener(new OnScrollListener(){

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {

				//Log.d(LOG_TAG, "firstVisible=" + firstVisibleItem + ", visibleItemCount=" + visibleItemCount + 
				//		", totalItemcount=" + totalItemCount);

				if(firstVisibleItem == 0){
					isShowingFirstItem = true;
				}else{
					isShowingFirstItem = false;
				}

				float percentage = (((float)firstVisibleItem + (float)visibleItemCount) / (float)totalItemCount ) * 100;
				//Log.d(LOG_TAG, "Percentage=" + percentage + ", isAllOldActivityFechted=" + isAllOldActivityFechted + ", available permit=" + available.availablePermits());

				if(percentage >= 70 &&
						false == isAllOldActivityFechted &&
						true == available.tryAcquire()){

					Log.d(LOG_TAG, "Get More!!!, percentage=" + percentage);

					if(entryAdapter.get().getCount() == 0){						
						wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);	
					}else{
						Item entry = (Item) entryAdapter.get().getItem(entryAdapter.get().getCount() - 1);
						String lastDate = entry.getDateValue();

						if(false == lastDate.isEmpty()){
							lastDate = lastDate.substring(1);	
						}

						//Log.d(LOG_TAG, "1 lastDate=" + lastDate + ", prevLastDate=" + prevLastDate + ", count=" + countLastDateRequest);
						if(false == prevLastDate.isEmpty() &&
								0 == lastDate.compareTo(prevLastDate)){
							countLastDateRequest += 1;
						}
						prevLastDate = lastDate;

						if(countLastDateRequest > 3){
							Log.d(LOG_TAG, "No More!!!");
							isAllOldActivityFechted = true;
							countLastDateRequest = 4;
							return;
						}
						//Log.d(LOG_TAG, "2 lastDate=" + lastDate + ", prevLastDate=" + prevLastDate + ", count=" + countLastDateRequest);

						wimple.getAllEntries(DateFormatUtils.getServerDateString(lastDate), DateFormatUtils.getLastMonthDateString(lastDate), 0);
					}
				}
			}
		});

		entryList.get().setOnDataSelectionListener(new OnItemSelectionListener() {

			@Override
			public void onDataSelected(AdapterView<?> parent, View v, int position, long id) {
				Item item = (Item) entryAdapter.get().getItem(position);
				//Toast.makeText(context, item.toString(), Toast.LENGTH_LONG).show();
				WimpleActivity.sm(CommandID.MODIFY_ENTRY, item.getId());
			}
		});


		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);		
		maxHeight = size.y;

		entryList.get().setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {

				switch(event.getAction())
				{
				case MotionEvent.ACTION_DOWN:
					//TOUCH STARTED
					touchBaseY = event.getY();
					isSwipeDowned = false;
					return false;
				case MotionEvent.ACTION_MOVE:

					float currentY = event.getY();
					double sizeInY = touchBaseY - currentY;

					// TODO : position check
					if( sizeInY < 0 && 
							isShowingFirstItem){
						if(Math.abs(sizeInY) > ((double)maxHeight * 0.17)){
							isSwipeDowned = true;	
						}
					}
					return false;
				case MotionEvent.ACTION_CANCEL:
				case MotionEvent.ACTION_UP:
					//TOUCH COMPLETED
					if(isSwipeDowned){
						isSwipeDowned = false;
						updateItems();
						Toast.makeText(context, getResources().getString(R.string.update_items), Toast.LENGTH_SHORT).show();
					}
					return false;
				}


				return false;
			}
		});

		// TODO : remove old data
		wimple.getStoredEntries();

		return view;
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
		/*
		case CommandID.CONNECTED :
		case CommandID.UPDATE_FEEDS :

			//String sid = obj.toString();
			getFeeds();

			break;

		 */

		case CommandID.WIMPLE_LOGGIN_SUCCESS :
		case CommandID.GET_ALL_SECTION_RECEIVED :{			
			wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);
			break;
		}

		case CommandID.GET_ENTRIES_RECEIVED :{
			try{				
				if(false == booleanStatus){				
					return;
				}

				if(null == entryAdapter.get()){
					return;
				}

				Collection<Entry> list = (Collection<Entry>) obj;

				for(Entry item : list){
					entryAdapter.get().addItem(item);
					//Log.d(LOG_TAG, "Entry adding - [" + item.getId() + "] " + item.getItem() + ", " + (new Date(item.getDate())).toString());
				}
				entryAdapter.get().notifyDataSetChanged();
				break;
			}finally{
				// TODO : I cant' find how to solve this issue.
				if(available.availablePermits() < 1){
					available.release();					
				}
			}
		}


		case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED : {	
			String entryDate = (String)obj;

			if(null == entryAdapter.get()){
				return;
			}

			wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getServerDateString(entryDate, -(int)(long)(monthlyDisplayAllowanceDays)), 0);

			if(booleanStatus){
				entryAdapter.get().removeSameDatedMonthlyItem(entryDate);
			}
			wimple.getMonthlyItems(true);
		}	
		break;

		case CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED : {

			String entryDate = (String)obj;

			if(booleanStatus){
				wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getServerDateString(entryDate, -(int)(long)(monthlyDisplayAllowanceDays)), 0);				
			}
		}
		break;

		case CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED : {
			if(false == booleanStatus){				
				return;
			}

			if(null == entryAdapter.get()){
				return;
			}

			Collection<Item> list = (Collection<Item>) obj;

			for(Item item : list){

				if(monthlyDisplayAllowanceDays < DateFormatUtils.getDifferenceDays(item.getDate())){
					//Log.d(LOG_TAG, "Skip Monthly item - " + item.getItem() + ", " + (new Date(item.getDate())).toString());
					continue;
				}

				entryAdapter.get().addItem(item);
				//Log.d(LOG_TAG, "Adding Monthly item - " + item.getItem() + ", " + (new Date(item.getDate())).toString());
			}
			entryAdapter.get().notifyDataSetChanged();

		}


		}
	}

	@Override
	public void onDetach() {

		context = null;

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
