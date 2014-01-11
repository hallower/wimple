package me.blog.imhallower.wimple;

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsFragment extends Fragment implements IWimpleFragment{

	//private final static String LOG_TAG = "SettingsFragment";

	private static View view = null;
	private static Context context = null;
	private static WimpleActivity wimpleActivity = null;

	/**
	 * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
	 * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
	 */


	@Override
	public void onResume() {
		context = WimpleActivity.context;


		super.onResume();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		context = WimpleActivity.context;
		view = (LinearLayout)inflater.inflate(R.layout.fragment_settings_tab, container, false);

		TextView loggout = (TextView) view.findViewById(R.id.settings_loggout);
		loggout.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				CookieSyncManager.createInstance(context);
				CookieManager cookieManager = CookieManager.getInstance();
				cookieManager.removeAllCookie();
				
				System.runFinalizersOnExit(true);
				System.exit(0);
			}

		});
		return view;
	}

	public void handleMessage(Message msg) {

		int command = msg.what;
		//boolean booleanStatus = msg.arg1 == 1;
		//Object obj = msg.obj;

		switch(command){

		/*
		case CommandID.CONNECTED :
		 */
		default:

			break;
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
		// TODO Auto-generated method stub
	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
		this.wimpleActivity= instance;
	}




}
