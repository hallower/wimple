package me.blog.imhallower.wimple;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import me.blog.imhallower.wimple.impl.IWimpleResponseListener;
import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Section;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class WimpleActivity extends FragmentActivity implements
ActionBar.TabListener {

	private static final String LOG_TAG = "WimpleActivity";
	private static final int PIN_NUMBER_REQUEST = 1379;

	private static final WimpleImpl wimple = WimpleImpl.getInstance();
	private static Handler mainHandler;
	private Context context;

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;


	public static final class CommandID {

		private CommandID() {}

		public static final int CMD_BASE = 10000;

		public static final int EXIT = CMD_BASE + 1;
		public static final int TOAST_LONG = CMD_BASE + 3;
		public static final int TOAST_SHORT = CMD_BASE + 5;
		public static final int GET_PIN = CMD_BASE + 7;

	}

	public static void sm(int cmd, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));    
	}    

	public static void sm(int cmd, int a1, int a2, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));    
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		context = getApplicationContext();
		wimple.setApplicationContext(context);
		wimple.setResponseListener(new IWimpleResponseListener(){

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) {

				if(null == tempToken || tempToken.isEmpty()){
					// TODO : do something
					return;
				}

				sm(CommandID.GET_PIN, tempToken);				
			}

			@Override
			public void onGetAuthAccessToken(boolean status,
					Map<String, String> result) {

				if(result.isEmpty()){
					Log.e(LOG_TAG, "Auth is failed.");
					// TODO : do something
					return;
				}

				for(String key : result.keySet()){
					Log.d(LOG_TAG, "[" + key + "]" + result.get(key));
				}

				String token = result.get("token");
				String tokenSecret = result.get("token_secret");
				String userID = result.get("user_id");

				if(null == tokenSecret ||
						true == tokenSecret.isEmpty()){
					Log.e(LOG_TAG, "Auth is failed.");
					// TODO : do something
					return;
				}

				wimple.getAllSections();				
			}

			@Override
			public void onGetAllSectionReceived(boolean status, Collection<Section> list) {

				for(Section section : list){

					Log.d(LOG_TAG, section.toString());

					wimple.getAllEntries(section.getId(), "20131208", "20131201");
					wimple.getLatestEntries(section.getId(), 0);
				}
			}

			@Override
			public void onGetAllAccountReceived(boolean status, Collection<Account> list) {

				for(Account account : list){
					Log.d(LOG_TAG, account.toString());
				}
			}

			@Override
			public void onGetEntriesReceived(boolean status, Collection<Entry> list) {
				for(Entry entry : list){
					Log.d(LOG_TAG, entry.toString());
				}
			}

			@Override
			public void onGetLatestEntriesReceived(boolean status, Collection<Entry> list) {
				for(Entry entry : list){
					Log.d(LOG_TAG, entry.toString());
				}
			}				

		});
		setupHandler();

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(
				getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager
		.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setSelectedNavigationItem(position);
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}


		wimple.getTempToken();

	}


	@SuppressLint("HandlerLeak")
	private void setupHandler() {
		mainHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {

				int command = msg.what;
				Object obj = msg.obj;


				switch(command){

				case CommandID.TOAST_LONG :
					Toast.makeText(context, obj.toString(), Toast.LENGTH_LONG).show();
					break;

				case CommandID.TOAST_SHORT :
					Toast.makeText(context, obj.toString(), Toast.LENGTH_SHORT).show();
					break;

				case CommandID.GET_PIN :
				{
					Intent intent = new Intent(context, WebViewActivity.class);
					intent.putExtra("temp_token", obj.toString());
					startActivityForResult(intent, PIN_NUMBER_REQUEST);
					break;	
				}

				// to all
				default : {	
					Log.d(LOG_TAG, "Invalid Command ID=" + command);
					break;
				}

				}
				super.handleMessage(msg);
			}
		};
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {


		if(requestCode == PIN_NUMBER_REQUEST){
			if(resultCode != RESULT_OK){
				return;
			}else{
				String tempToken = data.getExtras().getString("temp_token");
				String pin = data.getExtras().getString("pin");

				if(null != tempToken &&
						null != pin){
					wimple.getAccessToken(tempToken, pin);					
				}else{
					//throw new Exception("Intent Arguemtn is invlaid, at WimpleActivity");
					Log.e(LOG_TAG, "Intent Arguemtn is invlaid, at WimpleActivity");
				}

			}
		}else{
			super.onActivityResult(requestCode, resultCode, data);	
		}		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			// Return a DummySectionFragment (defined as a static inner class
			// below) with the page number as its lone argument.
			Fragment fragment = new DummySectionFragment();
			Bundle args = new Bundle();
			args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public int getCount() {
			// Show 3 total pages.
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
			case 0:
				return getString(R.string.title_section1).toUpperCase(l);
			case 1:
				return getString(R.string.title_section2).toUpperCase(l);
			case 2:
				return getString(R.string.title_section3).toUpperCase(l);
			}
			return null;
		}
	}

	/**
	 * A dummy fragment representing a section of the app, but that simply
	 * displays dummy text.
	 */
	public static class DummySectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		public DummySectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main_dummy,
					container, false);
			TextView dummyTextView = (TextView) rootView
					.findViewById(R.id.section_label);
			dummyTextView.setText(Integer.toString(getArguments().getInt(
					ARG_SECTION_NUMBER)));
			return rootView;
		}
	}

}
