package kr.blogspot.charlie0301;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.Semaphore;

import org.apache.http.cookie.SM;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.entry.EntryItemListAdapter;
import kr.blogspot.charlie0301.entry.EntryItemListView;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.Utils;
import kr.blogspot.charlie0301.model.Entry;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.LinearLayout;
import android.widget.Toast;

public class TransactionListFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionFragment";
	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;

	private static View view = null;
	private static Context context = null;

	private WeakReference<EntryItemListView> entryList;
	private WeakReference<EntryItemListAdapter> entryAdapter;

	private boolean isAllOldActivityFechted = false;
	private static final Semaphore available = new Semaphore(1);
	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */


	@Override
	public void onResume() {
		context = WimpleActivity.context;

		// TODO : what is better? below line is duplicated running when activity restarting and after log in. 
		wimple.getAllEntries(Utils.getCurrentDateString(), Utils.getLastMonthDateString(0L), 0);		
		super.onResume();
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

				float percentage = (((float)firstVisibleItem + (float)visibleItemCount) / (float)totalItemCount ) * 100;

				//Log.d(LOG_TAG, "Percentage=" + percentage + ", isAllOldActivityFechted=" + isAllOldActivityFechted + ", \navailable permit=" + available.availablePermits());

				if(percentage >= 70 &&
						false == isAllOldActivityFechted &&
						true == available.tryAcquire()){
					Log.d(LOG_TAG, "Get More!!!, percentage=" + percentage);
					if(entryAdapter.get().getCount() == 0){						
						wimple.getAllEntries(Utils.getCurrentDateString(), Utils.getLastMonthDateString(0L), 0);	
					}else{
						Entry entry = (Entry) entryAdapter.get().getItem(entryAdapter.get().getCount() - 1);
						Long lastDate = entry.getDate();
						wimple.getAllEntries(Utils.getServerDateString(lastDate), Utils.getLastMonthDateString(lastDate), 0);
					}					
				}
			}
		});

		entryList.get().setOnDataSelectionListener(new OnItemSelectionListener() {

			@Override
			public void onDataSelected(AdapterView<?> parent, View v, int position, long id) {
				Entry item = (Entry) entryAdapter.get().getItem(position);
				//Toast.makeText(context, item.toString(), Toast.LENGTH_LONG).show();
				WimpleActivity.sm(CommandID.MODIFY_ENTRY, item.getId());
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
			wimple.getAllEntries(Utils.getCurrentDateString(), Utils.getLastMonthDateString(0L), 0);
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

				if(list.isEmpty()){
					this.isAllOldActivityFechted = true;
					return;
				}
				this.isAllOldActivityFechted = false;

				for(Entry item : list){
					entryAdapter.get().addItem(item);				
				}
				entryAdapter.get().notifyDataSetChanged();
				break;				
			}finally{
				// TODO : I cant' find how to solve this issue.
				if(available.availablePermits() < 1){
					available.release();					
				}
				//Log.d(LOG_TAG, "available permit=" + available.availablePermits());
			}
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
