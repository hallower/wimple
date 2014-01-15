package me.blog.imhallower.wimple;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.Semaphore;

import me.blog.imhallower.wimple.WimpleActivity.CommandID;
import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.impl.util.Utils;
import me.blog.imhallower.wimple.model.Entry;
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
import android.widget.LinearLayout;

public class TransactionListFragment extends Fragment implements IWimpleFragment{

	private final static String LOG_TAG = "TransactionFragment";
	private final static WimpleImpl wimple = WimpleImpl.getInstance();
	private WimpleActivity mainActivity = null;

	private static View view = null;
	private static Context context = null;

	private WeakReference<EntryItemListView> entryList;
	private WeakReference<EntryItemListAdapter> entryAdapter;

	private boolean isAllOldActivityFechted = false;
	private final Semaphore available = new Semaphore(0);
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
				
				//Log.e(LOG_TAG, "firstVisible=" + firstVisibleItem + ", visibleItemCount=" + visibleItemCount + 
				//		", totalItemcount=" + totalItemCount);
				
				float percentage = (((float)firstVisibleItem + (float)visibleItemCount) / (float)totalItemCount ) * 100;

				//Log.e(LOG_TAG, "Percentage=" + percentage + ", available permit=" + available.availablePermits());

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
			}
		});
		
		return view;
	}


	@SuppressWarnings("unchecked")
	public void handleMessage(Message msg) {

		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		switch(command){
		/*
		case CommandID.CONNECTED :
		case CommandID.UPDATE_FEEDS :

			//String sid = obj.toString();
			getFeeds();

			break;

		 */

		//case CommandID.WIMPLE_LOGGIN_SUCCESS :
		case CommandID.GET_ALL_SECTION_RECEIVED :{
			wimple.getAllEntries(Utils.getCurrentDateString(), Utils.getLastMonthDateString(0L), 0);
			break;
		}

		case CommandID.GET_ENTRIES_RECEIVED :{
			if(false == booleanStatus){
				available.release();
				return;
			}
			
			if(null == entryAdapter.get()){
				available.release();
				return;
			}
				
			Collection<Entry> list = (Collection<Entry>) obj;
			
			if(list.isEmpty()){
				this.isAllOldActivityFechted = true;
				available.release();
				return;
			}
			
			for(Entry item : list){
				entryAdapter.get().addItem(item);				
			}
			entryAdapter.get().notifyDataSetChanged();
			available.release();
			break;
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
